"""Two-client multiplayer smoke: a dedicated server plus two McDevBridge clients on one Stonecutter node.

The single-player runner (Invoke-InClientSmoke.ps1) cannot show what ANOTHER player sees: swings, particles and
entity sync that travel server -> other clients. This boots `:<node>:runServer` (game dir `run-smoke/mp-server`,
offline, RCON on, both players opped, flat world) and two `runClient`s (`run-smoke/mp-a` as Watcher, `run-smoke/mp-b`
as Worker) that quick-play into it, and records each client's bridge url+token as it starts (bridge.json only ever
holds the newest client). Commands go through RCON; per-client actions through each bridge. Output and process ids
live in build/smoke-mp/<node>/. Gradle runs with --no-daemon, so `stop` takes down the trees it started; on the server
machine its last resort ends every java process of the remote checkout (the server's JVM and Gradle's alike), so
nothing of that checkout may keep running there mid-run.
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
  python -I mp.py stop <node>              stop clients + server (a remote server: every java of that checkout)

Loaders: NeoForge nodes pass the run switches through mp.init.gradle (an init script, so build.gradle.kts stays untouched);
a MinecraftForge node (`loader=forge`) reads the build's own `-PbcUser`, `-PbcQuickPlayServer` and `-PbcRunDir` (bc.forge).
A Fabric node has no arm yet and is skipped with a reason (exit 0).

The server may run on another machine, so the two clients join over a real network. Environment (read by every command):
  MP_REMOTE_CHECKOUT  the Server1 checkout (C:\\Dev\\<name>, synced with tools/remote/Sync-ToServer1.ps1 -Name <name>; COMMITTED
                      state only) the server half runs in, started and read through McDevBridge's tools/remote. Unset: the server
                      runs on this machine (the default, loopback, as before). With it set `server` blocks for the whole
                      Gradle build there, which is long on a fresh checkout.
  MP_SERVER_HOST      where the clients and RCON connect (default 127.0.0.1; Server1 when MP_REMOTE_CHECKOUT is set; a name is
                      resolved to its IPv4 address). The server then binds every interface.
  MP_PORT, MP_RCON_PORT   default 25661 and 25662, the scratch ports; 25565, 25566 and 25575 are refused.
  MP_RCON_PASSWORD    default is a fixed local value; with MP_REMOTE_CHECKOUT a per-node random one is written by `setup`
                      (build/smoke-mp/<node>/rcon.pw) so a server open on the network is not left on the fixed one.
  MP_LOCK_WAIT_MINUTES  how long `server` waits for another run's smoke lock before it gives up (default 0: refuse at once).
  MCDEVBRIDGE_ROOT    the McDevBridge checkout (default E:/GitHub/McDevBridge): its tools/remote and, for `setup`, its bridge jars.
"""
import base64, ctypes, hashlib, json, os, re, secrets, shutil, socket, struct, subprocess, sys, time, urllib.request, uuid
from ctypes import wintypes


def require_dir_name(name, what):
    """A name spliced into a remote PowerShell string must be a single directory name (letters, digits, . _ -): no path
    separators, no '..', no quotes (an apostrophe would step out of a single-quoted PowerShell string)."""
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', name or ''):
        raise SystemExit('%s %r is not a single directory name (letters, digits, "." "_" "-" only): it would be spliced '
                         'into remote PowerShell strings' % (what, name))
    return name


HERE_SCRIPTS = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE_SCRIPTS, '..', '..', '..'))
HERE = os.path.join(REPO, 'build', 'smoke-mp')
PORT = int(os.environ.get('MP_PORT', 25661))
RCON_PORT = int(os.environ.get('MP_RCON_PORT', 25662))
REMOTE = os.environ.get('MP_REMOTE_CHECKOUT') or None
if REMOTE:
    require_dir_name(REMOTE, 'MP_REMOTE_CHECKOUT')  # it is spliced into remote PowerShell strings
