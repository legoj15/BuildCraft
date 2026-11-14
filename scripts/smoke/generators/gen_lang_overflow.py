"""Every BuildCraft screen, its ledgers and its button tooltips, for a look at text that overflows its box (issue #185).

Run: python scripts/smoke/generators/gen_lang_overflow.py   (rewrites scripts/smoke/scenarios/lang-overflow.json)
     pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3 -Scenario scripts/smoke/scenarios/lang-overflow.json
     ... -Lang ru_ru    and    ... -Lang zh_cn     (the same tour translated; compare same-named shots with English)

The tour never clicks by widget text and never depends on a command's reply text, so it runs in any language; the
machine row is cleared and placed again on every run. The commands that may fail on a re-run (chunks already loaded,
settings already set, nothing to clear) sit in steps flagged "benign"; any other failed command fails its step in every
language. Per screen it takes:
  NN-<screen>.png                the screen as it opens: title, fixed labels, buttons
  NN-<screen>-<ledger>.png       each ledger opened on its own (owner, power, progress on the right; help, match info
                                 on the left), then closed again
  NN-<screen>-help-entry.png     the open help ledger describing whatever outlined element is under the cursor
  NN-<screen>-hoverKK.png        the tooltip of each vanilla widget (BCButton tooltips, the recipe book)
What to look for: text running past the panel edge or into a neighbouring element, a title wider than its window, a
ledger title cut off, a tooltip leaving the screen.

Ledger tabs are not vanilla widgets, so they are clicked by position: the scenario's first step resizes the window to
854x480 (nothing else pins it), which at GUI scale 2 is a 427x240 screen, and a container sits at
leftPos = (427 - width) / 2, topPos = (240 - height) / 2. Closed tabs are
22x24, stacked 29 apart from topPos + 5, right of the window or left of it (lib/gui/ledger/Ledger_Neptune). The runner
deletes config/buildcraft/gui_state.json before each boot, so every ledger starts closed.
"""
import json
import os

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, "..", "scenarios", "lang-overflow.json")
BC = "buildcraftunofficial:"
SCREEN_W, SCREEN_H = 427, 240


def o(a, n):
    return "{%s%s}" % (a, ("+%d" % n) if n > 0 else ("-%d" % -n) if n < 0 else "")


def at(x, y, z):
    return "%s %s %s" % (o('X', x), o('Y', y), o('Z', z))


def pipe(x, definition, plugs=""):
    return ["setblock %s %spipe_holder" % (at(x, 0, 0), BC),
            'data merge block %s {pipe:{def:"%s%s"}%s}' % (at(x, 0, 0), BC, definition, plugs)]


GATE = ',plugs:{south:{id:"%sgate",data:{data:{variant:{logic:1b,material:3b,modifier:3b}}}}}' % BC

