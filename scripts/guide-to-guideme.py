#!/usr/bin/env python3
"""
guide-to-guideme.py — export BuildCraft's in-game Guide Book as a GuideME guide.

GuideME is the guide library Applied Energistics 2 uses, shipped as its own mod. A guide made only of
resources needs no code from us: GuideME reads
    assets/buildcraftunofficial/guideme_guides/guide.json              (the guide definition)
    assets/buildcraftunofficial/guides/buildcraftunofficial/guide/**.md (one page per entry)
and players open it with GuideME's generic book, `guideme:guide[guideme:guide_id="buildcraftunofficial:guide"]`.
Without GuideME installed the files are inert.

The output is GENERATED and committed: edit the guide sources under compat/buildcraft/ (or this script)
and re-run it. The entries, titles and contents order come from guide_model.py, shared with
guide-to-wiki.py. What the conversion does with the parts GuideME has no equivalent for:
  * lore / hints / details follow the in-game defaults (lore shown, hints and details hidden);
  * `<usages>` becomes a static "Used to craft" list built from the recipe JSONs;
  * recipes registered in code (assembly table) and BC's special recipe types are left out, since
    GuideME can only draw recipe types it has a mapping for, and we ship no code for it;
  * the config book and the book's own `config/` pages are skipped (they describe this file format).

scripts/guide-to-guideme.stamp records a hash of every file it reads and of the output; the unit test
GuideMeExportStalenessTest fails when the two drift apart, so the export never silently goes stale.

Usage:  python3 scripts/guide-to-guideme.py           regenerate the export and the stamp
        python3 scripts/guide-to-guideme.py --check   exit 1 if the committed export is stale
Stdlib only.
"""

import hashlib
import json
import posixpath
import re
import shutil
import sys

from guide_model import (ASSETS, GUIDE_DIR, LANG, LANG_FILE, RECIPE_DIR, REPO, Guide, attrs_of, drop_block,
                         grouped, keep_block, lang_name, recipe_result_id, split_id, strip_comment_lines,
                         subtype_name, system_name, warn, warnings)

NS = "buildcraftunofficial"
GUIDE_OUT = ASSETS / f"guides/{NS}/guide"
GUIDE_DEF = ASSETS / "guideme_guides/guide.json"
STAMP = REPO / "scripts/guide-to-guideme.stamp"
SCRIPTS = [REPO / "scripts/guide_model.py", REPO / "scripts/guide-to-guideme.py"]

# Recipe types GuideME draws out of the box. Everything else BC registers (dyed_pipe, pipe_paint,
# facade_swap) is a special recipe with no fixed result, and assembly recipes live in code.
# hidden_shapeless extends ShapelessRecipe, so its RecipeType is plain crafting.
DRAWABLE_RECIPE_TYPES = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless",
                         f"{NS}:hidden_shapeless", "minecraft:smelting", "minecraft:blasting"}

# GuideME's symbolic colours resolve in the page's namespace, so spell the Minecraft text colours out.
COLOUR_HEX = {"black": "#000000", "dark_blue": "#0000AA", "dark_green": "#00AA00", "dark_aqua": "#00AAAA",
              "dark_red": "#AA0000", "dark_purple": "#AA00AA", "gold": "#FFAA00", "gray": "#AAAAAA",
              "grey": "#AAAAAA", "dark_gray": "#555555", "dark_grey": "#555555", "blue": "#5555FF",
              "green": "#55FF55", "aqua": "#55FFFF", "red": "#FF5555", "light_purple": "#FF55FF",
              "yellow": "#FFFF55", "white": "#FFFFFF"}

USAGES_CAP = 40
BOARD_ICON = f"{NS}:redstone_board"
VALID_PAGE = re.compile(r"[a-z0-9_./-]+")


# --------------------------------------------------------------------------- #
# Which entries become pages, and where they sit in the navigation tree
# --------------------------------------------------------------------------- #
def included(e):
    # No system means no place on the book's contents page: a page nothing registers is unreachable
    # in-game, so it stays out here too.
    return (e.book == "main" and e.system and not e.path.startswith("config/")
            and e.md and e.md.strip())