SERVER_HOST = os.environ.get('MP_SERVER_HOST') or ('Server1' if REMOTE else '127.0.0.1')
MCDEVBRIDGE_ROOT = os.environ.get('MCDEVBRIDGE_ROOT') or 'E:/GitHub/McDevBridge'
REMOTE_TOOLS = os.path.join(MCDEVBRIDGE_ROOT, 'tools', 'remote')
REMOTE_ROOT = 'C:\\Dev'  # Invoke-OnServer1's default RemoteRoot, where the checkouts live
USERS = {'A': 'Watcher', 'B': 'Worker'}
BRIDGE = os.path.expanduser('~/.mcdevbridge/bridge.json')
LOCK = os.path.join(os.path.expanduser('~'), '.mcdevbridge', 'smoke.lock')
LOCK_OWNER = 'multiplayer/mp.py'
SERVER_DIR = 'run-smoke/mp-server'  # relative to versions/<node>, on whichever machine the server runs
# Ports of the real servers on Server1 and of this machine's own: this script never takes them.
FORBIDDEN_PORTS = (25565, 25566, 25575)
if PORT in FORBIDDEN_PORTS or RCON_PORT in FORBIDDEN_PORTS:
    sys.exit('refusing MP_PORT/MP_RCON_PORT %d/%d: 25565, 25566 and 25575 belong to real servers' % (PORT, RCON_PORT))


def server_ip():
    """SERVER_HOST as an IPv4 address (the game connects the same one RCON does, whatever name resolution would pick)."""
    return socket.getaddrinfo(SERVER_HOST, None, socket.AF_INET)[0][4][0]


def rcon_password(node):
    """The RCON password of a run: MP_RCON_PASSWORD, else (remote server) the node's generated one, else the local default."""
    if os.environ.get('MP_RCON_PASSWORD'):
        return os.environ['MP_RCON_PASSWORD']
    if REMOTE:
        try:
            with open(os.path.join(HERE, node, 'rcon.pw'), encoding='utf-8') as f:
                return f.read().strip()
        except OSError:
            raise SystemExit('no RCON password for %s yet: run `python -I mp.py setup %s` first' % (node, node))
    return 'smoke-mp-local'


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


def lock_refuse_if_ours(node, why):
    """Exit when a live run of THIS rig on this node holds the lock; `why` tells the operator what to do instead. Another
    runner's lock is not this rig's server, so it does not refuse."""
    held = lock_read()
    if held is not None and lock_alive(held) and lock_is_ours(held, node):
        raise SystemExit('a run of this rig on %s still holds %s (pid %s). %s' % (node, LOCK, held['pid'], why))


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


def node_loader(node):
    """The node's loader: the `loader=` key of its own versions/<node>/gradle.properties (what the build reads; a NeoForge
    node carries none), else the name's suffix."""
    try:
        with open(os.path.join(node_dir(node), 'gradle.properties'), encoding='utf-8') as f:
            found = next((m.group(1).lower() for m in (re.match(r'\s*loader\s*=\s*(\w+)', line) for line in f) if m), None)
    except OSError:
        found = None
    if found:
        return found
    return 'fabric' if node.endswith('-fabric') else 'forge' if node.endswith('-forge') else 'neoforge'


def mc_version(node):
    """The Minecraft line of a node name as a tuple: '26.3-forge' -> (26, 3), '1.21.1' -> (1, 21, 1)."""
    return tuple(int(x) for x in re.match(r'\d+(?:\.\d+)*', node).group(0).split('.'))


def refuse_unsupported_node(node):
    """NeoForge nodes (mp.init.gradle) and MinecraftForge nodes (bc.forge's own switches) are driven; a Fabric node has no
    arm yet and is skipped, with the reason, and exits 0 so a caller treats it as skipped, not failed. Nothing is booted
    and the smoke lock is not taken."""
    if node_loader(node) == 'fabric':
        print('SKIPPED: %s is a Fabric node; the two-client multiplayer smoke has no Fabric arm '
              '(mp.init.gradle extends the NeoForge run extension, bc.forge reads the Forge switches)' % node)
        sys.exit(0)


