"""What another player sees (26.3 port V2d, docs/port-26.3.md B6 and B7), on a server + two clients from mp.py.

  python -I scene_swing_particles.py <node> <phase>
phases, in order: prep (rigs), deploy (Worker places three robots), watchrobots (Watcher films the robots-live rig
working), wrench (Worker wrenches a creative engine, Watcher films the arm), robotclick (Worker puts a cap on an idle
robot, then sneak-wrenches it off), dismantle (the second half alone), mine (Worker mines a gold pipe in survival
under Mining Fatigue while Watcher's crosshair rests on the stone beside it), cmd "<rcon command>"...
Shots land in build/smoke-mp/<node>/shots as <A|B>-<name>.png; grids.py tiles them for a 26.3 vs 26.2 comparison.

The held items carry a 40-tick swing animation (interact_animation on 26.3, swing_animation before), so the swing
is slow enough for the screenshot burst to catch it on the watching client; the plain-wrench burst checks the
default 6-tick swing too. Expected: Watcher sees every Worker swing on both nodes; while Worker mines, 26.3 shows
gold pipe-sprite crack particles on Watcher's screen (26.3 sends remote destroy-progress level events 2019/2020;
26.2 shows none), never invisible or vanilla terrain particles; both show the break particles and the drop.
"""
import json, os, sys, time, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mp

NODE = sys.argv[1]
PHASE = sys.argv[2]
OUT = os.path.join(mp.HERE, NODE, 'shots')
os.makedirs(OUT, exist_ok=True)
X, Y, Z = 0, -60, 0
ANIM = 'minecraft:interact_animation' if tuple(map(int, NODE.split('.'))) >= (26, 3) else 'minecraft:swing_animation'
R = mp.Rcon()
log = open(os.path.join(mp.HERE, NODE, 'scene.log'), 'a', encoding='utf-8')


def say(*a):
    s = ' '.join(str(x) for x in a)
    print(s)
    log.write(time.strftime('%H:%M:%S ') + s + '\n')
    log.flush()


def coords(t):
    import re
    def rep(m):
        base = {'X': X, 'Y': Y, 'Z': Z}[m.group(1)]
        off = int(m.group(2) or 0)
        return str(base + off)
    return re.sub(r'\{([XYZ])([+-]\d+)?\}', rep, t)


def cmd(*cs):
    for c in cs:
        c = coords(c)
        r = R.cmd(c)
        say('rcon>', c[:160], '=>', r[:200])


def b(side, method, path, body=None, timeout=60):
    return mp.bridge(NODE, side, method, path, body, timeout)


def shot(side, name, look=''):
    p = os.path.join(OUT, f'{side}-{name}.png').replace('\\', '/')
    b(side, 'POST', '/screenshot', {'path': p})
    say('shot', side, name, '-', look)
    return p


def tp(user, x, y, z, yaw=0, pitch=0):
    cmd(f'tp {user} {x} {y} {z} {yaw} {pitch}')


def look(side, yaw, pitch):
    b(side, 'POST', '/look', {'mode': 'absolute', 'yaw': yaw, 'pitch': pitch})
    time.sleep(0.2)


def aim(side, tx, ty, tz):
    p = b(side, 'GET', '/state')['player']['pos']
    dx, dy, dz = tx - p['x'], ty - (p['y'] + 1.62), tz - p['z']
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    look(side, yaw, pitch)
    hit = b(side, 'GET', '/state').get('hit')
    say('aim', side, round(yaw, 1), round(pitch, 1), json.dumps(hit)[:200])
    return hit


def hold(user, side, item):
    cmd(f'item replace entity {user} weapon.mainhand with {item}')
    want = item.split('[')[0]
    for _ in range(50):
        h = b(side, 'GET', '/state')['player'].get('selectedItem') or {}
        if (want == 'minecraft:air' and h.get('empty')) or h.get('id') == want:
            return
        time.sleep(0.1)
    say('WARN hold not seen client-side', side, item)


def burst(side, name, n, gap):
    for i in range(n):
        shot(side, f'{name}-{i}')
        time.sleep(gap)