class Export:
    def __init__(self, guide):
        self.guide = guide
        self.entries = [e for e in guide.entries if included(e)]
        self.pages = {e.path: f"{e.path}.md" for e in self.entries}
        # The first entry for a stack is its page for <ItemLink>, recipe slots and GuideME's
        # open-guide hotkey; a second claim of the same item id would be ambiguous.
        self.item_page = {}
        for e in self.entries:
            if e.stack and guide.by_stack.get(e.stack) is e:
                self.item_page[e.stack] = e
        # An item with no page of its own is adopted by a page that links it (the wire colours by
        # Pipe Wires, Map Location by the Zone Planner), so its <ItemLink> has a target. Block and
        # item pages go first: a concept page that mentions an item is rarely where it is explained.
        for e in sorted(self.entries, key=lambda e: e.path.startswith("concept/")):
            for t in re.findall(r"<link\b[^>]*>", visible_body(e.md)):
                a = attrs_of(t)
                target = a.get("to") or a.get("inline") or ""
                if a.get("type") == "item_stack" and split_id(target)[0] == NS:
                    self.item_page.setdefault(target, e)
        self.recipes_by_result = {}
        for r in guide.all_recipes:
            if r.get("type") in DRAWABLE_RECIPE_TYPES and recipe_result_id(r):
                self.recipes_by_result.setdefault(recipe_result_id(r), []).append(r)
        self.dropped_recipes = 0

    def tree(self):
        """[(system key, system page, title, [(subtype page or None, title, [entries])])]."""
        out = []
        for key, subs in grouped(self.entries):
            sys_page = f"contents/{key.lstrip('_')}.md"
            named = [s for s, _ in subs if subtype_name(s)]
            groups = []
            for sub, es in subs:
                sn = subtype_name(sub)
                # A lone or unnamed subtype sits directly under its system, as in the wiki sidebar.
                page = f"contents/{key.lstrip('_')}/{sub}.md" if sn and len(named) > 1 else None
                groups.append((page, sn, es))
            out.append((key, sys_page, system_name(key), groups))
        return out


# --------------------------------------------------------------------------- #
# Markdown / MDX helpers
# --------------------------------------------------------------------------- #
def yaml_str(s):
    return json.dumps(s, ensure_ascii=False)   # a JSON string is a valid YAML scalar

def frontmatter(title, parent=None, position=None, icon=None, item_ids=()):
    lines = ["---", "navigation:", f"  title: {yaml_str(title)}"]
    if parent:
        lines.append(f"  parent: {parent}")
    if position is not None:
        lines.append(f"  position: {position}")
    if icon:
        lines.append(f"  icon: {icon}")
    if item_ids:
        lines.append("item_ids:")
        lines += [f"  - {i}" for i in item_ids]
    lines.append("---")
    return "\n".join(lines)

def escape_text(s):
    """Prose -> MDX-safe text: braces open JSX expressions and a bare `<` opens a tag."""
    s = s.replace("{", "\\{").replace("}", "\\}")
    return re.sub(r"<", "&lt;", s)

def link_text(s):
    return escape_text(s).replace("[", "\\[").replace("]", "\\]")

def attr(v):
    return v.replace('"', "&quot;")

def rel_link(from_page, to_page):
    r = posixpath.relpath(to_page, posixpath.dirname(from_page) or ".")
    return r if r.startswith("../") else f"./{r}"


# --------------------------------------------------------------------------- #
# Body conversion
# --------------------------------------------------------------------------- #
SOURCE_TAG = re.compile(r"</?[a-z_]+\b[^>]*?/?>")
INLINE_STYLE = tuple(COLOUR_HEX) + ("bold", "italic", "strikethrough")
INLINE_TAG = re.compile(r"<(/?)(" + "|".join(INLINE_STYLE) + r")>")

