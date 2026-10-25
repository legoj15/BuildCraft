#!/usr/bin/env python3
"""
guide-to-wiki.py — convert BuildCraft's in-game Guide Book into a GitHub wiki.

The Guide Book content lives as a subset-of-markdown under
    src/main/resources/assets/buildcraftunofficial/compat/buildcraft/guide/en_us/**/*.md
with custom tags (<lore>/<no_lore>, <chapter>, <link>, <recipe*>, <image>, colour
tags, ...). Page titles, categories and TOC order are *not* in the markdown — they
come from the registration script guide.txt (which expands the aliases in util.txt)
plus the item/block display names in lang/en_us.json.

This script reads all of that and emits a self-contained GitHub wiki:
  * one page per Guide entry (prose converted to plain markdown),
  * faithful crafting recipes rendered from data/.../recipe/*.json as item-icon grids,
  * item textures embedded via raw.githubusercontent.com URLs,
  * a Home page and a _Sidebar mirroring the in-game "Sort by Type" contents tree.

Re-runnable: regenerate whenever the guide content changes. Output goes to
wiki-export/ (gitignored); push that to the BuildCraft.wiki.git repo to publish.

Usage:  python3 scripts/guide-to-wiki.py [output_dir]
Stdlib only; no third-party deps.
"""

import json
import os
import re
import sys
from pathlib import Path

# --------------------------------------------------------------------------- #
# Paths / constants
# --------------------------------------------------------------------------- #
REPO = Path(__file__).resolve().parent.parent
RES = REPO / "src/main/resources"
ASSETS = RES / "assets/buildcraftunofficial"
GUIDE_DIR = ASSETS / "compat/buildcraft/guide"
EN = GUIDE_DIR / "en_us"
LANG = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
RECIPE_DIR = RES / "data/buildcraftunofficial/recipe"
MODELS = ASSETS / "models"

# raw.githubusercontent base for embedding textures. Branch must hold the textures.
BRANCH = "main"
RAW = f"https://raw.githubusercontent.com/legoj15/BuildCraft/{BRANCH}/src/main/resources/"

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else (REPO / "wiki-export")

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
                 "sort", "creative", "book", "icon", "md", "content", "title")
    def __init__(self, path, ptype, subtype, stack=None, statement=None,
                 title_key=None, sort=0, creative=False, book="main", system=None, icon=None):
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
        self.icon = icon            # hero icon URL for entries with no stack (robot boards)
        self.md = None
        self.content = None

# The registration script is a tiny macro language: `alias <name> <argc> `<body>`` defines a
# macro whose body may use %0..%n and ${0} (the book passed to `import`); every other line calls a
# macro or the primitive `add "<path>" `<json>``. Expanding the aliases here, instead of mirroring
# each one's argument layout, keeps this script in step with util.txt when aliases change.
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
        icon = None
        if isinstance(obj.get("icon"), dict) and "board" in obj["icon"]:
            icon = RAW + "assets/buildcraftunofficial/textures/item/redstone_board.png"
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
            icon=icon,
        ))

    run(text)
    return entries

# --------------------------------------------------------------------------- #
# Texture / icon resolution
# --------------------------------------------------------------------------- #
_model_cache = {}
def _read_model(kind, leaf):
    key = (kind, leaf)
    if key in _model_cache:
        return _model_cache[key]
    f = MODELS / kind / f"{leaf}.json"
    obj = None
    if f.exists():
        try:
            obj = json.loads(f.read_text(encoding="utf-8"))
        except Exception as e:
            warn(f"model parse {f}: {e}")
    _model_cache[key] = obj
    return obj

def texture_url(tex):
    """A `domain:path` texture ref -> raw URL, only for textures we host."""
    if not tex or tex.startswith("#"):
        return None
    d, p = split_id(tex)
    if d != "buildcraftunofficial":
        return None
    return RAW + f"assets/{d}/textures/{p}.png"

def icon_url(stack_id):
    """Best-effort inventory icon for a BC item/block stack -> raw URL or None."""
    if not stack_id:
        return None
    d, p = split_id(stack_id)
    if d != "buildcraftunofficial":
        return None
    model = _read_model("item", p)
    if model:
        tex = model.get("textures", {})
        for k in ("layer0", "center", "top", "icon", "particle"):
            if k in tex:
                u = texture_url(tex[k])
                if u:
                    return u
        parent = model.get("parent", "")
        if parent.startswith("buildcraftunofficial:block/"):
            b = parent.split("block/", 1)[1]
            bm = _read_model("block", b)
            if bm:
                bt = bm.get("textures", {})
                for k in ("particle", "front", "side", "north", "top", "end", "all", "0", "texture", "down"):
                    if k in bt:
                        u = texture_url(bt[k])
                        if u:
                            return u
    # last resort: a flat block texture named like the item
    u = RAW + f"assets/buildcraftunofficial/textures/block/{p}.png"
    if (ASSETS / f"textures/block/{p}.png").exists():
        return u
    return None

