"""Claims cross-mod pass (issue #186): BuildCraft machines owned by the dev player, standing just OUTSIDE a claim that is
not theirs and aimed INTO it. Writes scripts/smoke/scenarios/crossmod-claims-<mod>.json for each claim mod.

Run: python scripts/smoke/generators/gen_crossmod_claims.py            (--noclaim opac, --swap ftbchunks-teams: negative controls)
     pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3-fabric -Scenario scripts/smoke/scenarios/crossmod-claims-opac.json

The same rig is built three times, 48 blocks apart along Z, each rig two chunks deep (Z offsets 0-31 of its own start):

  A  no claim anywhere near it             the control: every machine must work
  B  the target chunks are claimed by SOMEONE ELSE   nothing inside the claim may be broken or placed
  C  the target chunks are claimed by the dev player (the machines' owner)   every machine must work

Each rig has its machines in the chunk column west of the target (X+0..X+15) aimed east into the target column (X+16..),
which is the only column a claim ever covers (rig starts are chunk aligned: the base is a multiple of 16). z is the
rig's own offset:

  Quarry   (z+3)   a 5x5x5 frame whose west face (X+15) is outside the claim and the rest inside; mines the 3x3x3 of
                   stone below the platform inside the claim
  Builder  (z+10)  builds a 3x3x3 cobblestone blueprint (scanned by an Architect Table, put in through the Builder's GUI)
                   at X+16..18, with a dirt block in the way that it must break first
  Filler   (z+16)  fills a 3x3x3 box at X+16..18 with cobblestone
  Stripes  (z+21)  a stripes pipe at X+15 facing east, fed cobblestone by a wooden pipe, places at X+16
  Robot    (z+26)  a Stripes robot on a docking station at X+12 (a chest of cobblestone under it, the pipe owned by the dev)
                   whose Work-in-Area zone is the single cell X+17: it flies in from outside the claim and must place there
  Pump     (z+7)   (#208) a Pump on a creative engine at X+15 over a three-cell lava pool X+15..17 (Y-1): the cell under it is
                   outside the claim, the other two inside. A pump drains the farthest cell first and takes a lava source whole,
                   so "the source (level=0) is still there" is the refusal (flowing lava from a kept source refills the emptied
                   cell, which is why the probes name level=0)
  Flood    (z+29)  (#208) a Flood Gate at X+14 fed by a Pump on a three-cell water pool at X+13 (nothing of the pool is near the
                   claim), with a two-cell stone pen in front of it (X+15 outside the claim, X+16 inside): it places a water
                   SOURCE (level=0: a source at X+15 also flows into X+16 as vanilla water, which is not the gate's doing)
  Builder 2 (z+12..14, builder at X+10, z+13)  (#208) a 6x2x3 blueprint of mixed blocks (cobblestone, a stair, a chest with a block
                   entity) whose box X+13..18 straddles the claim border: X+13..15 outside, X+16..18 inside, with a dirt block in
                   the way on each side of the border

Probes are `execute if/unless block` commands run as plain `cmd` steps: a `cmd` step fails when the condition is false,
so a probe that is not what the rig expects is a failed step and a screenshot of it. The rigs run side by side, so one
wait (gated on the control rig's slowest machine, the quarry) covers all three.
"""
import hashlib
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(__file__))
from xmod import BC, DEV_UUID, Scenario, at, pipe  # noqa: E402
import robotrig  # noqa: E402

OWNER = 'ownerUUID:"%s",ownerName:"Dev"' % DEV_UUID
OWNER_DEV = OWNER
STRANGER = 'ownerUUID:"00000000-0000-4000-8000-0000000000ee",ownerName:"Stranger"'
RIG_Z = {"A": 0, "B": 48, "C": 96}
BASE = [2048, 150, 2048]    # chunk aligned (2048 = 128 * 16) so every rig starts on a chunk border
ARCH = (6, 0, -7)           # architect table facing west, like every builder: the blueprint then keeps its orientation
CUBE = (8, 0, -8)           # the 3x3x3 cobblestone cube it scans (x+8..10, z-8..-6): the builders' target box relative to
CHEST = (6, 0, -5)          # a builder (x+2..4, z-1..+1) is this relative to the architect. The chest carries the blueprint
PUMP_Z = 7                  # the pump lane of every rig (#208)
FLOOD_Z = 29                # the flood-gate lane
BLD2 = (10, 0, 13)          # the second builder (its blueprint box is X+13..18, Z+12..14: base = builder + 1 in x, plus the
ARCH2 = (6, 0, -11)         # snapshot's offset, which is architect-relative: box min (9, -12) - (arch + 1 in x = 7, -11) = (2, -1))
CHEST2 = (6, 0, -9)         # and this chest carries the second blueprint
MIXED_BOX = ((9, 0, -12), (14, 1, -10))     # the source structure the second architect scans (6 x 2 x 3)


class Mod:
    """What differs between the claim mods: how a claim is made (commands, run in order) and removed."""

    def __init__(self, name, loaders, foreign, own, clear, notes="", stranger_owner=False, alias=None, verify=None, gap_ms=0,
                 owners=None, setup=None):
        self.name, self.loaders = name, loaders
        # owners: {"B": owner NBT, "C": owner NBT} overrides of who owns each rig's machines (default: the dev, or the stranger in
        # rig B when stranger_owner). setup(S): steps run once before the claims (a mod whose claim owner is a team needs the team
        # and its members first).
        self.owners = dict(owners or {})
        self.setup = setup
        # Milliseconds waited after each claim command (OPaC drops a player's second claim command in the same server tick)
        self.gap_ms = gap_ms
        self.alias = alias or name      # the -PwithMods alias (a variant of a mod's scenario has a name of its own)
        self.foreign, self.own, self.clear, self.notes = foreign, own, clear, notes
        # verify(tag, area) -> (command, regex) or None: a reply that proves the rig's claim is the one the rig needs,
        # checked before any machine is placed. A claim command that finds nothing to act on reports no failure, and the
        # rig would then run against no claim (rig C) or the wrong one (rig B) and pass or fail for that reason alone.
        self.verify = verify
        # A mod with no way to make a claim that is not the dev's from a command: rig B is claimed by the dev's own
        # command like rig C, and its machines are owned by a stranger instead (the claim is still not their owner's).
        self.stranger_owner = stranger_owner