# name, screen class (regex), x in the row, setup commands, (width, height), right ledgers, left ledgers, extras.
# The sizes and the ledger lists are each screen's own (its super(...) call and initGuiElements); None = no tab clicks.
SCREENS = [
    ("filler", "GuiFiller", -12, ["setblock %s %sfiller" % (at(-12, 0, 0), BC),
                                  "data merge block %s {markerBox:0b,box_initialized:1b,box_minX:{X-12},box_minY:{Y+1},"
                                  "box_minZ:{Z-4},box_maxX:{X-10},box_maxY:{Y+3},box_maxZ:{Z-2}}" % at(-12, 0, 0)],
     # The pattern palette fills the filler's left side and pushes the help tab down: clicked where it is drawn.
     (176, 241), ["owner", "progress"], [("help", 114, 110)]),
    ("builder", "GuiBuilder", -11, ["setblock %s %sbuilder" % (at(-11, 0, 0), BC)], (256, 222), [], ["help"]),
    ("architect", "GuiArchitectTable", -10, ["setblock %s %sarchitect" % (at(-10, 0, 0), BC)],
     (176, 240), ["owner"], ["help"]),
    ("library", "GuiElectronicLibrary", -9, ["setblock %s %slibrary" % (at(-9, 0, 0), BC)],
     (244, 220), ["owner"], ["help"]),
    ("replacer", "GuiReplacer", -8, ["setblock %s %sreplacer" % (at(-8, 0, 0), BC)], (176, 241), ["owner"], ["help"]),
    ("engine-stone", "ScreenEngineStone", -7, ["setblock %s %sengine_stone" % (at(-7, 0, 0), BC)],
     (176, 166), ["owner", "power"], ["help"]),
    ("engine-iron", "ScreenEngineIron", -6, ["setblock %s %sengine_iron" % (at(-6, 0, 0), BC)],
     (176, 177), ["owner", "power"], ["help"]),
    ("engine-rf", "ScreenEngineFE", -5, ["setblock %s %sengine_rf" % (at(-5, 0, 0), BC)],
     (176, 177), ["owner", "power"], ["help"]),
    ("dynamo", "ScreenDynamoMJ", -4, ["setblock %s %smj_dynamo" % (at(-4, 0, 0), BC)],
     (176, 177), ["owner", "power"], ["help"]),
    ("autoworkbench", "GuiAutoCraftItems", -3, ["setblock %s %sautoworkbench_item" % (at(-3, 0, 0), BC)],
     (176, 197), ["owner"], []),
    ("tank", "GuiTank", -2, ["setblock %s %stank" % (at(-2, 0, 0), BC)], (176, 181), [], ["help"]),
    ("chute", "GuiChute", -1, ["setblock %s %schute" % (at(-1, 0, 0), BC)], (176, 153), [], ["help"]),
    ("distiller", "GuiDistiller", 0, ["setblock %s %sdistiller" % (at(0, 0, 0), BC)], (176, 161), ["owner"], ["help"]),
    ("programming-table", "GuiProgrammingTable", 1, ["setblock %s %sprogramming_table" % (at(1, 0, 0), BC)],
     (176, 207), ["owner", "power"], ["help"]),
    ("requester", "GuiRequester", 2, ["setblock %s %srequester" % (at(2, 0, 0), BC)], (196, 181), ["owner"], ["help"]),
    ("zone-planner", "GuiZonePlanner", 3, ["setblock %s %szone_planner" % (at(3, 0, 0), BC)], (256, 228), [], ["help"]),
    ("assembly-table", "GuiAssemblyTable", 4, ["setblock %s %sassembly_table" % (at(4, 0, 0), BC)],
     (176, 220), ["owner", "power"], ["help"]),
    ("integration-table", "GuiIntegrationTable", 5, ["setblock %s %sintegration_table" % (at(5, 0, 0), BC)],
     (176, 191), ["power"], ["help"]),
    ("advanced-crafting", "GuiAdvancedCraftingTable", 6, ["setblock %s %sadvanced_crafting_table" % (at(6, 0, 0), BC)],
     (176, 241), ["owner", "power"], []),
    ("filtered-buffer", "GuiFilteredBuffer", 7, ["setblock %s %sfiltered_buffer" % (at(7, 0, 0), BC)],
     (176, 169), ["owner"], ["help"]),
    ("diamond-pipe", "GuiDiamondPipe", 8, pipe(8, "diamond_item"), (175, 225), [], ["help"]),
    ("diamond-wood-pipe", "GuiDiamondWoodPipe", 9, pipe(9, "diamond_wood_item"), (175, 161), [], ["help"]),
    ("emzuli-pipe", "GuiEmzuliPipe", 10, pipe(10, "emzuli_item"), (176, 166), [], ["help"]),
    # The gate's left side holds the trigger palette, with the help tab under it: clicked where it is drawn (this
    # gate variant; another slot count moves it).
    ("gate", "GuiGate", 11, pipe(11, "stone_item", GATE), (176, 208), [], [("help", 115, 83)]),
]
# The heat exchanger only opens as a chain of three; it sits behind the row.
HEAT_X, HEAT_Z = 0, -6


