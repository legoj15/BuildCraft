"""World upgrade from 26.2 to 26.3 (docs/port-26.3.md B0 and B8): block states BuildCraft keeps in its own NBT.

Run: python scripts/smoke/generators/gen_upgrade_26_3.py
     (rewrites scripts/smoke/scenarios/upgrade-26-3-author.json and upgrade-26-3-verify.json)

26.3 renamed the block-state NBT keys (Name/Properties -> id/properties). BuildCraft stores block states in blueprints
(server and client snapshot files) and in facades (placed facade plugs, facade items), which vanilla's DataFixer never
sees. Both scenarios play the save `bc-upgrade`.

1. Author on 26.2 (the save is seeded from bc-smoke on the first run):
     pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.2 -Scenario scripts/smoke/scenarios/upgrade-26-3-author.json
   Builds two small structures with directional and axis states plus a water source, scans them with Architect Tables
   (blueprints A and B, template T of A), downloads all three into the Electronic Library's client list, keeps
   blueprint A, template T, three facade items and blanks in a chest, places four facade plugs (plain, hollow,
   phased; two by item use), then saves and quits to the title screen.
2. Carry the world over (no client running):  pwsh scripts/smoke/Copy-UpgradeWorld.ps1 -To 26.3 -Config
   It archives the author state once, copies saves/bc-upgrade, snapshots-server and snapshots-client into 26.3's
   run-smoke, and deletes blueprint B's server file there: the 26.3 server then only gets B through the library
   upload, which reads the client's 26.2-written file. -Config (B8) puts 26.2's buildcraftunofficial-common.toml over
   26.3's with `disableRfPipe = true`; `-RestoreConfig` puts 26.3's own back afterwards.
3. Verify on 26.3, then on 26.2 as the baseline (after `Copy-UpgradeWorld.ps1 -To 26.2`, which resets 26.2's copy):
     pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3 -Scenario scripts/smoke/scenarios/upgrade-26-3-verify.json
   Facade plugs and items must show their blocks, `data get` must show the facade states re-saved with the node's own
   keys (not air), the library must list upgrade-a, upgrade-b and upgrade-t, and Builders fed blueprint A (from the
   chest) and blueprint B (uploaded from the library) must rebuild both structures state for state
   (`execute if blocks ... all` passes). The template upload is the control: it holds no block state.
   B8: with `disableRfPipe = true` read from the carried-over -common.toml the RF pipe recipe is unknown on 26.3 (the
   26.2 baseline, on its own default config, unlocks it).
"""
import json
import os

HERE = os.path.dirname(__file__)
OUT_AUTHOR = os.path.join(HERE, "..", "scenarios", "upgrade-26-3-author.json")
OUT_VERIFY = os.path.join(HERE, "..", "scenarios", "upgrade-26-3-verify.json")
BC = "buildcraftunofficial:"
DEV_UUID = "380df991-f603-344c-a090-369bad2a924a"   # the dev client's offline UUID for "Dev", the same on every node


def o(a, n):
    return "{%s%s}" % (a, ("+%d" % n) if n > 0 else ("-%d" % -n) if n < 0 else "")


def at(x, y, z):
    return "%s %s %s" % (o('X', x), o('Y', y), o('Z', z))


class Scenario:
    def __init__(self):
        self.s = []

    def step(self, **kw):
        self.s.append(kw)

    def cmd(self, label, *run, **kw):
        self.step(label=label, do="cmd", run=list(run), **kw)

    def echo(self, label, c, **kw):
        self.step(label=label, do="cmd", run=[c], allowFail=True, echo=True, **kw)

    def wait_cmd(self, label, c, match, timeout=60):
        self.step(label=label, do="waitCmd", run=c, match=match, timeoutSec=timeout)

    def tp(self, x, y, z, yaw=0, pitch=0):
        self.step(do="tp", at=[x, y, z], yaw=yaw, pitch=pitch)

    def aim(self, x, y, z):
        self.step(do="aim", at=[x, y, z])

    def shot(self, name, look):
        self.step(name=name, do="shot", look=look)

    def wait(self, ms):
        self.step(do="wait", ms=ms)

    def hold(self, item):
        self.step(do="hold", item=item)


