"""Fluid Carrier, Bomber and Stripes (each confined by a zone), and the laser-powered Programming Table and
Integration Table producing a Carrier board and a programmed robot.

Run: python scripts/smoke/generators/gen_misc.py  (rewrites scripts/smoke/scenarios/robots-misc.json)
"""
import os, sys

sys.path.insert(0, os.path.dirname(__file__))
from robotrig import *  # noqa: F401,F403

OUT = os.path.join(os.path.dirname(__file__), "..", "scenarios", "robots-misc.json")

NODES_26 = ["1.21.10", "1.21.11", "26.1.2", "26.2", "26.3"]


def tank_water(x, z, nodes, nbt):
    return {"label": "fill tank at %d,%d (%s)" % (x, z, "/".join(nodes)), "do": "cmd", "nodes": nodes,
            "run": ["data merge block %s {Y} %s %s" % (o('X', x), o('Z', z), nbt)]}


def powered_laser(x, z):
    """A creative engine (4 MJ/t, redstone-powered) behind a laser at (x, z-?) firing south (+Z)."""
    X, Z = o('X', x), o('Z', z)
    Zb = o('Z', z - 1)
    return ["setblock %s {Y} %s buildcraftunofficial:laser[facing=south]" % (X, Z),
            "setblock %s {Y} %s buildcraftunofficial:engine_creative[facing=south]" % (X, Zb),
            "data merge block %s {Y} %s {currentOutputIndex:2}" % (X, Zb),
            "setblock %s {Y} %s minecraft:redstone_block" % (o('X', x - 1), Zb)]


S = []
S.append({"label": "load chunks", "do": "cmd", "run": ["gamemode creative @p", "?forceload add {X-16} {Z-12} {X+28} {Z+12}"]})
S.append({"do": "tp", "at": [0, 0, -10], "yaw": 0, "pitch": 25})
S.append({"do": "wait", "ms": 3000})
S.append({"label": "clear and floor", "do": "cmd", "run": [
    "difficulty peaceful", "time set 6000", "?gamerule doDaylightCycle false", "?gamerule advance_time false", "weather clear",
    "?kill @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..200]",
    "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..60]",
    "?kill @e[type=minecraft:tnt,x={X},y={Y},z={Z},distance=..60]",
    "?fill {X-16} {Y} {Z-12} {X+28} {Y+8} {Z+12} minecraft:air",
    "fill {X-16} {Y-1} {Z-12} {X+28} {Y-1} {Z+12} minecraft:smooth_stone"]})

# Fluid Carrier: supply tank (water) under a wooden fluid pipe -> destination tank under a cobblestone fluid pipe.
carrier_zone = (-11, -1, -1, -5, 3, 1)
S.append({"label": "fluid supply: wooden fluid pipe over a tank, Provide Fluids", "do": "cmd",
          "run": station(-10, 0, [], "buildcraft:station.provide_fluids", carrier_zone, pipe="wood_fluid",
                         below="buildcraftunofficial:tank", zaction="buildcraft:robot.load_unload_area")})
S.append({"label": "fluid destination: cobblestone fluid pipe over a tank, Accept Fluids", "do": "cmd",
          "run": station(-6, 0, [], "buildcraft:station.accept_fluids", pipe="cobblestone_fluid",
                         below="buildcraftunofficial:tank", beh="")})
S.append(tank_water(-10, 0, ["1.21.1"], '{fluid0:"minecraft:water",amount0:8000}'))
S.append(tank_water(-10, 0, NODES_26, '{stacks:[{id:"minecraft:water",amount:8000}]}'))
S.append(echo("supply tank holds water", "data get block {X-10} {Y} {Z}"))

# Stripes: places cobblestone into a small air box.
S.append({"label": "stripes home (16 cobblestone)", "do": "cmd",
          "run": station(-1, 0, ["minecraft:cobblestone 16"], "buildcraft:station.provide_items", (-3, 0, 4, -1, 1, 6))})