def img_tag(url, name, size=32):
    alt = name.replace('"', "")
    return f'<img src="{url}" width="{size}" height="{size}" alt="{alt}" title="{alt}">'

# --------------------------------------------------------------------------- #
# Recipes
# --------------------------------------------------------------------------- #
def load_recipes():
    by_result = {}     # result id (no components) -> [recipe dict]
    by_id = {}         # filename stem -> recipe dict
    uses = {}          # ingredient BC id -> set(result id)
    all_recipes = []
    for f in sorted(RECIPE_DIR.glob("*.json")):
        try:
            r = json.loads(f.read_text(encoding="utf-8"))
        except Exception as e:
            warn(f"recipe parse {f.name}: {e}")
            continue
        r["_id"] = f.stem
        all_recipes.append(r)
        by_id[f.stem] = r
        res = r.get("result", {})
        rid = res.get("id") if isinstance(res, dict) else res
        if rid:
            by_result.setdefault(rid, []).append(r)
        for kind, iid in recipe_ingredient_ids(r):
            if kind == "item" and iid.startswith("buildcraftunofficial:") and rid:
                uses.setdefault(iid, set()).add(rid)
    return by_result, by_id, uses, all_recipes

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

def ing_label(kind, iid, link_titles):
    """(image-or-None, display-name, markdown-name) for one ingredient."""
    if kind == "tag":
        d, p = split_id(iid)
        seg = p.split("/")
        nm = prettify(" ".join(reversed(seg))) if len(seg) > 1 else prettify(seg[-1])
        return None, nm, f"_{nm}_ (any)"
    d, p = split_id(iid)
    if d == "buildcraftunofficial":
        nm = lang_name(iid)
        url = icon_url(iid)
        title = link_titles.get(iid)
        md = f"[[{title}]]" if title else f"**{nm}**"
        return url, nm, md
    # vanilla
    nm = prettify(p.split("/")[-1])
    wiki = "https://minecraft.wiki/w/" + nm.replace(" ", "_")
    return None, nm, f"[{nm}]({wiki})"

def render_recipe(r, link_titles):
    res = r.get("result", {})
    rid = res.get("id") if isinstance(res, dict) else res
    count = res.get("count", 1) if isinstance(res, dict) else 1
    res_img = icon_url(rid)
    res_name = lang_name(rid, is_block=rid and "block" in (split_id(rid)[1]))
    res_cell = (img_tag(res_img, res_name, 40) + " " if res_img else "") + f"**{res_name}**" + (f" ×{count}" if count and count != 1 else "")
    rtype = r.get("type", "")

    if rtype == "minecraft:crafting_shaped":
        pattern = r.get("pattern", [])
        key = r.get("key", {})
        width = max((len(row) for row in pattern), default=3)
        width = max(width, 1)
        # resolve each key char to (img, name, md)
        legend = {}
        def cell(ch):
            if ch == " " or ch not in key:
                return ""
            ids = ing_ids(key[ch])
            if not ids:
                return ""
            img, name, md = ing_label(*ids[0], link_titles)
            legend[name] = md
            return img_tag(img, name) if img else f'<sub>{name}</sub>'
        lines = ["<table>"]
        for row in pattern:
            row = row.ljust(width)
            cells = "".join(f'<td align="center" width="40" height="40">{cell(ch)}</td>' for ch in row[:width])
            lines.append(f"<tr>{cells}</tr>")
        lines.append("</table>")
        grid = "\n".join(lines)
        leg = " · ".join(f"{md}" for md in dict.fromkeys(legend.values())) if legend else ""
        return f"{grid}\n\n→ {res_cell}" + (f"\n\n<sub>Ingredients: {leg}</sub>" if leg else "")

    if rtype == "minecraft:crafting_shapeless":
        parts = []
        seen = {}
        for ing in r.get("ingredients", []):
            ids = ing_ids(ing)
            if not ids:
                continue
            img, name, md = ing_label(*ids[0], link_titles)
            cellimg = (img_tag(img, name, 24) + " ") if img else ""
            parts.append(f"{cellimg}{md}")
        joined = " + ".join(parts)
        return f"**Shapeless:** {joined}\n\n→ {res_cell}"

    return f"_Recipe ({rtype}) → {res_cell}_"

