"""
guide_model.py — the in-game Guide Book as data, shared by the exporters.

Reads the registration script guide.txt (expanding the aliases in util.txt), the page bodies under
compat/buildcraft/guide/en_us/, lang/en_us.json and the data-driven recipes, and resolves every entry's
title and contents-page placement the way the book does. Rendering lives in the exporters:
  scripts/guide-to-wiki.py     GitHub wiki
  scripts/guide-to-guideme.py  GuideME guide (AE2's guide library)

Stdlib only.
"""

import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "src/main/resources"
ASSETS = RES / "assets/buildcraftunofficial"
GUIDE_DIR = ASSETS / "compat/buildcraft/guide"
EN = GUIDE_DIR / "en_us"
LANG_FILE = ASSETS / "lang/en_us.json"
RECIPE_DIR = RES / "data/buildcraftunofficial/recipe"
MODELS = ASSETS / "models"


class TrackingDict(dict):
    """A dict that remembers every key looked up, present or not. The GuideME exporter's staleness stamp
    lists exactly the lang keys a run read, so an unrelated lang edit doesn't mark the export stale."""

    def __init__(self, *a, **kw):
        super().__init__(*a, **kw)
        self.read = set()

    def __getitem__(self, k):
        self.read.add(k)
        return super().__getitem__(k)

    def get(self, k, default=None):
        self.read.add(k)
        return super().get(k, default)

    def __contains__(self, k):
        self.read.add(k)
        return super().__contains__(k)


LANG = TrackingDict(json.loads(LANG_FILE.read_text(encoding="utf-8")))

warnings = []
def warn(m):
    warnings.append(m)

# --------------------------------------------------------------------------- #
# Title helpers
# --------------------------------------------------------------------------- #
ACRONYMS = {"Rf": "RF", "Fe": "FE", "Mj": "MJ", "Led": "LED", "Json": "JSON",
            "Bc": "BC", "Insn": "Instruction", "Toc": "TOC", "Gui": "GUI"}
# Explicit title overrides keyed by the guide.txt "title" field or page leaf.
TITLE_OVERRIDE = {
    "logic_gates": "Logic Gates",
    "pipe_wires": "Pipe Wires",
    "json_insn_format": "JSON Instruction Format",
    "guide_page_format": "Guide Page Format",
}

def prettify(leaf):
    words = re.split(r"[_\s]+", leaf.strip())
    out = []
    for w in words:
        if not w:
            continue
        tc = w[:1].upper() + w[1:]
        out.append(ACRONYMS.get(tc, tc))
    return " ".join(out)

def split_id(rid):
    if ":" in rid:
        d, p = rid.split(":", 1)
    else:
        d, p = "minecraft", rid
    return d, p

def lang_name(stack_id, is_block=False):
    """Display name for an item/block stack from lang/en_us.json, with fallbacks."""
    d, p = split_id(stack_id)
    dd = d.replace(":", ".")
    order = (["block", "item"] if is_block else ["item", "block"])
    for kind in order:
        v = LANG.get(f"{kind}.{dd}.{p}")
        if v:
            return v
    return prettify(p.split("/")[-1])

# --------------------------------------------------------------------------- #
# Parse guide.txt + util.txt  ->  list of Entry
# --------------------------------------------------------------------------- #
class Entry:
    __slots__ = ("path", "ptype", "subtype", "system", "stack", "statement", "title_key",
                 "sort", "creative", "book", "board", "md", "content", "title")
    def __init__(self, path, ptype, subtype, stack=None, statement=None,
                 title_key=None, sort=0, creative=False, book="main", system=None, board=False):
        self.path = path            # e.g. "block/quarry" (matches en_us/<path>.md)
        self.ptype = ptype          # block | item | pipe | trigger | action | config | concept
        self.subtype = subtype      # automation | engine | pipe_item | ...
        self.system = system        # tag_system: getting_started | power | ... (None: config book, strays)
        self.stack = stack          # buildcraftunofficial:quarry  (None for statements)
        self.statement = statement
        self.title_key = title_key  # explicit title (lang buildcraft.guide.page.<key>, or an override)
        self.sort = sort
        self.creative = creative
        self.book = book
        self.board = board          # icon is a programmed Redstone Board (robot board entries, no stack)
        self.md = None
        self.content = None