# ── layout (offsets from base) ────────────────────────────────────────────────────────────────────────────────────
# Structure A: x0..2, y0..1, z0..2 (water source in the middle, walled by logs). Structure B: x0..1, y0..1, z6..7.
# Architects face north with their box behind them (south); a Builder facing north 10 blocks east rebuilds the same
# structure 10 blocks east. No two stairs touch, so no stair changes its shape when a neighbour is placed.
STRUCT_A = [
    # y0
    (0, 0, 0, "oak_log[axis=x]"), (1, 0, 0, "oak_log[axis=x]"), (2, 0, 0, "oak_log[axis=x]"),
    (0, 0, 1, "birch_log[axis=z]"), (1, 0, 1, "water"), (2, 0, 1, "birch_log[axis=z]"),
    (0, 0, 2, "oak_log[axis=x]"), (1, 0, 2, "oak_log[axis=x]"), (2, 0, 2, "oak_log[axis=x]"),
    # y1
    (0, 1, 0, "oak_stairs[facing=east,half=bottom]"), (1, 1, 0, "stone_slab[type=top]"),
    (2, 1, 0, "oak_stairs[facing=west,half=top]"),
    (0, 1, 1, "smooth_stone_slab[type=bottom]"), (2, 1, 1, "purpur_pillar[axis=x]"),
    (0, 1, 2, "oak_slab[type=double]"), (1, 1, 2, "stone_brick_stairs[facing=north,half=top]"),
    (2, 1, 2, "hay_block[axis=z]"),
]
STRUCT_B = [
    (0, 0, 6, "spruce_log[axis=z]"), (1, 0, 6, "oak_stairs[facing=south,half=top]"),
    (0, 0, 7, "andesite_slab[type=top]"), (1, 0, 7, "bone_block[axis=x]"),
    (0, 1, 6, "dark_oak_stairs[facing=west,half=bottom]"), (1, 1, 7, "birch_slab[type=bottom]"),
]
BOX_A = ((0, 0, 0), (2, 1, 2))
BOX_B = ((0, 0, 6), (1, 1, 7))
SHIFT = 10                     # the Builders rebuild both structures this many blocks east
ARCH_A, ARCH_T, ARCH_B = (1, 0, -2), (4, 0, -2), (0, 0, 4)
BUILDER_A, BUILDER_B = (1 + SHIFT, 0, -2), (0 + SHIFT, 0, 4)
LIBRARY = (6, 0, 4)
CHEST = (8, 0, 4)
PIPES = [(0, 0, 12), (2, 0, 12), (4, 0, 12), (6, 0, 12)]       # authored on 26.2
NEW_PIPES = [(8, 0, 12), (10, 0, 12), (12, 0, 12)]             # verify: facades placed from the carried-over items

# Facades in 26.2's own spelling (what its NbtUtils.writeBlockState writes): properties that differ from the default
# state, so a reader that drops them is caught as well as one that drops the block.
F_LOG = '{isHollow:0b,states:[{state:{Name:"minecraft:oak_log",Properties:{axis:"z"}}}]}'
F_HOLLOW_PURPUR = '{isHollow:1b,states:[{state:{Name:"minecraft:purpur_pillar",Properties:{axis:"x"}}}]}'
F_PHASED = ('{isHollow:0b,states:[{state:{Name:"minecraft:quartz_pillar",Properties:{axis:"x"}}},'
            '{state:{Name:"minecraft:red_wool"},activeColour:"RED"}]}')
F_HOLLOW_BIRCH = '{isHollow:1b,states:[{state:{Name:"minecraft:birch_log",Properties:{axis:"x"}}}]}'


def facade_item(f):
    return BC + 'plug_facade[minecraft:custom_data={facade:%s}]' % f


def chest_item(slot, item_id, count=1, components=None):
    c = (',components:{%s}' % components) if components else ''
    return '{Slot:%db,id:"%s",count:%d%s}' % (slot, item_id, count, c)


def plug(x, y, z, f):
    return ["setblock %s %spipe_holder" % (at(x, y, z), BC),
            'data merge block %s {pipe:{def:"%sstone_item"},plugs:{south:{id:"%sfacade",data:{facade:%s,activeState:0}}}}'
            % (at(x, y, z), BC, BC, f)]


