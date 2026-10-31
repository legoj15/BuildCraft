"""Two-client multiplayer smoke: a dedicated server plus two McDevBridge clients on one Stonecutter node.

The single-player runner (Invoke-InClientSmoke.ps1) cannot show what ANOTHER player sees: swings, particles and
entity sync that travel server -> other clients. This boots `:<node>:runServer` (game dir `run-smoke/mp-server`,
offline, RCON on, both players opped, flat world) and two `runClient`s (`run-smoke/mp-a` as Watcher, `run-smoke/mp-b`
as Worker) that quick-play into it, and records each client's bridge url+token as it starts (bridge.json only ever
holds the newest client). Commands go through RCON; per-client actions through each bridge. Output and process ids
live in build/smoke-mp/<node>/. Gradle runs with --no-daemon, so `stop` takes down exactly the trees it started.
From Git Bash set MSYS_NO_PATHCONV=1, or bridge paths like /state get rewritten.

One run per machine: `server` takes the smoke lock (~/.mcdevbridge/smoke.lock, the file Invoke-InClientSmoke.ps1 takes
too, so the two refuse each other) held by the server's Gradle process, `client` checks it is still this run's, and `stop`
releases it; a lock whose process is gone is taken over. Each client's pid and start time are pinned along with its
bridge, so `b` and the scene scripts fail loudly once that client is gone instead of answering for another one.

  python -I mp.py setup <node>            game dirs, server.properties, ops, bridge jar + options from run-smoke
  python -I mp.py server <node>            start the dedicated server, wait for "Done"
  python -I mp.py client <node> A|B        start a client that quick-plays into the server, capture its bridge
  python -I mp.py rcon <node> "<cmd>" ...  server console commands
  python -I mp.py b <node> A|B <METHOD> <path> [json]
  python -I mp.py stop <node>              stop clients + server (only processes this script started)
"""
import ctypes, hashlib, json, os, re, shutil, socket, struct, subprocess, sys, time, urllib.request, uuid
from ctypes import wintypes

HERE_SCRIPTS = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE_SCRIPTS, '..', '..', '..'))
HERE = os.path.join(REPO, 'build', 'smoke-mp')
PORT, RCON_PORT, RCON_PW = 25661, 25662, 'smoke-mp-local'
USERS = {'A': 'Watcher', 'B': 'Worker'}
BRIDGE = os.path.expanduser('~/.mcdevbridge/bridge.json')
LOCK = os.path.join(os.path.expanduser('~'), '.mcdevbridge', 'smoke.lock')
LOCK_OWNER = 'multiplayer/mp.py'


# -- One run per machine, and the client pin (issue #62) ---------------------------------------------------------------
# The lock is the same file and format as scripts/smoke/SmokeGuard.ps1's Enter-SmokeLock: JSON with pid, start (that
# process's start time as a FILETIME), owner, repo, node, since, detail. A lock is stale when its pid is gone or now
# belongs to a process that started at another time. This runner cannot keep the file open (every command is its own
# process), so its holder is the run's long-lived Gradle server process.

_K32 = None


def _k32():
    global _K32
    if _K32 is None:
        k = ctypes.WinDLL('kernel32', use_last_error=True)
        k.OpenProcess.restype = wintypes.HANDLE
        k.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        k.WaitForSingleObject.restype = wintypes.DWORD
        k.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
        k.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
        k.CloseHandle.argtypes = [wintypes.HANDLE]
        _K32 = k
    return _K32


