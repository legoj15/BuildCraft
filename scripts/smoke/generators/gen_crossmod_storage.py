"""Storage cross-mod pass on Fabric (issue #186): BuildCraft item and fluid pipes, a Pump and a Flood Gate against other
mods' storage. Writes scripts/smoke/scenarios/crossmod-storage.json.

Run: python scripts/smoke/generators/gen_crossmod_storage.py
     pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3-fabric -Scenario scripts/smoke/scenarios/crossmod-storage.json

Every lane is built along +X from the base and is separated from the next by 6 blocks of Z.

Items. A source chest of 64 cobblestone feeds a wooden pipe (a creative engine on top pulls) into a stone pipe that ends
against the other mod's block; a second wooden pipe pulls out of that block into a stone pipe and a chest. Phase one runs
the feeding half alone (the block must come to hold all 64), phase two switches the draining half on (the end chest must
come to hold all 64 and the block none): both directions are shown on their own.
  1  Tom's Simple Storage: chest <- inventory connector -> inventory interface; the pipes touch the interface
  2  Extended Drawers: a single drawer

Fluids, on a Fabric node (Fabric Transfer counts a bucket as 81000 droplets, BuildCraft whole millibuckets: 1 mB is 81
droplets and a move under 81 droplets is refused). A BuildCraft tank holds W mB, a wooden fluid pipe (creative engine on
top) carries it into the other mod's tank, and the second wooden fluid pipe, switched on later, pulls it back into a
second BuildCraft tank. Each amount is read in droplets in the other mod's tank (it holds W * 81) and in mB in the BuildCraft tanks.
  3  exactly one bucket (1000 mB) into a Tech Reborn tank unit and back
  4  1234 mB, not a whole number of buckets, the same way
  5  a Tech Reborn tank that already holds 100000 droplets (1234 mB and 46 droplets) pulled into a BuildCraft tank:
     BuildCraft takes the 1234 whole mB and the 46 droplets stay put, so 1234 * 81 + 46 = 100000 and nothing is lost or made
  6  a Pump on a one-source pool with a tank unit above it: the tank unit comes to hold whole buckets
  7  a Tech Reborn tank unit holding 3000 mB behind a wooden fluid pipe into a Flood Gate: the tank empties, the gate floods
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from xmod import BC, Scenario, at, pipe  # noqa: E402

BASE = [0, 150, 0]
DROPLETS = 81
WATER = "minecraft:water"


def tank_nbt(mb):
    """BuildCraft tank contents as NBT. 1.21.1 keeps fluid0/amount0, every later line a list of stacks."""
    return {"1.21.1": '{fluid0:"%s",amount0:%d}' % (WATER, mb),
            "stacks": '{stacks:[{id:"%s",amount:%d}]}' % (WATER, mb)}


def fill_tank(S, label, pos, mb):
    n = tank_nbt(mb)
    S.cmd(label, "data merge block %s %s" % (at(*pos), n["stacks"]), nodes=["1.21.10", "1.21.11", "26.1.2", "26.2", "26.3"])
    S.cmd(label, "data merge block %s %s" % (at(*pos), n["1.21.1"]), nodes=["1.21.1"])


def engine_on(S, label, x, z, y=1):
    """A creative engine pointing down onto the pipe below it, with a redstone block beside it."""
    S.cmd(label, "setblock %s %sengine_creative[facing=down]" % (at(x, y, z), BC),
          "setblock %s minecraft:redstone_block" % at(x + 1, y, z))


def item_lane(S, n, z, label, sink, probe_net):
    """Chest -> wooden pipe -> stone pipes -> `sink` (placed at x=5) and, in phase two, out again into a chest."""
    S.cmd("%d: %s: sink, source chest with 64 cobblestone, feeding pipes (engine off for now)" % (n, label),
          *sink,
          "setblock %s minecraft:chest" % at(5, 0, z - 4),
          "item replace block %s container.0 with minecraft:cobblestone 64" % at(5, 0, z - 4),
          *pipe(5, 0, z - 3, "wood_item"), *pipe(5, 0, z - 2, "stone_item"), *pipe(5, 0, z - 1, "stone_item"),
          *pipe(5, 0, z + 1, "wood_item"), *pipe(5, 0, z + 2, "stone_item"),
          "setblock %s minecraft:chest" % at(5, 0, z + 3))
    engine_on(S, "%d: %s: feeding engine on" % (n, label), 5, z - 3)
    S.wait_cmd("%d: %s: the source chest is empty" % (n, label), "data get block %s Items" % at(5, 0, z - 4),
               r"Items: \[\]", 60)
    S.wait_cmd("%d: %s: the %s holds all 64" % (n, label, "network" if n == 1 else "drawer"), *probe_net)
    S.cmd("%d: %s: the end chest is still empty (draining half is off)" % (n, label),
          "execute unless items block %s container.0 minecraft:cobblestone" % at(5, 0, z + 3))
    engine_on(S, "%d: %s: draining engine on" % (n, label), 5, z + 1)
    S.wait_cmd("%d: %s: the end chest holds all 64" % (n, label), "data get block %s Items" % at(5, 0, z + 3),
               r"count: 64,", 90)


def fluid_lane(S, n, z, mb, label, foreign_start=None):
    """BC tank (x0) -> wooden fluid pipe (x1, engine on top) -> cobblestone fluid pipe (x2) -> TR tank unit (x3), and back
    through a second wooden fluid pipe (x3, z+1) into a second BC tank (x3, z+3). With `foreign_start` (droplets) the
    unit already holds that much and the forward half is not run."""
    tank = "techreborn:basic_tank_unit"
    S.cmd("%d: %s: tanks and pipes" % (n, label),
          "setblock %s %stank" % (at(0, 0, z), BC),
          *pipe(1, 0, z, "wood_fluid"), *pipe(2, 0, z, "cobblestone_fluid"),
          "setblock %s %s" % (at(3, 0, z), tank),
          *pipe(3, 0, z + 1, "wood_fluid"), *pipe(3, 0, z + 2, "cobblestone_fluid"),
          "setblock %s %stank" % (at(3, 0, z + 3), BC))
    if foreign_start is None:
        engine_on(S, "%d: %s: forward engine on" % (n, label), 1, z)
        fill_tank(S, "%d: %s: the first BuildCraft tank gets %d mB" % (n, label, mb), (0, 0, z), mb)
        S.wait_cmd("%d: %s: the tank unit holds %d droplets" % (n, label, mb * DROPLETS),
                   "data get block %s TankStorage" % at(3, 0, z), r"value: %dL" % (mb * DROPLETS), 60)
        S.cmd("%d: %s: the first BuildCraft tank is empty" % (n, label),
              "execute unless data block %s stacks[0].amount" % at(0, 0, z))
    else:
        S.cmd("%d: %s: the tank unit already holds %d droplets of water" % (n, label, foreign_start),
              'data merge block %s {TankStorage:{amount:{value:%dL},fluid:{fluid:"%s"}}}' % (at(3, 0, z), foreign_start, WATER))
    engine_on(S, "%d: %s: draining engine on" % (n, label), 3, z + 1)
    whole = (foreign_start // DROPLETS) if foreign_start is not None else mb
    left = (foreign_start % DROPLETS) if foreign_start is not None else 0
    S.wait_cmd("%d: %s: the second BuildCraft tank holds %d mB" % (n, label, whole),
               "data get block %s stacks" % at(3, 0, z + 3), r"amount: %d," % whole, 60)
    S.wait_cmd("%d: %s: the tank unit is left with %d droplets" % (n, label, left),
               "data get block %s TankStorage.amount" % at(3, 0, z), r"value: %dL" % left, 30)


def build():
    S = Scenario("crossmod-storage", BASE,
                 description="Issue #186 on Fabric: BuildCraft item and fluid pipes, a Pump and a Flood Gate against Tom's "
                             "Simple Storage, Extended Drawers and Tech Reborn tank units, with the 81-droplets-per-"
                             "millibucket rule. Generated by generators/gen_crossmod_storage.py, whose docstring describes "
                             "the lanes.",
                 mods=["tomsstorage", "extendeddrawers", "techreborn"], loaders=["fabric"], save="bc-xmod-storage",
                 saveSeed="bc-smoke", freshSave=True)
    S.cmd("creative, daylight, the rig's chunks loaded", "gamemode creative @p", "difficulty peaceful", "time set day",
          "weather clear", "?forceload add {X-16} {Z-16} {X+32} {Z+64}")
    S.tp(10, 6, -8, 0, 25)
    S.wait(3000)
    S.cmd("a fresh rig: stone floor, air above, everything of an earlier run gone",
          "fill {X-4} {Y-8} {Z-8} {X+16} {Y-1} {Z+60} minecraft:stone",
          "fill {X-4} {Y} {Z-8} {X+16} {Y+8} {Z+60} minecraft:air")

    # 1. Tom's Simple Storage
    z = 6
    item_lane(S, 1, z, "Tom's Simple Storage",
              ["setblock %s toms_storage:inventory_connector" % at(6, 0, z), "setblock %s minecraft:chest" % at(7, 0, z),
               "setblock %s toms_storage:inventory_interface" % at(5, 0, z)],
              ("data get block %s Items" % at(7, 0, z), r"count: 64,", 60))
    # 2. Extended Drawers
    z = 20
    item_lane(S, 2, z, "Extended Drawers", ["setblock %s extended_drawers:single_drawer[facing=east]" % at(5, 0, z)],
              ("data get block %s items" % at(5, 0, z), r"amount: 64L", 60))
    S.tp(10, 6, 12, 0, 40)
    S.wait(1500)
    S.shot("items", "two item lanes seen from above: source chest, wooden pipe with a creative engine, stone pipes, the "
                    "Tom's interface or the drawer, then a wooden pipe and the end chest")

    # 3-5. tank units, whole and part buckets
    fluid_lane(S, 3, 34, 1000, "one bucket, 1000 mB")
    fluid_lane(S, 4, 40, 1234, "1234 mB, not a whole number of buckets")
    fluid_lane(S, 5, 46, 0, "a tank unit holding 100000 droplets (1234 mB and 46 droplets)", foreign_start=100000)
    S.tp(8, 6, 38, 0, 45)
    S.wait(1500)
    S.shot("fluids", "three fluid lanes: BuildCraft tank, wooden fluid pipe with a creative engine, cobblestone fluid pipe, a "
                     "Tech Reborn tank unit, and a second wooden fluid pipe with an engine leading to a BuildCraft tank "
                     "holding water")

    # 6. Pump -> tank unit above it, over a one-source pool
    z = 54
    S.cmd("6: a one-source pool, a Pump over it on a creative engine, a Tech Reborn tank unit above the Pump",
          "fill %s %s minecraft:smooth_stone" % (at(9, -2, z - 1), at(11, -2, z + 1)),
          "setblock %s minecraft:water" % at(10, -1, z),
          "setblock %s %spump" % (at(10, 0, z), BC),
          "setblock %s techreborn:basic_tank_unit" % at(10, 1, z),
          "setblock %s minecraft:redstone_block" % at(9, -1, z),
          "setblock %s %sengine_creative[facing=east]" % (at(9, 0, z), BC))
    S.wait_cmd("6: the tank unit above the Pump holds whole buckets of water",
               "data get block %s TankStorage" % at(10, 1, z),
               r"value: (%s)L" % "|".join(str(DROPLETS * 1000 * k) for k in range(1, 17)), 90)
    # 7. tank unit -> wooden fluid pipe -> Flood Gate
    z = 60
    S.cmd("7: a tank unit with 3000 mB, a wooden fluid pipe with an engine, a Flood Gate",
          "setblock %s techreborn:basic_tank_unit" % at(12, 0, z),
          'data merge block %s {TankStorage:{amount:{value:%dL},fluid:{fluid:"%s"}}}' % (at(12, 0, z), 3000 * DROPLETS, WATER),
          *pipe(13, 0, z, "wood_fluid"),
          "setblock %s %sflood_gate" % (at(14, 0, z), BC))
    engine_on(S, "7: engine on", 13, z)
    S.wait_cmd("7: the tank unit is empty (the Flood Gate took all 3000 mB)",
               "data get block %s TankStorage.amount" % at(12, 0, z), r"value: 0L", 60)
    S.wait(3000)
    S.cmd("7: the Flood Gate placed water beside itself", "execute if block %s minecraft:water" % at(15, 0, z))
    S.tp(10, 6, 54, 0, 40)
    S.wait(1500)
    S.shot("pump-and-gate", "left: a Pump over a pool with a tank unit on top holding water; right: a tank unit, a wooden "
                            "fluid pipe and a Flood Gate with water flooded around the gate")
    for label, pos, path in (("3 tank unit", (3, 0, 34), "TankStorage"), ("4 tank unit", (3, 0, 40), "TankStorage"),
                             ("5 tank unit", (3, 0, 46), "TankStorage"), ("6 tank unit above the Pump", (10, 1, 54), "TankStorage"),
                             ("7 tank unit", (12, 0, 60), "TankStorage")):
        S.echo("%s (droplets)" % label, "data get block %s %s" % (at(*pos), path))
    for label, pos in (("3 second tank", (3, 0, 37)), ("4 second tank", (3, 0, 43)), ("5 second tank", (3, 0, 49))):
        S.echo("%s (mB)" % label, "data get block %s stacks" % at(*pos),)
    return S


if __name__ == "__main__":
    build().write()
