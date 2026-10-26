"""Builder stall outlines and summary (issue #39): a Builder whose build has stalled outlines each cell it is skipping.

Run: python scripts/smoke/generators/gen_builder_stall.py  (rewrites scripts/smoke/scenarios/builder-stall.json)
     pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3 -Scenario scripts/smoke/scenarios/builder-stall.json

What it builds, the way a player gets there. A small structure is built, scanned by an Architect Table (its box is set
by NBT, the one thing no command can do through the world; the blank blueprint goes in the same way), and the used
blueprint travels architect output -> chest -> the player's inventory -> the Builder's snapshot slot through the real
Builder GUI. The Builder stands 18 blocks east of the architect, so it builds the same 3 x 3 x 3 box there ("the site"),
which the scenario has prepared so the build is stuck on six cells of four kinds (box-local x, y, z). The blueprint is a ring
of stone on the ground (8 cells) with a torch in its middle (1,0,1), two stones on top of the ring's north-west and
south-west corners, and a torch two cells above the middle with air between (1,2,1). The site:

  - (1,2,1) the torch hanging over the air cell (1,1,1), which is air in the blueprint too: the Builder never tries
    it. RED, "can't stand".
  - (2,1,0) the blueprint wants air; the site has bedrock. RED, "can't be broken".
  - (0,1,0) (0,1,2) stone the Builder has none of. YELLOW, "missing items".
  - (0,0,1) the blueprint wants stone; the site has a water source here (walled in: the ring's stones north and south, a
    stone outside the box to the west), and the Builder has no stone. YELLOW, "missing items": the third.
  - (1,0,1) the torch cell in the middle of the ring: the source beside it flows in (a dead end, the ring walls it
    on its other three sides). A torch is fragile (fluid washes it away), and the fluid beside it is in a cell that is
    still to be built into, so it may yet be displaced: the fluid gate holds the torch back. BLUE, "held back by fluid".
    Only in REPLACE mode: in the default NO_REPLACE a non-fluid cell holding nothing but fluid counts as built (the
    mode never replaces fluid), so it is never "left to place" and never stalled. The scenario clicks the Builder's
    fluid-mode button once (NO_REPLACE -> REPLACE) the way a player would.
  - the other seven ring stones are already built at the site: not stalled, not outlined.

The Builder has torches, no stone. It stalls on its own once it has sat idle for 2 s; the scenario waits for the
server's own counts (1, 1, 0, 3, 1 in EnumStallReason order) before the first shot.

Shots (window 1600x900, guiScale 3): the site from above-south-west holding a Marker Wrench, a Volume Wrench, and with
an empty hand (no outlines), a closer one from the south with the Marker Wrench, the Builder GUI with the cursor on
the readout (dark red, stalled tooltip), and last, after the Builder is given stone, the site again: the yellow and
blue outlines are gone, the two red remain. The GUI coordinates below assume that window and scale: the readout is at GUI-local
(10, 50), the screen being 534 x 300 GUI units wide with the 256 x 222 GUI centred in it.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "scenarios", "builder-stall.json")
BC = "buildcraftunofficial:"
DEV_UUID = "380df991-f603-344c-a090-369bad2a924a"   # the dev client's offline UUID for "Dev", the same on every node


def o(a, n):
    return "{%s%s}" % (a, ("+%d" % n) if n > 0 else ("-%d" % -n) if n < 0 else "")


def at(x, y, z):
    return "%s %s %s" % (o('X', x), o('Y', y), o('Z', z))


S = []


def step(**kw):
    S.append(kw)


def cmd(label, *run, **kw):
    step(label=label, do="cmd", run=list(run), **kw)


def echo(label, c, **kw):
    step(label=label, do="cmd", run=[c], allowFail=True, echo=True, **kw)


def wait_cmd(label, c, match, timeout=60):
    step(label=label, do="waitCmd", run=c, match=match, timeoutSec=timeout)


def tp(x, y, z, yaw=0, pitch=0):
    step(do="tp", at=[x, y, z], yaw=yaw, pitch=pitch)


def aim(x, y, z):
    step(do="aim", at=[x, y, z])


def shot(name, look):
    step(name=name, do="shot", look=look)


def wait(ms):
    step(do="wait", ms=ms)


def hold(item):
    step(do="hold", item=item)


# ── layout (offsets from base) ─────────────────────────────────────────────────────────────────────────────────────
SRC_X = -10          # the source structure's west edge (box-local x 0)
SHIFT = 18           # the Builder rebuilds it this many blocks east
SITE_X = SRC_X + SHIFT
ARCH = (SRC_X + 1, 0, -2)           # faces north, its box behind it (south): set by NBT below all the same
BUILDER = (ARCH[0] + SHIFT, 0, -2)
CHEST = (-6, 0, -2)


def src(lx, ly, lz):
    return SRC_X + lx, ly, lz


def site(lx, ly, lz):
    return SITE_X + lx, ly, lz


# Box-local cells of the blueprint, as the source builds them (y0 = the ground layer).
RING = [(0, 0, 0), (1, 0, 0), (2, 0, 0), (0, 0, 1), (2, 0, 1), (0, 0, 2), (1, 0, 2), (2, 0, 2)]
STONES = RING + [(0, 1, 0), (0, 1, 2)]
TORCH_IN_RING = (1, 0, 1)
TORCH_FLOATING = (1, 2, 1)
# What the site has before the Builder starts: the ring as built but for its west cell, which is the water source, a
# stone outside the box walling the source in on the west, and bedrock where the blueprint has air.
SITE_STONES = [c for c in RING if c != (0, 0, 1)] + [(-1, 0, 1)]
SITE_WATER = (0, 0, 1)
SITE_BEDROCK = (2, 1, 0)
BOX_LOCAL = ((0, 0, 0), (2, 2, 2))


def box_nbt(origin_x):
    (x0, y0, z0), (x1, y1, z1) = BOX_LOCAL
    return ("box_initialized:1b,box_minX:%s,box_minY:%s,box_minZ:%s,box_maxX:%s,box_maxY:%s,box_maxZ:%s"
            % (o('X', origin_x + x0), o('Y', y0), o('Z', z0), o('X', origin_x + x1), o('Y', y1), o('Z', z1)))


# ── setup ──────────────────────────────────────────────────────────────────────────────────────────────────────────
cmd("load the rig's chunks", "gamemode creative @p", "?forceload add {X-26} {Z-12} {X+26} {Z+16}")
tp(0, 0, 8, 180, 10)
wait(3000)
step(label="window 1600x900 (GUI coordinates below assume it with guiScale 3)", do="window", width=1600, height=900)
cmd("clear and floor",
    "difficulty peaceful", "time set 6000", "weather clear", "?clear @p",
    "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..60]",
    "?fill {X-22} {Y} {Z-10} {X+22} {Y+9} {Z+14} minecraft:air",
    "fill {X-22} {Y-1} {Z-10} {X+22} {Y-1} {Z+14} minecraft:smooth_stone",
    "?advancement grant @p everything")
wait(4000)

# ── the structure that gets scanned ────────────────────────────────────────────────────────────────────────────────
# Torches last, so no later setblock next to the floating one gives it a neighbour update that could pop it.
cmd("source structure: a ring of stone with a torch in it, two more stones, a torch hanging over air",
    *["setblock %s minecraft:stone" % at(*src(*c)) for c in STONES],
    "setblock %s minecraft:torch" % at(*src(*TORCH_IN_RING)),
    "setblock %s minecraft:torch" % at(*src(*TORCH_FLOATING)))
echo("the floating torch is still there (a popped one would read air)", "execute if block %s minecraft:torch"
     % at(*src(*TORCH_FLOATING)))

cmd("architect table facing north with its box set and a blank blueprint in",
    "setblock %s %sarchitect[facing=north]" % (at(*ARCH), BC),
    'data merge block %s {%s,isValid:1b,name:"stall-rig",items:{in:{items:[{stack:{id:"%sblueprint_clean",count:1}}]},'
    'out:{items:[]}}}' % (at(*ARCH), box_nbt(SRC_X), BC))
wait_cmd("the architect finished its blueprint", "data get block %s items.out.items[0].stack.id" % at(*ARCH),
         "blueprint_used", 40)

# The used blueprint reaches the player's inventory the way the upgrade scenarios do it (a chest is the only block whose
# slots `item replace` can read), then the GUI moves it into the Builder.
cmd("chest: the used blueprint in slot 0",
    "setblock %s minecraft:chest[facing=south]" % at(*CHEST),
    "data merge block %s {Items:[{Slot:0b,id:\"minecraft:stone\",count:1}]}" % at(*CHEST),
    "data modify block %s Items[{Slot:0b}] merge from block %s items.out.items[0].stack" % (at(*CHEST), at(*ARCH)),
    "item replace entity @p inventory.0 from block %s container.0" % at(*CHEST))

# ── the site ───────────────────────────────────────────────────────────────────────────────────────────────────────
cmd("site: the ring but for its west cell, the wall outside it, the bedrock, then the water source",
    *["setblock %s minecraft:stone" % at(*site(*c)) for c in SITE_STONES],
    "setblock %s minecraft:bedrock" % at(*site(*SITE_BEDROCK)),
    "setblock %s minecraft:water" % at(*site(*SITE_WATER)))
cmd("Builder facing north: owner, power, torches and no stone",
    "setblock %s %sbuilder[facing=north]" % (at(*BUILDER), BC),
    'data merge block %s {ownerUUID:"%s",ownerName:"Dev",battery_mj:16000000000L,'
    'items:{resources:{items:[{stack:{id:"minecraft:torch",count:16}}]}}}' % (at(*BUILDER), DEV_UUID))
wait(3000)   # the source flows into the walled cell beside it (the torch cell)
echo("the water has flowed into the torch cell too", "execute if block %s minecraft:water" % at(*site(*TORCH_IN_RING)))

# Invisible barrier blocks to stand on, for the shots from above (a glass block would sit in the corner of the frame).
WIDE = (6, 2, 5)      # standing spot of the wide view (feet), on a barrier one block lower
CLOSE = (9, 2, 4)     # straight south of the ring's middle, higher up: the torch cell's blue cube shows over the ring's rim
cmd("viewpoints: barrier blocks to stand on", "setblock %s minecraft:barrier" % at(WIDE[0], WIDE[1] - 1, WIDE[2]),
    "setblock %s minecraft:barrier" % at(CLOSE[0], CLOSE[1] - 1, CLOSE[2]))
tp(*WIDE, 45, 35)
aim(*site(1, 1, 1))
wait(800)
shot("site-before", "the prepared site before the Builder has a blueprint: a ring of stone with a gap holding water, "
     "water inside the ring, one bedrock block above it, no outlines")

# ── feed the Builder through its GUI ───────────────────────────────────────────────────────────────────────────────
hold("minecraft:air")
tp(BUILDER[0], 0, -6, 0, 10)
aim(*BUILDER)
wait(300)
step(label="open the Builder", do="use")
step(do="expectScreen", contains="GuiBuilder", timeoutSec=10)
step(name="builder-open", do="widgets")
step(label="pick the blueprint up from the inventory", do="click", slotIndex=52)
step(label="put it in the Builder's snapshot slot", do="click", slotIndex=0)
step(label="cycle the fluid mode once: Do not replace -> Replace fluids", do="click", widgetText="Do not replace fluids")
wait(1500)
echo("the Builder's fluid mode (expect 1: REPLACE)", "data get block %s fluidMode" % at(*BUILDER))
shot("builder-loaded", "the Builder holds the blueprint; the resource list shows the stone and torches it needs")
step(do="close")

wait_cmd("the server's stall counts: 1 can't stand, 1 can't be broken, 0 refused, 3 missing items, 1 fluid",
         "data get block %s builderClientData.stalledCounts" % at(*BUILDER), "1, 1, 0, 3, 1", 120)
echo("the stalled cells (box x y z triples) and their reasons (0 can't stand, 1 unbreakable, 3 missing items, 4 fluid)",
     "data get block %s builderClientData" % at(*BUILDER))
wait(5000)   # the sync every 5 ticks has long since reached the client

# ── 1. the site, holding a Marker Wrench ───────────────────────────────────────────────────────────────────────────
# Each wait after a `hold` lets the held-item name above the hotbar fade (about 2 s) before the shot.
tp(*WIDE, 45, 35)
aim(*site(1, 1, 1))
hold(BC + "marker_wrench")
wait(2800)
shot("stall-marker-wrench", "holding a Marker Wrench, seen from the south-west: solid laser cubes round the six stalled "
     "cells and none round the built or empty ones. RED x2: the floating torch's cell two above the ring's middle and "
     "the bedrock cell on the ring's north-east corner; YELLOW x3: the water-filled cell on the ring's west side and the "
     "two stone cells above its north-west and south-west corners; BLUE x1: the torch cell in the ring's middle. The "
     "Builder's own hazard-striped box surrounds them all")
hold(BC + "volume_wrench")
wait(2800)
shot("stall-volume-wrench", "the same view holding a Volume Wrench: the same five outlines")

# ── 2. the same view, empty hand ───────────────────────────────────────────────────────────────────────────────────
hold("minecraft:air")
wait(2800)
shot("stall-empty-hand", "the same view with an empty hand: NO stalled-cell outlines, only the Builder's own hazard-striped "
     "box")

# ── a closer look with the Marker Wrench ───────────────────────────────────────────────────────────────────────────
tp(*CLOSE, 40, 42)
aim(*site(1, 1, 1))
hold(BC + "marker_wrench")
wait(2800)
shot("stall-close", "closer, Marker Wrench held: red, yellow and blue cubes distinguishable, one per stalled cell")
hold("minecraft:air")

# ── 3. the Builder GUI ─────────────────────────────────────────────────────────────────────────────────────────────
tp(BUILDER[0], 0, -6, 0, 10)
aim(*BUILDER)
wait(300)
step(label="open the Builder again", do="use")
step(do="expectScreen", contains="GuiBuilder", timeoutSec=10)
step(name="builder-stalled", do="widgets")
wait(500)
shot("builder-stalled-gui", "the Builder GUI: the Left to Break / Left to Place readout is dark red (stalled)")
# GUI units: 534 x 300 screen, the 256 x 222 GUI centred: left (534 - 256) / 2 = 139, top (300 - 222) / 2 = 39; the
# readout starts at GUI-local (10, 50), so its first line is at screen (149, 89), 110 wide and 10 high.
step(label="hover the readout", do="hover", x=165, y=93)
shot("builder-stalled-tooltip", "hovering the readout: a tooltip 'Stalled: 1 can't stand, 1 can't be broken, 3 missing "
     "items, 1 held back by fluid'")
step(do="close")

# ── 4. the outlines follow the build: fix the cause of the yellow and blue cells ───────────────────────────────────
cmd("give the Builder stone", 'data merge block %s {items:{resources:{items:[{stack:{id:"minecraft:torch",count:16}},'
    '{stack:{id:"minecraft:stone",count:16}}]}}}' % at(*BUILDER))
wait_cmd("the Builder finished what it could and is stalled on the two red cells only",
         "data get block %s builderClientData.stalledCounts" % at(*BUILDER), "1, 1, 0, 0, 0", 120)
echo("the stalled cells now (box triples and reasons): the floating torch and the bedrock only",
     "data get block %s builderClientData" % at(*BUILDER))
wait(5000)
tp(*WIDE, 45, 35)
aim(*site(1, 1, 1))
hold(BC + "marker_wrench")
wait(2800)
shot("stall-after-stone", "after the Builder was given stone: the three stones and the torch in the ring are built, the "
     "yellow and blue outlines are gone, only the two red cubes (floating torch's cell, bedrock cell) remain")
hold("minecraft:air")

scenario = {
    "name": "builder-stall",
    "description": "Issue #39 on 26.3: a Builder with a stalled build outlines the cells it is skipping (red can't stand "
                   "or can't be broken, yellow missing items, blue fluid) for a player holding a Marker or Volume "
                   "Wrench, and its GUI readout turns dark red with a stalled tooltip. Generated by "
                   "generators/gen_builder_stall.py, whose docstring describes the rig.",
    "base": [0, 150, 0],
    "guiScale": 3,
    "steps": S,
}
with open(OUT, "w", newline=chr(10)) as f:
    f.write(json.dumps(scenario, indent=1) + chr(10))
print("wrote %s (%d steps)" % (os.path.normpath(OUT), len(S)))