def tab_clicks(size, right, left):
    """(ledger name, x, y) for each closed tab's centre."""
    if size is None:
        return []
    w, h = size
    left_pos, top_pos = (SCREEN_W - w) // 2, (SCREEN_H - h) // 2
    out = []
    for k, name in enumerate(right):
        out.append((name, left_pos + w + 11, top_pos + 5 + 29 * k + 12))
    # Left ledgers stack in add order; the help ledger is always added last. A (name, x, y) entry is a tab some other
    # left-side element displaces, clicked at that fixed point instead.
    for k, entry in enumerate(left):
        if isinstance(entry, tuple):
            out.append(entry)
        else:
            out.append((entry, left_pos - 11, top_pos + 5 + 29 * k + 12))
    return out


def look_at_screen(name, cls, size, right, left, hover=True):
    steps = [{"do": "expectScreen", "contains": cls},
             {"do": "wait", "ms": 400},
             {"name": name, "do": "shot", "look": "title and fixed labels stay inside the window; nothing overlaps"}]
    for ledger, x, y in tab_clicks(size, right, left):
        steps += [{"label": "open the %s ledger" % ledger, "do": "click", "x": x, "y": y, "allowMiss": True},
                  {"do": "wait", "ms": 900},
                  {"name": "%s-%s" % (name, ledger), "do": "shot",
                   "look": "the %s ledger is open; its title and lines fit inside it" % ledger}]
        if ledger == "help":
            # An open help ledger describes the outlined element under the cursor: sample the one a third of the way
            # down the window, which is a machine's own controls on most screens.
            steps += [{"label": "point at an outlined element", "do": "hover", "fx": 0.5, "fy": 0.3, "allowMiss": True},
                      # A long entry makes the ledger grow for a second or more: shoot it at its full height.
                      {"do": "wait", "ms": 1800},
                      {"name": "%s-help-entry" % name, "do": "shot",
                       "look": "the help entry's title and text wrap inside the ledger"}]
        steps += [{"label": "close the %s ledger" % ledger, "do": "click", "x": x, "y": y, "allowMiss": True},
                  {"do": "wait", "ms": 600}]
    if hover:
        steps.append({"name": name, "do": "hoverEach", "max": 10})
    steps.append({"do": "close"})
    return steps


steps = [
    # The first step once the world is entered: the runner does not pin a window size, and every ledger-tab position
    # below is computed for this one at GUI scale 2 (the runner's default).
    {"label": "window 854x480 (GUI scale 2: a 427x240 screen, which the ledger-tab positions assume)",
     "do": "window", "width": 854, "height": 480},
    {"label": "creative", "do": "cmd", "run": ["gamemode creative @p"]},
    # "benign": the game answers in the run's language, so the runner cannot read a reply to tell "already so" from a
    # real failure. A step flagged benign says its commands may legitimately fail on a re-run (the world persists
    # between runs: chunks already loaded, difficulty and time already set, floor already placed, nothing to clear),
    # and that holds in every language; a failed command in any other step fails it, as in English.
    {"label": "load the rig's chunks", "do": "cmd", "benign": True, "run": [
        "forceload add %s %s %s %s" % (o('X', -14), o('Z', -8), o('X', 14), o('Z', 4))]},
    {"do": "tp", "at": [0, 0, 2], "yaw": 180, "pitch": 30},
    {"do": "wait", "ms": 3000},
    {"label": "floor, noon, empty inventory", "do": "cmd", "benign": True, "run": [
        "difficulty peaceful", "time set 6000", "weather clear",
        "fill %s %s minecraft:smooth_stone" % (at(-14, -1, -8), at(14, -1, 4)),
        "fill %s %s minecraft:air" % (at(-14, 0, -8), at(14, 4, 4)),
        "clear @p"]},
    # Not benign: the row is placed on air the step above just cleared, so every setblock and merge must succeed.
    {"label": "the machine row", "do": "cmd", "run":
        [c for s in SCREENS for c in s[3]] +
        ["fill %s %s %sheat_exchange[facing=north]" % (at(HEAT_X - 1, 0, HEAT_Z), at(HEAT_X + 1, 0, HEAT_Z), BC)]},
    {"do": "hold", "item": "minecraft:air"},
    # The row was just placed from air: give the client a moment to receive every block and block entity.
    {"do": "wait", "ms": 2000},
]