# The registration script is a tiny macro language: `alias <name> <argc> `<body>`` defines a
# macro whose body may use %0..%n and ${0} (the book passed to `import`); every other line calls a
# macro or the primitive `add "<path>" `<json>``. Expanding the aliases here, instead of mirroring
# each one's argument layout, keeps the exporters in step with util.txt when aliases change.
_TOKEN = re.compile(r'"((?:[^"\\]|\\.)*)"|`((?:[^`\\]|\\.)*)`|(\n)|([^\s"`]+)|[ \t\r]+', re.DOTALL)

def _strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return "\n".join("" if l.strip().startswith(("//", "~")) else l for l in text.splitlines())

def _statements(text):
    """Yield (name, [args]) per statement; a statement ends at a newline outside quotes/backticks."""
    cur = []
    for m in _TOKEN.finditer(text):
        q, bt, nl, word = m.groups()
        if nl is not None:
            if cur:
                yield cur[0], cur[1:]
            cur = []
        elif q is not None:
            cur.append(q)
        elif bt is not None:
            cur.append(bt.replace("\\`", "`"))
        elif word is not None:
            cur.append(word)
    if cur:
        yield cur[0], cur[1:]

def _load_aliases():
    aliases = {}
    text = _strip_comments((GUIDE_DIR / "util.txt").read_text(encoding="utf-8"))
    for name, args in _statements(text):
        if name == "alias":
            aliases[args[0]] = (int(args[1]), args[2])
    return aliases

def parse_guide():
    aliases = _load_aliases()
    text = _strip_comments((GUIDE_DIR.parent / "guide.txt").read_text(encoding="utf-8"))
    entries = []
    state = {"book": "buildcraftunofficial:main"}   # set by each `import "<util>" "<book>"`

    def run(src, depth=0):
        for name, args in _statements(src):
            if name == "import":
                state["book"] = args[1] if len(args) > 1 else "buildcraftunofficial:main"
            elif name == "add" and len(args) == 2:
                add(args[0], args[1].replace("${0}", state["book"]))
            elif name in aliases and depth < 16:
                argc, body = aliases[name]
                if len(args) != argc:
                    warn(f"guide.txt: alias '{name}' wants {argc} args, got {len(args)}: {args}")
                for i in sorted(range(len(args)), reverse=True):
                    body = body.replace(f"%{i}", args[i])
                run(body, depth + 1)
            else:
                warn(f"guide.txt: unhandled statement '{name}' {args}")

    def add(path, raw):
        try:
            obj = json.loads(raw)
        except Exception as e:
            warn(f"guide.txt: bad JSON for '{path}': {e}")
            return
        entries.append(Entry(
            path=path,
            ptype=obj.get("tag_type", "item"),
            subtype=obj.get("tag_subtype", ""),
            system=obj.get("tag_system"),
            stack=obj.get("stack"),
            statement=obj.get("statement"),
            title_key=obj.get("title"),
            sort=int(obj.get("sort", 0) or 0),
            creative=bool(obj.get("creative_only", False)),
            book="config" if obj.get("book", "").endswith("config") else "main",
            board=isinstance(obj.get("icon"), dict) and "board" in obj["icon"],
        ))

    run(text)
    return entries

# --------------------------------------------------------------------------- #
# Recipes (data-driven only; assembly/refinery recipes are registered in code)
# --------------------------------------------------------------------------- #
def load_recipes():
    by_result = {}     # result id (no components) -> [recipe dict]
    by_id = {}         # filename stem -> recipe dict
    uses = {}          # ingredient BC id -> set(result id)
    all_recipes = []
    for f in sorted(p for p in RECIPE_DIR.iterdir() if p.is_file() and p.suffix == ".json"):
        try:
            r = json.loads(f.read_text(encoding="utf-8"))
        except Exception as e:
            warn(f"recipe parse {f.name}: {e}")
            continue
        r["_id"] = f.stem
        all_recipes.append(r)
        by_id[f.stem] = r
        rid = recipe_result_id(r)
        if rid:
            by_result.setdefault(rid, []).append(r)
        for kind, iid in recipe_ingredient_ids(r):
            if kind == "item" and iid.startswith("buildcraftunofficial:") and rid:
                uses.setdefault(iid, set()).add(rid)
    return by_result, by_id, uses, all_recipes

def recipe_result_id(r):
    res = r.get("result", {})
    return res.get("id") if isinstance(res, dict) else res