def visible_body(md):
    """The page as the book shows it by default: no `//` comments, lore shown, hints and details
    hidden (XmlPageLoader SHOW_LORE=true, SHOW_HINTS=false, SHOW_DETAIL=false)."""
    s = strip_comment_lines(md)
    s = keep_block("lore", s)
    s = drop_block("no_lore", s)
    s = drop_block("hint", s)
    s = keep_block("no_hint", s)
    s = drop_block("detail", s)
    return keep_block("no_detail", s)

def scope_inline_tags(line):
    """Colour and emphasis are per line in the book (XmlPageLoader resets them each line), and every
    line becomes its own paragraph here, which an MDX tag cannot span. So close whatever is still open
    at the line end, drop a close with nothing open, and keep crossed tags properly nested."""
    out, pos, open_ = [], 0, []
    for m in INLINE_TAG.finditer(line):
        out.append(line[pos:m.start()])
        pos = m.end()
        closing, name = m.group(1) == "/", m.group(2)
        if not closing:
            open_.append(name)
            out.append(m.group(0))
        elif name in open_:
            i = len(open_) - 1 - open_[::-1].index(name)
            above = open_[i + 1:]
            out += [f"</{n}>" for n in reversed(above)] + [f"</{name}>"] + [f"<{n}>" for n in above]
            del open_[i]
    out.append(line[pos:])
    out += [f"</{n}>" for n in reversed(open_)]
    return "".join(out)

def convert_body(md, page, ex):
    s = visible_body(md)

    stash = []
    def put(text):
        stash.append(text)
        return f"\x00{len(stash) - 1}\x00"

    # Literal example source -> fenced code (only the config pages use these, but stay correct).
    def code(lang):
        def repl(m):
            inner = m.group(1).replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            return put(f"\n```{lang}\n" + inner.strip("\n") + "\n```\n")
        return repl
    s = re.sub(r"<guide_md>(.*?)</guide_md>", code("xml"), s, flags=re.DOTALL)
    s = re.sub(r"<json_insn>(.*?)</json_insn>", code("json"), s, flags=re.DOTALL)

    # Raw `#` headings: the book maps `#`×n to a chapter one level below the page title. Only the
    # marker is stashed, so tags in the heading text still convert and its prose still escapes.
    s = re.sub(r"^[ \t]*(#+)[ \t]+", lambda m: put("#" * min(len(m.group(1)) + 1, 6) + " "),
               s, flags=re.MULTILINE)

    s = "\n".join(scope_inline_tags(ln) for ln in s.split("\n"))

    def tag(m):
        t = m.group(0)
        name = re.match(r"</?([a-z_]+)", t).group(1)
        closing = t.startswith("</")
        if closing and name not in COLOUR_HEX and name not in ("bold", "italic", "strikethrough"):
            return ""   # a stray close of a self-closing tag (</chapter>) carries nothing
        a = attrs_of(t)
        if name == "chapter":
            level = int(a.get("level", "0") or 0)
            return put("\n" + "#" * min(level + 2, 6) + " " + escape_text(a.get("name", "").strip()) + "\n")
        if name in ("recipe", "recipes", "usages", "recipes_usages"):
            return put(recipe_tags(name, a, ex))
        if name == "recipe_cycle":
            return put(recipe_cycle(a, ex))
        if name == "link":
            return put(link(a, page, ex))
        if name == "bold":
            return put("**")
        if name == "italic":
            return put("*")
        if name == "strikethrough":
            return put("~~")
        if name in COLOUR_HEX:
            return put("</Color>" if closing else f'<Color color="{COLOUR_HEX[name]}">')
        if name == "image":
            warn(f"{page}: <image> dropped (GuideME images must live in the guide folder)")
            return ""
        if name in ("underline", "obfuscated", "new_page", "group"):
            return ""
        warn(f"{page}: unknown tag <{name}> dropped")
        return ""
    s = SOURCE_TAG.sub(tag, s)

    # $[special.*] tokens the book expands in code.
    s = re.sub(r"\$\[[^\]]*\](\([^)]*\))?", "", s)

    # Everything left outside the stash is prose.
    s = re.sub(r"[^\x00]+|\x00\d+\x00", lambda m: m.group(0) if m.group(0).startswith("\x00")
               else escape_text(m.group(0)), s)
    s = re.sub(r"\x00(\d+)\x00", lambda m: stash[int(m.group(1))], s)
    return paragraphs(s)