def recipes_for(stack, by_result, link_titles, first_only=False):
    rs = by_result.get(stack, [])
    if not rs:
        return ""
    if first_only:
        rs = rs[:1]
    blocks = [render_recipe(r, link_titles) for r in rs]
    return "\n\n".join(blocks)

def usages_for(stack, uses, link_titles):
    outs = sorted(uses.get(stack, []))
    if not outs:
        return ""
    links = []
    for o in outs:
        t = link_titles.get(o)
        links.append(f"[[{t}]]" if t else f"**{lang_name(o)}**")
    cap = 40
    extra = ""
    if len(links) > cap:
        extra = f" …and {len(links) - cap} more"
        links = links[:cap]
    return "**Used to craft:** " + ", ".join(links) + extra

# --------------------------------------------------------------------------- #
# Markdown body conversion
# --------------------------------------------------------------------------- #
COLOUR_TAGS = ["red", "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
               "dark_purple", "gold", "gray", "grey", "dark_gray", "dark_grey",
               "blue", "green", "aqua", "light_purple", "yellow", "white",
               "obfuscated"]

def drop_block(tag, s):
    return re.sub(rf"<{tag}>.*?</{tag}>", "", s, flags=re.DOTALL)

def keep_block(tag, s):
    return re.sub(rf"<{tag}>(.*?)</{tag}>", r"\1", s, flags=re.DOTALL)

