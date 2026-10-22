"""26.3 port in-client verification (docs/port-26.3.md, Wave 3 V2): one scenario for 26.3 and its 26.2 baseline.

Run: python scripts/smoke/generators/gen_port_26_3.py  (rewrites scripts/smoke/scenarios/port-26-3.json)

Compare the same shot on 26.3 and 26.2: a 26.3-only difference is the finding. Labels name the plan's B-ids.

B1 (OIT): run 26.3 twice, `improvedTransparency:false` then `:true` in versions/26.3/run-smoke/options.txt (edit it
while no client runs). Everything that renders BuildCraft's own LED / debug render types is built in the LAST
section, so an OIT run that dies there has already taken every other shot; the closing cleanup removes those blocks,
because a world saved with them in view would die again on the next load.

On 26.3 (SDL3) a Win32 Esc posted to an unfocused window is dropped, so the runner's `pause` step falls back to
focusing the game window and pressing a real Esc; the step's Detail says when it needed that.

The oil section only finds a deposit when the run dir's config raises
`worldgen.oil.spawnProbability.mediumOilGenProb` (buildcraftunofficial-common.toml); with the default the
`place feature` command reports a failure and the shots show bare terrain.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "scenarios", "port-26-3.json")


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


def pipe(x, y, z, pdef, extra=""):
    return ["setblock %s buildcraftunofficial:pipe_holder" % at(x, y, z),
            'data merge block %s {pipe:{def:"buildcraftunofficial:%s"%s}}' % (at(x, y, z), pdef, extra)]


def plugged(x, y, z, pdef, plugs):
    return ["setblock %s buildcraftunofficial:pipe_holder" % at(x, y, z),
            'data merge block %s {pipe:{def:"buildcraftunofficial:%s"},plugs:{%s}}' % (at(x, y, z), pdef, plugs)]


# Facades: the legacy block-state key (Name, 26.2 and older) and the 26.3 key (id). Both must read back on every
# node (B0 compat helper), so a blank or missing facade on either spelling is a finding.
FACADE_OLD = '{isHollow:0b,states:[{state:{Name:"minecraft:oak_planks"}}]}'
FACADE_NEW = '{isHollow:0b,states:[{state:{id:"minecraft:stone_bricks"}}]}'
GATE_IRON = ('{variant:{logic:0b,material:1b,modifier:0b},'
             '"trigger[0]":{s:{kind:"buildcraftunofficial:trigger.true",side:6b}},'
             '"action[0]":{s:{kind:"buildcraft:redstone.output",side:6b}}}')
GATE_GOLD = '{variant:{logic:1b,material:3b,modifier:3b}}'

ITEM_FACADE_OLD = 'buildcraftunofficial:plug_facade[minecraft:custom_data={facade:%s}]' % FACADE_OLD
ITEM_FACADE_NEW = 'buildcraftunofficial:plug_facade[minecraft:custom_data={facade:%s}]' % FACADE_NEW
ITEM_GATE = 'buildcraftunofficial:plug_gate[minecraft:custom_data={gate:{logic:0b,material:3b,modifier:3b}}]'
ITEM_LENS = 'buildcraftunofficial:plug_lens[minecraft:custom_data={colour:"red",isFilter:0b}]'


def shard(fluid):
    return 'buildcraftunofficial:fragile_fluid_container[buildcraftunofficial:fluid_content={id:"%s",amount:1000}]' % fluid


def chest_item(slot, item_id, components=None):
    c = (',components:{%s}' % components) if components else ''
    return '{Slot:%db,id:"%s",count:1%s}' % (slot, item_id, c)


# ── setup ──────────────────────────────────────────────────────────────────────
cmd("load the rig's chunks", "gamemode creative @p", "?forceload add {X-16} {Z-20} {X+16} {Z+24}")
tp(0, 0, 4, 180, 10)
wait(3000)
cmd("clear and floor",
    "difficulty peaceful", "time set 6000", "weather clear", "?clear @p",
    "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..60]",
    "?fill {X-14} {Y} {Z-18} {X+14} {Y+7} {Z+22} minecraft:air",
    "fill {X-14} {Y-1} {Z-18} {X+14} {Y-1} {Z+22} minecraft:smooth_stone",
    # Every advancement up front: the rig fires several, and their toasts and chat lines would cover the shots.
    "?advancement grant @p everything")
wait(8000)

# The runner's `pause` posts a Win32 Esc to the game window; 26.3 reads input through SDL3, not GLFW.
step(label="runner pause step: Win32 Esc opens the pause menu", do="pause")
shot("pause-menu", "the pause menu over the world (26.3 runs SDL3: a missing menu means the runner's Esc post needs changing)")
step(label="back to the game", do="click", widgetText="Back to Game", allowMiss=True)
step(do="expectScreen", contains="", timeoutSec=5)

# ── B1: translucent things, markers and their lasers (no BuildCraft LED/debug type here) ──────────────────────
cmd("translucency row",
    "setblock %s minecraft:red_stained_glass" % at(-6, 0, 0),
    "setblock %s minecraft:water" % at(-4, -1, 0),
    "setblock %s buildcraftunofficial:tank" % at(-2, 0, 0),
    "setblock %s buildcraftunofficial:tank" % at(-2, 1, 0),
    *pipe(0, 0, 0, "stone_item", ',col:"lime"'),
    *pipe(0, 1, 0, "stone_fluid", ',col:"red"'),
    "setblock %s minecraft:blue_stained_glass_pane" % at(2, 0, 0),
    "setblock %s minecraft:slime_block" % at(4, 0, 0),
    "setblock %s minecraft:honey_block" % at(6, 0, 0),
    "setblock %s minecraft:white_stained_glass" % at(0, 0, -4),
    "setblock %s minecraft:white_stained_glass" % at(-4, 0, -4),
    "setblock %s minecraft:white_stained_glass" % at(4, 0, -4))
cmd("volume markers (x and z axis) and two path markers behind the row",
    "setblock %s buildcraftunofficial:marker_volume" % at(-8, 0, -3),
    "setblock %s buildcraftunofficial:marker_volume" % at(8, 0, -3),
    "setblock %s buildcraftunofficial:marker_volume" % at(-8, 0, -7),
    "setblock %s buildcraftunofficial:marker_path" % at(-5, 0, -9),
    "setblock %s buildcraftunofficial:marker_path" % at(5, 0, -9))
tp(-2, 0, 2, 180, 20)
hold("minecraft:water_bucket")
aim(-2, 0, 0)
step(label="fill the lower tank with water", do="use")
hold("minecraft:lava_bucket")
aim(-2, 1, 0)
step(label="fill the upper tank with lava", do="use")
hold("minecraft:air")
tp(-8, 0, -1, 180, 30)
aim(-8, 0, -3)
step(label="connect the volume markers", do="use")
tp(-5, 0, -7, 180, 30)
aim(-5, 0, -9)
step(label="connect the path markers", do="use")
tp(0, 0, 5, 180, 8)
wait(1500)
shot("b1-translucency", "B1: red glass, water hole, water tank under a lava tank, lime and red pipes, blue pane, slime, "
     "honey; white glass behind; red marker lasers along x and z behind everything and the path laser further back. "
     "Compare 26.3 OIT on / off / 26.2: missing or wrongly ordered translucency, lasers vanishing behind glass, "
     "a black or garbled surface is the finding")
tp(-8, 0, -1, 180, 40)
aim(-8, 0, -3)
wait(500)
shot("b2-markers-close", "B2 torch_center_lit (shade_direction_override): volume marker close up, the lit centre "
     "and the post's side faces; compare its face shading 26.3 vs 26.2")

# ── pipes, plugs and dropped items ─────────────────────────────────────────────────────────────────────────────
cmd("pipe row",
    *pipe(-6, 0, 8, "wood_item"), *pipe(-4, 0, 8, "stone_fluid", ',col:"red"'), *pipe(-2, 0, 8, "gold_power"),
    *pipe(0, 0, 8, "diamond_item"), *pipe(2, 0, 8, "quartz_item", ',col:"blue"'), *pipe(4, 0, 8, "stripes_item"),
    *pipe(6, 0, 8, "structure"))
cmd("plug row (south faces toward the camera)",
    *plugged(-5, 1, 8, "stone_item", 'south:{id:"buildcraftunofficial:facade",data:{facade:%s,activeState:0}}' % FACADE_OLD),
    *plugged(-3, 1, 8, "stone_item", 'south:{id:"buildcraftunofficial:facade",data:{facade:%s,activeState:0}}' % FACADE_NEW),
    *plugged(-1, 1, 8, "stone_item", 'south:{id:"buildcraftunofficial:gate",data:{data:%s}}' % GATE_GOLD),
    *plugged(1, 1, 8, "stone_item", 'south:{id:"buildcraftunofficial:lens",data:{colour:"red",f:0b}}'),
    *plugged(3, 1, 8, "stone_power", 'south:{id:"buildcraftunofficial:pulsar",data:{}}'),
    *plugged(5, 1, 8, "stone_item", 'south:{id:"buildcraftunofficial:blocker",data:{}}'))
drops = ["buildcraftunofficial:pipe_wood_item", "buildcraftunofficial:plug_facade", "buildcraftunofficial:plug_gate",
         "buildcraftunofficial:plug_lens", "buildcraftunofficial:robot", "buildcraftunofficial:fragile_fluid_container"]
comps = [None, '"minecraft:custom_data":{facade:%s}' % FACADE_OLD,
         '"minecraft:custom_data":{gate:{logic:0b,material:3b,modifier:3b}}',
         '"minecraft:custom_data":{colour:"red",isFilter:0b}', None,
         '"buildcraftunofficial:fluid_content":{id:"minecraft:lava",amount:1000}']
summons = []
for i, (iid, c) in enumerate(zip(drops, comps)):
    item = '{id:"%s",count:1%s}' % (iid, (',components:{%s}' % c) if c else '')
    summons.append("summon minecraft:item %s.5 {Y}.2 %s.5 {Item:%s,PickupDelay:32767,NoGravity:1b,Age:-32768}"
                   % (o('X', -5 + 2 * i), o('Z', 11), item))
cmd("dropped items: pipe, facade, gate, lens, robot, lava shard", *summons)
tp(0, 1, 14, 180, 18)
wait(1500)
shot("pipes-world", "pipes in world: wood, red stone fluid, gold power, diamond, blue quartz, stripes, structure; "
     "above: oak facade (legacy Name key), stone-brick facade (26.3 id key), gold gate, red lens, pulsar, blocker; "
     "front row dropped: pipe, facade, gate, lens, robot, lava shard (B2 pipe item and chassis shading)")
tp(0, 1, 14, 180, 40)
wait(500)
shot("b2-dropped-close", "B2: dropped pipe item, facade, gate, lens, robot and lava shard close up; compare face "
     "shading 26.3 vs 26.2 (pipe_item and robot_chassis_base had shade:false)")

# ── items in hand (B2 pipe item, chassis; B11 shards) ──────────────────────────────────────────────────────────
tp(0, 0, 13, 180, 25)
for name, item, look in [
        ("hand-pipe", "buildcraftunofficial:pipe_wood_item", "B2: wooden pipe item in hand"),
        ("hand-facade", ITEM_FACADE_OLD, "oak facade item in hand (legacy Name key)"),
        ("hand-gate", ITEM_GATE, "gold diamond-modifier gate item in hand"),
        ("hand-lens", ITEM_LENS, "red lens item in hand"),
        ("hand-robot", "buildcraftunofficial:robot", "B2: robot (chassis) item in hand"),
        ("hand-shard-lava", shard("minecraft:lava"), "B11: lava fluid shard in hand (orange; red/blue swap is a known "
                                                       "26.2 bug too, not a 26.3 regression)"),
        ("hand-shard-fuel", shard("buildcraftunofficial:fuel_light"), "B11: coloured (light fuel, yellow) shard in hand")]:
    hold(item)
    wait(1500)   # the equip animation settles, so the hand pose matches across nodes
    shot(name, look)
hold("minecraft:air")

# ── items in a GUI slot ────────────────────────────────────────────────────────────────────────────────────────
items = [
    chest_item(0, "buildcraftunofficial:pipe_wood_item"), chest_item(1, "buildcraftunofficial:pipe_stone_fluid"),
    chest_item(2, "buildcraftunofficial:pipe_gold_power"), chest_item(3, "buildcraftunofficial:pipe_diamond_item"),
    chest_item(4, "buildcraftunofficial:pipe_structure"),
    chest_item(5, "buildcraftunofficial:plug_facade", '"minecraft:custom_data":{facade:%s}' % FACADE_OLD),
    chest_item(6, "buildcraftunofficial:plug_facade", '"minecraft:custom_data":{facade:%s}' % FACADE_NEW),
    chest_item(7, "buildcraftunofficial:plug_gate", '"minecraft:custom_data":{gate:{logic:0b,material:1b,modifier:0b}}'),
    chest_item(8, "buildcraftunofficial:plug_gate", '"minecraft:custom_data":{gate:{logic:1b,material:3b,modifier:3b}}'),
    chest_item(9, "buildcraftunofficial:plug_lens", '"minecraft:custom_data":{colour:"red",isFilter:0b}'),
    chest_item(10, "buildcraftunofficial:plug_lens", '"minecraft:custom_data":{isFilter:1b}'),
    chest_item(11, "buildcraftunofficial:plug_blocker"), chest_item(12, "buildcraftunofficial:plug_pulsar"),
    chest_item(13, "buildcraftunofficial:plug_light_sensor"), chest_item(14, "buildcraftunofficial:plug_timer"),
    chest_item(15, "buildcraftunofficial:plug_power_adaptor"), chest_item(16, "buildcraftunofficial:robot"),
    chest_item(17, "buildcraftunofficial:robot_station"), chest_item(18, "buildcraftunofficial:wrench"),
    chest_item(19, "buildcraftunofficial:fragile_fluid_container",
               '"buildcraftunofficial:fluid_content":{id:"minecraft:lava",amount:1000}'),
    chest_item(20, "buildcraftunofficial:fragile_fluid_container",
               '"buildcraftunofficial:fluid_content":{id:"buildcraftunofficial:fuel_light",amount:1000}'),
    chest_item(21, "buildcraftunofficial:fragile_fluid_container",
               '"buildcraftunofficial:fluid_content":{id:"buildcraftunofficial:oil",amount:1000}'),
    chest_item(22, "buildcraftunofficial:fragile_fluid_container",
               '"buildcraftunofficial:fluid_content":{id:"minecraft:water",amount:1000}'),
    chest_item(23, "buildcraftunofficial:marker_volume"), chest_item(24, "buildcraftunofficial:marker_path"),
    chest_item(25, "buildcraftunofficial:gate_copier"), chest_item(26, "buildcraftunofficial:paintbrush")]
cmd("chest of items", "setblock %s minecraft:chest[facing=south]{Items:[%s]}" % (at(-11, 0, 0), ",".join(items)))
tp(-11, 0, 2, 180, 30)
aim(-11, 0, 0)
step(label="open the chest", do="use")
step(do="expectScreen", contains="ContainerScreen")
wait(500)
shot("gui-items", "items in GUI slots, row 1: wood/stone-fluid/gold-power/diamond/structure pipes, oak facade, "
     "stone-brick facade, iron gate, gold gate; row 2: red lens, clear filter, blocker, pulsar, light sensor, timer, "
     "power adaptor, robot, docking station; row 3: wrench, lava/light-fuel/oil/water shards, volume and path marker, "
     "gate copier, paintbrush. Compare shading and icons 26.3 vs 26.2")
step(label="hover the legacy-key facade", do="hover", slotIndex=5)
shot("gui-facade-old-tooltip", "tooltip names Oak Planks (B0: legacy Name key read back)")
step(label="hover the 26.3-key facade", do="hover", slotIndex=6)
shot("gui-facade-new-tooltip", "tooltip names Stone Bricks (B0: id key read back)")
step(label="hover the lava shard", do="hover", slotIndex=19)
shot("gui-shard-tooltip", "B11: lava shard tooltip and icon colour")
step(do="close")

# ── B4: Stirling engine fuels, JEI fuel pages ──────────────────────────────────────────────────────────────────
cmd("engines (redstone under the two Stirlings)",
    "setblock %s minecraft:redstone_block" % at(-4, -1, 16), "setblock %s minecraft:redstone_block" % at(-2, -1, 16),
    "setblock %s buildcraftunofficial:engine_stone" % at(-4, 0, 16),
    "setblock %s buildcraftunofficial:engine_stone" % at(-2, 0, 16),
    "setblock %s buildcraftunofficial:engine_iron" % at(0, 0, 16),
    # Stirling C has two wooden kinesis pipes to choose between, so the wrench can turn it.
    "setblock %s buildcraftunofficial:engine_stone" % at(3, 0, 16),
    *pipe(2, 0, 16, "wood_power"), *pipe(4, 0, 16, "wood_power"),
    "item replace entity @p inventory.0 with minecraft:coal 8",
    "item replace entity @p inventory.1 with minecraft:lava_bucket")
tp(-4, 0, 14, 0, 30)
aim(-4, 0, 16)
step(label="open Stirling A", do="use")
step(do="expectScreen", contains="ScreenEngineStone")
step(label="pick up the coal (menu slot 1 = inventory.0)", do="click", slotIndex=1)
step(label="put it in the fuel slot", do="click", slotIndex=0)
step(do="close")
tp(-2, 0, 14, 0, 30)
aim(-2, 0, 16)
step(label="open Stirling B", do="use")
step(do="expectScreen", contains="ScreenEngineStone")
step(label="pick up the lava bucket (menu slot 2 = inventory.1)", do="click", slotIndex=2)
step(label="put it in the fuel slot", do="click", slotIndex=0)
step(do="close")
wait(3000)
echo("B4 coal engine: expect a non-zero totalBurnTime (vanilla coal = 1600)", "data get block %s totalBurnTime" % at(-4, 0, 16))
echo("B4 coal engine: burnTime counting down", "data get block %s burnTime" % at(-4, 0, 16))
echo("B4 lava engine: expect a non-zero totalBurnTime (vanilla lava bucket = 20000)", "data get block %s totalBurnTime" % at(-2, 0, 16))
tp(-4, 0, 14, 0, 30)
aim(-4, 0, 16)
step(label="reopen Stirling A", do="use")
step(do="expectScreen", contains="ScreenEngineStone")
wait(500)
shot("b4-stirling-gui", "B4: Stirling engine GUI burning coal: lit flame, remaining coal in the slot")
step(label="click the flame (JEI click area)", do="click", x=213, y=69)
wait(1000)
shot("b4-jei-stirling", "B4: JEI Stirling fuel page lists solid fuels with burn times, not empty")
step(label="hover the first fuel", do="hover", x=140, y=106, allowMiss=True)
shot("b4-jei-stirling-first", "B4: name of the first Stirling fuel (its burn time is beside it)")
step(do="key", key="escape")
wait(400)
step(do="close")
tp(0, 0, 14, 0, 30)
aim(0, 0, 16)
step(label="open the combustion engine", do="use")
step(do="expectScreen", contains="ScreenEngineIron")
step(label="click the centre strip (JEI click area)", do="click", x=186, y=85)
wait(1000)
shot("b4-jei-combustion", "B4: JEI combustion page opens (coolant tab first)")
step(label="the combustion FUEL tab (second tab)", do="click", x=152, y=35, allowMiss=True)
wait(800)
shot("b4-jei-combustion-fuel", "B4: JEI combustion fuel tab: BuildCraft fuels with their burn time and MJ/t")
step(do="key", key="escape")
wait(400)
step(do="close")

# ── wrench on an engine: rotation, and the hand swing ──────────────────────────────────────────────────────────
tp(3, 0, 14, 0, 30)
hold("buildcraftunofficial:wrench")
aim(3, 0, 16)
shot("wrench-before", "Stirling engine C before the wrench click")
step(label="wrench the engine", do="use")
wait(400)
shot("wrench-after", "the engine turned to face the other wooden kinesis pipe (the hand swing itself is too short for this shot: "
     "the side probe screenshots it within 300 ms)")
shot("probe-swing-start", "side-probe window opens: a wrench click plus rapid screenshots of the hand swing")
step(label="side-probe window: a script outside the runner may click and screenshot the swing here", do="wait", ms=12000)
hold("minecraft:air")

# ── B5: gate GUI (statement clicks) ────────────────────────────────────────────────────────────────────────────
cmd("gate rig: stone pipe with an iron gate (trigger 'always true', action 'redstone output') on its north face",
    *plugged(-10, 0, 8, "stone_item", 'north:{id:"buildcraftunofficial:gate",data:{data:%s}}' % GATE_IRON))
tp(-10, 0, 5, 0, 20)
aim(-10, 0, 8)
step(label="open the gate", do="use")
step(do="expectScreen", contains="GuiGate")
wait(500)
step(name="gate", do="widgets")
shot("b5-gate", "iron gate GUI: trigger 'always true' and action 'redstone output' in row 1")
step(label="LEFT click the trigger slot", do="click", x=195, y=68, allowMiss=True)
wait(500)
shot("b5-gate-left", "B5: left click on a set trigger opens its variant menu (a strip of trigger choices)")
step(label="LEFT click the action slot", do="click", x=231, y=68, allowMiss=True)
wait(500)
shot("b5-gate-left-action", "B5: left click on the set action: its variant menu (compare with 26.2)")
step(do="close")
aim(-10, 0, 8)
step(label="reopen the gate", do="use")
step(do="expectScreen", contains="GuiGate")
wait(500)
step(label="RIGHT click the trigger slot", do="click", x=195, y=68, button=1, allowMiss=True)
wait(500)
shot("b5-gate-right", "B5: right click starts a drag that ends on the same slot: NO variant menu, trigger unchanged")
step(do="close")
echo("B5 gate after the clicks: trigger[0] still trigger.true", "data get block %s plugs.north" % at(-10, 0, 8))

# ── B5: emzuli paint buttons (BCButton left / right / middle) ──────────────────────────────────────────────────
cmd("emzuli pipe", *pipe(10, 0, 8, "emzuli_item"))
tp(10, 0, 5, 0, 20)
aim(10, 0, 8)
step(label="open the emzuli pipe", do="use")
step(do="expectScreen", contains="GuiEmzuliPipe")
step(name="emzuli", do="widgets")
shot("b5-emzuli", "emzuli GUI: four paint buttons with no paint")
step(label="LEFT click paint button 1 (colour forward)", do="click", x=184, y=66, allowMiss=True)
wait(400)
step(label="LEFT click again", do="click", x=184, y=66, allowMiss=True)
wait(400)
step(label="RIGHT click (colour back)", do="click", x=184, y=66, button=1, allowMiss=True)
wait(400)
step(label="hover the paint button", do="hover", x=184, y=66, allowMiss=True)
shot("b5-emzuli-painted", "B5: after left, left, right the first button shows ONE step of paint (white); "
     "its tooltip names the colour")
step(do="close")
echo("B5 emzuli: slot colours after left, left, right (expect the first colour)", "data get block %s pipe.beh" % at(10, 0, 8))

# ── guide: link confirmation, tag-ingredient recipe (B14), right-click clears the search (B5) ─────────────────
hold("buildcraftunofficial:guide")
step(do="look", yaw=0, pitch=-90)
step(label="open the guide", do="use")
step(do="expectScreen", contains="GuiGuide", timeoutSec=20)
step(label="open the cover", do="click", fx=0.5, fy=0.5)
wait(2500)
step(label="focus the search tab", do="click", x=58, y=8)
step(do="text", text="Guide")
wait(1000)
shot("guide-search", "search results for 'Guide'")
step(name="guide-search", do="widgets")
step(label="open the BuildCraft Guide Book entry", do="click", x=127, y=56, allowMiss=True)
wait(1000)
shot("guide-entry", "the Guide Book entry")
step(label="next page", do="click", widgetText="Next Page", allowMiss=True)
wait(500)
shot("guide-link-page", "the page with the 'GitHub issue tracker' link")
step(name="guide-link-page", do="widgets")
step(label="click the GitHub link", do="click", x=318, y=96, allowMiss=True)
wait(800)
step(label="vanilla ConfirmLinkScreen opened", do="expectScreen", contains="ConfirmLinkScreen", timeoutSec=5)
shot("guide-confirm-link", "vanilla link confirmation (Yes / Copy to Clipboard / No) with the untrusted-link warning")
step(label="decline (never open the browser)", do="click", widgetText="No", allowMiss=True)
wait(800)
step(do="expectScreen", contains="GuiGuide", timeoutSec=5)
step(do="close")
step(do="look", yaw=0, pitch=-90)
step(label="reopen the guide", do="use")
step(do="expectScreen", contains="GuiGuide", timeoutSec=20)
step(label="open the cover", do="click", fx=0.5, fy=0.5, allowMiss=True)
wait(2000)
step(label="focus the search tab", do="click", x=58, y=8, allowMiss=True)
step(do="text", text="Wooden Transport")
wait(1000)
shot("guide-search-wood", "search results for 'Wooden Transport'")
step(label="RIGHT click the search box (clears it)", do="click", x=110, y=8, button=1, allowMiss=True)
wait(600)
shot("b5-guide-search-cleared", "B5: right click on the search box cleared the query (full list again)")
step(do="text", text="Wooden Transport")
wait(1000)
step(label="open the Wooden Transport Pipe entry", do="click", x=132, y=56, allowMiss=True)
wait(1500)
shot("b14-guide-tag-recipe", "B14: Wooden Transport Pipe page: the crafting grid shows planks (#minecraft:planks, "
     "cycling) and glass (#c:glass_blocks/colorless) - blank cells are the finding")
step(do="close")
hold("minecraft:air")

# ── B12: BuildCraft fluid vs the 26.3 fluid tags; blocks_motion heightmap ───────────────────────────────────
cmd("glass box for the fluid test",
    "fill {X+9} {Y} {Z+13} {X+15} {Y+3} {Z+19} minecraft:glass hollow")
cmd("crude oil source in the box", "setblock %s buildcraftunofficial:oil" % at(12, 1, 16))
wait(6000)
echo("B12 setup: flowing oil reached the cell (expect Test passed)", "execute if block %s buildcraftunofficial:oil" % at(13, 1, 16))
cmd("water source right above that flowing-oil cell", "setblock %s minecraft:water" % at(13, 2, 16))
wait(3000)
echo("B12: is the cell still oil? 26.2: water replaces flowing oil (Test failed); 26.3 with oil absent from "
     "minecraft:washed_away_by_fluids: oil stays (Test passed)", "execute if block %s buildcraftunofficial:oil" % at(13, 1, 16))
tp(12, 1, 22, 180, 12)
wait(500)
shot("b12-fluid-box", "B12: oil (dark) and water (blue) in the glass box: did the water fall into the oil or sit on top?")
cmd("BuildCraft full block on the platform", "setblock %s buildcraftunofficial:decorated_laser" % at(11, 0, -6))
echo("B12/B9: motion_blocking heightmap counts a BuildCraft full block? 26.2: Test passed; 26.3 when the block "
     "is missing from minecraft:blocks_motion: Test failed",
     "execute positioned %s positioned over motion_blocking if block ~ ~-1 ~ buildcraftunofficial:decorated_laser"
     % ("%s 0 %s" % (o('X', 11), o('Z', -6))))
echo("control: smooth stone counts", "execute positioned %s 0 %s positioned over motion_blocking if block ~ ~-1 ~ "
     "minecraft:smooth_stone" % (o('X', 12), o('Z', -6)))

# ── oil feature ────────────────────────────────────────────────────────────────────────────────────────────
cmd("go somewhere unexplored for the oil deposit", "spreadplayers 400 400 0 1 false @p")
wait(4000)
cmd("place BuildCraft's oil feature on a 3x3 chunk grid (fails unless the config raises mediumOilGenProb)",
    "?execute at @p run place feature buildcraftunofficial:oil ~ ~ ~",
    "?execute at @p run place feature buildcraftunofficial:oil ~16 ~ ~", "?execute at @p run place feature buildcraftunofficial:oil ~-16 ~ ~",
    "?execute at @p run place feature buildcraftunofficial:oil ~ ~ ~16", "?execute at @p run place feature buildcraftunofficial:oil ~ ~ ~-16",
    "?execute at @p run place feature buildcraftunofficial:oil ~16 ~ ~16", "?execute at @p run place feature buildcraftunofficial:oil ~-16 ~ ~-16",
    "?execute at @p run place feature buildcraftunofficial:oil ~16 ~ ~-16", "?execute at @p run place feature buildcraftunofficial:oil ~-16 ~ ~16")
echo("oil feature at the centre chunk (Placed feature / Failed to place)", "execute at @p run place feature buildcraftunofficial:oil ~ ~ ~")
cmd("lift the camera onto a glass block", "execute as @p at @p run tp @p ~ ~40 ~", "execute at @p run setblock ~ ~-1 ~ minecraft:glass")
wait(3000)
for yaw in (0, 90, 180, 270):
    step(do="look", yaw=yaw, pitch=55)
    wait(1500)
    shot("oil-%d" % yaw, "oil deposit from above (yaw %d): black lake and/or a spout column; compare its shape and "
         "where the spout stops 26.3 vs 26.2 (B9)" % yaw)

# ── B1 last: debug and LED render types (an OIT crash ends the run here) ────────────────────────────────────
tp(-9, 0, -8, 180, 10)
wait(2000)
cmd("laser facing up, glass beside it", "setblock %s buildcraftunofficial:laser[facing=up]" % at(-9, 0, -14),
    "setblock %s minecraft:light_blue_stained_glass" % at(-8, 0, -12))
tp(-9, 0, -11, 180, 20)
hold("buildcraftunofficial:debugger")
aim(-9, 0, -14)
step(label="debugger on the laser (advanced-debug overlay, BCLibRenderTypes.debugSolid)", do="use")
step(do="look", yaw=180, pitch=-20)
wait(1500)
shot("b1-debug-solid", "B1: advanced debugger cones above the laser drawn with buildcraft:debug_solid; under OIT a "
     "missing OIT pipeline throws IllegalStateException (crash) instead of drawing")
hold("minecraft:air")
cmd("LED machines behind stained glass (BCLibRenderTypes.led)",
    "setblock %s buildcraftunofficial:filler[facing=south]" % at(-6, 0, -16),
    "setblock %s buildcraftunofficial:quarry[facing=south]" % at(-3, 0, -16),
    "setblock %s buildcraftunofficial:mining_well" % at(0, 0, -16),
    "setblock %s buildcraftunofficial:pump" % at(3, 0, -16),
    "setblock %s buildcraftunofficial:architect[facing=south]" % at(6, 0, -16),
    "setblock %s buildcraftunofficial:zone_planner[facing=south]" % at(9, 0, -16),
    "fill {X-7} {Y} {Z-14} {X+10} {Y} {Z-14} minecraft:light_blue_stained_glass_pane")
tp(1, 1, -9, 180, 12)
wait(2000)
shot("b1-led-row", "B1: filler, quarry, mining well, pump, architect table and zone planner LEDs seen through "
     "stained glass panes; under OIT the buildcraft:led type has no OIT pipeline (expected crash)")

# ── B5: Zone Planner paint (left) / erase (right); pan needs a press-drag-release the bridge cannot send ──────
cmd("red paintbrush into inventory.0 (menu slot 0)",
    'item replace entity @p inventory.0 with buildcraftunofficial:paintbrush[buildcraftunofficial:brush_color="red",'
    'buildcraftunofficial:brush_uses=64]')
tp(9, 0, -13, 180, 20)
aim(9, 0, -16)
step(label="open the zone planner", do="use")
step(do="expectScreen", contains="GuiZonePlanner", timeoutSec=10)
wait(2500)
step(name="planner", do="widgets")
shot("b5-planner", "zone planner map")
echo("the map shows the world below the rig: is it ocean there? (a flat blue map is then right)", "execute if block %s 62 %s minecraft:water" % (o('X', 9), o('Z', -16)))
echo("red layer before painting", "data get block %s layer_14" % at(9, 0, -16))
step(label="pick up the red brush", do="click", slotIndex=0)
step(label="LEFT click on the map (paint one cell)", do="click", fx=0.466, fy=0.271)
wait(1000)
shot("b5-planner-painted", "B5: a red cell painted at the map centre, brush on the cursor")
echo("B5 red layer after the left click (expect a painted chunk entry)", "data get block %s layer_14" % at(9, 0, -16))
step(label="RIGHT click on the same cell (erase)", do="click", fx=0.466, fy=0.271, button=1)
wait(1000)
shot("b5-planner-erased", "B5: the red cell erased")
echo("B5 red layer after the right click (expect empty again)", "data get block %s layer_14" % at(9, 0, -16))
step(label="put the brush back", do="click", slotIndex=0)
step(do="close")

# ── cleanup: no LED block may stay in a saved world (an OIT run would crash on load) ─────────────────────────
cmd("remove the LED / debug rig and the fluid box",
    "?fill {X-14} {Y} {Z-18} {X+14} {Y+7} {Z-10} minecraft:air",
    "?fill {X+9} {Y} {Z+13} {X+15} {Y+3} {Z+19} minecraft:air",
    "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..60]",
    "?clear @p")
tp(0, 0, 4, 180, 10)

scenario = {
    "name": "port-26-3",
    "description": "26.3 port V2 in-client checks (docs/port-26.3.md section 4): OIT, model shading, pipes and "
                   "plug items, Stirling fuel and JEI, mouse buttons, guide link and tag recipes, fluid shards, "
                   "oil feature, wrench. Generated by generators/gen_port_26_3.py: run it on 26.3 (OIT off, then on) "
                   "and on 26.2, and compare.",
    "base": [0, 150, 0],
    "steps": S,
}
with open(OUT, "w", newline=chr(10)) as f:
    f.write(json.dumps(scenario, indent=1) + chr(10))
print("wrote %s (%d steps)" % (os.path.normpath(OUT), len(S)))