def run_switches(node, role, side=None):
    """The Gradle -P switches that put a run where mp.py wants it. `role` is 'server' (game dir run-smoke/mp-server) or
    'client' (game dir run-smoke/mp-<side>, the player's name, quick-play into the server). NeoForge reads them through
    mp.init.gradle (mpUser, mpServer, mpServerDir), a Forge node through bc.forge (bcUser, bcQuickPlayServer, bcRunDir)."""
    forge = node_loader(node) == 'forge'
    if role == 'server':
        return [f'-PbcRunDir={SERVER_DIR}'] if forge else [f'-PmpServerDir={SERVER_DIR}']
    target = f'{server_ip()}:{PORT}'
    if forge:
        return [f'-PbcRunDir=run-smoke/mp-{side.lower()}', f'-PbcUser={USERS[side]}', f'-PbcQuickPlayServer={target}']
    return [f'-PbcRunDir=run-smoke/mp-{side.lower()}', f'-PmpUser={USERS[side]}', f'-PmpServer={target}']


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


def server_files(node):
    """What the server's game directory holds before its first boot: name -> bytes. Offline mode, RCON on, both players
    opped, a flat creative world. A local server binds loopback; a remote one every interface (it is reached over the LAN)."""
    props = {
        'online-mode': 'false', 'server-port': PORT, 'server-ip': '' if REMOTE else '127.0.0.1', 'enable-rcon': 'true',
        'rcon.port': RCON_PORT, 'rcon.password': rcon_password(node), 'level-type': 'minecraft\\:flat',
        'gamemode': 'creative',
        'spawn-protection': '0', 'difficulty': 'peaceful', 'view-distance': '6', 'simulation-distance': '6',
        'generate-structures': 'false', 'enforce-secure-profile': 'false', 'motd': 'bc-smoke-mp', 'level-name': 'world',
        'pause-when-empty-seconds': '0', 'spawn-monsters': 'false', 'allow-flight': 'true',
        'white-list': 'false', 'max-tick-time': '-1', 'sync-chunk-writes': 'false',
    }
    ops = [{'uuid': offline_uuid(n), 'name': n, 'level': 4, 'bypassesPlayerLimit': False} for n in USERS.values()]
    return {'eula.txt': b'eula=true\n',
            'server.properties': ''.join(f'{k}={v}\n' for k, v in props.items()).encode(),
            'ops.json': json.dumps(ops, indent=1).encode()}


def find_bridge_jar(node):
    """The newest McDevBridge jar for the node (Invoke-InClientSmoke.ps1's Find-BridgeJar): MCDEVBRIDGE_ROOT's own build
    output, then its worktrees'."""
    roots = [os.path.join(MCDEVBRIDGE_ROOT, 'versions', node, 'build', 'libs')]
    wt = os.path.join(MCDEVBRIDGE_ROOT, '.claude', 'worktrees')
    if os.path.isdir(wt):
        roots += [os.path.join(wt, d, 'versions', node, 'build', 'libs') for d in os.listdir(wt)]
    found = [os.path.join(r, j) for r in roots if os.path.isdir(r) for j in os.listdir(r)
             if j.startswith('mcdevbridge-') and j.endswith(f'+mc{node}.jar') and not re.search(r'-(sources|javadoc)', j)]
    return max(found, key=os.path.getmtime) if found else None


# The keys Invoke-InClientSmoke.ps1 writes into a fresh run-smoke/options.txt: no pause on lost focus, no first-run
# screens, silent, a fixed GUI scale.
DEFAULT_OPTIONS = ['pauseOnLostFocus:false', 'onboardAccessibility:false', 'tutorialStep:none', 'skipMultiplayerWarning:true',
                   'joinedFirstServer:true', 'narrator:0', 'guiScale:2', 'renderDistance:6', 'simulationDistance:6',
                   'soundCategory_master:0.0', 'particles:0', 'lang:en_us']


