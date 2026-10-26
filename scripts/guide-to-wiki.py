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

The guide itself (entries, titles, recipes, contents order) is read by guide_model.py, which
scripts/guide-to-guideme.py shares; this file only renders it as wiki markdown.

Usage:  python3 scripts/guide-to-wiki.py [output_dir]
Stdlib only; no third-party deps.
"""

import json
import re
import sys
from pathlib import Path

from guide_model import (ASSETS, CATEGORY_GROUPS, COLOUR_TAGS, MODELS, REPO, Guide, drop_block, grouped,
                         ing_ids, keep_block, lang_name, prettify, split_id, strip_comment_lines,
                         subtype_name, system_name, warn, warnings)

# raw.githubusercontent base for embedding textures. Branch must hold the textures.
BRANCH = "main"
RAW = f"https://raw.githubusercontent.com/legoj15/BuildCraft/{BRANCH}/src/main/resources/"
BOARD_ICON = RAW + "assets/buildcraftunofficial/textures/item/redstone_board.png"

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else (REPO / "wiki-export")

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
def convert_body(md, page, ctx):
    """ctx carries by_result, uses, link_titles, page maps."""
    s = md
    placeholders = []
    def stash(html):
        placeholders.append(html)
        return f"\x00PH{len(placeholders)-1}\x00"

    # 0) strip `//` comment lines.
    s = strip_comment_lines(s)

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
def main():
    guide = Guide()
    entries = guide.entries
    by_result, by_id, uses, all_recipes = guide.by_result, guide.by_id, guide.uses, guide.all_recipes

    # Link maps, keyed the way convert_body looks them up.
    page_titles = {f"buildcraftunofficial:{e.path}": e.title for e in entries}
    stack_to_title = {stack: e.title for stack, e in guide.by_stack.items()}
    category_titles = {}
    for gid, path in CATEGORY_GROUPS.items():
        if path in guide.by_path:
            category_titles[f"buildcraft:{gid}"] = guide.by_path[path].title
            category_titles[f"buildcraftunofficial:{gid}"] = guide.by_path[path].title
    leaf_titles = {leaf: e.title for leaf, e in guide.by_leaf.items()}

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
        hero = icon_url(e.stack) if e.stack else (BOARD_ICON if e.board else None)
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