def owner_name(owner):
    """The ownerName of an owner NBT string, for labels."""
    return owner.split('ownerName:"')[1].rstrip('"')


def area(z0):
    """Block coordinates of a rig's target column: X+16..X+31, Z+z0..Z+z0+31 (two chunks)."""
    return BASE[0] + 16, BASE[2] + z0, BASE[0] + 31, BASE[2] + z0 + 31


def machines(S, tag, z0, OWNER):
    """The machines of one rig, placed with the platform; every one is owned by the dev player."""
    def p(x, y, z):
        return at(x, y, z0 + z)

    S.cmd("%s: platform and air" % tag,
          "fill {X-4} {Y-4} {Z%+d} {X+36} {Y-1} {Z%+d} minecraft:stone" % (z0 - 3, z0 + 34),
          "fill {X-4} {Y} {Z%+d} {X+36} {Y+8} {Z%+d} minecraft:air" % (z0 - 3, z0 + 34),
          # an invisible block for the camera to stand on, above the claimed column (a creative player falls; the claim's name
          # then shows on the HUD)
          "setblock %s minecraft:barrier" % at(30, 16, z0 + 15),
          # and one straight above the claimed column, for the top-down shots
          "setblock %s minecraft:barrier" % at(20, 24, z0 + 16))

    # Quarry: frame x+15..19, z+1..5, y..y+4 (the west face x+15 is outside the claim); mines x+16..18, y-3..y+3, z+2..4.
    S.cmd("%s: quarry (frame crosses the claim border), creative engine behind it" % tag,
          # The boxes go in with the setblock: the quarry scans its frame from onLoaded, which a later `data merge` never
          # runs, so a quarry given its boxes afterwards sits idle with its scan queue empty.
          'setblock %s %squarry[facing=west]{%s,battery_mj:16000000000L,frame_init:1b,frame_minX:{X+15},frame_minY:{Y},'
          'frame_minZ:{Z%+d},frame_maxX:{X+19},frame_maxY:{Y+4},frame_maxZ:{Z%+d},box_init:1b,box_minX:{X+16},box_minY:{Y-3},'
          'box_minZ:{Z%+d},box_maxX:{X+18},box_maxY:{Y+3},box_maxZ:{Z%+d}}' % (
              p(14, 0, 3), BC, OWNER, z0 + 1, z0 + 5, z0 + 2, z0 + 4),
          "setblock %s minecraft:redstone_block" % p(13, 0, 2),
          "setblock %s %sengine_creative[facing=south]" % (p(14, 0, 2), BC))

    # Filler: the whole target box, cobblestone.
    S.cmd("%s: filler facing west, fill pattern, box x+16..18 z+15..17" % tag,
          "setblock %s %sfiller[facing=west]" % (p(14, 0, 16), BC),
          'data merge block %s {%s,battery_mj:16000000000L,box_initialized:1b,markerBox:0b,box_minX:{X+16},box_minY:{Y},'
          'box_minZ:{Z%+d},box_maxX:{X+18},box_maxY:{Y+2},box_maxZ:{Z%+d},patternStatement:{s:{kind:"buildcraft:fill"}},'
          'items:{resources:{items:[{stack:{id:"minecraft:cobblestone",count:64}}]}}}' % (
              p(14, 0, 16), OWNER, z0 + 15, z0 + 17))

    # Stripes pipe: wooden pipe pulls cobblestone out of the chest, a stone pipe carries it, the stripes pipe places it.
    S.cmd("%s: stripes pipe at x+15 facing east, fed by a wooden pipe" % tag,
          "setblock %s minecraft:chest[facing=west]" % p(12, 0, 21),
          "item replace block %s container.0 with minecraft:cobblestone 16" % p(12, 0, 21),
          *pipe(13, 0, 21 + z0, "wood_item"),
          *pipe(14, 0, 21 + z0, "stone_item"),
          "setblock %s %spipe_holder" % (p(15, 0, 21), BC),
          'data merge block %s {%s,pipe:{def:"%sstripes_item",beh:{direction:"EAST"}}}' % (p(15, 0, 21), OWNER, BC),
          "setblock %s minecraft:redstone_block" % p(12, 1, 21),
          "setblock %s %sengine_creative[facing=down]" % (p(13, 1, 21), BC))


def robot(S, tag, z0, station_owner):
    """A docking station (cobblestone chest below) whose Work-in-Area zone is one cell inside the claim, and a Stripes robot."""
    # A robot answers to its deployer AND to the owner of its home station's pipe (RobotProtection); a station on a pipe with no
    # owner is "[BuildCraft]", a stranger in the dev's own claim, so the pipe gets an owner: the dev, or in a rig whose
    # machines belong to a stranger the stranger (the dev still deploys the robot, so it is refused for the stranger's sake).
    S.cmd("%s: docking station X+12 with a cobblestone chest, zone = the cell X+17, the pipe owned by %s" % (
              tag, owner_name(station_owner)),
          *robotrig.station(12, z0 + 26, ["minecraft:cobblestone 16"], "buildcraft:station.provide_items",
                            (17, 0, z0 + 26, 17, 0, z0 + 26)),
          "data merge block %s {%s}" % (at(12, 1, z0 + 26), station_owner))
    S.steps.extend(robotrig.deploy("%s: deploy the Stripes robot" % tag, 12, z0 + 26, "boardRobotStripes"))


