"""Claims cross-mod pass (issue #186): BuildCraft machines owned by the dev player, standing just OUTSIDE a claim that is
not theirs and aimed INTO it. Writes scripts/smoke/scenarios/crossmod-claims-<mod>.json for each claim mod.

Run: python scripts/smoke/generators/gen_crossmod_claims.py
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

Probes are `execute if/unless block` commands run as plain `cmd` steps: a `cmd` step fails when the condition is false,
so a probe that is not what the rig expects is a failed step and a screenshot of it. The rigs run side by side, so one
wait (gated on the control rig's slowest machine, the quarry) covers all three.
"""
import os
import sys

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


class Mod:
    """What differs between the claim mods: how a claim is made (commands, run in order) and removed."""

    def __init__(self, name, loaders, foreign, own, clear, notes="", stranger_owner=False, alias=None, verify=None, gap_ms=0):
        self.name, self.loaders = name, loaders
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
              tag, "a stranger" if station_owner == STRANGER else "the dev"),
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


def build(mod):
    S = Scenario("crossmod-claims-" + mod.name, BASE,
                 description="Issue #186: BuildCraft machines owned by the dev player beside a claim that is %s's, aimed "
                             "into it (rig B), against the same rig with no claim (A) and with a claim the dev owns (C). "
                             "Generated by generators/gen_crossmod_claims.py, whose docstring describes the rig. %s" % (
                                 mod.name, mod.notes),
                 mods=[mod.alias], save="bc-xmod-claims-" + mod.name, saveSeed="bc-smoke", freshSave=True)
    if mod.loaders:
        S.top["loaders"] = mod.loaders
    S.cmd("creative, daylight, the three rigs' chunks loaded",
          "gamemode creative @p", "difficulty peaceful", "time set day", "weather clear",
          "?forceload add {X-16} {Z-16} {X+48} {Z+144}")
    S.tp(8, 4, 8, 0, 20)
    S.wait(3000)
    for tag in ("A", "B", "C"):
        z0 = RIG_Z[tag]
        for c in mod.clear(tag, area(z0)):
            S.cmd("%s: clear any claim an earlier run left" % tag, c, benign=True)

    # The blueprint: a cobblestone cube scanned by an architect table, carried through a chest into the player's inventory.
    S.cmd("blueprint source: a 3x3x3 cobblestone cube and the architect table scanning it",
          "fill {X-4} {Y-4} {Z-12} {X+12} {Y-1} {Z-3} minecraft:stone",
          "fill {X-4} {Y} {Z-12} {X+12} {Y+8} {Z-3} minecraft:air",
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

    # Claims first, then the machines: nothing may start before the rig it belongs to is claimed.
    for tag, make in (("B", mod.foreign), ("C", mod.own)):
        S.tp(20, 4, RIG_Z[tag] + 8, 0, 20)
        S.wait(1500)
        for c in make(area(RIG_Z[tag])):
            S.cmd("%s: the claim over the target chunks" % tag, c)
            if mod.gap_ms:
                S.wait(mod.gap_ms)
        check = mod.verify(tag, area(RIG_Z[tag])) if mod.verify else None
        if check:
            S.wait_cmd("%s: the claim is what the rig needs (reply matches '%s')" % (tag, check[1]), check[0], check[1], 10, 500)
        S.wait(2500)
    S.tp(8, 4, 8, 0, 20)

    def owner(tag):
        return STRANGER if (tag == "B" and mod.stranger_owner) else OWNER

    for tag, z0 in RIG_Z.items():
        machines(S, tag, z0, owner(tag))
    for tag, z0 in RIG_Z.items():
        builder(S, tag, z0, owner(tag))
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
                 "boxes, cobblestone beside the stripes pipe",
            "B": "rig B, claimed by someone else: the quarry's frame only on its west face, no pit, the builder and "
                 "filler boxes empty, nothing beside the stripes pipe, the dirt block untouched",
            "C": "rig C, the dev's own claim: identical to rig A"}[tag])
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
]


def main():
    for mod in MODS:
        build(mod).write()


if __name__ == "__main__":
    main()