# Bomber: TNT, zone a dirt pit well away from everything.
S.append({"label": "bomb pit (dirt, 10+ blocks from any station)", "do": "cmd",
          "run": ["fill {X+20} {Y-3} {Z-2} {X+24} {Y-1} {Z+2} minecraft:dirt"]})
S.append({"label": "bomber home (4 TNT)", "do": "cmd",
          "run": station(10, 0, ["minecraft:tnt 4"], "buildcraft:station.provide_items", (20, -3, -2, 24, -1, 2))})

# Delivery: a supply chest of diamonds (Provide Items) and a Requester next to a second station's pipe asking for 4.
S.append({"label": "delivery supply (16 diamonds, Provide Items)", "do": "cmd",
          "run": station(-12, 8, ["minecraft:diamond 16"], "buildcraft:station.provide_items")})
S.append({"label": "requester dock + Requester asking for 4 diamonds", "do": "cmd",
          "run": station(-6, 8, [], "buildcraft:station.accept_items", pipe="cobblestone_item", below="minecraft:smooth_stone", beh="")
          + ["setblock {X-5} {Y+1} {Z+8} buildcraftunofficial:requester",
             'data merge block {X-5} {Y+1} {Z+8} {items:{req:{items:[{stack:{id:"minecraft:diamond",count:4}}]}}}']})
S.append(echo("requester request saved", "data get block {X-5} {Y+1} {Z+8} items"))

# Tables: two lasers, each fed by its own creative engine.
S.append({"label": "programming table + laser", "do": "cmd",
          "run": powered_laser(4, -6) + ["setblock {X+4} {Y} {Z-4} buildcraftunofficial:programming_table"]})
S.append({"label": "integration table + laser", "do": "cmd",
          "run": powered_laser(8, -6) + ["setblock {X+8} {Y} {Z-4} buildcraftunofficial:integration_table"]})
S.append({"label": "programming table: blank board, Carrier program (option 1)", "do": "cmd", "run": [
    'data merge block {X+4} {Y} {Z-4} {items:{board:{items:[{stack:{id:"buildcraftunofficial:redstone_board",count:1}}]}},recipeId:"buildcraftunofficial:redstone_board",optionId:1}']})
S.append({"label": "integration table: blank robot + a Carrier board", "do": "cmd", "run": [
    'data merge block {X+8} {Y} {Z-4} {items:{target:{items:[{stack:{id:"buildcraftunofficial:robot",count:1}}]},toIntegrate:{items:[{stack:{id:"buildcraftunofficial:redstone_board",count:1,components:{"minecraft:custom_data":{board:{id:"buildcraftunofficial:boardRobotCarrier"}}}}}]}}}']})
S.append({"do": "wait", "ms": 1500})
S.append({"name": "rig4-empty", "do": "shot", "look": "fluid stations with tanks (left), stripes station, bomber station, dirt pit far right; two lasers firing at the Programming and Integration Tables in front"})