def proc_start(pid):
    """When process `pid` started, as a FILETIME (100 ns ticks since 1601 UTC, the number SmokeGuard.ps1 writes).
    None: no such process, or it has exited. -1: it runs but will not say when it started (another user's)."""
    k = _k32()
    h = k.OpenProcess(0x1000 | 0x100000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE
    if not h:
        return -1 if ctypes.get_last_error() == 5 else None  # ERROR_ACCESS_DENIED: it exists
    try:
        if k.WaitForSingleObject(h, 0) != 0x102:  # anything but WAIT_TIMEOUT: it has exited
            return None
        t = [wintypes.FILETIME() for _ in range(4)]
        if not k.GetProcessTimes(h, *[ctypes.byref(x) for x in t]):
            return -1
        return (t[0].dwHighDateTime << 32) | t[0].dwLowDateTime
    finally:
        k.CloseHandle(h)


def same_process(start, recorded):
    """`start` is proc_start() now, `recorded` what was noted down earlier: a different start time means the pid
    number has been handed to another process since."""
    if start is None:
        return False
    if start < 0 or not recorded or recorded <= 0:
        return True
    return abs(start - recorded) < 20000000  # 2 s


def lock_read():
    """The lock file's content; None when it is absent, empty (its holder is mid-write) or not a lock."""
    try:
        with open(LOCK, encoding='utf-8') as f:
            o = json.load(f)
    except (OSError, ValueError):
        return None
    return o if isinstance(o, dict) and isinstance(o.get('pid'), int) else None


def lock_alive(held):
    return same_process(proc_start(held['pid']), held.get('start'))


def lock_busy_message(held):
    detail, repo = held.get('detail'), held.get('repo')
    return ('another smoke run holds %s: %s pid %s since %s%s%s. Wait for it to finish; a lock whose process is gone '
            'is taken over automatically.' % (LOCK, held.get('owner', 'a runner'), held['pid'], held.get('since', '?'),
                                              ', ' + detail if detail else '', ' (repo %s)' % repo if repo else ''))


def lock_is_ours(held, node):
    return held.get('owner') == LOCK_OWNER and held.get('node') == node and held.get('repo') == REPO


def lock_refuse_if_busy():
    """Exit with the holder's name when a live process holds the lock (checked before anything is started)."""
    held = lock_read()
    if held is not None and lock_alive(held):
        raise SystemExit(lock_busy_message(held))


def lock_acquire(node, holder_pid):
    """Take the lock for this run, held by `holder_pid`; exit naming the holder when a live process has it."""
    os.makedirs(os.path.dirname(LOCK), exist_ok=True)
    me = {'pid': holder_pid, 'start': proc_start(holder_pid), 'owner': LOCK_OWNER, 'repo': REPO, 'node': node,
          'since': time.strftime('%Y-%m-%dT%H:%M:%S'), 'detail': 'dedicated server and two clients on ' + node}
    for attempt in range(12):
        try:
            fd = os.open(LOCK, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_BINARY)  # the atomic step
        except (FileExistsError, PermissionError):
            fd = None
        if fd is not None:
            with os.fdopen(fd, 'w', encoding='utf-8') as f:
                json.dump(me, f)
            return
        held = lock_read()
        if held is not None and lock_alive(held):
            raise SystemExit(lock_busy_message(held))
        if held is None and not os.path.exists(LOCK):
            continue  # released between our create and our read
        if held is None and attempt < 4:
            time.sleep(0.15)  # a holder between creating the file and writing it
            continue
        if held is not None and lock_read() != held:
            continue  # somebody else took it over meanwhile: judge the new one
        try:
            os.remove(LOCK)  # stale, or never completed
        except OSError:
            time.sleep(0.15)
    raise SystemExit('could not take the smoke lock at %s (neither readable nor removable); delete it by hand if no '
                     'smoke run is going' % LOCK)


def lock_require(node):
    """For a command that joins a run: the lock must be this run's. When it is free (a run started before locks
    existed, or one whose holder died) take it, held by the server's Gradle process; a live holder that is not
    this run's refuses."""
    held = lock_read()
    if held is not None and lock_alive(held):
        if lock_is_ours(held, node):
            return
        raise SystemExit(lock_busy_message(held))
    srv = load_state(node).get('server')
    if not srv or proc_start(srv) is None:
        raise SystemExit('no server is running for %s: start it first (python -I mp.py server %s)' % (node, node))
    lock_acquire(node, srv)


def lock_release(node):
    """Drop the lock when it is this run's (never one that names another run)."""
    held = lock_read()
    if held is not None and lock_is_ours(held, node):
        try:
            os.remove(LOCK)
        except OSError:
            pass


def node_dir(node):
    return os.path.join(REPO, 'versions', node)


def state_file(node):
    os.makedirs(os.path.join(HERE, node), exist_ok=True)
    return os.path.join(HERE, node, 'procs.json')


def load_state(node):
    try:
        with open(state_file(node)) as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


def save_state(node, st):
    with open(state_file(node), 'w') as f:
        json.dump(st, f, indent=1)


def offline_uuid(name):
    h = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode()).digest())
    h[6] = (h[6] & 0x0f) | 0x30
    h[8] = (h[8] & 0x3f) | 0x80
    return str(uuid.UUID(bytes=bytes(h)))