def setup(node):
    nd = node_dir(node)
    os.makedirs(os.path.join(HERE, node), exist_ok=True)
    if REMOTE and not os.environ.get('MP_RCON_PASSWORD'):
        lock_refuse_if_ours(node, 'Run `python -I mp.py stop %s` first: setup writes a fresh rcon.pw, and the running server '
                            'still has the old one' % node)
        with open(os.path.join(HERE, node, 'rcon.pw'), 'w', encoding='utf-8') as f:
            f.write(secrets.token_hex(12))
    if REMOTE:
        srv = f'{REMOTE_ROOT}\\{REMOTE}\\versions\\{node}\\run-smoke\\mp-server (on the server machine, written by `server`)'
    else:
        srv = os.path.join(nd, SERVER_DIR)
        os.makedirs(srv, exist_ok=True)
        for name, data in server_files(node).items():
            with open(os.path.join(srv, name), 'wb') as f:
                f.write(data)
    smoke = os.path.join(nd, 'run-smoke')
    own = os.path.join(smoke, 'mods')
    jars = [os.path.join(own, j) for j in os.listdir(own) if j.startswith('mcdevbridge-')] if os.path.isdir(own) else []
    if not jars:  # no smoke run has prepared this node yet: take the bridge jar the way the runner would
        jar = find_bridge_jar(node)
        if not jar:
            raise SystemExit('no McDevBridge jar for %s under %s (build it: gradlew :%s:build there)' % (node, MCDEVBRIDGE_ROOT, node))
        jars = [jar]
    options = os.path.join(smoke, 'options.txt')
    for side in USERS:
        d = os.path.join(nd, f'run-smoke/mp-{side.lower()}')
        os.makedirs(os.path.join(d, 'mods'), exist_ok=True)
        for old in os.listdir(os.path.join(d, 'mods')):  # a jar of another bridge version would load beside the new one
            if old.startswith('mcdevbridge-') and old not in {os.path.basename(j) for j in jars}:
                os.remove(os.path.join(d, 'mods', old))
        for j in jars:
            shutil.copy2(j, os.path.join(d, 'mods', os.path.basename(j)))
        if os.path.isfile(options):
            shutil.copy2(options, os.path.join(d, 'options.txt'))
        else:
            with open(os.path.join(d, 'options.txt'), 'w', encoding='utf-8') as f:
                f.write('\n'.join(DEFAULT_OPTIONS) + '\n')
    print('setup ok', srv, [os.path.basename(j) for j in jars])


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


# -- The server on another machine (tools/remote of McDevBridge) -------------------------------------------------------
# `Invoke-OnServer1.ps1 -NoDesktop` runs one PowerShell script in C:\Dev\<checkout> on Server1 and polls until it ends, so
# the long-lived local process for the run's server half is that poller (its pid is what the smoke lock names and what
# `stop` waits on); the script on Server1 writes the game directory and runs `gradlew :<node>:runServer`. The server's log
# is fetched back over scp for the "Done" wait, and `stop` ends the server with RCON `stop` (Gradle then exits, the poller
# returns). Server1's checkout must be synced first and holds COMMITTED state only (Sync-ToServer1.ps1 -Name <checkout>).

def remote_script(node, nonce):
    """The PowerShell 7 text run on Server1: the game directory's files (base64, so no quoting), the old log removed and this
    run's nonce written (the waiter trusts the log only once the nonce is there, so an earlier run's `Done` never counts), then
    the server."""
    require_dir_name(node, 'the node')
    lines = ["$ErrorActionPreference = 'Stop'", f"$d = 'versions/{node}/{SERVER_DIR}'",
             "New-Item -ItemType Directory -Force -Path $d | Out-Null", "$d = (Resolve-Path $d).Path"]
    for name, data in server_files(node).items():
        lines.append(f"[IO.File]::WriteAllBytes((Join-Path $d '{name}'), [Convert]::FromBase64String('{base64.b64encode(data).decode()}'))")
    lines += ["Remove-Item (Join-Path $d 'logs/latest.log') -ErrorAction SilentlyContinue",
              f"Set-Content -LiteralPath (Join-Path $d 'mp-nonce') -Value '{nonce}' -NoNewline", "$ErrorActionPreference = 'Continue'",
              f"./gradlew.bat --no-daemon --console=plain -I scripts/smoke/multiplayer/mp.init.gradle :{node}:runServer "
              + ' '.join(run_switches(node, 'server'))]
    return '\n'.join(lines) + '\n'


def remote_server_log(node):
    """Where the Server1 server's latest.log lands locally when fetched."""
    return os.path.join(HERE, node, 'remote-fetch', 'latest.log')