CYCLE_NOTE = "@@cycle "     # recipe_cycle's count, worded by paragraphs() once its <Row> closes
NUMBER_WORDS = ["", "One", "Two", "Three", "Four", "Five", "Six"]

def cycle_notes(counts):
    out = []
    for n in sorted(set(counts)):
        k = counts.count(n)
        out.append(f"*One of these recipes stands for {n} of its kind.*" if k == 1 else
                   f"*{NUMBER_WORDS[k] if k < len(NUMBER_WORDS) else k} of these recipes each stand for {n} of their kind.*")
    return out

def paragraphs(s):
    """The book renders every source line as its own line; Markdown would run them together, so
    separate lines with blank ones. Runs of list items stay a single list, and runs of single
    recipes share one wrapping <Row> instead of stacking down the page."""
    out = []
    prev = None
    in_code = False
    cycles = []
    for ln in s.splitlines():
        if ln.startswith(CYCLE_NOTE):
            cycles.append(int(ln[len(CYCLE_NOTE):]))
            continue
        if ln.startswith("```"):
            in_code = not in_code
            out.append(ln)
            continue
        if in_code:
            out.append(ln)
            continue
        ln = ln.strip()
        if not ln:
            continue
        kind = "list" if ln.startswith("- ") else "recipe" if ln.startswith("<Recipe") else None
        if prev == "recipe" and kind != "recipe":
            out.append("</Row>")
            for note in cycle_notes(cycles):
                out += ["", note]
            cycles = []
        if not (kind and kind == prev):
            if out:
                out.append("")
            if kind == "recipe":
                out.append("<Row>")
        out.append(ln)
        prev = kind
    if prev == "recipe":
        out.append("</Row>")
        for note in cycle_notes(cycles):
            out += ["", note]
    return "\n".join(out).strip()

def item_link(item_id, ex):
    # One of our items no page claims is named in plain text: an <ItemLink> to it logs a "no page"
    # warning on GuideME's 1.21.1 line, `optional` or not.
    if split_id(item_id)[0] == NS and item_id not in ex.item_page:
        return escape_text(lang_name(item_id))
    return f'<ItemLink id="{attr(item_id)}" />'

def link(a, page, ex):
    target = a.get("to") or a.get("inline")
    block = "to" in a
    if a.get("url"):
        return f"[{link_text(a.get('text') or a['url'])}]({a['url']})"
    if not target:
        return ""
    if a.get("type") == "item_stack" or target.startswith("minecraft:"):
        out = item_link(target, ex)
    else:
        e = ex.guide.resolve_link(target)
        if e is None:
            warn(f"{page}: unresolved <link to='{target}'> omitted")
            return ""
        if e.path not in ex.pages:
            # Its target is a page this export skips (config book, no contents placement).
            warn(f"{page}: link to unexported page '{e.path}' kept as plain text")
            out = escape_text(e.title)
        else:
            out = f"[{link_text(e.title)}]({rel_link(page, ex.pages[e.path])})"
    return f"\n- {out}\n" if block else out

def recipe_tags(name, a, ex):
    stack = a.get("stack")
    if not stack and a.get("id"):
        return single_recipe(a["id"], ex, a.get("type"))
    if not stack:
        return ""
    parts = []
    if name in ("recipe", "recipes", "recipes_usages") and stack in ex.recipes_by_result:
        tag = "RecipeFor" if name == "recipe" else "RecipesFor"
        # Empty fallback: a config-disabled recipe (rf_enabled) shows nothing instead of an error.
        parts.append(f'<{tag} id="{attr(stack)}" fallbackText="" />')
    if name in ("usages", "recipes_usages"):
        outs = sorted(ex.guide.uses.get(stack, []))
        if outs:
            links = ", ".join(item_link(o, ex) for o in outs[:USAGES_CAP])
            more = f" and {len(outs) - USAGES_CAP} more" if len(outs) > USAGES_CAP else ""
            parts.append(f"**Used to craft:** {links}{more}")
    return "\n" + "\n\n".join(parts) + "\n" if parts else ""