def setup(node):
    nd = node_dir(node)
    srv = os.path.join(nd, 'run-smoke/mp-server')
    os.makedirs(srv, exist_ok=True)
    with open(os.path.join(srv, 'eula.txt'), 'w') as f:
        f.write('eula=true\n')
    props = {
        'online-mode': 'false', 'server-port': PORT, 'server-ip': '127.0.0.1', 'enable-rcon': 'true',
        'rcon.port': RCON_PORT, 'rcon.password': RCON_PW, 'level-type': 'minecraft\\:flat', 'gamemode': 'creative',
        'spawn-protection': '0', 'difficulty': 'peaceful', 'view-distance': '6', 'simulation-distance': '6',
        'generate-structures': 'false', 'enforce-secure-profile': 'false', 'motd': 'bc-smoke-mp', 'level-name': 'world',
        'pause-when-empty-seconds': '0', 'spawn-monsters': 'false', 'allow-flight': 'true',
        'white-list': 'false', 'max-tick-time': '-1', 'sync-chunk-writes': 'false',
    }
    with open(os.path.join(srv, 'server.properties'), 'w') as f:
        f.writelines(f'{k}={v}\n' for k, v in props.items())
    ops = [{'uuid': offline_uuid(n), 'name': n, 'level': 4, 'bypassesPlayerLimit': False} for n in USERS.values()]
    with open(os.path.join(srv, 'ops.json'), 'w') as f:
        json.dump(ops, f, indent=1)
    smoke = os.path.join(nd, 'run-smoke')
    jars = [j for j in os.listdir(os.path.join(smoke, 'mods')) if j.startswith('mcdevbridge-')]
    for side in USERS:
        d = os.path.join(nd, f'run-smoke/mp-{side.lower()}')
        os.makedirs(os.path.join(d, 'mods'), exist_ok=True)
        for j in jars:
            shutil.copy2(os.path.join(smoke, 'mods', j), os.path.join(d, 'mods', j))
        shutil.copy2(os.path.join(smoke, 'options.txt'), os.path.join(d, 'options.txt'))
    print('setup ok', srv, jars)


def gradle(node, args, log):
    os.makedirs(os.path.join(HERE, node), exist_ok=True)
    out = open(os.path.join(HERE, node, log), 'w')
    cmd = ['cmd', '/c', os.path.join(REPO, 'gradlew.bat'), '--no-daemon', '--console=plain', '-I',
           os.path.join(HERE_SCRIPTS, 'mp.init.gradle')] + args
    p = subprocess.Popen(cmd, cwd=REPO, stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                         creationflags=subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW)
    return p.pid


def wait_log(path, pattern, timeout, since_size=0):
    end = time.time() + timeout
    rx = re.compile(pattern)
    while time.time() < end:
        try:
            with open(path, encoding='utf-8', errors='replace') as f:
                f.seek(since_size)
                m = rx.search(f.read())
                if m:
                    return m
        except FileNotFoundError:
            pass
        time.sleep(2)
    raise SystemExit(f'timeout waiting for {pattern} in {path}')


def server(node):
    lock_refuse_if_busy()
    st = load_state(node)
    log = os.path.join(node_dir(node), 'run-smoke/mp-server', 'logs', 'latest.log')
    if os.path.exists(log):
        os.remove(log)
    st['server'] = gradle(node, [f':{node}:runServer', '-PmpServerDir=run-smoke/mp-server'], 'server.gradle.log')
    save_state(node, st)
    try:
        lock_acquire(node, st['server'])
    except SystemExit:  # lost a race for the lock: take down what was just started
        subprocess.run(['taskkill', '/PID', str(st['server']), '/T', '/F'], capture_output=True)
        raise
    m = wait_log(log, r'Done \(([0-9.]+)s\)!', 900)
    print('server up', m.group(0))