def remote_fetch_log(node, rel='logs/latest.log'):
    """Copy a file of the Server1 game directory into build/smoke-mp/<node>/remote-fetch/ (Receive-FromServer1.ps1: scp).
    The previous copy is dropped first, so a stale one can never answer for the fetch that failed. True when it arrived."""
    require_dir_name(node, 'the node')
    dest = os.path.dirname(remote_server_log(node))
    os.makedirs(dest, exist_ok=True)
    local = os.path.join(dest, os.path.basename(rel))
    if os.path.exists(local):
        os.remove(local)
    remote = f'{REMOTE}/versions/{node}/{SERVER_DIR}/{rel}'
    try:
        subprocess.run(['pwsh', '-NoProfile', '-File', os.path.join(REMOTE_TOOLS, 'Receive-FromServer1.ps1'), '-Path', remote,
                        '-Destination', dest], capture_output=True, text=True, timeout=120)
    except (subprocess.TimeoutExpired, OSError):
        pass  # no log fetched: the missing file below reports that
    return os.path.exists(local)


def remote_wait_done(node, poller, timeout, nonce):
    """Poll the fetched server log for vanilla's `Done (...)!`, once the game directory carries this run's nonce. Fails early
    when the poller (the remote command) ended."""
    end = time.time() + timeout
    rx = re.compile(r'Done \(([0-9.]+)s\)!')
    while time.time() < end:
        mine = False
        if remote_fetch_log(node, 'mp-nonce'):
            with open(os.path.join(os.path.dirname(remote_server_log(node)), 'mp-nonce'), encoding='utf-8', errors='replace') as f:
                mine = f.read().strip() == nonce
        if mine and remote_fetch_log(node):
            with open(remote_server_log(node), encoding='utf-8', errors='replace') as f:
                m = rx.search(f.read())
            if m:
                return m
        if proc_start(poller) is None:
            print(remote_kill_stragglers()[1])  # the poller is gone; a remote java process may have outlived it
            tail = ''
            try:
                with open(os.path.join(HERE, node, 'server.remote.log'), encoding='utf-8', errors='replace') as f:
                    tail = ''.join(f.readlines()[-40:])
            except OSError:
                pass
            raise SystemExit('the remote server command ended before the server was up (RunId and output in '
                             'build/smoke-mp/%s/server.remote.log):\n%s' % (node, tail))
        time.sleep(10)
    raise SystemExit(f'timeout waiting for the server on {REMOTE} to print Done')


def remote_start(node, nonce, timeout_minutes=240):
    """Start the server half on Server1; returns the local poller's pid."""
    script = os.path.join(HERE, node, 'remote-server.ps1')
    with open(script, 'w', encoding='utf-8', newline='\n') as f:
        f.write(remote_script(node, nonce))
    out = open(os.path.join(HERE, node, 'server.remote.log'), 'w')
    p = subprocess.Popen(['pwsh', '-NoProfile', '-File', os.path.join(REMOTE_TOOLS, 'Invoke-OnServer1.ps1'), '-Name', REMOTE,
                          '-NoDesktop', '-TimeoutMinutes', str(timeout_minutes), '-ScriptFile', script],
                         stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                         creationflags=subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW)
    out.close()  # the child keeps its own handle
    return p.pid


def remote_launched(node, pid, launch_seconds=150):
    """True once the remote command is running (its poller's output has a `-- t+` status line) or the poller ended on its own
    (remote_wait_done reports why); False when neither happened in `launch_seconds`. `Invoke-OnServer1.ps1` sometimes sits for
    good in its ssh step with no run directory on Server1 (trap 16 of McDevBridge's tools/remote/README.md, seen twice in one
    session): its output ends at the `=== run <id> ===` header. The caller ends that poller (this script's own process) and
    starts again."""
    log = os.path.join(HERE, node, 'server.remote.log')
    end = time.time() + launch_seconds
    while time.time() < end:
        if proc_start(pid) is None:
            return True
        try:
            with open(log, encoding='utf-8', errors='replace') as f:
                if '-- t+' in f.read():
                    return True
        except OSError:
            pass
        time.sleep(3)
    return False