def ing_ids(val):
    """Normalise any ingredient form -> list of (kind, id)."""
    out = []
    if val is None:
        return out
    if isinstance(val, str):
        return [("tag", val[1:]) if val.startswith("#") else ("item", val)]
    if isinstance(val, list):
        for v in val:
            out += ing_ids(v)
        return out
    if isinstance(val, dict):
        if "item" in val:
            out.append(("item", val["item"]))
        if "tag" in val:
            out.append(("tag", val["tag"]))
        if "items" in val:
            items = val["items"]
            if isinstance(items, str):
                out += ing_ids(items)
            elif isinstance(items, list):
                for v in items:
                    out += ing_ids(v)
    return out

def recipe_ingredient_ids(r):
    out = []
    if "key" in r:
        for v in r["key"].values():
            out += ing_ids(v)
    for v in r.get("ingredients", []):
        out += ing_ids(v)
    return out

# --------------------------------------------------------------------------- #
# Page body preprocessing shared by every exporter
# --------------------------------------------------------------------------- #
def strip_comment_lines(md):
    """Drop `//` comment lines the in-game loader ignores; `////` escapes a literal `//`.
    See XmlPageLoader#loadParts."""
    kept = []
    for ln in md.splitlines():
        st = ln.lstrip()
        if st.startswith("////"):
            kept.append(ln.replace("////", "//", 1))
        elif st.startswith("//"):
            continue
        else:
            kept.append(ln)
    return "\n".join(kept)

def drop_block(tag, s):
    return re.sub(rf"<{tag}\b[^>/]*>.*?</{tag}>", "", s, flags=re.DOTALL)

def keep_block(tag, s):
    return re.sub(rf"<{tag}\b[^>/]*>(.*?)</{tag}>", r"\1", s, flags=re.DOTALL)

def attrs_of(tag_text):
    return dict(re.findall(r'(\w+)="([^"]*)"', tag_text))

COLOUR_TAGS = ["red", "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
               "dark_purple", "gold", "gray", "grey", "dark_gray", "dark_grey",
               "blue", "green", "aqua", "light_purple", "yellow", "white",
               "obfuscated"]

# --------------------------------------------------------------------------- #
# Statement titles
# --------------------------------------------------------------------------- #
# Trigger/action pages: the book titles them with the statement's description, which each Java
# statement class builds itself (getDescription). Statement id -> its lang key, or (key, arg) for a
# formatted one. Keep in step with the statement classes; an unknown id warns and falls back.
STATEMENT_LANG = {
    **{f"buildcraft:engine.stage.{s}": f"gate.trigger.engine.{s}"
       for s in ("blue", "green", "yellow", "red", "overheat")},
    "buildcraft:energyStoredhigh": "gate.trigger.machine.energyStored.high",
    "buildcraft:energyStoredlow": "gate.trigger.machine.energyStored.low",
    "buildcraft:powerRequested": "gate.trigger.pipe.requestsEnergy",
    "buildcraft:pipe_contains_items": "gate.trigger.pipe.containsItems",
    "buildcraft:pipe_contains_fluids": "gate.trigger.pipe.containsFluids",
    **{f"buildcraft:pipe.dir.{d}": ("gate.action.pipe.direction", f"direction.{d}")
       for d in ("north", "east", "south", "west", "up", "down")},
    **{f"buildcraft:inventory.{s}": f"gate.trigger.inventory.{s}" for s in ("empty", "contains", "space", "full")},
    **{f"buildcraft:fluid.{s}": f"gate.trigger.fluid.{s}" for s in ("empty", "contains", "space", "full")},
    "buildcraftunofficial:trigger.true": "gate.trigger.true",
    "buildcraft:redstone.input.active": "gate.trigger.redstone.input.active",
    "buildcraft:redstone.input.inactive": "gate.trigger.redstone.input.inactive",
    "buildcraft:redstone.output": "gate.action.redstone.signal",
    "buildcraft:work.scheduled": "gate.trigger.machine.scheduled",
    "buildcraft:work.done": "gate.trigger.machine.done",
    **{f"buildcraft:timer_{n}": ("gate.trigger.timer", s) for n, s in (("short", "5"), ("medium", "10"), ("long", "15"))},
    "buildcraft:pulsar.constant": "gate.action.pulsar.constant",
    "buildcraft:pulsar.single": "gate.action.pulsar.single",
    "buildcraft:robot.in.station": "gate.trigger.robot.in.station",
    "buildcraft:robot.goto_station": "gate.action.robot.goto_station",
    "buildcraft:robot.work_in_area": "gate.action.robot.work_in_area",
    "buildcraft:robot.wakeup": "gate.action.robot.wakeup",
    "buildcraft:robot.linked": "gate.trigger.robot.linked",
    "buildcraft:robot.reserved": "gate.trigger.robot.reserved",
    **{f"buildcraft:station.{s}": f"gate.action.station.{s}"
       for s in ("provide_items", "accept_items", "request_items", "provide_machine_request",
                 "accept_fluids", "provide_fluids", "forbid_robot", "force_robot")},
}
# Where the book's title only reads right beside its icon and chapter, or can't be a page name
# (`<`, `/` and `%` break wiki links; inventory and tank share "Contains < 25%"), the exports say
# what the book's context shows. Keyed by statement id.
STATEMENT_TITLE_OVERRIDE = {
    **{f"buildcraft:inventorylevel.below{n}": f"Inventory Below {n} Percent" for n in (25, 50, 75)},
    **{f"buildcraft:fluidlevel.below{n}": f"Tank Below {n} Percent" for n in (25, 50, 75)},
    "buildcraft:robot.load_unload_area": "Load or Unload in Area",
    "buildcraft:machine.on": "Machine On",
    "buildcraft:machine.off": "Machine Off",
    "buildcraft:machine.loop": "Machine Loop",
    "buildcraft:light_bright": "Light Bright",
    "buildcraft:light_dark": "Light Dark",
    "buildcraft:robot.sleep": "Robot Sleep",
    "buildcraft:robot.work_filter": "Robot Filter",
    "buildcraft:robot.work_filter_tool": "Robot Filter Tool",
    # These two pages describe the whole family (every colour), not the one statement registered.
    "buildcraft:pipe.color.black": LANG.get("buildcraft.guide.group.to.buildcraft.paint_pipe_colour", "Paint Passing Items"),
    "buildcraft:extraction.preset.red": "Extraction Preset",
}

