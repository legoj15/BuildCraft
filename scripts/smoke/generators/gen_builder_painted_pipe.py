"""Painted pipes in the Builder's placement preview (#229): the flying cubes, against a placed painted pipe.

Run: python scripts/smoke/generators/gen_builder_painted_pipe.py  (rewrites scripts/smoke/scenarios/builder-painted-pipe.json)
     pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 1.21.1,1.21.1-forge -Scenario scripts/smoke/scenarios/builder-painted-pipe.json

The question (#229): on the Forge 1.21.1 node a painted item pipe drawn WITHOUT a level may differ from the NeoForge 1.21.1
one, because the Forge mixin that scales a mask quad's vertex alpha (platform/forge/mixin/client/ModelBlockRendererMixin)
only wraps the chunk mesh's putQuadData on 1.21.1, while vanilla's no-level putBulkData passes readExistingColor=false. The
no-level path is the Builder's placement preview (PlaceTaskCubeRenderer): each queued place task flies its item from the
Builder to its cell as a 0.30-block cube. The client sync writes a display copy of the task's item carrying the cell's
captured paint (the PIPE_COLOUR component, SnapshotBuilder.displayStackFor), and the cube is drawn from that item model on
1.21.1 (the classic item renderer, whose tint applies the paint) and as a paint-tinted sprite cube on 1.21.10/1.21.11;
26.x draws the same display stack's item model through its SubmitNodeCollector path. So the preview does know the cell's
paint. The run puts the two loaders side by side on a node, and the same scenario on two nodes shows whether they differ.

The rig, from the base (floor at y-1):

  source row   x -12..0, z 0     seven cells, two apart, a painted item pipe (stone_item) RED, LIME and BLUE, an unpainted
                                 one, a painted fluid pipe (stone_fluid, its dye baked into the texture) RED and an unpainted
                                 one, then a red stained glass block. The glass is the control: a genuinely translucent block
                                 going through the same preview path.
  architect    (-11, 0, -2)      facing north, the box set by NBT (the one thing no command does); the blueprint goes
                                 architect -> chest -> inventory -> the Builder's snapshot slot through its GUI, as in
                                 gen_builder_stall.py
  Builder      (15, 0, -2)       facing north, so it rebuilds the row 26 blocks east (x 14..26, z 0): "the site"
  reference    x 14..26, z 3     the same seven cells placed by command, drawn by the chunk mesh: what a painted pipe looks
                                 like when the model sees its level (the translucent tint), and what the preview is not

Battery and tick rate. A place task's flight lasts only as long as the Builder takes to draw power for it, which at a full
battery is one or two ticks. The Builder is therefore given the blueprint first with no power and no pipes, and the pipes and
a battery sized to run dry as the last task finishes (440 MJ for the 412 MJ the seven tasks cost: the draw per tick is
proportional to what is stored, so the tail crawls) arrive by `data merge` once the camera is in place; `tick rate 4` then
stretches the whole build to about sixteen seconds, enough for a real-time burst. Mid-flight the game is frozen (`tick
freeze`) so a few close shots can be taken from different spots at the very same frame, then it thaws, the burst resumes,
and the rate goes back to 20 before the finished pipes are shot. The echo of `builderClientData.placeTasks` in the report
shows the items the cubes are drawn from.

Shots (window 1600x900): `pp-ref-before` the reference row and the empty site; `blueprint-tooltip` the blueprint item's
tooltip with its rotating 3D preview (a second no-level path, which draws a painted pipe through its own mask overlay, not
ModelBlockRenderer); `fly-1` .. `fly-8` the flight from the south (two before the freeze, six after); `frozen-wide` and
`frozen-close-a` .. `-c` the frozen cubes; `built-wide` and `built-close` the finished row against the reference row. The
`look` text on each says what to compare.

What it showed (2026-11-22, run on 1.21.1, 1.21.1-forge, 26.3 and 26.3-forge, 90 steps each, all green):
  - 1.21.1 and 1.21.1-forge draw the same thing. `pp-ref-before`, `built-wide` and `built-close` are pixel-identical between
    the two (the chunk-mesh painted pipes: translucent tint over the glass on the item pipes, opaque dyed border on the fluid
    pipe), and the placed site row matches the reference row on each. In flight only the red glass cube is drawn, translucent
    on both (mean colour within two levels); the place-task echo lists five tasks at the freeze (glass and four pipes), so
    four cubes are NOT drawn on either loader.
  - Why: the task carries the plain pipe item (SchematicBlockPipe.resolvePipeItem leaves the paint off the requirement) and
    the <26.1 preview draws `defaultBlockState()` through renderSingleBlock; the pipe_holder block model is a particle-only
    placeholder, and both loaders' pipe block models fall back to it when the model data holds no pipe. So the preview can
    neither show nor hide paint, and the Forge mixin's alpha scaling is never reached by it.
  - 26.3 and 26.3-forge draw the pipe ITEM model for a pipe task (a small plain pipe, unpainted), the same on both loaders.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from xmod import BC, DEV_UUID, Scenario, at  # noqa: E402

SRC_X = -12
SHIFT = 26
SITE_X = SRC_X + SHIFT
ARCH = (SRC_X + 1, 0, -2)
BUILDER = (ARCH[0] + SHIFT, 0, -2)
CHEST = (SRC_X - 3, 0, -2)
REF_Z = 3
# (index along the row, kind, pipe definition or block, paint) in the order the row is laid out.
CELLS = [
    ("pipe", "stone_item", "RED"),
    ("pipe", "stone_item", "LIME"),
    ("pipe", "stone_item", None),
    ("pipe", "stone_fluid", "RED"),
    ("pipe", "stone_fluid", None),
    ("pipe", "stone_item", "BLUE"),
    ("block", "minecraft:red_stained_glass", None),
]
COLS = {i: 2 * i for i in range(len(CELLS))}
BOX_W = COLS[len(CELLS) - 1]          # the box is BOX_W + 1 wide, one block high and deep

# Battery: 440 MJ in micro-joules (the Builder's `battery_mj` is raw micro-joules, 1 MJ = 1_000_000).
BATTERY_UJ = 440_000_000

# Stand-points (feet, offsets from the base); each stands on a barrier block one lower, a creative player falling otherwise.
WIDE = (20, 3, 10)
FLY = (20, 3, 7)
CLOSE_A = (19, 2, 2)
CLOSE_B = (19, 3, -6)
CLOSE_C = (17, 2, 2)
BUILT_CLOSE = (16, 3, 7)

S = Scenario(
    "builder-painted-pipe", [0, 150, 0],
    description="Issue #229: a Builder's placement preview (the flying cube of each queued pipe) beside a placed painted pipe, "
                "to compare the preview on NeoForge 1.21.1 and Forge 1.21.1. Generated by "
                "generators/gen_builder_painted_pipe.py, whose docstring describes the rig.")


def cell(base_x, z, i, kind, what, paint):
    """The commands that place one cell of the row at base_x + 2*i."""
    x = base_x + COLS[i]
    if kind == "block":
        return ["setblock %s %s" % (at(x, 0, z), what)]
    extra = ',col:"%s"' % paint if paint else ""
    return ["setblock %s %spipe_holder" % (at(x, 0, z), BC),
            'data merge block %s {pipe:{def:"%s%s"%s}}' % (at(x, 0, z), BC, what, extra)]


def row(base_x, z):
    out = []
    for i, (kind, what, paint) in enumerate(CELLS):
        out.extend(cell(base_x, z, i, kind, what, paint))
    return out


# -- setup ---------------------------------------------------------------------------------------------------------------
S.cmd("creative, peaceful, daylight; the rig's chunks loaded",
      "gamemode creative @p", "difficulty peaceful", "time set day", "weather clear",
      "?forceload add {X-20} {Z-12} {X+30} {Z+16}")
S.tp(0, 0, 8, 180, 10)
S.wait(3000)
S.step(label="window 1600x900", do="window", width=1600, height=900)
S.cmd("clear and floor",
      "?clear @p",
      "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..60]",
      "?fill {X-20} {Y} {Z-12} {X+30} {Y+9} {Z+16} minecraft:air",
      "fill {X-20} {Y-1} {Z-12} {X+30} {Y-1} {Z+16} minecraft:smooth_stone")
S.wait(4000)

# -- the source row, scanned ---------------------------------------------------------------------------------------------
S.cmd("source row: painted item pipes (red, lime, blue), a plain one, a painted fluid pipe, a plain one, red stained glass",
      *row(SRC_X, 0))
S.cmd("the source pipes kept their paint (red item pipe is RED, the plain one has none, the fluid pipe is RED)",
      'execute if data block %s pipe{col:"RED"}' % at(SRC_X + COLS[0], 0, 0),
      'execute unless data block %s pipe.col' % at(SRC_X + COLS[2], 0, 0),
      'execute if data block %s pipe{col:"RED"}' % at(SRC_X + COLS[3], 0, 0))
S.cmd("architect facing north with its box set and a blank blueprint in",
      "setblock %s %sarchitect[facing=north]" % (at(*ARCH), BC),
      'data merge block %s {box_initialized:1b,box_minX:{X%+d},box_minY:{Y},box_minZ:{Z},box_maxX:{X%+d},box_maxY:{Y},box_maxZ:{Z},'
      'isValid:1b,name:"painted-pipes",items:{in:{items:[{stack:{id:"%sblueprint_clean",count:1}}]},out:{items:[]}}}'
      % (at(*ARCH), SRC_X, SRC_X + BOX_W, BC))
S.wait_cmd("the architect finished its blueprint", "data get block %s items.out.items[0].stack.id" % at(*ARCH),
           "blueprint_used", 60)
S.hold("minecraft:air")   # before the blueprint goes into slot 0: `hold` replaces whatever the selected slot holds
S.cmd("chest: the used blueprint in slot 0, then into the player's first slot",
      "setblock %s minecraft:chest[facing=south]" % at(*CHEST),
      'data merge block %s {Items:[{Slot:0b,id:"minecraft:stone",count:1}]}' % at(*CHEST),
      "data modify block %s Items[{Slot:0b}] merge from block %s items.out.items[0].stack" % (at(*CHEST), at(*ARCH)),
      "item replace entity @p inventory.0 from block %s container.0" % at(*CHEST))

# -- the reference row, placed, and the Builder with no power --------------------------------------------------------------
S.cmd("reference row: the same seven cells, placed by command, three blocks south of the site", *row(SITE_X, REF_Z))
S.cmd("viewpoints: barrier blocks to stand on",
      *["setblock %s minecraft:barrier" % at(p[0], p[1] - 1, p[2]) for p in (WIDE, FLY, CLOSE_A, CLOSE_B, CLOSE_C, BUILT_CLOSE)])
S.cmd("Builder facing north: owner, NO power and no pipes yet",
      "setblock %s %sbuilder[facing=north]" % (at(*BUILDER), BC),
      'data merge block %s {ownerUUID:"%s",ownerName:"Dev"}' % (at(*BUILDER), DEV_UUID))
S.wait(1500)
S.tp(*WIDE, 180, 28)
S.aim(SITE_X + 6, 0, 1)
S.wait(1200)
S.shot("pp-ref-before", "the REFERENCE row (south of the empty site): three painted item pipes (red, lime, blue) tinted "
       "translucent over their glass, a plain one, a red fluid pipe with an opaque red border and a plain one, then a red "
       "stained-glass block. The site row behind it is still empty, the Builder (north of it) idle")

# -- feed the Builder through its GUI --------------------------------------------------------------------------------------
S.tp(BUILDER[0], 0, -6, 0, 10)
S.aim(*BUILDER)
S.wait(300)
S.step(label="open the Builder", do="use")
S.step(do="expectScreen", contains="GuiBuilder", timeoutSec=10)
# The blueprint item's tooltip carries a 3D preview of the snapshot, drawn from its captured NBT with no level: the second
# no-level path a painted pipe goes through (its own mask overlay and alpha, not ModelBlockRenderer). The first hover only
# asks the server for the snapshot, so hover, wait and hover again.
S.step(label="hover the blueprint in the inventory", do="hover", slotIndex=52)
S.wait(1500)
S.step(label="hover it again now that the snapshot has arrived", do="hover", slotIndex=52)
S.wait(800)
S.shot("blueprint-tooltip", "the blueprint's tooltip with its 3D preview underneath (a 100x100 panel): the seven cells drawn "
       "without a level: painted item pipes tinted, the fluid pipe's red border, the red glass cube")
S.step(label="pick the blueprint up from the inventory", do="click", slotIndex=52)
S.step(label="put it in the Builder's snapshot slot", do="click", slotIndex=0)
S.step(do="close")
S.echo("the Builder holds the blueprint (expect snapshotType 1)", "data get block %s snapshotType" % at(*BUILDER))

# -- the flight ------------------------------------------------------------------------------------------------------------
S.tp(*FLY, 180, 20)
S.aim(SITE_X + 6, 1, -1)
S.wait(800)
S.cmd("tick rate 4: the build now takes about sixteen seconds", "tick rate 4")
S.cmd("give the Builder its pipes and the battery: it starts",
      'data merge block %s {battery_mj:%dL,items:{resources:{items:['
      '{stack:{id:"%spipe_stone_item",count:8}},{stack:{id:"%spipe_stone_fluid",count:8}},'
      '{stack:{id:"minecraft:red_stained_glass",count:8}}]}}}' % (at(*BUILDER), BATTERY_UJ, BC, BC))
S.wait(1800)
S.echo("the place tasks of the Builder: the items the cubes fly as (the pipe items, carrying the paint component where the blueprint captured paint)",
       "data get block %s builderClientData.placeTasks" % at(*BUILDER))
FLY_LOOK = ("the Builder's flying cubes (0.30 block each) arcing from the Builder (north, centre-left) to the row's cells: "
            "compare each cube's look with the placed pipes of the reference row in front. Judge whether a cube is "
            "opaque, translucent (the cells behind it show through) or tinted red/lime/blue")
for n in range(1, 3):
    S.shot("fly-%d" % n, FLY_LOOK)
    S.wait(700)
S.cmd("freeze the game mid-flight: every task is still in the air", "tick freeze")
S.wait(800)
S.echo("the place tasks at the freeze (the two cells next to the Builder are placed; the rest are still in the air)",
       "data get block %s builderClientData.placeTasks" % at(*BUILDER))
S.shot("frozen-wide", "frozen mid-flight, the same view as the flight shots, the glass cube and four pipe tasks in the air by the task list. "
       "Count the cubes drawn and judge each one's opacity and tint")
S.tp(*CLOSE_A, 180, 10)
S.aim(19, 2, -1)
S.wait(800)
S.shot("frozen-close-a", "close to the frozen cubes (the middle of the flight): look at the cubes' faces: texture, opacity")
S.tp(*CLOSE_C, 180, 10)
S.aim(17, 2, -1)
S.wait(800)
S.shot("frozen-close-c", "the same, from the west end of the row")
S.tp(*CLOSE_B, 180, 10)
S.aim(19, 2, 0)
S.wait(800)
S.shot("frozen-close-b", "the same, from the north side (behind the Builder), looking south over the flights")
S.cmd("thaw the game", "tick unfreeze")
S.tp(*FLY, 180, 20)
S.aim(SITE_X + 6, 1, -1)
S.wait(600)
for n in range(3, 9):
    S.shot("fly-%d" % n, FLY_LOOK + " (the later cubes: the long flights, the last ones still moving)")
    S.wait(700)

# -- the finished row ------------------------------------------------------------------------------------------------------
S.wait_cmd("the Builder placed the last cell (the stained glass)", "execute if block %s minecraft:red_stained_glass"
           % at(SITE_X + COLS[6], 0, 0), "passed", 120, 1500)
S.cmd("tick rate 20 again", "tick rate 20")
S.wait(3000)
S.cmd("every pipe was placed with its paint (RED, LIME, none, RED fluid, none, BLUE)",
      'execute if data block %s pipe{col:"RED"}' % at(SITE_X + COLS[0], 0, 0),
      'execute if data block %s pipe{col:"LIME"}' % at(SITE_X + COLS[1], 0, 0),
      'execute unless data block %s pipe.col' % at(SITE_X + COLS[2], 0, 0),
      'execute if data block %s pipe{col:"RED"}' % at(SITE_X + COLS[3], 0, 0),
      'execute unless data block %s pipe.col' % at(SITE_X + COLS[4], 0, 0),
      'execute if data block %s pipe{col:"BLUE"}' % at(SITE_X + COLS[5], 0, 0))
S.tp(*WIDE, 180, 28)
S.aim(SITE_X + 6, 0, 1)
S.wait(1500)
S.shot("built-wide", "the finished site row (z 0) behind the reference row (z+3): the same seven cells in the same order, "
       "pipe for pipe identical in tint, border and translucency")
S.tp(*BUILT_CLOSE, 180, 30)
S.aim(SITE_X + 2, 0, 1)
S.wait(1500)
S.shot("built-close", "close: the first three cells of the site row and the reference row, the red, lime and plain item "
       "pipes of each")
S.cmd("tick rate 20 (belt and braces)", "tick rate 20")

S.write()