def builder(S, tag, z0, OWNER):
    """A builder whose blueprint is put in through its GUI (the one place a blueprint enters a Builder)."""
    def p(x, y, z):
        return at(x, y, z0 + z)

    S.cmd("%s: dirt where the blueprint wants cobblestone, builder facing west with cobblestone, no blueprint yet" % tag,
          "setblock %s minecraft:dirt" % p(16, 0, 9),
          "setblock %s %sbuilder[facing=west]" % (p(14, 0, 10), BC),
          'data merge block %s {%s,battery_mj:16000000000L,items:{resources:{items:['
          '{stack:{id:"minecraft:cobblestone",count:64}}]}}}' % (p(14, 0, 10), OWNER))
    S.hold("minecraft:air")
    S.cmd("%s: the blueprint into the player's inventory, standing west of the builder" % tag,
          "item replace entity @p inventory.0 from block %s container.0" % at(*CHEST))
    S.tp(11, 0, z0 + 10, -90, 0)
    S.aim(14, 0, z0 + 10)
    S.wait(300)
    S.step(label="%s: open the builder" % tag, do="use")
    S.step(do="expectScreen", contains="GuiBuilder", timeoutSec=10)
    S.step(label="%s: pick the blueprint up" % tag, do="click", slotIndex=52)
    S.step(label="%s: into the snapshot slot" % tag, do="click", slotIndex=0)
    S.step(do="close")
    S.echo("%s: the builder holds it (expect snapshotType 1)" % tag, "data get block %s snapshotType" % p(14, 0, 10))


def fluids(S, tag, z0, OWNER):
    """The Pump and Flood Gate lanes (#208), both owned by the rig's owner."""
    def p(x, y, z):
        return at(x, y, z0 + z)
    zp, zf = PUMP_Z, FLOOD_Z
    # barriers for the camera to stand on, straight above each lane's border cells
    S.cmd("%s: camera footholds over the two fluid lanes" % tag,
          "setblock %s minecraft:barrier" % p(15, 11, zp), "setblock %s minecraft:barrier" % p(15, 11, zf))
    # Pump over a lava pool across the claim border. Lava, not water: a pump leaves a water pool alone (pumpsConsumeWater is off
    # by default, "infinite" water), a lava source it takes whole, so "still lava" is the refusal.
    S.cmd("%s: pump lane z+%d: lava pool X+15..17 (X+15 outside the claim), a Pump on its west cell, a creative engine behind it" % (
              tag, zp),
          "fill %s %s minecraft:lava" % (p(15, -1, zp), p(17, -1, zp)),
          "setblock %s %spump" % (p(15, 0, zp), BC),
          "data merge block %s {%s}" % (p(15, 0, zp), OWNER),
          "setblock %s minecraft:redstone_block" % p(14, -1, zp),
          "setblock %s %sengine_creative[facing=east]" % (p(14, 0, zp), BC))
    # Flood Gate: the water comes from a Pump on a three-cell pool (infinite) that pushes into the gate beside it. The gate's pen
    # is two cells long (X+15, X+16), walled in so the water it places cannot flow anywhere else.
    S.cmd("%s: flood lane z+%d: water pool, a Pump on it, a Flood Gate beside the Pump, a stone pen in front of the gate" % (tag, zf),
          "fill %s %s minecraft:water" % (p(13, -1, zf - 1), p(13, -1, zf + 1)),
          "fill %s %s minecraft:stone" % (p(14, 0, zf - 1), p(17, 0, zf - 1)),
          "fill %s %s minecraft:stone" % (p(14, 0, zf + 1), p(17, 0, zf + 1)),
          "setblock %s minecraft:stone" % p(17, 0, zf),
          "setblock %s %spump" % (p(13, 0, zf), BC),
          "data merge block %s {%s}" % (p(13, 0, zf), OWNER),
          "setblock %s %sflood_gate" % (p(14, 0, zf), BC),
          "data merge block %s {%s}" % (p(14, 0, zf), OWNER),
          "setblock %s minecraft:redstone_block" % p(12, -1, zf),
          "setblock %s %sengine_creative[facing=east]" % (p(12, 0, zf), BC))


def builder_mixed(S, tag, z0, OWNER):
    """A second builder whose 6x2x3 blueprint mixes blocks and straddles the claim border (#208)."""
    def p(x, y, z):
        return at(x, y, z0 + z)
    S.cmd("%s: dirt where the mixed blueprint wants cobblestone (one each side of the border), builder 2 facing west with "
          "cobblestone, stairs and chests, no blueprint yet" % tag,
          "setblock %s minecraft:dirt" % p(13, 1, 12),
          "setblock %s minecraft:dirt" % p(16, 1, 13),
          "setblock %s %sbuilder[facing=west]" % (p(*BLD2), BC),
          'data merge block %s {%s,battery_mj:16000000000L,items:{resources:{items:['
          '{stack:{id:"minecraft:cobblestone",count:64}},{stack:{id:"minecraft:oak_stairs",count:8}},'
          '{stack:{id:"minecraft:chest",count:8}}]}}}' % (p(*BLD2), OWNER))
    S.hold("minecraft:air")
    S.cmd("%s: the mixed blueprint into the player's inventory, standing west of builder 2" % tag,
          "item replace entity @p inventory.0 from block %s container.0" % at(*CHEST2))
    S.tp(BLD2[0] - 3, 0, z0 + BLD2[2], -90, 0)
    S.aim(BLD2[0], 0, z0 + BLD2[2])
    S.wait(300)
    S.step(label="%s: open builder 2" % tag, do="use")
    S.step(do="expectScreen", contains="GuiBuilder", timeoutSec=10)
    S.step(label="%s: pick the blueprint up" % tag, do="click", slotIndex=52)
    S.step(label="%s: into the snapshot slot" % tag, do="click", slotIndex=0)
    S.step(do="close")
    S.echo("%s: builder 2 holds it (expect snapshotType 1)" % tag, "data get block %s snapshotType" % p(*BLD2))