def bare_pipe(x, y, z):
    return ["setblock %s %spipe_holder" % (at(x, y, z), BC),
            'data merge block %s {pipe:{def:"%sstone_item"}}' % (at(x, y, z), BC)]


def box_nbt(box):
    (x0, y0, z0), (x1, y1, z1) = box
    return ("box_initialized:1b,box_minX:%s,box_minY:%s,box_minZ:%s,box_maxX:%s,box_maxY:%s,box_maxZ:%s"
            % (o('X', x0), o('Y', y0), o('Z', z0), o('X', x1), o('Y', y1), o('Z', z1)))


def architect(pos, box, name, blank):
    return ["setblock %s %sarchitect[facing=north]" % (at(*pos), BC),
            'data merge block %s {%s,isValid:1b,name:"%s",items:{in:{items:[{stack:{id:"%s%s",count:1}}]},out:{items:[]}}}'
            % (at(*pos), box_nbt(box), name, BC, blank)]


RESOURCES = ["oak_log", "birch_log", "oak_stairs", "stone_slab", "smooth_stone_slab", "purpur_pillar", "oak_slab",
             "stone_brick_stairs", "hay_block", "spruce_log", "andesite_slab", "bone_block", "dark_oak_stairs",
             "birch_slab"]


def builder(pos):
    stacks = ",".join('{stack:{id:"minecraft:%s",count:16}}' % r for r in RESOURCES)
    return ["setblock %s %sbuilder[facing=north]" % (at(*pos), BC),
            'data merge block %s {ownerUUID:"%s",ownerName:"Dev",battery_mj:16000000000L,'
            'tank_0_fluid:"minecraft:water",tank_0_amount:8000,items:{resources:{items:[%s]}}}'
            % (at(*pos), DEV_UUID, stacks)]


def setup(sc):
    sc.cmd("load the rig's chunks", "gamemode creative @p", "?forceload add {X-16} {Z-16} {X+24} {Z+24}")
    sc.tp(6, 0, 16, 180, 10)
    sc.wait(3000)


def mirror(box, dx):
    (x0, y0, z0), (x1, y1, z1) = box
    return at(x0, y0, z0), at(x1, y1, z1), at(x0 + dx, y0, z0)


# ══ author (26.2) ═════════════════════════════════════════════════════════════════════════════════════════════════
A = Scenario()
setup(A)
A.cmd("clear and floor",
      "difficulty peaceful", "time set 6000", "weather clear", "?clear @p",
      "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..40]",
      "?fill {X-6} {Y} {Z-8} {X+20} {Y+6} {Z+20} minecraft:air",
      "fill {X-6} {Y-1} {Z-8} {X+20} {Y-1} {Z+20} minecraft:smooth_stone",
      "?advancement grant @p everything")
A.wait(4000)
A.cmd("structure A (directional, axis, slab, double-slab states and a walled water source)",
      *["setblock %s minecraft:%s" % (at(x, y, z), b) for x, y, z, b in STRUCT_A])
A.cmd("structure B", *["setblock %s minecraft:%s" % (at(x, y, z), b) for x, y, z, b in STRUCT_B])
A.cmd("architect tables: blueprint A, template T (of A), blueprint B",
      *architect(ARCH_A, BOX_A, "upgrade-a", "blueprint_clean"))
A.wait_cmd("architect A finished its blueprint", "data get block %s items.out.items[0].stack.id" % at(*ARCH_A),
           "blueprint_used", 30)
A.cmd("template architect", *architect(ARCH_T, BOX_A, "upgrade-t", "template_clean"))
A.wait_cmd("architect T finished its template", "data get block %s items.out.items[0].stack.id" % at(*ARCH_T),
           "template_used", 30)
A.wait(1500)   # B's server file is the newest one: the copy step deletes it by that
A.cmd("blueprint B architect", *architect(ARCH_B, BOX_B, "upgrade-b", "blueprint_clean"))
A.wait_cmd("architect B finished its blueprint", "data get block %s items.out.items[0].stack.id" % at(*ARCH_B),
           "blueprint_used", 30)