def single_recipe(rid, ex, kind=None):
    r = ex.guide.by_id.get(split_id(rid)[1])
    if r is None or r.get("type") not in DRAWABLE_RECIPE_TYPES:
        if kind != "assembling":    # assembly recipes are registered in code; anything else is a typo
            warn(f"recipe {rid}: no drawable data recipe with that id")
        else:
            ex.dropped_recipes += 1
        return ""
    return f'\n<Recipe id="{NS}:{r["_id"]}" fallbackText="" />\n'

def recipe_cycle(a, ex):
    """The book cycles one panel through every recipe whose id contains `match`; GuideME has no
    cycling panel, so show the first drawable one and say how many it stands for. Assembling cycles
    name code-registered recipes: none here."""
    match = a.get("match", "")
    matches = [r for sid, r in sorted(ex.guide.by_id.items())
               if match and match in sid and r.get("type") in DRAWABLE_RECIPE_TYPES]
    if a.get("type") == "assembling" or not matches:
        if a.get("type") != "assembling":
            warn(f"recipe_cycle match={match!r}: no drawable data recipe matches")
        else:
            ex.dropped_recipes += 1
        return ""
    shown = single_recipe(f"{NS}:{matches[0]['_id']}", ex)
    if len(matches) == 1:
        return shown
    return f"{shown}\n{CYCLE_NOTE}{len(matches)}\n"


# --------------------------------------------------------------------------- #
# Pages
# --------------------------------------------------------------------------- #
def entry_icon(e):
    return e.stack or (BOARD_ICON if e.board else None)

def write_pages(ex):
    files = {}
    tree = ex.tree()

    intro = ("Everything in BuildCraft's own Guide Book, arranged the same way: pick a section below or "
             "in the sidebar, or search for an item.")
    title = LANG.get(f"buildcraft.guide.book.{NS}_main", "BuildCraft Guide Book")
    toc = "\n".join(f"- [{escape_text(name)}]({rel_link('index.md', sys_page)})" for _, sys_page, name, _ in tree)
    files["index.md"] = "\n\n".join([frontmatter(title, position=0, icon=f"{NS}:guide"),
                                     f"# {escape_text(title)}", intro, "## Contents", toc])

    for pos, (key, sys_page, sys_title, groups) in enumerate(tree, start=1):
        sys_icon = next((entry_icon(e) for _, _, es in groups for e in es if entry_icon(e)), None)
        files[sys_page] = index_page(sys_title, None, pos * 10, sys_icon)
        child_pos = 0
        for sub_page, sub_title, es in groups:
            parent = sys_page
            if sub_page:
                child_pos += 1
                icon = next((entry_icon(e) for e in es if entry_icon(e)), None)
                files[sub_page] = index_page(sub_title, sys_page, child_pos, icon)
                parent = sub_page
            for e in es:
                child_pos += 1
                files[ex.pages[e.path]] = entry_page(e, parent, child_pos, ex)
    return files

def index_page(title, parent, position, icon):
    return "\n\n".join([frontmatter(title, parent=parent, position=position, icon=icon),
                        f"# {escape_text(title)}", "<SubPages />"])

def entry_page(e, parent, position, ex):
    page = ex.pages[e.path]
    item_ids = sorted(i for i, owner in ex.item_page.items() if owner is e)
    parts = [frontmatter(e.title, parent=parent, position=position, icon=entry_icon(e), item_ids=item_ids),
             f"# {escape_text(e.title)}"]
    if e.stack:
        parts.append(f'<ItemImage id="{attr(e.stack)}" scale="2" float="right" />')
    parts.append(convert_body(e.md, page, ex))
    if e.creative:
        parts.append("*Creative-only: there is no survival recipe.*")
    return "\n\n".join(p for p in parts if p)