def probes(S, tag, z0, expect):
    """Assertions after the rig has had time to run, one per machine and per kind of act (a claim mod may guard breaking
    and placing differently). `execute if block` fails the step when it is false."""
    def p(x, y, z):
        return at(x, y, z0 + z)
    works = expect == "works"
    yes = "if" if works else "unless"
    no = "unless" if works else "if"
    S.cmd("%s: quarry's frame west of the claim (X+15) is built, in every rig" % tag,
          "execute if block %s %sframe" % (p(15, 0, 3), BC))
    S.cmd("%s: PLACE quarry frame inside the claim (X+17): %s" % (tag, "built" if works else "refused"),
          "execute %s block %s %sframe" % (yes, p(17, 0, 1), BC))
    S.cmd("%s: BREAK quarry mines the stone inside the claim: %s" % (tag, "mined" if works else "untouched"),
          "execute %s block %s minecraft:stone" % (no, p(17, -1, 3)))
    S.cmd("%s: PLACE builder puts cobblestone in the empty cell inside the claim: %s" % (tag, "placed" if works else "refused"),
          "execute %s block %s minecraft:cobblestone" % (yes, p(17, 1, 10)))
    S.cmd("%s: BREAK builder removes the dirt in its way inside the claim: %s" % (tag, "broken" if works else "left"),
          "execute %s block %s minecraft:dirt" % (no, p(16, 0, 9)))
    S.cmd("%s: PLACE filler fills the box inside the claim: %s" % (tag, "placed" if works else "refused"),
          "execute %s block %s minecraft:cobblestone" % (yes, p(17, 1, 16)))
    S.cmd("%s: PLACE stripes pipe sets cobblestone inside the claim: %s" % (tag, "placed" if works else "refused"),
          "execute %s block %s minecraft:cobblestone" % (yes, p(16, 0, 21)))
    S.cmd("%s: PLACE Stripes robot sets cobblestone in its zone inside the claim: %s" % (tag, "placed" if works else "refused"),
          "execute %s block %s minecraft:cobblestone" % (yes, p(17, 0, 26)))
    # #208: the Pump (breaks fluid), the Flood Gate (places a fluid source) and a builder with a mixed blueprint across the border.
    zp, zf = PUMP_Z, FLOOD_Z
    # A lava SOURCE, not any lava: a source left in the pool flows into the cell a pump emptied beside it (in rig B the refused cells
    # keep their lava, and the cell under the pump, drained, fills with flowing lava from them), so "still lava" would pass there.
    lava = "minecraft:lava[level=0]"
    S.cmd("%s: the pump drained the lava cell under it (X+15, outside the claim), in every rig" % tag,
          "execute unless block %s %s" % (p(15, -1, zp), lava))
    S.cmd("%s: BREAK pump drains the lava inside the claim (X+16): %s" % (tag, "drained" if works else "untouched"),
          "execute %s block %s %s" % (no, p(16, -1, zp), lava))
    S.cmd("%s: BREAK pump drains the lava inside the claim (X+17): %s" % (tag, "drained" if works else "untouched"),
          "execute %s block %s %s" % (no, p(17, -1, zp), lava))
    source = "minecraft:water[level=0]"
    S.cmd("%s: the flood gate placed its water source outside the claim (X+15), in every rig" % tag,
          "execute if block %s %s" % (p(15, 0, zf), source))
    S.cmd("%s: PLACE flood gate puts a water source inside the claim (X+16): %s" % (tag, "placed" if works else "refused"),
          "execute %s block %s %s" % (yes, p(16, 0, zf), source))
    # The mixed blueprint: box X+13..18 x Y..Y+1 x z+12..14, blueprint cell (ix, iy, iz) at (13+ix, iy, 12+iz).
    S.cmd("%s: mixed blueprint outside the claim (X+13), built in every rig: cobblestone" % tag,
          "execute if block %s minecraft:cobblestone" % p(13, 0, 12))
    S.cmd("%s: mixed blueprint outside the claim (X+14), built in every rig: stair, orientation kept" % tag,
          "execute if block %s minecraft:oak_stairs[facing=east]" % p(14, 0, 13))
    S.cmd("%s: mixed blueprint outside the claim (X+15), built in every rig: chest" % tag,
          "execute if block %s minecraft:chest[facing=south]" % p(15, 0, 14))
    S.cmd("%s: mixed blueprint outside the claim (X+13), built in every rig: the dirt in its way replaced by cobblestone" % tag,
          "execute if block %s minecraft:cobblestone" % p(13, 1, 12))
    S.cmd("%s: PLACE mixed blueprint cobblestone inside the claim (X+16): %s" % (tag, "placed" if works else "refused"),
          "execute %s block %s minecraft:cobblestone" % (yes, p(16, 0, 12)))
    S.cmd("%s: PLACE mixed blueprint stair inside the claim, orientation kept: %s" % (tag, "placed" if works else "refused"),
          "execute %s block %s minecraft:oak_stairs[facing=east]" % (yes, p(17, 0, 13)))
    S.cmd("%s: PLACE mixed blueprint chest (a block entity) inside the claim: %s" % (tag, "placed" if works else "refused"),
          "execute %s block %s minecraft:chest[facing=south]" % (yes, p(18, 0, 14)))
    S.cmd("%s: BREAK mixed blueprint removes the dirt in its way inside the claim: %s" % (tag, "broken" if works else "left"),
          "execute %s block %s minecraft:dirt" % (no, p(16, 1, 13)))


