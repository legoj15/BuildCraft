"""BuildCraft machines over a real connection, on a server + two clients from mp.py (#209: the Forge networking paths).

  python -I scene_machines.py <node> <phase>
phases: prep (a floor, a Tank, a Stone Engine, a creative engine feeding kinesis pipes), tank, engine, all (prep, tank, engine).

What it exercises across the wire, none of which a single-player run touches:
  tank    Worker pours a water bucket into the Tank (an item-handler path on the server), opens its GUI (the menu's opening
          data and the container's fluid sync, to Worker), then Watcher, who never touched it, opens the same GUI (the tank's
          fluid reaches a second client). The server's own `data get` must hold the water.
  engine  Worker opens the Stone Engine's GUI (opening data again, another menu type); Watcher films the creative engine
          pushing power along kinesis pipes (the pipe holder's and the engine's block-entity sync, and the flow's own
          packets) before and after Worker's GUI is open.
Every `check` is a recorded pass/fail (exit 1 when one fails); the shots land in build/smoke-mp/<node>/shots as
<A|B>-<name>.png for a human to look at: Watcher is A, Worker is B.
"""
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mp
from scene_lib import Scene

NODE = sys.argv[1]
PHASE = sys.argv[2]
S = Scene(NODE)
X, Y, Z = S.X, S.Y, S.Z
cmd, b, shot, tp, aim, hold = S.cmd, S.b, S.shot, S.tp, S.aim, S.hold
A, B = 'Watcher', 'Worker'


def prep():
    cmd('forceload add {X-12} {Z-12} {X+12} {Z+12}')
    for c in ['difficulty peaceful', 'time set 6000', 'weather clear', 'gamemode creative Watcher', 'gamemode creative Worker',
              'kill @e[type=minecraft:item]', 'fill {X-12} {Y} {Z-12} {X+12} {Y+8} {Z+12} minecraft:air',
              'fill {X-12} {Y-1} {Z-12} {X+12} {Y-1} {Z+12} minecraft:smooth_stone',
              'gamerule advance_time false', 'gamerule doDaylightCycle false']:  # one of the two fails per node
        cmd(c)
    cmd('setblock {X+2} {Y} {Z} buildcraftunofficial:tank',
        'setblock {X+6} {Y} {Z} buildcraftunofficial:engine_stone',
        # the line8 power rig, on the north side: a creative engine on a redstone block feeding a wood and a stone kinesis pipe
        'setblock {X-3} {Y-1} {Z-6} minecraft:redstone_block',
        'setblock {X-3} {Y} {Z-6} buildcraftunofficial:engine_creative[facing=east]',
        'setblock {X-2} {Y} {Z-6} buildcraftunofficial:pipe_holder',
        'data merge block {X-2} {Y} {Z-6} {pipe:{def:"buildcraftunofficial:wood_power"}}',
        'setblock {X-1} {Y} {Z-6} buildcraftunofficial:pipe_holder',
        'data merge block {X-1} {Y} {Z-6} {pipe:{def:"buildcraftunofficial:stone_power"}}',
        'setblock {X} {Y} {Z-6} buildcraftunofficial:power_tester')
    time.sleep(1.5)


def tank():
    # Worker in front of the Tank, Watcher two blocks to the side of it.
    tp(B, X + 2.5, Y, Z + 2.5, 180, 20)
    tp(A, X + 5.5, Y, Z + 1.5, 120, 15)
    time.sleep(1.5)
    hold(B, 'B', 'minecraft:water_bucket')
    hit = aim('B', X + 2.5, Y + 0.5, Z + 0.5)
    S.check('tank: Worker aims at the Tank', (hit or {}).get('type') == 'BLOCK', str(hit)[:120])
    shot('A', 'tank-empty', 'Tank before the pour, seen by Watcher')
    S.say('pour', b('B', 'POST', '/worldclick', {'action': 'right_click'}))
    time.sleep(1.0)
    hold(B, 'B', 'minecraft:air')
    nbt = cmd('data get block {X+2} {Y} {Z}')
    S.check('tank: the server holds the water', 'water' in nbt, nbt[:160])
    aim('A', X + 2.5, Y + 0.5, Z + 0.5)
    shot('A', 'tank-water', 'Watcher sees water in a Tank it never touched')
    # Worker opens the GUI: the menu's opening data crosses the connection, then the fluid syncs into the container.
    aim('B', X + 2.5, Y + 0.5, Z + 0.5)
    S.say('open (Worker)', b('B', 'POST', '/worldclick', {'action': 'right_click'}))
    S.check('tank: the GUI opens on Worker', S.wait_screen('B', 'GuiTank'), S.screen_class('B'))
    time.sleep(1.0)
    shot('B', 'tank-gui-worker', 'Tank GUI with the water gauge, as Worker pours-then-opens')
    b('B', 'POST', '/screenclose')
    # Watcher opens it cold.
    aim('A', X + 2.5, Y + 0.5, Z + 0.5)
    S.say('open (Watcher)', b('A', 'POST', '/worldclick', {'action': 'right_click'}))
    S.check('tank: the GUI opens on Watcher', S.wait_screen('A', 'GuiTank'), S.screen_class('A'))
    time.sleep(1.0)
    shot('A', 'tank-gui-watcher', 'the same Tank GUI on the client that never poured: the water must show here too')
    b('A', 'POST', '/screenclose')


def engine():
    # Worker at the Stone Engine, Watcher facing the creative engine and its pipes.
    tp(B, X + 6.5, Y, Z + 2.5, 180, 25)
    tp(A, X - 1.5, Y, Z - 2.5, 180, 15)
    time.sleep(2.0)
    aim('A', X - 1.5, Y + 0.5, Z - 6.5)
    shot('A', 'engine-rig-0', 'creative engine pushing power along the kinesis pipes (Watcher)')
    time.sleep(2.0)
    shot('A', 'engine-rig-1', 'a moment later: the flow has moved')
    aim('B', X + 6.5, Y + 0.5, Z + 0.5)
    S.say('open engine (Worker)', b('B', 'POST', '/worldclick', {'action': 'right_click'}))
    S.check('engine: the GUI opens on Worker', S.wait_screen('B', 'ScreenEngineStone'), S.screen_class('B'))
    time.sleep(1.0)
    shot('B', 'engine-gui-worker', 'Stone Engine GUI (fuel slot, heat gauge)')
    shot('A', 'engine-rig-2', 'Watcher\'s view while Worker has the engine GUI open')
    b('B', 'POST', '/screenclose')
    reply = cmd('execute if block {X-3} {Y} {Z-6} buildcraftunofficial:engine_creative')
    S.check('engine: the creative engine is still there', 'passed' in reply.lower() or 'test passed' in reply.lower(), reply[:120])


if PHASE in ('prep', 'all'):
    prep()
if PHASE in ('tank', 'all'):
    tank()
if PHASE in ('engine', 'all'):
    engine()
if PHASE == 'cmd':
    cmd(*sys.argv[3:])
S.finish()