def guide_definition():
    return json.dumps({
        "item_settings": {
            "display_name": {"type": "translatable", "translate": f"buildcraft.guide.book.{NS}_main"},
            "model": f"{NS}:item/guide",
        },
    }, indent=2) + "\n"


# --------------------------------------------------------------------------- #
# Staleness stamp. GuideMeExportStalenessTest recomputes both hashes the same way; keep the two in step.
# --------------------------------------------------------------------------- #
def _digest(paths):
    h = hashlib.sha256()
    for p in sorted(paths, key=lambda p: p.relative_to(REPO).as_posix()):
        h.update(p.relative_to(REPO).as_posix().encode("utf-8") + b"\n")
        h.update(p.read_bytes().replace(b"\r", b"") + b"\n")
    return h

def input_files():
    files = list(SCRIPTS) + [GUIDE_DIR.parent / "guide.txt"]
    files += [p for p in GUIDE_DIR.rglob("*") if p.is_file()]
    # Exact-case suffix test, like load_recipes and the Java test (Windows glob ignores case).
    files += [p for p in RECIPE_DIR.iterdir() if p.is_file() and p.suffix == ".json"]
    return files

def output_files():
    return [p for p in GUIDE_OUT.rglob("*") if p.is_file()] + [GUIDE_DEF]

def input_hash(lang_keys):
    h = _digest(input_files())
    lang = json.loads(LANG_FILE.read_text(encoding="utf-8"))
    for k in sorted(lang_keys):
        v = lang.get(k)
        h.update(f"{k}={v if v is not None else chr(0)}\n".encode("utf-8"))
    return h.hexdigest()

def read_stamp():
    if not STAMP.exists():
        return None, None, set()
    inputs = outputs = None
    keys = set()
    for ln in STAMP.read_text(encoding="utf-8").splitlines():
        if ln.startswith("inputs "):
            inputs = ln[7:]
        elif ln.startswith("outputs "):
            outputs = ln[8:]
        elif ln.startswith("lang "):
            keys.add(ln[5:])
    return inputs, outputs, keys


def check():
    inputs, outputs, keys = read_stamp()
    ok = inputs == input_hash(keys) and outputs == _digest(output_files()).hexdigest()
    print("GuideME export is current." if ok else
          "GuideME export is STALE: run python scripts/guide-to-guideme.py and commit the result.")
    return 0 if ok else 1

def main():
    if "--check" in sys.argv[1:]:
        return check()

    ex = Export(Guide())
    files = write_pages(ex)

    for path in files:
        if not VALID_PAGE.fullmatch(path):
            warn(f"page id '{path}' is not a valid resource path")
    if GUIDE_OUT.exists():
        shutil.rmtree(GUIDE_OUT)
    for path, text in files.items():
        f = GUIDE_OUT / path
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(text.rstrip() + "\n", encoding="utf-8", newline="\n")
    GUIDE_DEF.parent.mkdir(parents=True, exist_ok=True)
    GUIDE_DEF.write_text(guide_definition(), encoding="utf-8", newline="\n")

    keys = set(LANG.read)
    stamp = ["# Written by scripts/guide-to-guideme.py; GuideMeExportStalenessTest checks it. Do not edit.",
             f"inputs {input_hash(keys)}",
             f"outputs {_digest(output_files()).hexdigest()}"]
    stamp += [f"lang {k}" for k in sorted(keys)]
    STAMP.write_text("\n".join(stamp) + "\n", encoding="utf-8", newline="\n")

    print(f"Wrote {len(files)} GuideME pages ({len(ex.entries)} entries) to {GUIDE_OUT.relative_to(REPO)}")
    print(f"Code-registered recipes left out: {ex.dropped_recipes}")
    if warnings:
        print(f"\n{len(warnings)} warning(s):")
        for w in warnings:
            print("  -", w)
    return 0

if __name__ == "__main__":
    sys.exit(main())