def build(mod):
    S = Scenario("crossmod-claims-" + mod.name, BASE,
                 description="Issues #186, #208: BuildCraft machines (Quarry, Builders, Filler, stripes pipe, Stripes robot, Pump, "
                             "Flood Gate) owned by the dev player beside a claim that is %s's, aimed into it (rig B), against the "
                             "same rig with no claim (A) and with a claim the dev owns (C). "
                             "Generated by generators/gen_crossmod_claims.py, whose docstring describes the rig. %s" % (
                                 mod.name, mod.notes),
                 mods=[mod.alias], save="bc-xmod-claims-" + mod.name, saveSeed="bc-smoke", freshSave=True)
    if mod.loaders:
        S.top["loaders"] = mod.loaders
    S.cmd("creative, daylight, peaceful",
          "gamemode creative @p", "difficulty peaceful", "time set day", "weather clear")
    # One forceload per rig: on the 1.21.x lines the bridge's reply to a single forceload over all three rigs timed out
    # while the server was still loading the chunks (#205).
    for tag in ("A", "B", "C"):
        z0 = RIG_Z[tag]
        S.cmd("%s: the rig's chunks force-loaded" % tag, "?forceload add {X-16} {Z%+d} {X+48} {Z%+d}" % (z0 - 16, z0 + 48))
    S.tp(8, 4, 8, 0, 20)
    S.wait(3000)
    for tag in ("A", "B", "C"):
        z0 = RIG_Z[tag]
        for c in mod.clear(tag, area(z0)):
            S.cmd("%s: clear any claim an earlier run left" % tag, c, benign=True)

    # The blueprint: a cobblestone cube scanned by an architect table, carried through a chest into the player's inventory.
    S.cmd("blueprint source: a 3x3x3 cobblestone cube and the architect table scanning it",
          "fill {X-4} {Y-4} {Z-12} {X+16} {Y-1} {Z-3} minecraft:stone",
          "fill {X-4} {Y} {Z-12} {X+16} {Y+8} {Z-3} minecraft:air",
          "fill %s %s minecraft:cobblestone" % (at(*CUBE), at(CUBE[0] + 2, 2, CUBE[2] + 2)),
          "setblock %s %sarchitect[facing=west]" % (at(*ARCH), BC),
          'data merge block %s {box_initialized:1b,box_minX:{X+%d},box_minY:{Y},box_minZ:{Z%+d},box_maxX:{X+%d},box_maxY:{Y+2},'
          'box_maxZ:{Z%+d},isValid:1b,name:"claims-cube",items:{in:{items:[{stack:{id:"%sblueprint_clean",count:1}}]},'
          'out:{items:[]}}}' % (at(*ARCH), CUBE[0], CUBE[2], CUBE[0] + 2, CUBE[2] + 2, BC))
    S.wait_cmd("the architect finished the blueprint", "data get block %s items.out.items[0].stack.id" % at(*ARCH),
               "blueprint_used", 40)
    S.cmd("chest: the used blueprint in slot 0",
          "setblock %s minecraft:chest[facing=south]" % at(*CHEST),
          'data merge block %s {Items:[{Slot:0b,id:"minecraft:stone",count:1}]}' % at(*CHEST),
          "data modify block %s Items[{Slot:0b}] merge from block %s items.out.items[0].stack" % (at(*CHEST), at(*ARCH)))

    # The second blueprint (#208): 6x2x3 of mixed blocks, scanned by a second architect, carried in a second chest. Index (ix, iy, iz)
    # of the structure is at (9 + ix, iy, -12 + iz); a stair and a chest sit on each side of the border the builder's box will
    # straddle (ix 0..2 land outside the claim, 3..5 inside), and a cobblestone block on the layer above each side (the builder
    # finds dirt there).
    (x0, y0, z0m), (x1, y1, z1m) = MIXED_BOX
    S.cmd("blueprint source 2: a 6x2x3 structure of cobblestone, stairs and chests and the architect table scanning it",
          "fill %s %s minecraft:cobblestone" % (at(x0, y0, z0m), at(x1, y0, z1m)),
          "setblock %s minecraft:oak_stairs[facing=east]" % at(x0 + 1, 0, z0m + 1),
          "setblock %s minecraft:chest[facing=south]" % at(x0 + 2, 0, z0m + 2),
          "setblock %s minecraft:oak_stairs[facing=east]" % at(x0 + 4, 0, z0m + 1),
          "setblock %s minecraft:chest[facing=south]" % at(x0 + 5, 0, z0m + 2),
          "setblock %s minecraft:cobblestone" % at(x0, 1, z0m),
          "setblock %s minecraft:cobblestone" % at(x0 + 3, 1, z0m + 1),
          "setblock %s %sarchitect[facing=west]" % (at(*ARCH2), BC),
          'data merge block %s {box_initialized:1b,box_minX:{X+%d},box_minY:{Y},box_minZ:{Z%+d},box_maxX:{X+%d},box_maxY:{Y+%d},'
          'box_maxZ:{Z%+d},isValid:1b,name:"claims-mixed",items:{in:{items:[{stack:{id:"%sblueprint_clean",count:1}}]},'
          'out:{items:[]}}}' % (at(*ARCH2), x0, z0m, x1, y1, z1m, BC))
    S.wait_cmd("the second architect finished its blueprint", "data get block %s items.out.items[0].stack.id" % at(*ARCH2),
               "blueprint_used", 90)
    S.cmd("chest 2: the used mixed blueprint in slot 0",
          "setblock %s minecraft:chest[facing=south]" % at(*CHEST2),
          'data merge block %s {Items:[{Slot:0b,id:"minecraft:stone",count:1}]}' % at(*CHEST2),
          "data modify block %s Items[{Slot:0b}] merge from block %s items.out.items[0].stack" % (at(*CHEST2), at(*ARCH2)))

    if mod.setup:
        mod.setup(S)

    # Claims first, then the machines: nothing may start before the rig it belongs to is claimed.
    for tag, make in (("B", mod.foreign), ("C", mod.own)):
        S.tp(20, 4, RIG_Z[tag] + 8, 0, 20)
        S.wait(1500)
        for c in make(area(RIG_Z[tag])):
            S.cmd("%s: the claim over the target chunks" % tag, c)
            if mod.gap_ms:
                S.wait(mod.gap_ms)
        checks = mod.verify(tag, area(RIG_Z[tag])) if mod.verify else None
        if checks and isinstance(checks, tuple):
            checks = [checks]
        for check in checks or []:
            S.wait_cmd("%s: the claim is what the rig needs (reply matches '%s')" % (tag, check[1]), check[0], check[1], 10, 500)
        S.wait(2500)
    S.tp(8, 4, 8, 0, 20)

    def owner(tag):
        if tag in mod.owners:
            return mod.owners[tag]
        return STRANGER if (tag == "B" and mod.stranger_owner) else OWNER

    for tag, z0 in RIG_Z.items():
        machines(S, tag, z0, owner(tag))
        fluids(S, tag, z0, owner(tag))
    for tag, z0 in RIG_Z.items():
        builder(S, tag, z0, owner(tag))
    for tag, z0 in RIG_Z.items():
        builder_mixed(S, tag, z0, owner(tag))
    for tag, z0 in RIG_Z.items():
        robot(S, tag, z0, owner(tag))

    S.hold("minecraft:air")
    for tag, z0 in RIG_Z.items():
        S.tp(30, 17, z0 + 15, 90, 35)
        S.wait(2000)
        S.shot("%s-early" % tag.lower(), "rig %s a moment after the machines started" % tag)
    A = RIG_Z["A"]
    S.wait_cmd("the control rig's quarry mined its stone (the slowest machine)",
               "execute unless block %s minecraft:stone" % at(17, -1, A + 3), "passed", 240, 2000)
    S.wait_cmd("the control rig's robot placed its block (the other slow one)",
               "execute if block %s minecraft:cobblestone" % at(17, 0, A + 26), "passed", 180, 2000)
    S.wait_cmd("the control rig's pump drained the far lava cell", "execute unless block %s minecraft:lava[level=0]" % at(17, -1, A + PUMP_Z),
               "passed", 120, 2000)
    S.wait_cmd("the control rig's flood gate placed the second water source",
               "execute if block %s minecraft:water[level=0]" % at(16, 0, A + FLOOD_Z), "passed", 120, 2000)
    S.wait_cmd("the control rig's mixed blueprint chest inside the claim area was built",
               "execute if block %s minecraft:chest" % at(18, 0, A + 14), "passed", 120, 2000)
    S.wait(15000, "let the other rigs go on as long as the control did")
    for tag, z0 in RIG_Z.items():
        S.tp(20, 25, z0 + 16, 0, 90)
        S.wait(2000)
        S.shot("%s-top" % tag.lower(), "straight down on the claimed column (right of centre, X+16..31) and the machines west of "
                                       "it: frame scaffold, cobblestone blocks, the pit the quarry digs (rig %s)" % tag)
        S.tp(30, 17, z0 + 15, 90, 35)
        S.wait(2000)
        S.shot("%s-late" % tag.lower(), {
            "A": "rig A, no claim: the quarry's frame and a pit in the stone, cobblestone cube at the builder and filler "
                 "boxes, cobblestone beside the stripes pipe, the lava pool drained, a water source at both cells of the flood "
                 "gate's pen, the mixed blueprint (cobblestone, stairs, chests) complete across X+13..18",
            "B": "rig B, claimed by someone else: the quarry's frame only on its west face, no pit, the builder and "
                 "filler boxes empty, nothing beside the stripes pipe, the dirt block untouched, lava left in the two pool cells "
                 "inside the claim, water only in the pen cell outside it, the mixed blueprint built only on the west three "
                 "columns (X+13..15) with the dirt untouched inside the claim",
            "C": "rig C, a claim whose owner the machines answer to: identical to rig A"}[tag])
        for lane, z, look in (
                ("pump", PUMP_Z, {"A": "rig A, no claim: the pool's three cells empty (the pump took all of them)",
                                  "B": "rig B, claimed by someone else: the pool cell under the pump empty, the two cells on the "
                                       "claim side (right, X+16..17) still lava",
                                  "C": "rig C, a claim the machines answer to: identical to rig A"}),
                ("flood", FLOOD_Z, {"A": "rig A, no claim: water in both cells of the stone pen in front of the flood gate",
                                    "B": "rig B, claimed by someone else: water only in the pen cell outside the claim, the cell "
                                         "on the claim side (right) empty or with flowing water only",
                                    "C": "rig C, a claim the machines answer to: identical to rig A"})):
            S.tp(15, 12, z0 + z, 0, 90)
            S.wait(1500)
            S.shot("%s-%s" % (tag.lower(), lane), "straight down on the %s lane at the claim border (X+16 is the first claimed column): %s" % (
                lane, look[tag]))
        probes(S, tag, z0, "blocked" if tag == "B" else "works")
    return S