def convert_body(md, page, ctx):
    """ctx carries by_result, uses, link_titles, page maps."""
    s = md
    placeholders = []
    def stash(html):
        placeholders.append(html)
        return f"\x00PH{len(placeholders)-1}\x00"

    # 0) strip `//` comment lines (the in-game loader ignores them; `////` escapes a
    #    literal `//`). See XmlPageLoader#loadParts.
    kept = []
    for ln in s.splitlines():
        st = ln.lstrip()
        if st.startswith("////"):
            kept.append(ln.replace("////", "//", 1))
        elif st.startswith("//"):
            continue
        else:
            kept.append(ln)
    s = "\n".join(kept)

    # 1) toggled content: formal voice (no_lore), keep hints + details.
    s = drop_block("lore", s)
    s = keep_block("no_lore", s)
    s = keep_block("hint", s)
    s = drop_block("no_hint", s)
    s = keep_block("detail", s)
    s = drop_block("no_detail", s)

    # 2) <guide_md>...</guide_md> -> fenced code block (literal example source).
    def code_block(lang):
        def repl(m):
            inner = m.group(1).replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            return stash(f"\n```{lang}\n" + inner.strip("\n") + "\n```\n")
        return repl
    s = re.sub(r"<guide_md>(.*?)</guide_md>", code_block("xml"), s, flags=re.DOTALL)
    s = re.sub(r"<json_insn>(.*?)</json_insn>", code_block("json"), s, flags=re.DOTALL)

    # 2.5) raw markdown headings: the in-game loader maps `#`×n to a chapter of
    #      level n-1 (heading depth n+1). Demote them one level so they nest under
    #      the page-title H1 instead of colliding with it. Code-block examples are
    #      already stashed above, so only live headings are touched.
    s = re.sub(r"^(#+)[ \t]+", lambda m: "#" * min(len(m.group(1)) + 1, 6) + " ",
               s, flags=re.MULTILINE)

    # 3) recipes / usages -> rendered tables (stashed so later passes skip them).
    def do_recipe(m):
        attrs = dict(re.findall(r'(\w+)="([^"]*)"', m.group(0)))
        tag = m.group(1)
        stack = attrs.get("stack")
        if not stack and attrs.get("id"):
            r = ctx["by_id"].get(attrs["id"].split(":")[-1])
            return stash(render_recipe(r, ctx["link_titles"]) if r else "")
        if not stack:
            return ""
        blocks = []
        if tag in ("recipe",):
            blocks.append(recipes_for(stack, ctx["by_result"], ctx["link_titles"], first_only=True))
        elif tag in ("recipes", "recipes_usages"):
            blocks.append(recipes_for(stack, ctx["by_result"], ctx["link_titles"]))
        if tag in ("usages", "recipes_usages"):
            blocks.append(usages_for(stack, ctx["uses"], ctx["link_titles"]))
        blocks = [b for b in blocks if b]
        if not blocks:
            return ""
        head = {"recipe": "Recipe", "recipes": "Recipe", "usages": "Usages",
                "recipes_usages": "Crafting"}.get(tag, "Recipe")
        return stash(f"\n**{head}**\n\n" + "\n\n".join(blocks) + "\n")
    s = re.sub(r"<(recipe|recipes|usages|recipes_usages)\b[^>]*/?>", do_recipe, s)
    # recipe_cycle: render all matching recipes
    def do_cycle(m):
        attrs = dict(re.findall(r'(\w+)="([^"]*)"', m.group(0)))
        match = attrs.get("match", "")
        rs = [r for sid, r in ctx["by_id"].items() if match and match in sid]
        if not rs:
            return ""
        return stash("\n**Recipes**\n\n" + "\n\n".join(render_recipe(r, ctx["link_titles"]) for r in rs) + "\n")
    s = re.sub(r"<recipe_cycle\b[^>]*/?>", do_cycle, s)

    # 4) images
    def do_image(m):
        attrs = dict(re.findall(r'(\w+)="([^"]*)"', m.group(0)))
        url = texture_url(attrs.get("src", ""))
        if not url:
            return ""
        w = attrs.get("width", "")
        h = attrs.get("height", "")
        dim = (f' width="{w}"' if w else "") + (f' height="{h}"' if h else "")
        return stash(f'<img src="{url}"{dim} alt="image">')
    s = re.sub(r"<image\b[^>]*/?>", do_image, s)

    # 5) links
    def do_link(m):
        attrs = dict(re.findall(r'(\w+)="([^"]*)"', m.group(0)))
        target = attrs.get("to") or attrs.get("inline")
        if not target:
            return ""
        if attrs.get("type") == "item_stack" or target.startswith("minecraft:"):
            d, p = split_id(target)
            nm = lang_name(target, is_block=False)
            if d == "minecraft":
                return f"[{nm}](https://minecraft.wiki/w/{prettify(p.split('/')[-1]).replace(' ', '_')})"
            t = ctx["link_titles"].get(target)
            return f"[[{t}]]" if t else f"**{nm}**"
        # page or category id
        t = ctx["page_titles"].get(target) or ctx["category_titles"].get(target)
        if not t:
            # fall back to matching by leaf name (e.g. placeholder/laser -> Laser)
            leaf = target.split("/")[-1].split(":")[-1]
            t = ctx["leaf_titles"].get(leaf)
        if t:
            return f"[[{t}]]"
        # Unresolved target: the in-game loader logs a warning and renders nothing
        # (the link silently disappears). Mirror that — these are stale source links.
        warn(f"{page.path}: unresolved <link to='{target}'> (omitted, as in-game)")
        return ""
    s = re.sub(r"<link\b[^>]*/?>", do_link, s)

    # 6) chapters / headings
    def do_chapter(m):
        attrs = dict(re.findall(r'(\w+)="([^"]*)"', m.group(0)))
        name = attrs.get("name", "").strip()
        level = int(attrs.get("level", "0") or 0)
        hashes = "#" * min(level + 2, 6)
        return f"\n{hashes} {name}\n"
    s = re.sub(r"<chapter\b[^>]*/?>", do_chapter, s)

    # 7) formatting tags
    s = re.sub(r"<bold>(.*?)</bold>", r"**\1**", s, flags=re.DOTALL)
    s = re.sub(r"<italic>(.*?)</italic>", r"*\1*", s, flags=re.DOTALL)
    s = re.sub(r"<strikethrough>(.*?)</strikethrough>", r"~~\1~~", s, flags=re.DOTALL)
    s = re.sub(r"<underline>(.*?)</underline>", r"<ins>\1</ins>", s, flags=re.DOTALL)
    for c in COLOUR_TAGS:
        s = re.sub(rf"</?{c}>", "", s)

    # 8) misc
    s = s.replace("<new_page/>", "\n\n---\n\n")
    s = re.sub(r"\$\[special\.new_page\]", "\n\n---\n\n", s)
    s = re.sub(r"\$\[special\.all_crafting\]\([^)]*\)", "", s)
    s = re.sub(r"\$\[[^\]]*\](\([^)]*\))?", "", s)   # any other $[...] tokens
    s = re.sub(r"<group\b[^>]*/?>", "", s)            # programmatic group listing

    # leftover unknown self-closing/paired tags -> warn + strip
    for t in set(re.findall(r"</?([a-z_]+)[ />]", s)):
        if t not in ("ins", "sub", "table", "tr", "td", "img", "br", "b", "i"):
            warn(f"{page.path}: leftover tag <{t}>")
    s = re.sub(r"<(/?)(?!ins|sub|table|tr|td|img|br|b|i)[a-z_]+\b[^>]*>", "", s)

    # restore placeholders
    for i, html in enumerate(placeholders):
        s = s.replace(f"\x00PH{i}\x00", html)

    # tidy blank lines
    s = re.sub(r"\n{3,}", "\n\n", s).strip()
    return s

