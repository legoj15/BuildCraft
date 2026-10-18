"""Six zoned work boards plus a low-battery recharge.

Run: python scripts/smoke/generators/gen_boards.py  (rewrites scripts/smoke/scenarios/robots-boards.json)
"""
import os, sys

sys.path.insert(0, os.path.dirname(__file__))
from robotrig import *  # noqa: F401,F403

OUT = os.path.join(os.path.dirname(__file__), "..", "scenarios", "robots-boards.json")

S = []
S.append({"label": "load chunks", "do": "cmd", "run": ["gamemode creative @p", "?forceload add {X-16} {Z-8} {X+16} {Z+18}"]})
S.append({"do": "tp", "at": [0, 0, -7], "yaw": 0, "pitch": 25})
S.append({"do": "wait", "ms": 3000})
S.append({"label": "clear and floor", "do": "cmd", "run": [
    "difficulty peaceful", "time set 6000", "?gamerule doDaylightCycle false", "?gamerule advance_time false", "weather clear",
    "?kill @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..40]",
    "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..40]",
    "?kill @e[type=minecraft:cow,x={X},y={Y},z={Z},distance=..40]",
    "?fill {X-16} {Y} {Z-8} {X+16} {Y+6} {Z+12} minecraft:air",
    "fill {X-16} {Y-1} {Z-8} {X+16} {Y-1} {Z+12} minecraft:smooth_stone"]})
S.append({"label": "farm plot: dirt, water, a row of ripe wheat", "do": "cmd", "run": [
    "fill {X-11} {Y-1} {Z+4} {X-5} {Y-1} {Z+8} minecraft:dirt", "setblock {X-8} {Y-1} {Z+6} minecraft:water",
    "fill {X-11} {Y-1} {Z+9} {X-5} {Y-1} {Z+9} minecraft:farmland[moisture=7]",
    "fill {X-11} {Y} {Z+9} {X-5} {Y} {Z+9} minecraft:wheat[age=7]"]})
farm = (-11, -1, 4, -5, 1, 9)
S.append({"label": "farmer home (hoe)", "do": "cmd", "run": station(-11, 0, ["minecraft:iron_hoe", "minecraft:iron_hoe"], "buildcraft:station.provide_items", farm)})
S.append({"label": "planter home (seeds)", "do": "cmd", "run": station(-8, 0, ["minecraft:wheat_seeds 32"], "buildcraft:station.provide_items", farm)})
S.append({"label": "harvester home (accepts produce)", "do": "cmd", "run": station(-5, 0, [], "buildcraft:station.accept_items", farm)})
S.append({"label": "cow pen", "do": "cmd", "run": [
    "fill {X-1} {Y} {Z+3} {X+5} {Y} {Z+9} minecraft:oak_fence", "fill {X} {Y} {Z+4} {X+4} {Y} {Z+8} minecraft:air",
    "summon minecraft:cow {X+1} {Y} {Z+5}", "summon minecraft:cow {X+3} {Y} {Z+7}",
    ]})
S.append({"label": "named cow (text component)", "do": "cmd", "nodes": ["1.21.10", "1.21.11", "26.1.2", "26.2"], "run": ["summon minecraft:cow {X+2} {Y} {Z+6} {CustomName:\"Daisy\"}"]})
S.append({"label": "named cow (JSON string)", "do": "cmd", "nodes": ["1.21.1"], "run": ["summon minecraft:cow {X+2} {Y} {Z+6} {CustomName:'\"Daisy\"'}"]})
S.append({"label": "butcher home (sword)", "do": "cmd", "run": station(2, 0, ["minecraft:iron_sword"], "buildcraft:station.provide_items", (-1, -1, 3, 5, 3, 9))})
S.append({"label": "ore patch", "do": "cmd", "run": [
    "fill {X+7} {Y} {Z+5} {X+9} {Y} {Z+5} minecraft:iron_ore", "setblock {X+8} {Y+1} {Z+5} minecraft:coal_ore",
    "setblock {X+8} {Y} {Z+6} minecraft:stone"]})
S.append({"label": "miner home (pickaxe)", "do": "cmd", "run": station(8, 0, ["minecraft:iron_pickaxe"], "buildcraft:station.provide_items", (6, -1, 4, 10, 3, 7))})
S.append({"label": "dig patch", "do": "cmd", "run": [
    "setblock {X+12} {Y} {Z+5} minecraft:dirt", "setblock {X+13} {Y} {Z+5} minecraft:sand", "setblock {X+14} {Y} {Z+5} minecraft:gravel"]})
S.append({"label": "shovelman home (shovel)", "do": "cmd", "run": station(13, 0, ["minecraft:iron_shovel"], "buildcraft:station.provide_items", (11, -1, 4, 15, 3, 7))})
S.append({"label": "charging station + a low-battery robot's home", "do": "cmd", "run": [
    "setblock {X+4} {Y-1} {Z-4} minecraft:redstone_block", "setblock {X+4} {Y} {Z-4} buildcraftunofficial:engine_creative[facing=up]",
    "setblock {X+4} {Y+1} {Z-4} buildcraftunofficial:pipe_holder",
    'data merge block {X+4} {Y+1} {Z-4} {pipe:{def:"buildcraftunofficial:wood_power"},plugs:{up:{id:"buildcraftunofficial:robot_station",data:{}}}}']
    + station(-2, -4, [], "buildcraft:station.accept_items", (-3, -1, -6, -1, 2, -3))})