def opac_chunks(a):
    """The chunk corners (block x, z) of the area, one per chunk. OPaC's claim command treats an area as one chunk when
    (left + right) / 2 == left and (top + bottom) / 2 == top in chunk coordinates, integer division included, so the
    rigs' 1x2-chunk area claimed only its first chunk row: the Filler, stripes pipe and robot targets (second row) were
    never claimed. Each chunk gets a command of its own (from = to), which no area shortcut can misread."""
    x1, z1, x2, z2 = a
    return [(x, z) for x in range(x1, x2 + 1, 16) for z in range(z1, z2 + 1, 16)]


def ftbc_claims(a):
    """One `ftbchunks claim` per chunk of the area, at that chunk's position (the command claims where it is run)."""
    x1, z1, x2, z2 = a
    return ["execute as @p positioned %d %d %d run ftbchunks claim" % (x, BASE[1], z)
            for x in range(x1, x2, 16) for z in range(z1, z2, 16)]


def offline_uuid(name):
    """The UUID Minecraft gives a player name with no account behind it: UUID.nameUUIDFromBytes("OfflinePlayer:" + name), which is
    what the profile cache hands out for a name it has never seen (DEV_UUID is this, for "Dev")."""
    b = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
    b[6] = (b[6] & 0x0F) | 0x30
    b[8] = (b[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(b)))