def statement_title(sid):
    if sid in STATEMENT_TITLE_OVERRIDE:
        return STATEMENT_TITLE_OVERRIDE[sid]
    spec = STATEMENT_LANG.get(sid)
    if spec is None:
        warn(f"statement '{sid}' has no name mapping; add it to STATEMENT_LANG")
        return None
    key, arg = (spec, None) if isinstance(spec, str) else spec
    if key not in LANG:
        warn(f"statement '{sid}': lang key '{key}' missing")
        return None
    text = LANG[key]
    if arg is not None:
        text = text.replace("%s", LANG.get(arg, arg)).replace("%d", LANG.get(arg, arg))
    return text

# --------------------------------------------------------------------------- #
# Categories, systems, contents order
# --------------------------------------------------------------------------- #
# Category pages are registered in code, not guide.txt: their system and subtype come from the
# registerCategory calls in GuideManager.java. Keep in step with those.
CATEGORY_PLACEMENT = {
    "concept/filler_patterns": ("building", "builders"),
    "concept/emzuli_extraction_presets": ("transport", "pipe_item"),
    "concept/pipe_signals": ("logic", "wire"),
    "concept/set_pipe_direction": ("transport", "pipe_item"),
    "action/pipe_colour": ("transport", "pipe_item"),
    "concept/set_power_limit": ("power", "power_delivery"),
}
# Category group ids used by <link to="buildcraft:<group>"/> -> the page that stands for them.
CATEGORY_GROUPS = {
    "filler_patterns": "concept/filler_patterns",
    "extraction_presets": "concept/emzuli_extraction_presets",
    "pipe_signals": "concept/pipe_signals",
    "set_pipe_direction": "concept/set_pipe_direction",
    "paint_pipe_colour": "action/pipe_colour",
    "set_power_limit": "concept/set_power_limit",
}

# Same top-level order as the book's contents page (GuideManager.SYSTEM_CHAPTER_ORDER): a reading
# path through the mod, not A-Z. Pages with no system (unregistered concept pages) come next, then
# the separate config book.
SYSTEM_ORDER = ["getting_started", "power", "transport", "logic", "building", "mining_fluids",
                "silicon", "robots"]
OTHER, CONFIG = "_other", "_config"

def system_name(sys_key):
    if sys_key == OTHER:
        return "Other Pages"
    if sys_key == CONFIG:
        return "Configuration"
    return LANG.get(f"buildcraft.guide.chapter.system.{sys_key}", prettify(sys_key))