S.append({"do": "wait", "ms": 1500})
S.append({"name": "rig2-empty", "do": "shot", "look": "farm plot with water and ripe wheat (left), cow pen with 3 cows, iron/coal ore patch, dirt/sand/gravel patch (right); six gated stations; creative-engine charging station in front"})
S += deploy("deploy farmer", -11, 0, "boardRobotFarmer")
S += deploy("deploy planter", -8, 0, "boardRobotPlanter")
S += deploy("deploy harvester", -5, 0, "boardRobotHarvester")
S += deploy("deploy butcher", 2, 0, "boardRobotButcher")
S += deploy("deploy miner", 8, 0, "boardRobotMiner")
S += deploy("deploy shovelman", 13, 0, "boardRobotShovelman")
S += deploy("deploy low-battery picker (500 MJ, below the 2000 MJ safety line)", -2, -4, "boardRobotPicker", 500000000)
S.append({"do": "hold", "item": "minecraft:air"})
S.append(echo("robots deployed (expect 7)", "execute if entity @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..30]"))
S.append({"label": "viewing pillar", "do": "cmd", "run": ["setblock {X+1} {Y+3} {Z-7} minecraft:glass"]})
S.append({"do": "tp", "at": [1, 4, -7], "yaw": 0, "pitch": 30})
S.append({"do": "wait", "ms": 10000})
S.append({"name": "rig2-10s", "do": "shot", "look": "robots out working: farmer tilling, harvester at the wheat, butcher in the pen, miner at the ores, shovelman at the patch; the low-battery picker on the charging station"})
S.append({"do": "wait", "ms": 25000})
S.append({"name": "rig2-35s", "do": "shot", "look": "tilled farmland, seedlings planted, wheat row harvested, unnamed cows gone but Daisy alive, ores mined, dig patch dug"})
S.append({"do": "wait", "ms": 40000})
S.append({"name": "rig2-75s", "do": "shot", "look": "steady state; no robot stuck mid-air; nothing outside the zones dug or tilled (smooth-stone floor intact)"})
S.append(echo("named cow spared (expect pass)", "execute if entity @e[type=minecraft:cow,name=Daisy]"))
S.append(echo("unnamed cows left (expect fail)", "execute if entity @e[type=minecraft:cow,x={X+2},y={Y},z={Z+6},distance=..8,name=!Daisy]"))
S.append(echo("iron ore left (expect fail)", "execute if block {X+8} {Y} {Z+5} minecraft:iron_ore"))
S.append(echo("plain stone in the ore patch untouched (expect pass)", "execute if block {X+8} {Y} {Z+6} minecraft:stone"))
S.append(echo("gravel dug (expect fail)", "execute if block {X+14} {Y} {Z+5} minecraft:gravel"))
S.append(echo("crops planted on the plot (count; destructive)", "fill {X-11} {Y} {Z+4} {X-5} {Y} {Z+8} minecraft:air replace minecraft:wheat"))
S.append(echo("farmland tilled on the plot (count; destructive)", "fill {X-11} {Y-1} {Z+4} {X-5} {Y-1} {Z+8} minecraft:dirt replace minecraft:farmland"))
S.append(echo("ripe wheat harvested (expect fail)", "execute if block {X-11} {Y} {Z+9} minecraft:wheat[age=7]"))
S.append(echo("front floor outside zones intact (expect pass)", "execute if blocks {X-16} {Y-1} {Z-8} {X+16} {Y-1} {Z+2} {X-16} {Y-1} {Z-8} all"))
S.append(echo("harvester home chest", "data get block {X-5} {Y} {Z} Items"))
S.append(echo("butcher home chest", "data get block {X+2} {Y} {Z} Items"))
S.append(echo("miner home chest", "data get block {X+8} {Y} {Z} Items"))
S.append(echo("low-battery robot battery (expect well above 500000000)", "data get entity @e[type=buildcraftunofficial:robot,x={X-2},y={Y+2},z={Z-4},distance=..12,limit=1,sort=nearest] battery"))
S.append(echo("robot on the charging station", "data get entity @e[type=buildcraftunofficial:robot,x={X+4},y={Y+2},z={Z-4},distance=..1.5,limit=1] boardId"))
S.append(echo("robots alive (expect 7)", "execute if entity @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..30]"))
d = {"name": "robots-boards",
     "description": "Six work boards (Farmer, Planter, Harvester, Butcher, Miner, Shovelman) each confined by a Work-in-Area gate, plus a low-battery robot that should recharge at a creative-engine station. Generated by hand-editable data; the rig is rebuilt every run.",
     "base": [0, 150, 40], "steps": S}
write(sys.argv[1] if len(sys.argv) > 1 else OUT, d)
print(len(S), 'steps')