assert offline_uuid("Dev") == DEV_UUID


def player_owner(name):
    return 'ownerUUID:"%s",ownerName:"%s"' % (offline_uuid(name), name)


# The names are lower case on purpose: the profile cache lower-cases a name before it makes the offline profile (`get(name)` looks up
# name.toLowerCase), so a name with capitals gets the UUID of its lower-case spelling and a different one from offline_uuid(name).
FTB_OUTSIDER = "bcclaimoutsider"     # a player with a team of his own, in no party
FTB_TEAMMATE = "bcclaimteammate"     # a player forced into the dev's party
FTB_PARTY = "BCClaimParty"           # the dev's party: the claim owner of the teams scenario


def ftb_teams_setup(S):
    """FTB Teams: two players the world has never seen get a team each (`ftbteams add_fake_player`, a dev-runtime-only command: it
    is what the mod's own tests use), the dev opens a party and the teammate is put into it by `ftbteams force add`. Nothing here
    needs a second client: a team is data keyed by a UUID, and a machine's fake player carries its owner's UUID."""
    # The two spellings: FTB Teams 2101 (the 1.21.1 line) has a root command `ftbteams_add_fake_player` and `force-add`; 2111 and 26.1
    # have `ftbteams add_fake_player` and `ftbteams force add`.
    for nodes, fake, force in ((["1.21.1"], "ftbteams_add_fake_player", "ftbteams force-add"),
                               (["1.21.11", "26.1.2"], "ftbteams add_fake_player", "ftbteams force add")):
        with S.only_on(nodes):
            S.cmd("FTB Teams: the outsider and the teammate get teams of their own (dev command)",
                  "%s %s" % (fake, FTB_OUTSIDER), "%s %s" % (fake, FTB_TEAMMATE))
            # `party create` is a requirement on the source's entity, read before `execute as` applies: it must be typed by the player.
            S.player_cmd("FTB Teams: the dev opens a party (typed by the player)", "ftbteams party create %s" % FTB_PARTY)
            S.wait_cmd("the dev is in a party of his own now", "ftbteams info @p", r"\[party\]", 10, 500)
            S.cmd("FTB Teams: the teammate is forced into the dev's party", "%s @p %s" % (force, FTB_TEAMMATE))
    # `ftbteams info <player>` names the player's team id, which for a personal team is the player's UUID: the UUID a machine
    # owned by that name carries is the one the team is under.
    S.wait_cmd("the outsider's own team is under the UUID his machines carry", "ftbteams info %s" % FTB_OUTSIDER,
               offline_uuid(FTB_OUTSIDER), 10, 500)
    S.wait_cmd("the teammate's own team is under the UUID his machines carry", "ftbteams info %s" % FTB_TEAMMATE,
               offline_uuid(FTB_TEAMMATE), 10, 500)
    S.wait_cmd("the dev's party lists the teammate", "ftbteams info @p", FTB_TEAMMATE, 10, 500)
    S.echo("the dev's party (expect the teammate listed and not the outsider)", "ftbteams info @p")


def ftb_claim_owner(tag, a):
    """verify hook: `ftbchunks info` at each chunk of the rig's claim must name the dev's party as the owner."""
    x1, z1, x2, z2 = a
    return [("ftbchunks info %d %d" % (x1, z), FTB_PARTY) for z in range(z1, z2, 16)]