A.cmd("chest: facade items, blanks, then the used blueprint A and template T; the library beside it",
      "setblock %s %slibrary[facing=south]" % (at(*LIBRARY), BC),
      "setblock %s minecraft:chest[facing=south]" % at(*CHEST),
      "data merge block %s {Items:[%s]}" % (at(*CHEST), ",".join([
          chest_item(2, BC + "plug_facade", 1, '"minecraft:custom_data":{facade:%s}' % F_LOG),
          chest_item(3, BC + "plug_facade", 1, '"minecraft:custom_data":{facade:%s}' % F_HOLLOW_PURPUR),
          chest_item(4, BC + "plug_facade", 1, '"minecraft:custom_data":{facade:%s}' % F_PHASED),
          chest_item(5, BC + "blueprint_clean", 2), chest_item(6, BC + "template_clean", 1)])),
      # A placeholder in the slot first, then the architect's stack merged over it (id, count and components).
      'data modify block %s Items append value {Slot:0b,id:"minecraft:stone",count:1}' % at(*CHEST),
      "data modify block %s Items[{Slot:0b}] merge from block %s items.out.items[0].stack" % (at(*CHEST), at(*ARCH_A)),
      'data modify block %s Items append value {Slot:1b,id:"minecraft:stone",count:1}' % at(*CHEST),
      "data modify block %s Items[{Slot:1b}] merge from block %s items.out.items[0].stack" % (at(*CHEST), at(*ARCH_T)))
A.echo("chest contents as 26.2 saved them", "data get block %s Items" % at(*CHEST))

A.cmd("facade pipes: hollow purpur and phased by NBT, two bare pipes for item use",
      *plug(*PIPES[1], F_HOLLOW_PURPUR), *plug(*PIPES[2], F_PHASED), *bare_pipe(*PIPES[0]), *bare_pipe(*PIPES[3]))
A.tp(PIPES[0][0], 0, 14, 180, 0)
A.hold(facade_item(F_LOG))
A.aim(*PIPES[0])
A.step(label="place the oak-log (axis z) facade by hand", do="use")
A.tp(PIPES[3][0], 0, 14, 180, 0)
A.hold(facade_item(F_HOLLOW_BIRCH))
A.aim(*PIPES[3])
A.step(label="place the hollow birch-log (axis x) facade by hand", do="use")
A.hold("minecraft:air")
for i, p in enumerate(PIPES):
    A.echo("pipe %d facade as 26.2 saves it (Name/Properties keys)" % (i + 1), "data get block %s plugs" % at(*p))

# Electronic Library: a download reaches only players watching the GUI, so open it first.
A.tp(LIBRARY[0], 0, 8, 180, 0)
A.aim(*LIBRARY)
A.step(label="open the Electronic Library", do="use")
A.step(do="expectScreen", contains="Library", timeoutSec=10)
for name, arch in (("A", ARCH_A), ("B", ARCH_B), ("T", ARCH_T)):
    A.cmd("library: download %s" % name,
          "data modify block %s items.downOut.items set value []" % at(*LIBRARY),
          "data modify block %s items.downIn.items set from block %s items.out.items" % (at(*LIBRARY), at(*arch)))
    A.wait_cmd("library: %s downloaded" % name, "data get block %s items.downOut.items[0].stack.id" % at(*LIBRARY),
               "_used", 20)
    A.wait(500)
A.wait(1000)
A.shot("library-list", "Electronic Library list: upgrade-a, upgrade-b, upgrade-t (26.2, as downloaded)")
A.step(name="library", do="widgets")
A.step(do="close")
A.cmd("empty the library's slots", "data modify block %s items.downOut.items set value []" % at(*LIBRARY))

A.tp(6, 0, 19, 180, 8)
A.wait(1500)
A.shot("facades", "four pipes, left to right: oak log (bark facing you, axis z), hollow purpur pillar (axis x), "
       "phased facade showing quartz pillar (axis x), hollow birch log (axis x)")
A.cmd("a glass block to stand on above the structures", "setblock %s minecraft:glass" % at(6, 3, -6))
A.tp(6, 4, -6, 0, 40)
A.wait(1500)
A.shot("structures", "structure A (front left) and B (behind it): stairs, slabs, logs, water; nothing east yet")

A.echo("save the world", "save-all flush")
A.step(label="pause menu", do="pause")
A.step(label="save and quit", do="click", widgetText="Save and Quit to Title")
A.step(do="expectScreen", contains="TitleScreen", timeoutSec=60)