A, B = 'Watcher', 'Worker'

if PHASE == 'prep':
    cmd('forceload add {X-12} {Z-12} {X+12} {Z+12}')
    for c in ['difficulty peaceful', 'time set 6000', 'weather clear', 'gamemode creative Watcher',
              'gamemode creative Worker', 'kill @e[type=buildcraftunofficial:robot]',
              'kill @e[type=minecraft:item]', 'fill {X-12} {Y} {Z-12} {X+12} {Y+8} {Z+12} minecraft:air',
              'fill {X-12} {Y-1} {Z-12} {X+12} {Y-1} {Z+12} minecraft:smooth_stone',
              'gamerule advance_time false', 'gamerule doDaylightCycle false']:  # one of the two fails per node
        cmd(c)
    # robots-live rig (lumberjack, picker, charger, tree, loot)
    rl = json.load(open(os.path.join(mp.REPO, 'scripts/smoke/scenarios/robots-live.json')))
    for i in range(4, 8):
        for c in rl['steps'][i]['run']:
            if c.startswith('?'):
                continue
            cmd(c)
    # wrench rig: creative engine on a redstone block, east of the robots
    cmd('setblock {X+8} {Y-1} {Z-4} minecraft:redstone_block',
        'setblock {X+8} {Y} {Z-4} buildcraftunofficial:engine_creative[facing=up]')
    # mining rig: a gold item pipe in front of a black wall, a stone marker block beside it
    cmd('fill {X+9} {Y} {Z+2} {X+9} {Y+2} {Z+6} minecraft:black_concrete',
        'setblock {X+8} {Y} {Z+4} buildcraftunofficial:pipe_holder',
        'data merge block {X+8} {Y} {Z+4} {pipe:{def:"buildcraftunofficial:gold_item"}}',
        'setblock {X+8} {Y} {Z+2} minecraft:stone')
    # an idle robot for the click checks: picker with nothing to pick, on a station over a chest, far west
    cmd('setblock {X-8} {Y} {Z-4} minecraft:chest',
        'setblock {X-8} {Y+1} {Z-4} buildcraftunofficial:pipe_holder',
        'data merge block {X-8} {Y+1} {Z-4} {pipe:{def:"buildcraftunofficial:wood_item",beh:{currentDir:"DOWN"}},plugs:{up:{id:"buildcraftunofficial:robot_station",data:{}}}}')

elif PHASE == 'deploy':
    # B deploys the lumberjack, the picker and the idle robot from above each station.
    for (sx, sz, board) in [(0, 0, 'boardRobotLumberjack'), (-4, 0, 'boardRobotPicker'), (-8, -4, 'boardRobotKnight')]:
        tp(B, X + sx + 0.5, Y + 2, Z + sz + 0.5, 0, 90)
        time.sleep(1.0)
        hold(B, 'B', 'buildcraftunofficial:robot[minecraft:custom_data={board:{id:"buildcraftunofficial:%s"},energy:5000000000L}]' % board)
        look('B', 0, 90)
        say('deploy', board, b('B', 'POST', '/worldclick', {'action': 'right_click'}))
        time.sleep(1.0)
    hold(B, 'B', 'minecraft:air')
    cmd('execute if entity @e[type=buildcraftunofficial:robot]')

elif PHASE == 'watchrobots':
    tp(A, X - 0.5, Y, Z - 5.5, 0, 15)
    tp(B, X + 6.5, Y, Z - 10.5, 0, 0)
    time.sleep(2)
    for t in (2, 10, 20, 30):
        shot('A', f'robots-{t:02d}s', 'robots work as seen by the other player')
        time.sleep(8 if t < 30 else 0)
    cmd('execute if block {X} {Y} {Z+6} minecraft:oak_log', 'data get block {X} {Y} {Z} Items',
        'execute if entity @e[type=minecraft:item,x={X-4},y={Y},z={Z+4},distance=..6]')