def remote_kill_stragglers():
    """Last resort when a server will not stop or its poller was ended: end EVERY java process whose command line names this
    checkout's directory on Server1, the server's JVM and Gradle's wrapper JVM alike, so nothing of that checkout may keep
    running there mid-run (other checkouts' processes are untouched). Ending the LOCAL poller does not stop a remote run that
    has started, so every path that kills a poller of a started run needs this. Plain ssh with an encoded command (the
    tools/remote scripts sometimes stall in their own launch). Returns (confirmed, line): confirmed is False when ssh failed
    or timed out, and then the kill could not be confirmed."""
    script = ("Get-CimInstance Win32_Process -Filter \"Name = 'java.exe'\" | Where-Object { $_.CommandLine -like '*"
              + f"{REMOTE_ROOT}\\{REMOTE}\\*" + "' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force; \"stopped $($_.ProcessId)\" }")
    enc = base64.b64encode(script.encode('utf-16-le')).decode()
    try:
        r = subprocess.run(['ssh', '-o', 'BatchMode=yes', os.environ.get('MP_REMOTE_SSH') or 'server1',
                            'powershell -NoProfile -ExecutionPolicy Bypass -EncodedCommand ' + enc],
                           capture_output=True, text=True, timeout=180)
    except subprocess.TimeoutExpired:
        return False, ('remote kill could NOT be confirmed (ssh timed out after 180 s); java processes of this checkout '
                       'may still run on the server machine')
    except OSError as e:
        return False, 'remote kill could NOT be confirmed (ssh failed to run: %s)' % e
    if r.returncode != 0:
        err = (r.stderr or '').strip().splitlines()
        why = 'ssh exit %d, %s' % (r.returncode, err[0]) if err else 'ssh exit %d, no stderr' % r.returncode
        return False, ('remote kill could NOT be confirmed (%s); java processes of this checkout may still run on the '
                       'server machine' % why)
    return True, r.stdout.strip() or '(nothing of this checkout was running)'


def lock_wait_if_busy():
    """Refuse when a live process holds the lock, but when MP_LOCK_WAIT_MINUTES is set wait that long for the holder to finish (polling every 2 s: a
    slower poll loses every gap between another run's scenarios)."""
    end = time.time() + 60 * float(os.environ.get('MP_LOCK_WAIT_MINUTES') or 0)
    announced = False
    while True:
        held = lock_read()
        if held is None or not lock_alive(held):
            return
        if time.time() >= end:
            raise SystemExit(lock_busy_message(held))
        if not announced:
            print('waiting for the smoke lock:', lock_busy_message(held), flush=True)
            announced = True
        time.sleep(2)


def server(node):
    lock_wait_if_busy()
    st = load_state(node)
    if REMOTE:
        if not os.path.exists(os.path.join(HERE, node, 'rcon.pw')) and not os.environ.get('MP_RCON_PASSWORD'):
            raise SystemExit('run `python -I mp.py setup %s` first (it writes the RCON password)' % node)
        nonce = uuid.uuid4().hex
        st['server'] = remote_start(node, nonce)
    else:
        log = os.path.join(node_dir(node), SERVER_DIR, 'logs', 'latest.log')
        if os.path.exists(log):
            os.remove(log)
        st['server'] = gradle(node, [f':{node}:runServer'] + run_switches(node, 'server'), 'server.gradle.log')
    save_state(node, st)
    try:
        lock_acquire(node, st['server'])
    except SystemExit:  # lost a race for the lock: take down what was just started
        subprocess.run(['taskkill', '/PID', str(st['server']), '/T', '/F'], capture_output=True)
        if REMOTE:
            print(remote_kill_stragglers()[1])
        raise
    if REMOTE:
        for attempt in range(1, 4):
            if remote_launched(node, st['server']):
                break
            print(remote_kill_stragglers()[1])  # killing the poller must not leave a started remote run going
            subprocess.run(['taskkill', '/PID', str(st['server']), '/T', '/F'], capture_output=True)
            if attempt == 3:
                raise SystemExit('the remote launch stalled 3 times; see build/smoke-mp/%s/server.remote.log' % node)
            print('the remote launch stalled (no status line); starting it again (attempt %d of 3)' % (attempt + 1), flush=True)
            lock_release(node)
            st['server'] = remote_start(node, nonce)
            save_state(node, st)
            lock_acquire(node, st['server'])
        m = remote_wait_done(node, st['server'], 3600, nonce)  # a fresh checkout configures every node first
    else:
        m = wait_log(log, r'Done \(([0-9.]+)s\)!', 900)
    print('server up', m.group(0), ('on ' + SERVER_HOST) if REMOTE else '')