# ══ verify (26.3, then 26.2 as the baseline) ══════════════════════════════════════════════════════════════════════
V = Scenario()
setup(V)
V.cmd("daylight, no drops", "time set 6000", "weather clear", "?clear @p",
      "?kill @e[type=minecraft:item,x={X},y={Y},z={Z},distance=..40]")
V.wait(3000)
V.shot("facades", "B0: the four facades from 26.2 exactly as on 26.2: oak log (axis z), hollow purpur pillar (axis x), "
       "phased quartz pillar (axis x), hollow birch log (axis x); no bare pipe, no missing texture")
for i, p in enumerate(PIPES):
    V.echo("B0: pipe %d facade read back (expect the block and its axis in the node's own keys, never air)" % (i + 1),
           "data get block %s plugs" % at(*p))

# Facade items carried over in the chest: place each one on a fresh pipe.
V.cmd("three bare pipes for the carried-over facade items", *[c for p in NEW_PIPES for c in bare_pipe(*p)])
for slot, p, what in ((2, NEW_PIPES[0], "oak log axis z"), (3, NEW_PIPES[1], "hollow purpur axis x"),
                      (4, NEW_PIPES[2], "phased quartz/red wool")):
    V.tp(p[0], 0, 14, 180, 0)
    V.cmd("take the %s facade item from the chest" % what,
          "item replace entity @p weapon.mainhand from block %s container.%d" % (at(*CHEST), slot))
    V.wait(600)
    V.aim(*p)
    V.step(label="place the carried-over %s facade item" % what, do="use")
    V.echo("B0: the placed %s facade (expect that block, not air)" % what, "data get block %s plugs" % at(*p))
V.hold("minecraft:air")
V.tp(6, 0, 19, 180, 8)
V.wait(1500)
V.shot("facades-all", "B0: seven pipes: the four 26.2 facades, then oak log (axis z), hollow purpur (axis x), "
       "phased quartz (axis x) placed from the carried-over items")

# Facade item tooltips in the chest.
V.tp(CHEST[0], 0, 7, 180, 0)
V.aim(*CHEST)
V.step(label="open the chest", do="use")
V.step(do="expectScreen", contains="ContainerScreen", timeoutSec=10)
V.wait(500)
V.shot("chest", "B0: the chest from 26.2: used blueprint, used template, three facade items drawn as their blocks")
V.step(label="hover the oak-log facade item", do="hover", slotIndex=2)
V.shot("chest-facade-tooltip", "B0: tooltip names the facade block (Oak Log), not a bare 'Facade'")
V.step(label="hover the phased facade item", do="hover", slotIndex=4)
V.shot("chest-phased-tooltip", "B0: phased facade tooltip lists its states (quartz pillar, red wool)")
V.step(do="close")

# Builder A from the blueprint kept in the chest (the server's snapshot file, written on 26.2).
V.cmd("Builder A (owner, power, resources, water)", *builder(BUILDER_A),
      "item replace entity @p inventory.0 from block %s container.0" % at(*CHEST))
V.tp(BUILDER_A[0], 0, -5, 0, 0)
V.hold("minecraft:air")
V.aim(*BUILDER_A)
V.step(label="open Builder A", do="use")
V.step(do="expectScreen", contains="GuiBuilder", timeoutSec=10)
V.step(label="pick blueprint A up from the inventory", do="click", slotIndex=52)
V.step(label="put it in the Builder", do="click", slotIndex=0)
V.wait(1500)
V.shot("builder-a", "Builder A holds blueprint A; the required list shows logs, stairs, slabs, pillars, hay, water")
V.step(do="close")
s0, s1, d = mirror(BOX_A, SHIFT)
V.wait_cmd("B0: Builder A rebuilt structure A state for state", "execute if blocks %s %s %s all" % (s0, s1, d),
           "(?i)passed", 120)

# Library: the client list from the files 26.2 wrote; upload B (the 26.3 server only has it from this upload).
V.tp(LIBRARY[0], 0, 8, 180, 0)
V.aim(*LIBRARY)
V.step(label="open the Electronic Library", do="use")
V.step(do="expectScreen", contains="Library", timeoutSec=10)
V.wait(1000)
V.shot("library-list", "B0: the library lists upgrade-a, upgrade-b, upgrade-t as on 26.2")
V.step(name="library", do="widgets")
LIB_ROW_B = (139, 44)   # GUI units: the second list row (guiLeft 91, guiTop 10; rows 8 high from y 22)
# Control first: the template carries no block state, so its upload does not depend on the key spelling.
V.step(label="select upgrade-t", do="click", x=LIB_ROW_B[0], y=LIB_ROW_B[1] + 8)
V.cmd("a blank template into the upload slot",
      'data modify block %s items.upIn.items set value [{stack:{id:"%stemplate_clean",count:1}}]' % (at(*LIBRARY), BC))