S += deploy("deploy fluid carrier at the supply", -10, 0, "boardRobotFluidCarrier")
S += deploy("deploy stripes", -1, 0, "boardRobotStripes")
S += deploy("deploy bomber", 10, 0, "boardRobotBomber")
S += deploy("deploy delivery robot at the supply", -12, 8, "boardRobotDelivery")
S.append({"do": "hold", "item": "minecraft:air"})
S.append(echo("robots deployed (expect 4)", "execute if entity @e[type=buildcraftunofficial:robot,x={X},y={Y},z={Z},distance=..40]"))
S.append({"label": "viewing pillar", "do": "cmd", "run": ["setblock {X+4} {Y+5} {Z-11} minecraft:glass"]})
S.append({"do": "tp", "at": [4, 6, -11], "yaw": 0, "pitch": 30})
S.append({"do": "wait", "ms": 15000})
S.append({"name": "rig4-15s", "do": "shot", "look": "lasers beaming into both tables; fluid carrier moving between tanks; stripes placing cobblestone; bomber flying to the pit"})
S.append({"do": "wait", "ms": 30000})
S.append({"name": "rig4-45s", "do": "shot", "look": "craters in the dirt pit; cobblestone placed in the stripes box; nothing outside the zones damaged"})
S.append({"do": "wait", "ms": 40000})
S.append({"name": "rig4-85s", "do": "shot", "look": "steady state; tables finished (result slots filled)"})
S.append(echo("programming table (expect a Carrier board in result)", "data get block {X+4} {Y} {Z-4} items"))
S.append(echo("integration table (expect a Carrier robot in result)", "data get block {X+8} {Y} {Z-4} items"))
S.append(echo("destination tank (expect water)", "data get block {X-6} {Y} {Z}"))
S.append(echo("supply tank after", "data get block {X-10} {Y} {Z}"))
S.append(echo("supply pipe fluid", "data get block {X-10} {Y+1} {Z} pipe.flow"))
S.append(echo("destination pipe fluid", "data get block {X-6} {Y+1} {Z} pipe.flow"))
S.append(echo("cobblestone placed by stripes (count; destructive)", "fill {X-3} {Y} {Z+4} {X-1} {Y+1} {Z+6} minecraft:air replace minecraft:cobblestone"))
S.append(echo("dirt left in the pit (count; destructive; 75 at start)", "fill {X+20} {Y-3} {Z-2} {X+24} {Y-1} {Z+2} minecraft:stone replace minecraft:dirt"))
S.append(echo("bomber chest TNT left", "data get block {X+10} {Y} {Z} Items"))
S.append(echo("floor outside the pit intact (expect pass)", "execute if blocks {X-16} {Y-1} {Z-12} {X+18} {Y-1} {Z+12} {X-16} {Y-1} {Z-12} all"))
S.append(echo("requester holds the delivered diamonds (expect 4 in inv)", "data get block {X-5} {Y+1} {Z+8} items"))
S.append(echo("delivery supply chest left (expect 12)", "data get block {X-12} {Y} {Z+8} Items"))
for b in ["FluidCarrier", "Stripes", "Bomber", "Delivery"]:
    S.append(echo("%s alive" % b, 'execute if entity @e[type=buildcraftunofficial:robot,nbt={boardId:"buildcraftunofficial:boardRobot%s"}]' % b))
S.append(echo("fluid carrier robot tank", 'data get entity @e[type=buildcraftunofficial:robot,nbt={boardId:"buildcraftunofficial:boardRobotFluidCarrier"},limit=1] tank'))
S.append({"label": "tables GUI", "do": "tp", "at": [6, 0, -2], "yaw": 180, "pitch": 20})
S.append({"do": "aim", "at": [4, 0, -4]})
S.append({"do": "use"})
S.append({"do": "expectScreen", "contains": "Programming"})
S.append({"do": "wait", "ms": 500})
S.append({"name": "programming-table-done", "do": "shot", "look": "Programming Table GUI: the Carrier option selected, a programmed board in the output, energy bar"})
S.append({"do": "widgets", "name": "programming-table"})
S.append({"do": "close"})
S.append({"do": "aim", "at": [8, 0, -4]})
S.append({"do": "use"})
S.append({"do": "expectScreen", "contains": "Integration"})
S.append({"do": "wait", "ms": 500})
S.append({"name": "integration-table-done", "do": "shot", "look": "Integration Table GUI: robot preview, the programmed robot in the output, progress complete"})
S.append({"do": "close"})

d = {"name": "robots-misc",
     "description": "Fluid Carrier, Bomber and Stripes confined by zones, and the laser-powered Programming / Integration Tables making a Carrier board and robot.",
     "base": [0, 150, 120], "steps": S}
write(sys.argv[1] if len(sys.argv) > 1 else OUT, d)
print(len(S), 'steps')