def client(node, side):
    lock_require(node)
    st = load_state(node)
    d = os.path.join(node_dir(node), f'run-smoke/mp-{side.lower()}')
    log = os.path.join(d, 'logs', 'latest.log')
    if os.path.exists(log):
        os.remove(log)
    st['client' + side] = gradle(node, [f':{node}:runClient'] + run_switches(node, 'client', side), f'client{side}.gradle.log')
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
    def __init__(self, node):
        self.s = socket.create_connection((server_ip(), RCON_PORT), timeout=30)
        self.i = 0
        self._send(3, rcon_password(node))
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

    def cmd(self, c, timeout=None):
        """Run a console command. `timeout` (seconds) bounds the wait for the reply, else the connection's 30 s; a late
        reply after a timeout would answer the next command, so a connection that timed out must be replaced."""
        if timeout is not None:
            self.s.settimeout(timeout)
        try:
            self._send(2, c)
            return self._recv()[2]
        finally:
            if timeout is not None:
                self.s.settimeout(30)


def stop(node):
    st = load_state(node)
    for side in ('B', 'A'):
        b = st.get('bridge' + side)
        if b:
            subprocess.run(['taskkill', '/PID', str(b['pid']), '/T', '/F'], capture_output=True)
    stopped = False
    try:
        r = Rcon(node)
        print('rcon stop:', r.cmd('stop'))
        stopped = True
    except (Exception, SystemExit) as e:  # SystemExit too: refused login or missing rcon.pw, so no stop was asked
        print('rcon stop failed (%s): the server was not asked to stop' % e)
    remote_unconfirmed = False
    if REMOTE:
        poller = st.get('server')
        if stopped and poller:  # the server's Gradle ends, the remote command with it, then the poller returns
            end = time.time() + 240
            while time.time() < end and proc_start(poller) is not None:
                time.sleep(5)
        if remote_fetch_log(node):  # keep the server's side of the run beside the clients' logs
            shutil.copy2(remote_server_log(node), os.path.join(HERE, node, 'server-remote-latest.log'))
        if not stopped or (poller and proc_start(poller) is not None):  # the server did not stop on RCON
            print('remote server did not stop; ending the java processes of this checkout on the server machine')
            confirmed, line = remote_kill_stragglers()
            print(line)
            remote_unconfirmed = not confirmed
    else:
        time.sleep(15 if stopped else 0)
    for k in ('clientB', 'clientA', 'server'):
        if k in st:
            print(k, subprocess.run(['taskkill', '/PID', str(st[k]), '/T', '/F'], capture_output=True, text=True).stdout.strip())
    if remote_unconfirmed:
        # the remote kill could not be confirmed: keep the server's half of the state and the smoke lock for a retry
        save_state(node, {'server': st['server']} if 'server' in st else {})
        print('the remote server may still be running; the run\'s state and the smoke lock were kept: '
              'run `python -I mp.py stop %s` again' % node)
        return
    save_state(node, {})
    lock_release(node)


if __name__ == '__main__':
    a = sys.argv[1:]
    if not a or a[0] not in ('setup', 'server', 'client', 'rcon', 'b', 'stop'):
        print('usage: python -I mp.py setup <node> | server <node> | client <node> A|B | rcon <node> "<cmd>" ... | '
              'b <node> A|B <METHOD> <path> [json] | stop <node>   (the docstring at the top of mp.py explains each)')
        sys.exit(0 if a[:1] in (['-h'], ['--help']) else 2)
    if len(a) > 1:
        refuse_unsupported_node(a[1])
    if a[0] == 'setup':
        setup(a[1])
    elif a[0] == 'server':
        server(a[1])
    elif a[0] == 'client':
        client(a[1], a[2])
    elif a[0] == 'rcon':
        r = Rcon(a[1])
        for c in a[2:]:
            print('>', c, '\n ', r.cmd(c))
    elif a[0] == 'b':
        print(json.dumps(bridge(a[1], a[2], a[3], a[4], a[5] if len(a) > 5 else None), indent=1)[:4000])
    elif a[0] == 'stop':
        stop(a[1])