def subtype_name(sub):
    return LANG.get(f"buildcraft.guide.chapter.subtype.{sub}", prettify(sub) if sub else "")

def grouped(entries):
    """[(system, [(subtype, [entries])])] in contents-page order. As in the book, a subtype
    inherits its lowest member's sort weight; equal weights fall back to the subtype's name."""
    tree = {}
    for e in entries:
        key = CONFIG if e.book == "config" else (e.system or OTHER)
        tree.setdefault(key, {}).setdefault(e.subtype, []).append(e)
    known = [s for s in SYSTEM_ORDER if s in tree]
    rest = sorted(s for s in tree if s not in SYSTEM_ORDER and s not in (OTHER, CONFIG))
    out = []
    for key in known + rest + [k for k in (OTHER, CONFIG) if k in tree]:
        subs = tree[key]
        for es in subs.values():
            es.sort(key=lambda e: (e.sort, e.title.lower()))
        order = sorted(subs, key=lambda s: (min(e.sort for e in subs[s]), subtype_name(s).lower()))
        out.append((key, [(s, subs[s]) for s in order]))
    return out

# --------------------------------------------------------------------------- #
# The whole book
# --------------------------------------------------------------------------- #
class Guide:
    """Every entry with its body and resolved title, plus the link maps the exporters share."""

    def __init__(self):
        entries = parse_guide()

        # Synthesise entries for any .md not registered (concept/*, stray pages).
        registered = {e.path for e in entries}
        for f in sorted(EN.rglob("*.md")):
            rel = f.relative_to(EN).with_suffix("").as_posix()
            if rel in registered:
                continue
            if rel == "item/guide":
                continue  # the book's own intro page; each exporter writes its own front page
            top = rel.split("/")[0]
            ptype = "concept" if top == "concept" else top
            system, subtype = CATEGORY_PLACEMENT.get(rel, (None, ""))
            entries.append(Entry(rel, ptype, subtype, stack=None, system=system))
            registered.add(rel)

        for e in entries:
            f = EN / (e.path + ".md")
            if f.exists():
                e.md = f.read_text(encoding="utf-8")
            e.title = self._title(e)

        # Disambiguate duplicate titles.
        seen = {}
        for e in entries:
            if e.title in seen and seen[e.title].path != e.path:
                e.title = f"{e.title} ({prettify(e.ptype)})"
            seen[e.title] = e
        self.entries = entries
        self.by_path = {e.path: e for e in entries}

        # buildcraftunofficial:quarry -> first entry for that stack
        self.by_stack = {}
        for e in entries:
            if e.stack:
                self.by_stack.setdefault(e.stack, e)

        # leaf -> entry, only when unambiguous (a last-ditch link fallback)
        leaves = {}
        for e in entries:
            leaves.setdefault(e.path.split("/")[-1], []).append(e)
        self.by_leaf = {k: v[0] for k, v in leaves.items() if len(v) == 1}

        self.by_result, self.by_id, self.uses, self.all_recipes = load_recipes()

    @staticmethod
    def _title(e):
        if e.title_key and e.title_key in TITLE_OVERRIDE:
            return TITLE_OVERRIDE[e.title_key]
        if e.title_key and f"buildcraft.guide.page.{e.title_key}" in LANG:
            return LANG[f"buildcraft.guide.page.{e.title_key}"]
        if e.title_key:
            return prettify(e.title_key)
        if e.statement and statement_title(e.statement):
            return statement_title(e.statement)
        if e.stack:
            return lang_name(e.stack, is_block=(e.ptype == "block"))
        leaf = e.path.split("/")[-1]
        return TITLE_OVERRIDE.get(leaf, prettify(leaf))

    def resolve_link(self, target):
        """A page link target (`ns:<dir>/<name>`, or a category group `ns:<group>`) -> Entry or None.
        Mirrors XmlPageLoader#resolveLinkTarget, plus an unambiguous-leaf fallback the book does not
        have (GuideLinkTargetTest keeps every shipped link resolvable the book's way)."""
        d, p = split_id(target)
        if d == "buildcraftunofficial" and p in self.by_path:
            return self.by_path[p]
        if d in ("buildcraft", "buildcraftunofficial") and p in CATEGORY_GROUPS:
            return self.by_path.get(CATEGORY_GROUPS[p])
        return self.by_leaf.get(p.split("/")[-1])