V.wait_cmd("control: upload of upgrade-t (no block states) finished",
           "data get block %s items.upOut.items[0].stack.id" % at(*LIBRARY), "template_used", 30)
V.step(label="pick the uploaded template up", do="click", slotIndex=3)
V.step(label="put it in the inventory", do="click", slotIndex=6)
V.step(label="select upgrade-b", do="click", x=LIB_ROW_B[0], y=LIB_ROW_B[1])
V.wait(500)
V.shot("library-selected", "upgrade-b highlighted")
V.echo("the library's selection on the server (expect the upgrade-b header)", "data get block %s selected" % at(*LIBRARY))
V.cmd("a blank blueprint into the upload slot",
      'data modify block %s items.upIn.items set value [{stack:{id:"%sblueprint_clean",count:1}}]'
      % (at(*LIBRARY), BC))
V.wait_cmd("B0: upload of upgrade-b finished", "data get block %s items.upOut.items[0].stack.id" % at(*LIBRARY),
           "blueprint_used", 45)
V.shot("library-uploaded", "upgrade-b selected; the upload output holds a used blueprint")
V.step(label="pick the uploaded blueprint up", do="click", slotIndex=3)
V.step(label="put it in the inventory", do="click", slotIndex=5)
V.step(do="close")

V.cmd("Builder B", *builder(BUILDER_B))
V.tp(BUILDER_B[0] + 3, 0, BUILDER_B[2], 90, 0)   # from the east: structure A's copy is south of it
V.aim(*BUILDER_B)
V.step(label="open Builder B", do="use")
V.step(do="expectScreen", contains="GuiBuilder", timeoutSec=10)
V.step(label="pick blueprint B up from the inventory", do="click", slotIndex=53)
V.step(label="put it in the Builder", do="click", slotIndex=0)
V.wait(1000)
V.step(do="close")
s0, s1, d = mirror(BOX_B, SHIFT)
V.wait_cmd("B0: Builder B rebuilt structure B (from the library upload) state for state",
           "execute if blocks %s %s %s all" % (s0, s1, d), "(?i)passed", 120)
V.cmd("a glass block to stand on above the structures", "setblock %s minecraft:glass" % at(6, 3, -6))
V.tp(6, 4, -6, 0, 40)
V.wait(1500)
V.shot("structures", "B0: structures A and B on the left, their rebuilt copies 10 blocks east, identical")

# B8: the carried-over -common.toml has disableRfPipe = true on 26.3 (the 26.2 baseline keeps its default false).
V.cmd("forget the RF pipe recipe", "?recipe take @p %spipe_cobble_rf" % BC)
V.echo("B8: RF pipe recipe (26.3 with disableRfPipe=true: unknown recipe; 26.2 default: unlocked)",
       "recipe give @p %spipe_cobble_rf" % BC)


def write(path, name, description, sc, **extra):
    data = {"name": name, "description": description, "base": [0, 150, 0], "save": "bc-upgrade", **extra,
            "steps": sc.s}
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


write(OUT_AUTHOR, "upgrade-26-3-author",
      "B0/B8 world upgrade, part 1 (run on 26.2): blueprints, a template, the library list and facades saved by 26.2. "
      "Generated by generators/gen_upgrade_26_3.py, whose docstring has the carry-over steps.",
      A, saveSeed="bc-smoke")
write(OUT_VERIFY, "upgrade-26-3-verify",
      "B0/B8 world upgrade, part 2 (run on 26.3, then 26.2 as the baseline) on the save carried over from the author "
      "run: facades render and read back, the library lists the snapshots, Builders rebuild both structures. "
      "Generated by generators/gen_upgrade_26_3.py.",
      V)
print("wrote", OUT_AUTHOR, len(A.s), "steps;", OUT_VERIFY, len(V.s), "steps")