def client(node, side):
    lock_require(node)
    st = load_state(node)
    d = os.path.join(node_dir(node), f'run-smoke/mp-{side.lower()}')
    log = os.path.join(d, 'logs', 'latest.log')
    if os.path.exists(log):
        os.remove(log)
    st['client' + side] = gradle(node, [f':{node}:runClient', f'-PbcRunDir=run-smoke/mp-{side.lower()}',
                                        f'-PmpUser={USERS[side]}', f'-PmpServer=127.0.0.1:{PORT}'],
                                 f'client{side}.gradle.log')
    save_state(node, st)
    m = wait_log(log, r'listening on http://127\.0\.0\.1:(\d+)', 900)
    port = int(m.group(1))
    end = time.time() + 60
    while time.time() < end:
        try:
            with open(BRIDGE) as f:
                b = json.load(f)
            if b.get('port') == port:
                break
        except Exception:
            pass
        time.sleep(0.5)
    else:
        raise SystemExit('bridge.json never matched port %d' % port)
    st = load_state(node)
    b['startFileTime'] = proc_start(b['pid'])  # pins the process, not just its pid number
    st['bridge' + side] = b
    save_state(node, st)
    print('client', side, 'bridge', port, 'pid', b['pid'])


def bridge(node, side, method, path, body=None, timeout=60):
    b = load_state(node)['bridge' + side]
    if not same_process(proc_start(b['pid']), b.get('startFileTime')):
        raise SystemExit('client %s (pid %s, port %s) is no longer running, so its pinned bridge is gone; start it again '
                         'with: python -I mp.py client %s %s' % (side, b['pid'], b['port'], node, side))
    data = None if body is None else (body if isinstance(body, str) else json.dumps(body)).encode()
    req = urllib.request.Request(b['url'] + path, data=data, method=method,
                                 headers={'X-MCDB-Token': b['token'], 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode() or 'null')


class Rcon:
    def __init__(self):
        self.s = socket.create_connection(('127.0.0.1', RCON_PORT), timeout=30)
        self.i = 0
        self._send(3, RCON_PW)
        rid, _, _ = self._recv()
        if rid == -1:
            raise SystemExit('rcon auth failed')

    def _send(self, kind, body):
        self.i += 1
        payload = struct.pack('<ii', self.i, kind) + body.encode() + b'\x00\x00'
        self.s.sendall(struct.pack('<i', len(payload)) + payload)

    def _recv(self):
        def exact(n):
            buf = b''
            while len(buf) < n:
                c = self.s.recv(n - len(buf))
                if not c:
                    raise EOFError
                buf += c
            return buf
        n = struct.unpack('<i', exact(4))[0]
        data = exact(n)
        rid, kind = struct.unpack('<ii', data[:8])
        return rid, kind, data[8:-2].decode(errors='replace')

    def cmd(self, c):
        self._send(2, c)
        return self._recv()[2]


def stop(node):
    st = load_state(node)
    for side in ('B', 'A'):
        b = st.get('bridge' + side)
        if b:
            subprocess.run(['taskkill', '/PID', str(b['pid']), '/T', '/F'], capture_output=True)
    try:
        r = Rcon()
        print('rcon stop:', r.cmd('stop'))
        time.sleep(15)
    except Exception as e:
        print('rcon stop failed', e)
    for k in ('clientB', 'clientA', 'server'):
        if k in st:
            print(k, subprocess.run(['taskkill', '/PID', str(st[k]), '/T', '/F'], capture_output=True, text=True).stdout.strip())
    save_state(node, {})
    lock_release(node)


if __name__ == '__main__':
    a = sys.argv[1:]
    if a[0] == 'setup':
        setup(a[1])
    elif a[0] == 'server':
        server(a[1])
    elif a[0] == 'client':
        client(a[1], a[2])
    elif a[0] == 'rcon':
        r = Rcon()
        for c in a[2:]:
            print('>', c, '\n ', r.cmd(c))
    elif a[0] == 'b':
        print(json.dumps(bridge(a[1], a[2], a[3], a[4], a[5] if len(a) > 5 else None), indent=1)[:4000])
    elif a[0] == 'stop':
        stop(a[1])