elif PHASE == 'wrench':
    # B faces the creative engine from the west; A watches B from the north-west, side-on.
    tp(B, X + 6.5, Y, Z - 3.5, -90, 0)
    tp(A, X + 3.5, Y, Z - 7.5, 0, 0)
    time.sleep(1.5)
    aim('A', X + 6.5, Y + 1.2, Z - 3.5)
    hold(B, 'B', 'buildcraftunofficial:wrench[%s={duration:40}]' % ANIM)
    aim('B', X + 8.5, Y + 0.5, Z - 3.5)
    shot('A', 'wrench-before', 'B standing, arm down')
    say('wrench click', b('B', 'POST', '/worldclick', {'action': 'right_click'}))
    burst('A', 'wrench-swing', 5, 0.25)
    shot('B', 'wrench-self')
    time.sleep(2.5)
    # second click: plain 6-tick swing, no component, as a player would see it
    hold(B, 'B', 'buildcraftunofficial:wrench')
    say('wrench click plain', b('B', 'POST', '/worldclick', {'action': 'right_click'}))
    burst('A', 'wrench-plain', 3, 0.08)

elif PHASE in ('robotclick', 'dismantle'):
    # idle robot at the far-west station: B wears a helmet onto it, then sneak-wrenches it off
    rx, ry, rz = X - 8 + 0.5, Y + 2.3, Z - 4 + 0.5
    tp(B, X - 8 + 0.5, Y, Z - 1.5, 180, 0)
    tp(A, X - 4.5, Y, Z - 2.5, 90, 0)
    time.sleep(1.5)
    aim('A', X - 8 + 0.5, Y + 1.4, Z - 1.5)
    hold(B, 'B', 'minecraft:leather_helmet[%s={duration:40}]' % ANIM)
    aim('B', rx, ry, rz)
    shot('B', 'robotclick-aim')
    if PHASE == 'robotclick':
      say('helmet click', b('B', 'POST', '/worldclick', {'action': 'right_click'}))
      burst('A', 'helmet-swing', 4, 0.3)
      time.sleep(2.5)
    hold(B, 'B', 'buildcraftunofficial:wrench[%s={duration:40}]' % ANIM)
    b('B', 'POST', '/keybind', {'keyName': 'key.sneak', 'action': 'press'})
    time.sleep(0.6)
    hit = aim('B', rx, ry + 0.35, rz)
    if (hit or {}).get('type') != 'ENTITY':
        hit = aim('B', rx, ry + 0.6, rz)
    say('dismantle click', b('B', 'POST', '/worldclick', {'action': 'right_click'}))
    burst('A', 'dismantle-swing', 4, 0.3)
    b('B', 'POST', '/keybind', {'keyName': 'key.sneak', 'action': 'release'})
    cmd('data get entity @e[type=buildcraftunofficial:robot,x={X-8},y={Y},z={Z-4},distance=..3,limit=1] wearables')

elif PHASE == 'mine':
    # B mines the gold pipe in survival under mining fatigue; A stands close, crosshair on the stone beside it.
    tp(B, X + 6.5, Y, Z + 4.5, -90, 0)
    tp(A, X + 6.5, Y, Z + 1.5, -60, 10)
    cmd('gamemode survival Worker', 'effect give Worker minecraft:mining_fatigue 120 2 true')
    hold(B, 'B', 'minecraft:air')
    time.sleep(1.5)
    aim('A', X + 8.5, Y + 0.5, Z + 2.5)
    aim('B', X + 8.5, Y + 0.5, Z + 4.5)
    shot('A', 'mine-before', 'pipe intact, no particles')
    b('B', 'POST', '/keybind', {'keyName': 'key.attack', 'action': 'press'})
    time.sleep(0.8)
    burst('A', 'mine-progress', 6, 0.35)
    shot('B', 'mine-self')
    cmd('effect clear Worker')
    time.sleep(0.25)
    burst('A', 'mine-break', 4, 0.12)
    b('B', 'POST', '/keybind', {'keyName': 'key.attack', 'action': 'release'})
    cmd('execute if block {X+8} {Y} {Z+4} buildcraftunofficial:pipe_holder', 'gamemode creative Worker')

elif PHASE == 'cmd':
    cmd(*sys.argv[3:])
