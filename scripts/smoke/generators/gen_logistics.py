"""Carrier, Leave Cutter, Knight and Pump.

Run: python scripts/smoke/generators/gen_logistics.py  (rewrites scripts/smoke/scenarios/robots-logistics.json)
"""
import os, sys

sys.path.insert(0, os.path.dirname(__file__))
from robotrig import *  # noqa: F401,F403

OUT = os.path.join(os.path.dirname(__file__), "..", "scenarios", "robots-logistics.json")

S = []
S.append({"label": "load chunks", "do": "cmd", "run": ["gamemode creative @p", "?forceload add {X-16} {Z-8} {X+16} {Z+12}"]})
S.append({"do": "tp", "at": [0, 0, -7], "yaw": 0, "pitch": 25})
S.append({"do": "wait", "ms": 3000})
S.append({"label": "clear and floor", "do": "cmd", "run": [
    "difficulty easy", "time set 6000", "?gamerule doDaylightCycle false", "?gamerule advance_time false", "weather clear",
    "?kill @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..200]",
    "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..200]",
    "?kill @e[type=minecraft:husk,x={X},y={Y},z={Z},distance=..200]",
    "?fill {X-16} {Y} {Z-8} {X+16} {Y+6} {Z+10} minecraft:air",
    "fill {X-16} {Y-1} {Z-8} {X+16} {Y-1} {Z+10} minecraft:smooth_stone"]})
S.append({"label": "carrier supply (16 cobblestone, Provide Items)", "do": "cmd", "run": station(-10, 0, ["minecraft:cobblestone 16"], "buildcraft:station.provide_items", (-11, -1, -1, -5, 3, 1), zaction="buildcraft:robot.load_unload_area")})
S.append({"label": "carrier destination (Accept Items)", "do": "cmd", "run": station(-6, 0, [], "buildcraft:station.accept_items")})
S.append({"label": "leaves for the leave cutter", "do": "cmd", "run": ["fill {X-3} {Y+1} {Z+4} {X+1} {Y+2} {Z+6} minecraft:oak_leaves[persistent=true]"]})
S.append({"label": "leave cutter home (shears)", "do": "cmd", "run": station(-1, 0, ["minecraft:shears"], "buildcraft:station.provide_items", (-3, 0, 3, 1, 3, 7))})
S.append({"label": "husk pen", "do": "cmd", "run": [
    "fill {X+3} {Y} {Z+3} {X+8} {Y+1} {Z+8} minecraft:oak_fence", "fill {X+4} {Y} {Z+4} {X+7} {Y+1} {Z+7} minecraft:air",
    "summon minecraft:husk {X+5} {Y} {Z+5} {PersistenceRequired:1b}", "summon minecraft:husk {X+6} {Y} {Z+6} {PersistenceRequired:1b}"]})
S.append({"label": "knight home (sword)", "do": "cmd", "run": station(5, 0, ["minecraft:iron_sword"], "buildcraft:station.provide_items", (3, -1, 3, 8, 3, 8))})
S.append({"label": "water pool (2x2, regenerating)", "do": "cmd", "run": ["fill {X+12} {Y-1} {Z+5} {X+13} {Y-1} {Z+6} minecraft:water"]})
S.append({"label": "pump home: tank under a stone fluid pipe, Accept Fluids", "do": "cmd",
          "run": station(12, 0, [], "buildcraft:station.accept_fluids", (11, -2, -1, 14, 1, 7), pipe="stone_fluid", below="buildcraftunofficial:tank", beh="")})
S.append({"do": "wait", "ms": 1500})
S.append({"name": "rig3-empty", "do": "shot", "look": "left to right (screen right to left): carrier supply + destination stations, leaf block, husk pen with two husks, water pool by a tank"})
S += deploy("deploy carrier at the supply station", -10, 0, "boardRobotCarrier")
S.append(echo("count after carrier", "execute if entity @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..30]"))
S += deploy("deploy leave cutter", -1, 0, "boardRobotLeaveCutter")
S.append(echo("count after leave cutter", "execute if entity @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..30]"))
S.append(echo("held after leave cutter deploy", "data get entity @p SelectedItem"))
S += deploy("deploy knight", 5, 0, "boardRobotKnight")
S.append(echo("count after knight", "execute if entity @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..30]"))
S += deploy("deploy pump", 12, 0, "boardRobotPump")
S.append({"do": "hold", "item": "minecraft:air"})
S.append(echo("robots deployed (expect 4)", "execute if entity @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..30]"))
S.append({"label": "viewing pillar", "do": "cmd", "run": ["setblock {X+1} {Y+3} {Z-7} minecraft:glass"]})
S.append({"do": "tp", "at": [1, 4, -7], "yaw": 0, "pitch": 30})
S.append({"do": "wait", "ms": 10000})
S.append({"name": "rig3-10s", "do": "shot", "look": "carrier carrying cobblestone between stations, leave cutter at the leaves with shears, knight fighting husks, pump at the pool"})
S.append({"do": "wait", "ms": 30000})
S.append({"name": "rig3-40s", "do": "shot", "look": "leaves cut, husks dead, pump going back and forth to the tank; no robot stuck"})
S.append({"do": "wait", "ms": 30000})
S.append({"name": "rig3-70s", "do": "shot", "look": "steady state; robots docked or working; floor intact"})
S.append(echo("cobblestone reached the destination chest", "data get block {X-6} {Y} {Z} Items"))
S.append(echo("supply chest left", "data get block {X-10} {Y} {Z} Items"))
S.append(echo("husks left (expect fail)", "execute if entity @e[type=minecraft:husk,x={X+5},y={Y},z={Z+5},distance=..6]"))
S.append(echo("leaves left (count; destructive)", "fill {X-3} {Y+1} {Z+4} {X+1} {Y+2} {Z+6} minecraft:air replace minecraft:oak_leaves"))
S.append(echo("tank contents (expect water)", "data get block {X+12} {Y} {Z}"))
S.append(echo("pump robot tank", "data get entity @e[type=buildcraftunofficial:robot,x={X+12},y={Y+2},z={Z},distance=..12,limit=1,sort=nearest] tank"))
for b in ["Carrier", "LeaveCutter", "Knight", "Pump"]:
    S.append(echo("%s alive" % b, 'execute if entity @e[type=buildcraftunofficial:robot,nbt={boardId:"buildcraftunofficial:boardRobot%s"}]' % b))
S.append(echo("pump robot full", 'data get entity @e[type=buildcraftunofficial:robot,nbt={boardId:"buildcraftunofficial:boardRobotPump"},limit=1]'))
S.append(echo("pump station pipe", "data get block {X+12} {Y+1} {Z}"))
S.append(echo("robots alive (expect 4)", "execute if entity @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..30]"))
S.append({"label": "back to peaceful", "do": "cmd", "run": ["difficulty peaceful"]})
d = {"name": "robots-logistics",
     "description": "Carrier (station to station), Leave Cutter, Knight (husks, which do not burn in daylight) and Pump (water pool into a tank through an Accept Fluids station). Generated by scratch gen script; edit freely.",
     "base": [0, 150, 80], "steps": S}
write(sys.argv[1] if len(sys.argv) > 1 else OUT, d)
print(len(S), 'steps')
