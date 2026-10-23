"""Release 2026.2.1 visual checks: one scenario, one shot per thing a person must judge, grouped by item.

Run: python scripts/smoke/generators/gen_release_2026_2_1.py  (rewrites scripts/smoke/scenarios/release-2026-2-1.json)
Then: pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3 -Scenario scripts/smoke/scenarios/release-2026-2-1.json

Shot names carry the item letter, so the review goes one item at a time:
  A  robots glide on the client (EntityRobot interpolation): a Carrier shuttling between two Docking Stations twelve
     blocks apart. Smoothness only shows live, so the run pauses 20 s on the view ("WATCH LIVE"); the burst of shots
     taken mid-flight is the still evidence (robot positions between 20 Hz server updates).
  B  a stripes pipe retracting sideways turns on the client (PipeExtensionManager perpendicular branch), plus the
     plain extension as a sanity check. The L rig: a wooden pipe pulls from a chest (TILE connection, so the straight
     retraction is refused) into a stripes pipe pointed EAST that also touches a cobblestone pipe to the north. The
     wooden pipe's red lens dyes what it pulls, and the blue FILTER lens on the stripes pipe's north face refuses red
     items, so the void pipe drops out of the stripes pipe instead of wandering into the cobblestone pipe. The stripes
     pipe then retracts NORTH onto the cobblestone pipe's cell and must point SOUTH (its gold beam stub).
  C  a guide link part wrapped over a page break clicks on both pages (GuideInlineLinkText / GuidePart page tags). No
     shipped link TITLE splits across the break at any lore/hint setting (all 21 pages with multi-word inline links
     were scanned on 26.3); the case the fix is about is the PART: "Set Power Limit" (lore on, the default) has one
     paragraph that starts on page 1 with the "Iron FE Pipe" link on the page's last row and carries on at the top of
     page 2 with "Diamond FE Pipe". Before the fix only the last page's links clicked.
  D  Programming Table paging: no shipped recipe has more than 24 options (one board recipe, 18 boards incl. blank),
     so the pager never shows in normal play; the shot pins the grid with the pager absent.
  E  cheap extras from docs/todo-details.md "In-client smoke 2026-09-26" (blocks-atlas sweep render sites): gate
     plugs, LED variable models, the Architect's pipe-preview pluggables, painted fluid-pipe item models.

GUI coordinates assume the scenario's window (1600x900) and guiScale 3, i.e. a 534x300 GUI.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from robotrig import o, station, deploy, echo as echo_step, write  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "scenarios", "release-2026-2-1.json")

S = []


def step(**kw):
    S.append(kw)


def cmd(label, *run, **kw):
    step(label=label, do="cmd", run=list(run), **kw)


def tp(x, y, z, yaw=0, pitch=0):
    step(do="tp", at=[x, y, z], yaw=yaw, pitch=pitch)


def aim(x, y, z):
    step(do="aim", at=[x, y, z])


def shot(name, look):
    step(name=name, do="shot", look=look)


def wait(ms, label=None):
    if label:
        step(label=label, do="wait", ms=ms)
    else:
        step(do="wait", ms=ms)


def hold(item):
    step(do="hold", item=item)


def at(x, y, z):
    return "%s %s %s" % (o('X', x), o('Y', y), o('Z', z))


def pipe(x, y, z, pdef, extra=""):
    """A pipe holder with a pipe. `extra` is appended inside the top-level compound (plugs, ...)."""
    return ["setblock %s buildcraftunofficial:pipe_holder" % at(x, y, z),
            'data merge block %s {pipe:{def:"buildcraftunofficial:%s"}%s}' % (at(x, y, z), pdef, extra)]


def echo(label, c):
    S.append(echo_step(label, c))


def wait_cmd(label, run, match, timeout=60, poll=1000):
    step(label=label, do="waitCmd", run=run, match=match, timeoutSec=timeout, pollMs=poll)


# The base keeps every rig coordinate positive, so "{X+n}.5" spells a block centre (robot dock checks).
BASE = [32, 150, 32]
ROBOT = "buildcraftunofficial:robot"

# ── setup ──────────────────────────────────────────────────────────────────────────────────────────────────────
step(label="window 1600x900 (GUI coordinates below assume it with guiScale 3)", do="window", width=1600, height=900)
cmd("load the rig's chunks", "gamemode creative @p", "?forceload add {X-16} {Z-24} {X+16} {Z+16}")
tp(0, 0, -8, 0, 12)
wait(3000)
cmd("clear and floor",
    "difficulty peaceful", "time set 6000", "?gamerule doDaylightCycle false", "?gamerule advance_time false",
    "weather clear", "?clear @p",
    "?kill @e[type=%s,x={X},y={Y},z={Z},distance=..80]" % ROBOT,
    "?fill {X-16} {Y} {Z-24} {X+16} {Y+8} {Z+16} minecraft:air",
    "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..80]",
    "fill {X-16} {Y-1} {Z-24} {X+16} {Y-1} {Z+16} minecraft:smooth_stone",
    # Every advancement up front: the rigs fire several, and their toasts would cover the shots.
    "?advancement grant @p everything")
wait(3000)

# ── A: robots glide ────────────────────────────────────────────────────────────────────────────────────────────
cmd("A: supply station (27 stacks of cobblestone, Provide Items + Load/Unload Area over both stations)",
    *station(-6, 0, ["minecraft:cobblestone 64"] * 27, "buildcraft:station.provide_items", (-7, -1, -1, 7, 3, 1),
             zaction="buildcraft:robot.load_unload_area"))
cmd("A: destination station twelve blocks east of it (Accept Items)",
    *station(6, 0, [], "buildcraft:station.accept_items"))
S.extend(deploy("A: deploy a Carrier at the supply station (its home)", -6, 0, "boardRobotCarrier"))
hold("minecraft:air")
echo("A: robots in the rig (expect Test passed, Count: 1)",
     "execute if entity @e[type=%s,x={X},y={Y},z={Z},distance=..20]" % ROBOT)
tp(0, 0, -8, 0, 12)
wait(1500)
shot("a1-rig", "A: the two Docking Stations on chests, supply (screen right, x-6) and destination (screen left, x+6), "
     "and the Carrier robot somewhere between or docked on one of them")
wait(20000, "WATCH LIVE: robot gliding - the Carrier must fly between the stations without 20 Hz stepping, and "
            "dock / undock with a short glide, not a jump")
wait_cmd("A: wait until the robot is docked at the destination (block centre, 0.5 lower than in flight)",
         "execute if entity @e[type=%s,x={X+6}.5,y={Y+2},z={Z}.5,distance=..0.05]" % ROBOT, "Test passed",
         timeout=60, poll=100)
shot("a2-docked", "A: the robot sits docked on the destination station (screen left), level with the station, "
     "not floating or sunk")
wait_cmd("A: wait until the robot is in mid-flight (the middle four blocks of the run)",
         "execute if entity @e[type=%s,x={X-2},y={Y+1},z={Z-1},dx=4,dy=3,dz=2]" % ROBOT, "Test passed",
         timeout=60, poll=100)
for i in range(1, 6):
    shot("a3-flight-%d" % i, "A: burst %d/5, shots taken back to back mid-flight: the robot should be at a slightly "
                             "different place in each (a robot that stays put for several shots and then jumps "
                             "is stepping)" % i)

# ── B: stripes retraction turns on the client, extension sanity ───────────────────────────────────────────────
cmd("B: retraction rig, cobblestone pipe north of where the stripes pipe goes",
    *pipe(-11, 0, 7, "cobblestone_item"))
# Touching two pipes, the stripes pipe would point nowhere by itself; a saved direction on an unconnected face stays.
cmd("B: stripes pipe pointed EAST, blue filter lens on its north face",
    "setblock %s buildcraftunofficial:pipe_holder" % at(-11, 0, 8),
    'data merge block %s {pipe:{def:"buildcraftunofficial:stripes_item",beh:{direction:"EAST"}},'
    'plugs:{north:{id:"buildcraftunofficial:lens",data:{colour:"blue",f:1b}}}}' % at(-11, 0, 8))
cmd("B: wooden pipe with a red (dyeing) lens on the chest side, chest, redstone engine on top",
    *pipe(-12, 0, 8, "wood_item", ',plugs:{west:{id:"buildcraftunofficial:lens",data:{colour:"red",f:0b}}}'),
    "setblock %s minecraft:chest[facing=north]" % at(-13, 0, 8),
    "setblock %s buildcraftunofficial:engine_redstone[facing=down]" % at(-12, 1, 8),
    "item replace block %s container.0 with buildcraftunofficial:pipe_void_item" % at(-13, 0, 8))
cmd("B: extension rig: chest, wooden pipe, stripes pipe (points EAST by itself), redstone engine",
    "setblock %s minecraft:chest[facing=north]" % at(-13, 0, 12),
    *pipe(-12, 0, 12, "wood_item"), *pipe(-11, 0, 12, "stripes_item"),
    "setblock %s buildcraftunofficial:engine_redstone[facing=down]" % at(-12, 1, 12),
    "item replace block %s container.0 with buildcraftunofficial:pipe_stone_item" % at(-13, 0, 12))
echo("B: server: stripes pipe before (expect direction EAST)", "data get block %s pipe.beh" % at(-11, 0, 8))
tp(-8, 0, 11)
aim(-11, 0, 8)
wait(1500)
shot("b1-retract-before", "B: the L: chest, wooden pipe (red lens on the chest side, redstone engine on top), yellow "
     "stripes pipe with its small gold beam stub pointing EAST (toward the camera's right) and a blue lens on its far "
     "(north) side touching a cobblestone pipe behind it")
cmd("B: power both engines (the wooden pipes pull the void pipe and the stone pipe)",
    "setblock %s minecraft:redstone_block" % at(-12, 2, 8), "setblock %s minecraft:redstone_block" % at(-12, 2, 12))
wait_cmd("B: server: the stripes pipe moved north onto the cobblestone pipe's cell",
         "data get block %s pipe.def" % at(-11, 0, 7), "stripes_item", timeout=60)
echo("B: server: its direction now (expect SOUTH)", "data get block %s pipe.beh.direction" % at(-11, 0, 7))
wait(1500)
shot("b2-retract-after", "B: the stripes pipe now stands in the cobblestone pipe's old cell (north, behind) with its gold "
     "beam stub pointing SOUTH (toward the camera, at the empty cell it came from); its old cell is empty, the "
     "cobblestone pipe and void pipe lie on the floor as items. A gold stub still pointing EAST is the bug the fix "
     "was for; a black cobblestone pipe drawn in that cell instead of the yellow stripes pipe is a different finding "
     "(the client keeping the old pipe of a cell that was emptied and refilled in one tick)")
wait_cmd("B: server: the extension moved the stripes pipe one block east",
         "data get block %s pipe.def" % at(-10, 0, 12), "stripes_item", timeout=60)
echo("B: server: the cell it left now holds the stone pipe", "data get block %s pipe.def" % at(-11, 0, 12))
tp(-9, 0, 15)
aim(-11, 0, 12)
wait(1500)
shot("b3-extend", "B sanity (unchanged behaviour): the extension line: chest, wooden pipe, a grey STONE pipe in the "
     "stripes pipe's old cell, then the yellow stripes pipe one block further east with its gold stub pointing east. "
     "A yellow stripes pipe drawn in the stone pipe's cell is the stale-cell finding again")

# ── C: guide link part wrapped over a page break ──────────────────────────────────────────────────────────────
hold("buildcraftunofficial:guide")
step(do="look", yaw=0, pitch=-90)
wait(300)
step(label="C: open the guide", do="use")
step(do="expectScreen", contains="GuiGuide", timeoutSec=20)
step(label="C: open the cover", do="click", fx=0.5, fy=0.5)
wait(2500)
step(label="C: focus the search tab", do="click", x=112, y=36)
step(do="text", text="Set Power Limit")
wait(1000)
step(label="C: open the Set Power Limit page (third result row)", do="click", x=140, y=87)
wait(1500)
step(label="C: park the cursor off the text", do="hover", x=10, y=10)
shot("c1-spread", "C: Set Power Limit, pages 1/6 and 2/6. The paragraph 'The MJ kinesis pipes (...' ends page 1 with "
     "'The FE/RF pipes (Iron FE Pipe' on the last row and carries on at the top of page 2 with 'and Diamond FE Pipe) "
     "expose the ...': one link part over the page break, a blue link on each page")
step(label="C: hover the page-1 link (Iron FE Pipe, last row of page 1)", do="hover", x=217, y=235)
shot("c2-hover-page1", "C: the Iron FE Pipe link on page 1 shows its hover highlight")
step(label="C: click it", do="click", x=217, y=235)
wait(1200)
shot("c3-click-page1", "C: the click on page 1 opened the Iron FE Pipe page (before the fix it did nothing)")
step(label="C: back", do="click", widgetText="Back")
wait(1200)
step(label="C: hover the page-2 link (Diamond FE Pipe, top row of page 2)", do="hover", x=331, y=55)
shot("c4-hover-page2", "C: back on Set Power Limit 1/6 and 2/6; the Diamond FE Pipe link on page 2 shows its hover "
     "highlight")
step(label="C: click it", do="click", x=331, y=55)
wait(1200)
shot("c5-click-page2", "C: the click on page 2 opened the Diamond FE Pipe page")
step(label="C: back", do="click", widgetText="Back")
wait(1200)
step(label="C: click page 1 where the page-2 link would sit in page 1's column (plain text there)",
     do="click", x=157, y=55)
wait(1200)
step(label="C: park the cursor off the text", do="hover", x=10, y=10)
shot("c6-no-phantom", "C: still Set Power Limit 1/6: a click on page 1's plain text at the page-2 link's position opens "
     "nothing (the old phantom hit opened Diamond FE Pipe from here)")
step(do="close")
hold("minecraft:air")

# ── D: Programming Table paging (no shipped recipe has more than 24 options) ───────────────────────────────────
cmd("D: programming table, four blank boards in inventory.0 (menu slot 26)",
    "setblock %s buildcraftunofficial:programming_table" % at(10, 0, -8),
    "item replace entity @p inventory.0 with buildcraftunofficial:redstone_board 4")
tp(10, 0, -6, 180, 30)
aim(10, 0, -8)
step(label="D: open the table", do="use")
step(do="expectScreen", contains="GuiProgrammingTable", timeoutSec=10)
step(label="D: pick up the boards", do="click", slotIndex=26)
step(label="D: put them in the input slot", do="click", slotIndex=0)
wait(800)
step(label="D: park the cursor", do="hover", x=10, y=10)
step(name="d-programming", do="widgets")
shot("d1-programming-table", "D: blank board in the input slot, the option grid filled left to right with all 18 "
     "boards (blank board first, cheapest to dearest) over three rows, the fourth row empty, and NO '<' / '>' pager "
     "buttons under the grid (the widget dump lists them visible:false). No shipped recipe has more than 24 options, "
     "so the pager cannot show in normal play")
step(label="D: hover the last option (the 18th board)", do="hover", slotIndex=19)
shot("d2-last-option", "D: tooltip of the last option: the dearest board, the Builder board")
step(do="close")
cmd("D: take the boards back out of the world", "?clear @p buildcraftunofficial:redstone_board",
    "setblock %s minecraft:air" % at(10, 0, -8))

# ── E: extras from the 2026-09-26 still-owed list ──────────────────────────────────────────────────────────────
GATE_ACTIVE = ('{variant:{logic:0b,material:1b,modifier:0b},'
               '"trigger[0]":{s:{kind:"buildcraftunofficial:trigger.true",side:6b}},'
               '"action[0]":{s:{kind:"buildcraft:redstone.output",side:6b}}}')
gates = ['{variant:{logic:0b,material:0b,modifier:0b}}', '{variant:{logic:1b,material:1b,modifier:1b}}',
         '{variant:{logic:0b,material:2b,modifier:2b}}', '{variant:{logic:1b,material:3b,modifier:3b}}', GATE_ACTIVE]
gate_cmds = []
for i, g in enumerate(gates):
    gate_cmds += pipe(-8 + 2 * i, 0, -18, "stone_item",
                      ',plugs:{south:{id:"buildcraftunofficial:gate",data:{data:%s}}}' % g)
cmd("E: gate plugs on the south face of five stone pipes", *gate_cmds)
tp(-4, 0, -12, 180, 15)  # six blocks back; the LED row (z-13) goes in after this shot
aim(-4, 0, -18)
wait(1500)
shot("e1-gate-plugs", "E gate plugs (camera faces north, so west is screen left): from screen left to right: clay AND, iron "
     "OR lapis, nether-brick AND quartz, gold OR diamond, and an iron AND whose trigger is always true (lit, red "
     "lamp on). Textured gate faces, no missing-texture squares or black faces")
cmd("E: LED machines in a row (filler, quarry, mining well, pump, architect table, zone planner)",
    "setblock %s buildcraftunofficial:filler[facing=south]" % at(-8, 0, -13),
    "setblock %s buildcraftunofficial:quarry[facing=south]" % at(-6, 0, -13),
    "setblock %s buildcraftunofficial:mining_well" % at(-4, 0, -13),
    "setblock %s buildcraftunofficial:pump" % at(-2, 0, -13),
    "setblock %s buildcraftunofficial:architect[facing=south]" % at(0, 0, -13),
    "setblock %s buildcraftunofficial:zone_planner[facing=south]" % at(2, 0, -13))
tp(-3, 0, -7, 180, 15)
aim(-3, 0, -13)
wait(1500)
shot("e2-led-models", "E LED variable models: filler, quarry, mining well, pump, architect table, zone planner (screen "
     "left to right) with their small LED lights drawn on the faces, idle colours, no missing-texture squares")
cmd("E: a plugged pipe inside a 3x1x3 marked volume (gate, red lens, pulsar, docking station, blocker)",
    "setblock %s buildcraftunofficial:marker_volume" % at(9, 0, -18),
    "setblock %s buildcraftunofficial:marker_volume" % at(11, 0, -18),
    "setblock %s buildcraftunofficial:marker_volume" % at(9, 0, -16),
    *pipe(10, 0, -17, "stone_item",
          ',plugs:{up:{id:"buildcraftunofficial:gate",data:{data:{variant:{logic:1b,material:3b,modifier:3b}}}},'
          'north:{id:"buildcraftunofficial:lens",data:{colour:"red",f:0b}},east:{id:"buildcraftunofficial:pulsar",data:{}},'
          'south:{id:"buildcraftunofficial:robot_station",data:{}},west:{id:"buildcraftunofficial:blocker",data:{}}}'))
tp(9, 0, -20, 0, 30)
aim(9, 0, -18)
step(label="E: connect the volume markers", do="use")
wait(500)
hold("buildcraftunofficial:architect")
tp(9, 0, -21, 0, 30)
aim(9, -1, -19)
step(label="E: place the architect table in front of the marker corner (its back to the volume)", do="use")
wait(800)
hold("minecraft:air")
echo("E: architect found the volume (expect markerBox: 1b)", "data get block %s markerBox" % at(9, 0, -19))
aim(9, 0, -19)
step(label="E: open the architect table", do="use")
step(do="expectScreen", contains="GuiArchitectTable", timeoutSec=10)
wait(2500)
step(label="E: park the cursor", do="hover", x=10, y=10)
shot("e3-architect-preview", "E pipe-preview pluggables: the Architect's rotating preview at the top shows the stone "
     "pipe WITH its plugs: a gold gate on top, red lens, pulsar, white docking station, blocker (small: crop the "
     "middle of the preview). Bare pipe with no plugs is the finding")
step(do="close")
chest = []
for i, (p, col) in enumerate([("stone_fluid", "red"), ("cobble_fluid", "blue"), ("wood_fluid", "lime"),
                              ("gold_fluid", "yellow"), ("iron_fluid", "purple"), ("quartz_fluid", "black"),
                              ("diamond_fluid", "orange"), ("sandstone_fluid", "cyan"), ("stone_fluid", None)]):
    comp = (',components:{"buildcraftunofficial:pipe_colour":"%s"}' % col) if col else ''
    chest.append('{Slot:%db,id:"buildcraftunofficial:pipe_%s",count:1%s}' % (i, p, comp))
cmd("E: chest of painted fluid pipes (and one unpainted stone fluid pipe last)",
    "setblock %s minecraft:chest[facing=south]{Items:[%s]}" % (at(4, 0, -8), ",".join(chest)))
tp(4, 0, -6, 180, 30)
aim(4, 0, -8)
step(label="E: open the chest", do="use")
step(do="expectScreen", contains="ContainerScreen")
wait(500)
step(label="E: park the cursor", do="hover", x=10, y=10)
shot("e4-painted-fluid-pipes", "E painted fluid-pipe item models, top row: stone red, cobblestone blue, wooden lime, gold "
     "yellow, iron purple, quartz black, diamond orange, sandstone cyan, then an unpainted stone fluid pipe: each "
     "painted one carries its colour on the pipe frame, none renders black or as a missing texture")
step(label="E: hover the red stone fluid pipe", do="hover", slotIndex=0)
shot("e5-painted-tooltip", "E: tooltip names the colour and the pipe (e.g. 'Red Stone Fluid Pipe')")
step(do="close")
hold('buildcraftunofficial:pipe_stone_fluid[buildcraftunofficial:pipe_colour="red"]')
step(do="look", yaw=180, pitch=20)
wait(1500)
shot("e6-painted-in-hand", "E: the red-painted stone fluid pipe in hand, red frame like in the GUI slot")
hold("minecraft:air")

scenario = {
    "name": "release-2026-2-1",
    "description": "Release 2026.2.1 visual checks, one item per shot-name letter: A robots glide (watch live + "
                   "mid-flight burst), B stripes sideways retraction turns on the client (+ extension sanity), C guide "
                   "link part over a page break clicks on both pages, D Programming Table pager absent (no recipe has "
                   "more than 24 options), E gate plugs / LED models / architect preview pluggables / painted fluid "
                   "pipe items. Generated by generators/gen_release_2026_2_1.py; its docstring explains the rigs.",
    "base": BASE,
    "guiScale": 3,
    "steps": S,
}
write(OUT, scenario)
print("wrote %s (%d steps)" % (os.path.normpath(OUT), len(S)))