# --------------------------------------------------------------------------- #
# Filenames
# --------------------------------------------------------------------------- #
def filename(title):
    safe = re.sub(r'[\\/:*?"<>|#\[\]]', "", title)
    return safe.replace(" ", "-") + ".md"

# --------------------------------------------------------------------------- #
# Main
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
# Where the book's title only reads right beside its icon and chapter, or can't be a wiki page
# name (`<`, `/` and `%` break wiki links; inventory and tank share "Contains < 25%"), the wiki says
# what the book's context shows. Keyed by statement id.
STATEMENT_TITLE_WIKI = {
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
    if sid in STATEMENT_TITLE_WIKI:
        return STATEMENT_TITLE_WIKI[sid]
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

def main():
    entries = parse_guide()

    # Synthesise entries for any .md not registered (concept/*, stray pages).
    registered = {e.path for e in entries}
    for f in sorted(EN.rglob("*.md")):
        rel = f.relative_to(EN).with_suffix("").as_posix()
        if rel in registered:
            continue
        if rel == "item/guide":
            continue  # the book's own intro page; Home has its own wording
        top = rel.split("/")[0]
        ptype = "concept" if top == "concept" else top
        system, subtype = CATEGORY_PLACEMENT.get(rel, (None, ""))
        entries.append(Entry(rel, ptype, subtype, stack=None, system=system))
        registered.add(rel)

    # Attach md content; resolve titles.
    for e in entries:
        f = EN / (e.path + ".md")
        if f.exists():
            e.md = f.read_text(encoding="utf-8")
        # title
        if e.title_key and e.title_key in TITLE_OVERRIDE:
            e.title = TITLE_OVERRIDE[e.title_key]
        elif e.title_key and f"buildcraft.guide.page.{e.title_key}" in LANG:
            e.title = LANG[f"buildcraft.guide.page.{e.title_key}"]
        elif e.title_key:
            e.title = prettify(e.title_key)
        elif e.statement and statement_title(e.statement):
            e.title = statement_title(e.statement)
        elif e.stack:
            e.title = lang_name(e.stack, is_block=(e.ptype == "block"))
        else:
            leaf = e.path.split("/")[-1]
            e.title = TITLE_OVERRIDE.get(leaf, prettify(leaf))

    # Disambiguate duplicate titles.
    seen = {}
    for e in entries:
        if e.title in seen and seen[e.title].path != e.path:
            e.title = f"{e.title} ({prettify(e.ptype)})"
        seen[e.title] = e

    # Build link maps.
    page_titles = {}       # buildcraftunofficial:block/quarry -> "Quarry"
    stack_to_title = {}    # buildcraftunofficial:quarry -> "Quarry"
    for e in entries:
        page_titles[f"buildcraftunofficial:{e.path}"] = e.title
        if e.stack:
            stack_to_title.setdefault(e.stack, e.title)
    # category links (concept group ids used by <link to="buildcraft:pipe_signals"/>)
    category_titles = {}
    catmap = {
        "filler_patterns": "concept/filler_patterns",
        "extraction_presets": "concept/emzuli_extraction_presets",
        "pipe_signals": "concept/pipe_signals",
        "set_pipe_direction": "concept/set_pipe_direction",
        "paint_pipe_colour": "action/pipe_colour",
        "set_power_limit": "concept/set_power_limit",
    }
    bypath = {e.path: e for e in entries}
    for gid, path in catmap.items():
        if path in bypath:
            category_titles[f"buildcraft:{gid}"] = bypath[path].title
            category_titles[f"buildcraftunofficial:{gid}"] = bypath[path].title

    # leaf -> title, only when unambiguous (used as a last-ditch link fallback)
    leaf_counts = {}
    for e in entries:
        leaf_counts.setdefault(e.path.split("/")[-1], []).append(e.title)
    leaf_titles = {k: v[0] for k, v in leaf_counts.items() if len(v) == 1}

    by_result, by_id, uses, all_recipes = load_recipes()
    ctx = dict(by_result=by_result, by_id=by_id, uses=uses,
               link_titles=stack_to_title, page_titles=page_titles,
               category_titles=category_titles, leaf_titles=leaf_titles)

    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.md"):
        old.unlink()

    stub_count = 0
    written = 0
    for e in entries:
        parts = [f"# {e.title}\n"]
        # hero icon
        hero = icon_url(e.stack) if e.stack else e.icon
        if hero:
            parts.append(f'<img src="{hero}" width="64" height="64" align="right" alt="{e.title}">\n')
        body = ""
        if e.md and e.md.strip():
            body = convert_body(e.md, e, ctx)
        if body:
            parts.append(body)
        else:
            stub_count += 1
            parts.append("_This entry hasn't been written up in the guide yet._")
            # still show a recipe if we have one
            if e.stack:
                r = recipes_for(e.stack, by_result, stack_to_title)
                if r:
                    parts.append(f"\n**Crafting**\n\n{r}")
        # auto-append crafting for content pages that didn't embed a recipe tag
        if body and e.stack and "<recipe" not in (e.md or "") and "recipes_usages" not in (e.md or ""):
            r = recipes_for(e.stack, by_result, stack_to_title)
            if r:
                parts.append(f"\n## Crafting\n\n{r}")
        if e.creative:
            parts.append("\n<sub>⚙️ Creative-only — no survival recipe.</sub>")
        (OUT / filename(e.title)).write_text("\n".join(parts).strip() + "\n", encoding="utf-8")
        written += 1

    write_sidebar(entries)
    write_home(entries)
    write_footer()

    print(f"Wrote {written} pages to {OUT}  ({stub_count} content-less stubs)")
    print(f"Recipes indexed: {len(all_recipes)}")
    if warnings:
        print(f"\n{len(warnings)} warning(s):")
        for w in warnings[:60]:
            print("  -", w)
        if len(warnings) > 60:
            print(f"  ...and {len(warnings)-60} more")

# --------------------------------------------------------------------------- #
# Sidebar / Home / Footer
# --------------------------------------------------------------------------- #
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

def write_sidebar(entries):
    lines = ["### BuildCraft Wiki", "", "[Home](Home)", ""]
    for key, subs in grouped(entries):
        lines.append(f"**{system_name(key)}**")
        for sub, es in subs:
            sn = subtype_name(sub)
            if sn and len(subs) > 1:
                lines.append(f"- _{sn}_")
                lines += [f"  - [[{e.title}]]" for e in es]
            else:  # a lone or un-subtyped group sits directly under the system
                lines += [f"- [[{e.title}]]" for e in es]
        lines.append("")
    (OUT / "_Sidebar.md").write_text("\n".join(lines).strip() + "\n", encoding="utf-8")

def write_home(entries):
    total = len(entries)
    # Intro wording is the user's own (hand-edited on the wiki 2026-05-31); keep it.
    lines = [
        "# BuildCraft Wiki", "",
        "This Wiki directly mirrors the content within the mod's own in-game guide book "
        f"({total} entries). You can find the contents of the book here: "
        f"https://github.com/legoj15/BuildCraft/tree/{BRANCH}/src/main/resources/"
        "assets/buildcraftunofficial/compat/buildcraft/guide",
        "",
        "## Sections", "",
    ]
    for key, subs in grouped(entries):
        count = sum(len(es) for _, es in subs)
        lines.append(f"### {system_name(key)} ({count})")
        for sub, es in subs:
            sn = subtype_name(sub)
            names = ", ".join(f"[[{e.title}]]" for e in es)
            lines.append(f"- **{sn}:** {names}" if sn else f"- {names}")
        lines.append("")
    lines += [
        "---",
        "<sub>Generated from the in-game guide by "
        "[`scripts/guide-to-wiki.py`](https://github.com/legoj15/BuildCraft/blob/"
        f"{BRANCH}/scripts/guide-to-wiki.py). Edits here may be overwritten on the next "
        "regeneration — change the source `.md` under "
        "`src/main/resources/assets/buildcraftunofficial/compat/buildcraft/guide/` instead.</sub>",
    ]
    (OUT / "Home.md").write_text("\n".join(lines).strip() + "\n", encoding="utf-8")

def write_footer():
    (OUT / "_Footer.md").write_text(
        "<sub>BuildCraft (Unofficial) for NeoForge · "
        "[Repository](https://github.com/legoj15/BuildCraft) · "
        "Generated from the in-game Guide Book.</sub>\n", encoding="utf-8")

if __name__ == "__main__":
    main()