def flan_at(a):
    """A point inside a rig's claim, as the position of the Flan commands that act on "the claim at" the command source.

    Never `at @p`: `flan add` floors the claim 10 blocks under the y it is given, and the dev player, teleported up to
    y+4 with nothing under him yet (the platforms come after the claims), is in free fall by the time the next command
    runs: on NeoForge he was at y 124, 16 blocks under that floor, about two seconds after the teleport. `setAdminClaim`
    then finds no claim, says so without failing, and rig B stayed the dev's own claim (#204: every machine of the dev's
    rightly passed it). The same scenario passed on Fabric, where the player evidently still stood in the claim's height
    (not measured).
    """
    return "%d %d %d" % ((a[0] + a[2]) // 2, BASE[1] + 4, (a[1] + a[3]) // 2)


def flan_set_admin(a):
    return "execute as @p positioned %s run flan setAdminClaim true" % flan_at(a)


def flan_owner(owner_of):
    """verify hook: `flan info` at the rig's claim must name the owner `owner_of(tag)` ("Admin" or "Dev")."""
    return lambda tag, a: ("execute as @p positioned %s run flan info" % flan_at(a), "Owner: %s," % owner_of(tag))


MODS = [
    # `oclaims server claim` needs a dimension from a source with no player; `oclaims claim` (as the player) claims for the dev.
    Mod("opac", None,
        foreign=lambda a: ["oclaims server claim in minecraft:overworld %d %d %d %d" % (x, z, x, z) for x, z in opac_chunks(a)],
        own=lambda a: ["execute as @p run oclaims claim %d %d %d %d" % (x, z, x, z) for x, z in opac_chunks(a)],
        clear=lambda tag, a: [], gap_ms=300),
    # Flan: `flan add x y z x y z` claims for the player (the 512-block area is over the 500 claim blocks everyone starts
    # with, and its refusal goes to the player's chat, not to the command's reply, so the blocks are given first). An admin claim has
    # no owner, so the dev player is a stranger in it, unless he is an operator: Flan lets a player of permission level 2 or
    # higher through an admin claim, and a machine's fake player has its owner's level on NeoForge (#204). The dev world has
    # cheats off, so the dev is not one. `flan setAdminClaim` acts on the claim at the command's position: see flan_at.
    Mod("flan", None,
        foreign=lambda a: ["execute as @p run flan giveClaimBlocks Dev 5000",
                           "execute as @p run flan add %d 150 %d %d 150 %d" % (a[0], a[1], a[2], a[3]),
                           flan_set_admin(a)],
        own=lambda a: ["execute as @p run flan giveClaimBlocks Dev 5000",
                       "execute as @p run flan add %d 150 %d %d 150 %d" % (a[0], a[1], a[2], a[3])],
        clear=lambda tag, a: [],
        verify=flan_owner(lambda tag: "Admin" if tag == "B" else "Dev")),
    # OPaC with the dev's own claim and machines that belong to a stranger (see flan-stranger).
    Mod("opac-stranger", None, alias="opac", stranger_owner=True,
        foreign=lambda a: ["execute as @p run oclaims claim %d %d %d %d" % (x, z, x, z) for x, z in opac_chunks(a)],
        own=lambda a: ["execute as @p run oclaims claim %d %d %d %d" % (x, z, x, z) for x, z in opac_chunks(a)],
        clear=lambda tag, a: [], gap_ms=300,
        notes="Rig B's claim is the dev's like rig C's and rig B's machines are owned by a stranger."),
    # The same claim mod with a claim that is the dev's and machines that belong to a stranger instead of an admin claim.
    Mod("flan-stranger", None, alias="flan", stranger_owner=True,
        foreign=lambda a: ["execute as @p run flan giveClaimBlocks Dev 5000",
                           "execute as @p run flan add %d 150 %d %d 150 %d" % (a[0], a[1], a[2], a[3])],
        own=lambda a: ["execute as @p run flan giveClaimBlocks Dev 5000",
                       "execute as @p run flan add %d 150 %d %d 150 %d" % (a[0], a[1], a[2], a[3])],
        clear=lambda tag, a: [],
        verify=flan_owner(lambda tag: "Dev"),
        notes="Rig B's claim is the dev's like rig C's and rig B's machines are owned by a stranger (its robot is deployed by "
              "the dev, its station pipe is the stranger's)."),
    # An admin claim and machines owned by a stranger: a machine whose owner is not a known player is refused through Flan's
    # separate fake-player permission, so this one tells that rule apart from `flan` (admin claim, dev's machines).
    Mod("flan-admin-stranger", ["neoforge"], alias="flan", stranger_owner=True,
        foreign=lambda a: ["execute as @p run flan giveClaimBlocks Dev 5000",
                           "execute as @p run flan add %d 150 %d %d 150 %d" % (a[0], a[1], a[2], a[3]),
                           flan_set_admin(a)],
        own=lambda a: ["execute as @p run flan giveClaimBlocks Dev 5000",
                       "execute as @p run flan add %d 150 %d %d 150 %d" % (a[0], a[1], a[2], a[3])],
        clear=lambda tag, a: [],
        verify=flan_owner(lambda tag: "Admin" if tag == "B" else "Dev"),
        notes="Rig B's claim is an admin claim and its machines are owned by a stranger."),
    # FTB Chunks (NeoForge 26.1.2 only): a team's claim by `ftbchunks claim`. `claim_as` only takes a known profile and a
    # fake player's team may claim nothing, so the foreign case is the machines being owned by a stranger.
    Mod("ftbchunks", ["neoforge"], foreign=ftbc_claims, own=ftbc_claims, clear=lambda tag, a: [], stranger_owner=True,
        notes="Rig B's claim is the dev's team's like rig C's, and rig B's machines are owned by a stranger (its robot is "
              "deployed by the dev but its station pipe is the stranger's)."),
    # FTB Chunks with real teams (#208): the claim belongs to the dev's party in both rigs. Rig B's machines are owned by a player
    # who has a team of his own and is in no party ("foreign team"), rig C's by a player forced into the dev's party who is not
    # the claimer ("teammate": it must work, or the check is only "owner == claimer").
    Mod("ftbchunks-teams", ["neoforge"], alias="ftbchunks", foreign=ftbc_claims, own=ftbc_claims, clear=lambda tag, a: [],
        owners={"B": player_owner(FTB_OUTSIDER), "C": player_owner(FTB_TEAMMATE)}, setup=ftb_teams_setup, verify=ftb_claim_owner,
        notes="Both claims are the dev's party's. Rig B's machines belong to a player with a team of his own (%s), rig C's to a "
              "player in the dev's party (%s); the dev is the party's owner and the robot's deployer in both." % (
                  FTB_OUTSIDER, FTB_TEAMMATE)),
]


def without_claim_b(mod):
    """The negative control of a mod's scenario: the same rigs, rig B never claimed. Every probe that expects a refusal in rig B must
    FAIL in this run (rig B then behaves as rig A); that is what shows the assertion bites. The JSON is for a one-off run: delete it
    afterwards, the batch's default list would otherwise run it."""
    import copy
    ctl = copy.copy(mod)
    ctl.name = mod.name + "-noclaim"
    ctl.foreign = lambda a: []
    if mod.verify:
        ctl.verify = lambda tag, a: None if tag == "B" else mod.verify(tag, a)
    return ctl


def with_swapped_owners(mod):
    """The negative control of a team scenario: rig B's machines belong to rig C's player and the other way round. Every probe of
    rigs B and C that depends on the owner must FAIL (the teammate is refused where it was let through and the outsider let through
    where it was refused is not what happens either: both rigs run as the other's expectation), which shows the owner's team, and
    nothing else about the rigs, decides. Delete the JSON after the run."""
    import copy
    ctl = copy.copy(mod)
    ctl.name = mod.name + "-swapped"
    ctl.owners = {"B": mod.owners["C"], "C": mod.owners["B"]}
    return ctl


def main(argv):
    """No arguments: every scenario. `--noclaim <mod> ...` (e.g. opac) or `--swap <mod> ...` (a mod with owners, ftbchunks-teams):
    the negative controls of the named mods instead."""
    if argv[:1] in (["--noclaim"], ["--swap"]):
        make = without_claim_b if argv[0] == "--noclaim" else with_swapped_owners
        for mod in MODS:
            if mod.name in argv[1:]:
                build(make(mod)).write()
        return
    for mod in MODS:
        build(mod).write()


if __name__ == "__main__":
    main(sys.argv[1:])