for name, cls, x, _setup, size, right, left in SCREENS:
    steps.append({"do": "tp", "at": [x, 0, 2], "yaw": 180, "pitch": 30})
    if name == "gate":
        # The plug sits on the pipe's south face: aim at the plate itself, not the pipe behind it.
        steps.append({"do": "aim", "at": [x + 0.5, 0.5, 0.85], "exact": True})
    else:
        steps.append({"do": "aim", "at": [x, 0, 0]})
    steps.append({"label": "open %s" % name, "do": "use"})
    steps += look_at_screen(name, cls, size, right, left)

steps += [{"do": "tp", "at": [HEAT_X, 0, HEAT_Z + 2], "yaw": 180, "pitch": 30},
          {"do": "aim", "at": [HEAT_X, 0, HEAT_Z]},
          {"label": "open heat-exchanger", "do": "use"}]
steps += look_at_screen("heat-exchanger", "GuiHeatExchange", (176, 171), [], ["help"])

# Item screens: held and used on the air overhead.
steps += [{"do": "tp", "at": [0, 0, 3], "yaw": 0, "pitch": -80},
          {"do": "hold", "item": BC + "list"},
          {"do": "look", "yaw": 0, "pitch": -80},
          {"label": "open list", "do": "use"}]
steps += look_at_screen("list", "GuiList", (176, 191), [], ["match-info", "help"])
steps += [{"do": "hold", "item": BC + "guide"},
          {"do": "look", "yaw": 0, "pitch": -80},
          {"label": "open guide", "do": "use"},
          {"do": "expectScreen", "contains": "GuiGuide", "timeoutSec": 20},
          {"do": "wait", "ms": 600},
          {"name": "guide-cover", "do": "shot", "look": "the book's title fits the cover"},
          {"label": "open the book", "do": "click", "x": 213, "y": 120, "allowMiss": True},
          {"do": "wait", "ms": 600},
          {"name": "guide-contents", "do": "shot", "look": "contents entries and the sort buttons' area"},
          {"name": "guide-contents", "do": "hoverEach", "max": 10},
          # Was x=250, y=3: the tab's click area is its text width + 20 wide, and 250 missed it in Russian and Chinese
          # (issue #197).
          {"label": "open the chapters overlay", "do": "click", "x": 232, "y": 4, "allowMiss": True},
          {"do": "wait", "ms": 500},
          {"name": "guide-chapters", "do": "shot", "look": "chapter names fit their rows; the Chapters tab fits its label"},
          {"label": "pick the third chapter", "do": "click", "x": 155, "y": 50, "allowMiss": True},
          {"do": "wait", "ms": 500},
          {"name": "guide-chapter-page", "do": "shot", "look": "page text wraps inside the page"},
          {"do": "close"},
          {"do": "hold", "item": "minecraft:air"}]

scenario = {
    "name": "lang-overflow",
    "description": "Every BuildCraft screen with each ledger opened in turn and every widget tooltip, clicked by "
                   "position only so it runs under -Lang (ru_ru, zh_cn) to look for text that overflows its box. "
                   "Generated by generators/gen_lang_overflow.py.",
    "base": [60, 150, -60],
    "steps": steps,
}
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(scenario, f, indent=1, ensure_ascii=False)
    f.write("\n")
print("wrote %s (%d steps)" % (os.path.normpath(OUT), len(steps)))
