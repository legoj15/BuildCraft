"""Two-client multiplayer smoke: a dedicated server plus two McDevBridge clients on one Stonecutter node.

The single-player runner (Invoke-InClientSmoke.ps1) cannot show what ANOTHER player sees: swings, particles and
entity sync that travel server -> other clients. This boots `:<node>:runServer` (game dir `run-smoke/mp-server`,
offline, RCON on, both players opped, flat world) and two `runClient`s (`run-smoke/mp-a` as Watcher, `run-smoke/mp-b`
as Worker) that quick-play into it, and records each client's bridge url+token as it starts (bridge.json only ever
holds the newest client). Commands go through RCON; per-client actions through each bridge. Output and process ids
live in build/smoke-mp/<node>/. Gradle runs with --no-daemon, so `stop` takes down the trees it started; on the server
machine its last resort ends every java process of the remote checkout (the server's JVM and Gradle's alike), so
nothing of that checkout may keep running there mid-run. A kill there that cannot be confirmed keeps the run's state
and smoke lock until a later `stop` confirms it, so nothing else starts on that checkout meanwhile.
From Git Bash set MSYS_NO_PATHCONV=1, or bridge paths like /state get rewritten.

One run per machine: `server` takes the smoke lock (~/.mcdevbridge/smoke.lock, the file Invoke-InClientSmoke.ps1 takes
too, so the two refuse each other) held by the server's Gradle process, `client` checks it is still this run's, and `stop`
releases it; a lock whose process is gone is taken over. Each client's pid and start time are pinned along with its
bridge, so `b` and the scene scripts fail loudly once that client is gone instead of answering for another one. The
ssh tunnel's pid is pinned the same way; a tunnel key the older mp.py wrote (a bare pid) is dropped, never killed.

  python -I mp.py setup <node>            game dirs, server.properties, ops, bridge jar + options from run-smoke
  python -I mp.py server <node>            start the dedicated server, wait for "Done"
  python -I mp.py client <node> A|B        start a client that quick-plays into the server, capture its bridge, wait for the join
  python -I mp.py rcon <node> "<cmd>" ...  server console commands
  python -I mp.py b <node> A|B <METHOD> <path> [json]
  python -I mp.py stop <node>              stop clients + server (a remote server: every java of that checkout)
  python -I mp.py stop <node> --forget     drop the run's state + smoke lock, killing no server/client (after a refused stop)

Loaders: NeoForge nodes pass the run switches through mp.init.gradle (an init script, so build.gradle.kts stays untouched);
a MinecraftForge node (`loader=forge`) and a Fabric node (`loader=fabric`) read the build's own `-PbcUser`,
`-PbcQuickPlayServer` and `-PbcRunDir` (bc.forge and bc.fabric: on Fabric the folder moves the run's working directory
too, so the server's files and both clients' logs land under run-smoke/).

The server may run on another machine, so the two clients join over a real network; when that machine's firewall does not let the
server's JVM in (a Java-21 line on Server1), `server` forwards the game and RCON ports over ssh (`ensure_reachable`) and the clients
and RCON use this machine's loopback, `stop` ending the tunnel. Environment (read by every command):
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
# The scratch world's generator-settings: the classic superflat stack (bedrock, two dirt, grass) on the plains biome,
# the shape vanilla's own flat preset builds. An empty value is rejected by the FlatLevelGeneratorSettings codec, which
# logs 'No key layers in MapLike[{}]' on every boot and falls back to its default layers (#230).
FLAT_GENERATOR_SETTINGS = ('{"layers":[{"block":"minecraft:bedrock","height":1},'
                           '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
                           '"biome":"minecraft:plains"}')
# Ports of the real servers on Server1 and of this machine's own: this script never takes them.
FORBIDDEN_PORTS = (25565, 25566, 25575)
if PORT in FORBIDDEN_PORTS or RCON_PORT in FORBIDDEN_PORTS:
    sys.exit('refusing MP_PORT/MP_RCON_PORT %d/%d: 25565, 25566 and 25575 belong to real servers' % (PORT, RCON_PORT))


def server_ip(node=None):
    """Where this run's clients and RCON connect, as an IPv4 address (the game connects the same one RCON does, whatever
    name resolution would pick): SERVER_HOST, or this machine's loopback when `node`'s run has an ssh tunnel to the server
    machine (see ensure_reachable). A tunnel key whose process is gone, or that cannot be proved ours, is an error and
    not a silent fall-back to SERVER_HOST: that address is the one the tunnel exists to avoid."""
    if node and REMOTE and load_state(node).get('tunnel'):
        pid, start, pinned = tunnel_state(node)
        if not pinned or not same_process(proc_start(pid), start):
            raise SystemExit('the ssh tunnel of the run on %s (pid %s) is gone or not provably this rig\'s, so this '
                             'machine\'s loopback no longer reaches the server and SERVER_HOST (%s) is the address the '
                             'firewall blocks: run `python -I mp.py stop %s` and `server` again'
                             % (node, pid, SERVER_HOST, node))
        return '127.0.0.1'
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


def server_process_alive(st):
    """True when a process the run recorded as its server is provably alive: the server JVM when a local run pinned it
    (serverJvm, #230), else the Gradle wrapper / ssh poller — each pinned by its recorded start time, never by the pid
    number alone (the OS hands a dead process's number to something else; state written before start times were recorded
    stays lenient, the way same_process already is)."""
    for key in ('serverJvm', 'server'):
        pid = st.get(key)
        if pid and same_process(proc_start(pid), st.get(key + 'Start')):
            return True
    return False


def lock_require(node):
    """For a command that joins a run: the lock must be this run's. When it is free (a run started before locks
    existed, or one whose holder died) take it, held by the server's Gradle process; a live holder that is not
    this run's refuses."""
    held = lock_read()
    if held is not None and lock_alive(held):
        if lock_is_ours(held, node):
            return
        raise SystemExit(lock_busy_message(held))
    st = load_state(node)
    if not st.get('server') or not server_process_alive(st):
        raise SystemExit('no server is running for %s: start it first (python -I mp.py server %s)' % (node, node))
    lock_acquire(node, st['server'])


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


def run_switches(node, role, side=None):
    """The Gradle -P switches that put a run where mp.py wants it. `role` is 'server' (game dir run-smoke/mp-server) or
    'client' (game dir run-smoke/mp-<side>, the player's name, quick-play into the server). NeoForge reads them through
    mp.init.gradle (mpUser, mpServer, mpServerDir); a MinecraftForge or Fabric node reads the bc* switches its own build
    provides (bcUser, bcQuickPlayServer, bcRunDir in bc.forge and bc.fabric alike)."""
    own = node_loader(node) in ('forge', 'fabric')
    if role == 'server':
        return [f'-PbcRunDir={SERVER_DIR}'] if own else [f'-PmpServerDir={SERVER_DIR}']
    target = f'{server_ip(node)}:{PORT}'
    if own:
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
        'generator-settings': FLAT_GENERATOR_SETTINGS,
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
    (remote_wait_done reports why); False when neither happened in `launch_seconds`, or when the poller ended without ever
    reaching Server1 because its own ssh failed (`remote script on server1 exited 255` before any status line: a transient
    refusal, seen once when Server1's sshd was busy). `Invoke-OnServer1.ps1` also sometimes sits for good in its ssh step with no
    run directory on Server1 (trap 16 of McDevBridge's tools/remote/README.md, seen twice in one session): its output ends at
    the `=== run <id> ===` header. The caller ends that poller (this script's own process) and starts again. The log is
    re-read once an exit is seen: a poller that wrote `exited 255` and ended between the loop's read and its exit check is
    a failed ssh (False, retry), not a launch."""
    log = os.path.join(HERE, node, 'server.remote.log')
    end = time.time() + launch_seconds
    while time.time() < end:
        try:
            with open(log, encoding='utf-8', errors='replace') as f:
                text = f.read()
        except OSError:
            text = ''
        if '-- t+' in text:
            return True
        if proc_start(pid) is None:
            try:
                with open(log, encoding='utf-8', errors='replace') as f:
                    text = f.read()  # its last lines may have landed after the read above
            except OSError:
                pass
            return 'exited 255' not in text  # the poller ended on its own: a failed ssh is retried, anything else is reported
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


# -- Reaching a server whose JVM the server machine's firewall does not let in ---------------------------------------------
# Server1's inbound allow rules name specific java.exe paths (the Microsoft JDK 25.0.2.10 yes; the Adoptium 21 that Gradle's
# toolchain provisions for the Java-21 lines, 1.21.1 and 1.21.11, no: measured 2026-11-22, the clients' connect and RCON both
# timed out). Changing a firewall is not this script's business, so a server that is up (`Done`) yet unreachable on its two
# ports is reached through `ssh -L`: both ports forwarded to the server machine's loopback (the firewall does not filter
# loopback), the clients and RCON then connect to this machine's own 127.0.0.1:<port>. Traffic still crosses the LAN, inside ssh.

def tcp_open(host, port, timeout=6):
    try:
        socket.create_connection((host, port), timeout=timeout).close()
        return True
    except OSError:
        return False


def tunnel_state(node):
    """The run's ssh tunnel from state as (pid, start, pinned). `pinned` is True only for the form this rig writes now:
    a pid with its start time, like a bridge's. A key the older mp.py wrote (a bare pid) carries no start time, so the
    number cannot be proved this rig's tunnel (it may have been reused): nothing kills it or connects through it, and
    `stop` drops the key. (None, None, False) when state has no tunnel key."""
    t = load_state(node).get('tunnel')
    if isinstance(t, dict) and isinstance(t.get('pid'), int):
        return t['pid'], t.get('start'), True
    if isinstance(t, int) and t:
        return t, None, False
    return None, None, False


def tunnel_alive(node):
    """True when the run's ssh tunnel is a live process this rig started: the pid pinned by its recorded start time
    (a pid the OS has handed to another process answers False), the way a bridge is pinned."""
    pid, start, pinned = tunnel_state(node)
    return pinned and same_process(proc_start(pid), start)


def tunnel_start(node):
    """`ssh -N -L` for the game and RCON ports; pid and start time go into the run's state, so `stop` ends only a
    process provably this rig's. ExitOnForwardFailure: a local port already in use ends ssh at once instead of leaving
    a tunnel that forwards nothing."""
    log = open(os.path.join(HERE, node, 'tunnel.log'), 'w')
    cmd = ['ssh', '-N', '-o', 'BatchMode=yes', '-o', 'ExitOnForwardFailure=yes', '-o', 'ServerAliveInterval=15',
           '-o', 'ServerAliveCountMax=4']
    for p in (PORT, RCON_PORT):
        cmd += ['-L', f'127.0.0.1:{p}:127.0.0.1:{p}']
    cmd.append(os.environ.get('MP_REMOTE_SSH') or 'server1')
    proc = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                            creationflags=subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW)
    log.close()
    st = load_state(node)
    st['tunnel'] = {'pid': proc.pid, 'start': proc_start(proc.pid)}
    save_state(node, st)
    return proc


def tunnel_log_tail(node):
    """The last of the tunnel's own log, for the message when ssh will not forward."""
    try:
        with open(os.path.join(HERE, node, 'tunnel.log'), encoding='utf-8', errors='replace') as f:
            return f.read().strip()[-300:]
    except OSError:
        return ''


def ensure_reachable(node):
    """After the remote server printed `Done`: the game port answers directly AND RCON logs in there (a port that merely
    accepts is not yet this run's server), or a tunnel is started onto ports nothing else listens on and RCON logs
    in through it (the server itself answering, not just the local end accepting)."""
    direct = server_ip(node)
    if tcp_open(direct, PORT):
        try:
            Rcon(node).s.close()  # logs in on the direct address: the server answered
        except (Exception, SystemExit) as e:
            raise SystemExit('%s:%d answers but RCON cannot log in there (%r): not this run\'s server, or its RCON is '
                             'broken; not forwarding past it to another host' % (direct, PORT, e))
        print('server reachable directly at %s:%d' % (direct, PORT))
        return
    busy = [p for p in (PORT, RCON_PORT) if tcp_open('127.0.0.1', p)]
    if busy:
        # a listener that was there before ssh would answer for the server, so the readiness loop below would lie
        raise SystemExit('cannot start the ssh tunnel: 127.0.0.1:%s already accept%s a connection, which is not this '
                         'run\'s forward; free %s or set MP_PORT/MP_RCON_PORT to free ones'
                         % (' and 127.0.0.1:'.join(str(p) for p in busy), 's' if len(busy) > 1 else '',
                            'those ports' if len(busy) > 1 else 'that port'))
    print('server on %s is up but %s:%d / %d do not answer (no inbound firewall rule for its java.exe); '
          'forwarding both ports over ssh' % (SERVER_HOST, direct, PORT, RCON_PORT), flush=True)
    proc = tunnel_start(node)
    end = time.time() + 30
    while time.time() < end and proc.poll() is None and not all(tcp_open('127.0.0.1', p, 2) for p in (PORT, RCON_PORT)):
        time.sleep(1)
    if proc.poll() is None and not all(tcp_open('127.0.0.1', p, 2) for p in (PORT, RCON_PORT)):
        tunnel_stop(node)
        raise SystemExit('the ssh tunnel (pid %d) still accepted nothing on 127.0.0.1:%d / %d after 30 s: %s'
                         % (proc.pid, PORT, RCON_PORT, tunnel_log_tail(node) or 'see build/smoke-mp/%s/tunnel.log' % node))
    if proc.poll() is not None:  # whatever accepted on the ports, ssh itself is gone: the forward is not ours
        tunnel_stop(node)
        raise SystemExit('the ssh tunnel ended at once (exit %s): %s' % (proc.returncode, tunnel_log_tail(node)))
    try:
        Rcon(node).s.close()  # logs in through the tunnel: the server answered
    except (Exception, SystemExit) as e:
        tunnel_stop(node)  # a forward that cannot reach this run's server is no tunnel to keep
        raise SystemExit('the ssh tunnel is up (127.0.0.1:%d) but RCON cannot log in through it (%r): %s'
                         % (PORT, e, tunnel_log_tail(node) or 'see build/smoke-mp/%s/tunnel.log' % node))
    print('server reachable through the ssh tunnel at 127.0.0.1:%d (ssh pid %d)' % (PORT, proc.pid))


def tunnel_stop(node):
    """End the run's ssh tunnel — only a process provably this rig's, the pid pinned by its recorded start time as a
    bridge's is (a pid the OS has handed to another process in the meantime is left alone), and never a bare-pid key
    the older mp.py wrote."""
    pid, start, pinned = tunnel_state(node)
    if not pinned:
        return
    kill_pinned('tunnel', pid, start)


def find_server_jvm():
    """The pid of the process listening on this run's two ports — the server JVM, which on this machine nothing else may
    hold (the real servers' ports are refused) — from netstat. The recorded `server` pid is the Gradle wrapper's (local)
    or the ssh poller's (remote) while the JVM is a grandchild, so a kill that loses the tree would miss it; its own pid
    is recorded too (#230). None when netstat cannot be asked, or names nothing or more than one pid: `stop` then keeps
    to the wrapper tree kill it always had."""
    try:
        r = subprocess.run(['netstat', '-ano', '-p', 'tcp'], capture_output=True, text=True, timeout=30)
    except (subprocess.TimeoutExpired, OSError):
        return None
    pids = set()
    for line in r.stdout.splitlines():
        m = re.match(r'\s*TCP\s+\S+:(\d+)\s+\S+\s+LISTENING\s+(\d+)\s*$', line)
        if m and int(m.group(1)) in (PORT, RCON_PORT):
            pids.add(int(m.group(2)))
    if len(pids) != 1:
        return None
    pid = next(iter(pids))
    return pid if proc_start(pid) is not None else None


def record_server_jvm(node, st, tries=6):
    """Pin the local server JVM's pid and start time into the run's state (find_server_jvm), retried a few times: the
    ports follow the `Done` line by moments. Remote runs record nothing — their JVM is on the server machine, where the
    straggler kill sweeps by checkout, not by a pid this script could pin."""
    for _ in range(tries):
        pid = find_server_jvm()
        if pid is not None:
            st['serverJvm'] = pid
            st['serverJvmStart'] = proc_start(pid)
            save_state(node, st)
            print('server jvm pid', pid)
            return
        time.sleep(5)
    print('no single process was listening on the run\'s ports; `stop` falls back to the Gradle tree kill for the '
          'server JVM')


def server(node):
    lock_wait_if_busy()
    st = load_state(node)
    if REMOTE and st.get('remote_kill_unconfirmed'):
        # a stop could not confirm its remote kill, so a server of that checkout may still be running there: never a
        # second one on it (#230; server() used not to look at the flag). Repeat the kill and refuse until confirmed.
        confirmed, line = remote_kill_stragglers()
        print(line)
        if not confirmed:
            raise SystemExit('the remote kill of the earlier run on %s could still not be confirmed, so no server may '
                             'be started there; the run\'s state and the smoke lock are still kept: run '
                             '`python -I mp.py stop %s` again' % (REMOTE, node))
        del st['remote_kill_unconfirmed']  # confirmed now: nothing of that checkout is running
        save_state(node, st)
    if REMOTE:
        if not os.path.exists(os.path.join(HERE, node, 'rcon.pw')) and not os.environ.get('MP_RCON_PASSWORD'):
            raise SystemExit('run `python -I mp.py setup %s` first (it writes the RCON password)' % node)
        nonce = uuid.uuid4().hex
        st['server'] = remote_start(node, nonce)
        st['serverStart'] = proc_start(st['server'])
    else:
        log = os.path.join(node_dir(node), SERVER_DIR, 'logs', 'latest.log')
        if os.path.exists(log):
            os.remove(log)
        st['server'] = gradle(node, [f':{node}:runServer'] + run_switches(node, 'server'), 'server.gradle.log')
        st['serverStart'] = proc_start(st['server'])
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
            confirmed, line = remote_kill_stragglers()  # killing the poller must not leave a started remote run going
            print(line)
            if not confirmed:
                # never two servers on one remote checkout: an unconfirmed kill keeps the state, AND the poller with
                # the smoke lock it holds, for `stop` (which repeats the kill and clears both once it is confirmed)
                st['remote_kill_unconfirmed'] = True
                save_state(node, st)
                raise SystemExit('the remote kill of the stalled launch could not be confirmed, so nothing may be '
                                 'started on that checkout yet; the run\'s state and the smoke lock were kept (the '
                                 'poller stays, holding it): run `python -I mp.py stop %s`, which repeats the kill and '
                                 'clears them once it is confirmed' % node)
            subprocess.run(['taskkill', '/PID', str(st['server']), '/T', '/F'], capture_output=True)
            if attempt == 3:
                raise SystemExit('the remote launch stalled 3 times; see build/smoke-mp/%s/server.remote.log' % node)
            print('the remote launch stalled (no status line); starting it again (attempt %d of 3)' % (attempt + 1), flush=True)
            lock_release(node)
            st['server'] = remote_start(node, nonce)
            st['serverStart'] = proc_start(st['server'])
            save_state(node, st)
            lock_acquire(node, st['server'])
        m = remote_wait_done(node, st['server'], 3600, nonce)  # a fresh checkout configures every node first
    else:
        m = wait_log(log, r'Done \(([0-9.]+)s\)!', 900)
    print('server up', m.group(0), ('on ' + SERVER_HOST) if REMOTE else '')
    if REMOTE:
        ensure_reachable(node)
    else:
        record_server_jvm(node, st)


def wait_player_joined(node, side, timeout=300):
    """Wait until the server's player list names this client's player (`list` over RCON). The bridge's port only says
    the client booted, not that it quick-played in (#230): until the join, a command naming the player answers 'No
    player was found' and a scene's first such command would pass silently. Bounded, and the failure names both logs."""
    name = USERS[side]
    cl = os.path.join(node_dir(node), f'run-smoke/mp-{side.lower()}', 'logs', 'latest.log')
    end = time.time() + timeout
    reply = ''
    while time.time() < end:
        try:
            r = Rcon(node)  # a fresh connection each round: a stale one would fail the whole wait on one hiccup
            reply = r.cmd('list', timeout=10)
            r.s.close()
        except Exception:
            reply = ''
        if re.search(r'\b%s\b' % re.escape(name), reply):
            print('player', name, 'on the server:', reply.strip())
            return
        time.sleep(3)
    raise SystemExit('%s never joined the server within %d s (the last `list` said %r); the client log is %s, the '
                     'server log is %s' % (name, timeout, reply.strip(), cl,
                                           os.path.join(node_dir(node), SERVER_DIR, 'logs', 'latest.log')))


def client(node, side):
    lock_require(node)
    st = load_state(node)
    d = os.path.join(node_dir(node), f'run-smoke/mp-{side.lower()}')
    log = os.path.join(d, 'logs', 'latest.log')
    if os.path.exists(log):
        os.remove(log)
    st['client' + side] = gradle(node, [f':{node}:runClient'] + run_switches(node, 'client', side), f'client{side}.gradle.log')
    st['client' + side + 'Start'] = proc_start(st['client' + side])
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
    wait_player_joined(node, side)
    print('client', side, 'bridge', port, 'pid', b['pid'], '-', USERS[side], 'joined')


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
        self.s = socket.create_connection((server_ip(node), RCON_PORT), timeout=30)
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


def kill_pinned(label, pid, recorded):
    """Tree-kill `pid` only while it is provably the process this rig recorded (pid and a readable, matching start
    time): a state file outlives a crashed run, and Windows hands a dead process's pid number to something else.
    False, without killing, for a record with no start time (state an older mp.py wrote): that pid may still be the
    rig's, so a caller that must not lose the process keeps what it recorded (#230). False too when the process is
    still provably there 5 s after taskkill (a slow exit, or one taskkill could not end). True when it ended, was
    already gone, or the number now belongs to another process (a start time that differs, or that cannot be read: a
    rig process is readable by its own user), since the recorded process is then gone too."""
    now = proc_start(pid)
    if now is None:
        print('%s (pid %s) is already gone' % (label, pid))
        return True
    if not recorded or recorded <= 0:
        print('%s: pid %s has no recorded start time, so it is not provably a process of this rig: not killed'
              % (label, pid))
        return False
    if now < 0 or not same_process(now, recorded):
        print('%s (pid %s) is already gone: the number now belongs to another process, which is left alone'
              % (label, pid))
        return True
    r = subprocess.run(['taskkill', '/PID', str(pid), '/T', '/F'], capture_output=True, text=True)
    print(label, (r.stdout or r.stderr).strip())
    for _ in range(10):  # taskkill can fail (access denied) or return before the process is gone
        left = proc_start(pid)
        if left is None or left < 0 or not same_process(left, recorded):  # gone, as the check before the kill reads it
            return True
        time.sleep(0.5)
    print('%s (pid %s) survived taskkill' % (label, pid))
    return False


def forget(node):
    """`stop <node> --forget`: drop the run's state and release the smoke lock without killing any recorded server,
    client or bridge (only the rig's own pinned ssh tunnel is ended). The way out
    of a stop that keeps refusing a pid it cannot prove is the rig's (state an older mp.py wrote, no start times):
    the caller has ended those processes by hand, or checked they are not the rig's (#230). Refuses while an earlier
    remote kill is unconfirmed or a pinned server is provably still running: a plain stop handles both."""
    st = load_state(node)
    if st.get('remote_kill_unconfirmed'):
        # the poller still holds the lock for a remote server that may be running: plain stop repeats the kill and is
        # the only command that can confirm it; forgetting here would let a second server start on the same checkout
        raise SystemExit('the remote kill of an earlier stop is still unconfirmed: run `python -I mp.py stop %s` '
                         '(without --forget), which repeats it' % node)
    for k in ('serverJvm', 'server'):
        start = st.get(k + 'Start')
        now = proc_start(st[k]) if st.get(k) else None
        if start and start > 0 and now and now > 0 and same_process(now, start):  # an unreadable start proves nothing
            # provably the rig's and still running: a plain stop can end it, so forgetting it would only let a second
            # server start on this checkout beside it
            raise SystemExit("%s (pid %s) is provably this run's and still running: run `python -I mp.py stop %s` "
                             "(without --forget)" % (k, st[k], node))
    for k in ('server', 'serverJvm', 'clientB', 'clientA'):
        if k in st:
            print('forgetting %s (pid %s): not killed' % (k, st[k]))
    for side in ('A', 'B'):
        b = st.get('bridge' + side)
        if b:
            print('forgetting bridge%s (pid %s): not killed' % (side, b.get('pid')))
    if 'tunnel' in st:
        print("ending the run's ssh tunnel (if it is provably this rig's)")
        tunnel_stop(node)  # pinned by start time, so only the rig's own tunnel: dropping the record would orphan it
    save_state(node, {})
    lock_release(node)


def stop(node):
    st = load_state(node)
    t = st.get('tunnel')
    if isinstance(t, int) and t:  # the older mp.py's bare tunnel pid: not provably this rig's (the number may be reused)
        print('dropping the tunnel key the older mp.py wrote (pid %d, no start time): it is not provably this rig\'s '
              'tunnel, so it is not killed' % t)
        del st['tunnel']
    refused = {}  # key -> pid of each recorded process that could not be proved ended
    for side in ('B', 'A'):
        b = st.get('bridge' + side)
        if b and not kill_pinned('bridge' + side, b['pid'], b.get('startFileTime')):
            refused['bridge' + side] = b['pid']
    stopped = False
    try:
        r = Rcon(node)
        print('rcon stop:', r.cmd('stop'))
        stopped = True
    except (Exception, SystemExit) as e:  # SystemExit too: refused login, a missing rcon.pw or a gone tunnel, so no stop was asked
        print('rcon stop failed (%s): the server was not asked to stop' % e)
    remote_unconfirmed = False
    if REMOTE:
        poller = st.get('server')
        unconfirmed_before = bool(st.pop('remote_kill_unconfirmed', None))  # an earlier stop's kill was never confirmed
        if unconfirmed_before:
            print('an earlier stop could not confirm the remote kill: repeating it whatever the local poller says '
                  '(the earlier stop left that poller running on purpose, holding the smoke lock)')
        # the server's Gradle ends, the remote command with it, then the poller returns. A poller with no recorded start
        # time (older state) is waited on too: skipping it would let the straggler kill below end a server mid-save.
        if stopped and poller and not unconfirmed_before:
            end = time.time() + 240
            while time.time() < end and same_process(proc_start(poller), st.get('serverStart')):
                time.sleep(5)
        if remote_fetch_log(node):  # keep the server's side of the run beside the clients' logs
            shutil.copy2(remote_server_log(node), os.path.join(HERE, node, 'server-remote-latest.log'))
        still_up = poller and same_process(proc_start(poller), st.get('serverStart'))
        if not stopped or unconfirmed_before or still_up:  # did not stop on RCON, or a kill still needs confirming
            print('remote server did not stop; ending the java processes of this checkout on the server machine')
            confirmed, line = remote_kill_stragglers()
            print(line)
            remote_unconfirmed = not confirmed
    elif stopped:
        # wait for the server JVM to finish its save and exit before anything is force-killed: a fixed sleep cut slow
        # saves short (86 s has been seen on the remote box). Without a pinned JVM that is still alive (none recorded, or
        # a stale pin from an earlier run), keep the old 15 s grace.
        jvm, jvm_start = st.get('serverJvm'), st.get('serverJvmStart')
        if jvm and jvm_start and jvm_start > 0 and same_process(proc_start(jvm), jvm_start):
            end = time.time() + 240
            while time.time() < end and same_process(proc_start(jvm), jvm_start):
                time.sleep(2)
        else:
            time.sleep(15)
    for k in ('serverJvm', 'clientB', 'clientA', 'server'):
        if k not in st:
            continue
        if k == 'server' and remote_unconfirmed:
            # the ssh poller is what the smoke lock names: while the remote kill is unconfirmed it is not ended (while
            # it lives it keeps the lock for this run, the way the docstring promises — stop used to end it first,
            # which left the kept lock stale and a second server able to start, #230)
            print('server (pid %s) not killed, and the smoke lock not released: the remote kill is unconfirmed' % st[k])
            continue
        if k == 'server' and refused:
            # the wrapper (local) or poller (remote) is what the smoke lock names: killing it while the lock is kept for
            # a process not proved ended would leave the lock with a dead holder, which the next `server` takes over
            print('server (pid %s) not killed, so the kept smoke lock stays held: %s not proved ended'
                  % (st[k], ', '.join(refused)))
            refused[k] = st[k]
            continue
        if not kill_pinned(k, st[k], st.get(k + 'Start')):
            refused[k] = st[k]
    if remote_unconfirmed:
        # the remote kill could not be confirmed: keep the server's half of the state and the smoke lock for a retry,
        # marked so the retry forces the kill again instead of trusting the poller the first stop kept
        st['remote_kill_unconfirmed'] = True
    if remote_unconfirmed or refused:
        # ... and the same for a kill that was refused because the pid was not provably this rig's (state written
        # before start times were recorded, or a reused number): the recorded process may still be alive, so the state
        # is not dropped and the lock not released over it (#230)
        save_state(node, {k: st[k] for k in ('server', 'serverStart', 'serverJvm', 'serverJvmStart',
                                             'clientB', 'clientBStart', 'clientA', 'clientAStart',
                                             'bridgeA', 'bridgeB', 'tunnel', 'remote_kill_unconfirmed') if k in st})
        if remote_unconfirmed:
            print('the remote server may still be running; the run\'s state and the smoke lock were kept: '
                  'run `python -I mp.py stop %s` again' % node)
        if refused:
            raise SystemExit('%s: a recorded process could not be proved ended (pid %s may still be running), so the '
                             'run\'s state and the smoke lock were kept; end it by hand if it is this rig\'s, then run '
                             '`python -I mp.py stop %s --forget` to drop the state and release the lock (after any '
                             'unconfirmed remote kill is confirmed by a plain stop)'
                             % (', '.join(refused), ', '.join(str(v) for v in refused.values()), node))
        return
    if 'tunnel' in st:
        tunnel_stop(node)
    save_state(node, {})
    lock_release(node)


if __name__ == '__main__':
    a = sys.argv[1:]
    if not a or a[0] not in ('setup', 'server', 'client', 'rcon', 'b', 'stop'):
        print('usage: python -I mp.py setup <node> | server <node> | client <node> A|B | rcon <node> "<cmd>" ... | '
              'b <node> A|B <METHOD> <path> [json] | stop <node> [--forget]   (the docstring at the top of mp.py explains each)')
        sys.exit(0 if a[:1] in (['-h'], ['--help']) else 2)
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
        if len(a) < 2 or a[1].startswith('-'):
            sys.exit('usage: python -I mp.py stop <node> [--forget]')
        if a[2:] == ['--forget']:
            forget(a[1])
        elif a[2:]:
            sys.exit('stop takes only --forget after the node, not %s' % ' '.join(a[2:]))
        else:
            stop(a[1])
