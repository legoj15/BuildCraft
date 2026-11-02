#!/usr/bin/env python3
# Copyright (c) 2026 the BuildCraftUnofficial contributors
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
# distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
"""Contact sheet for the Filler pattern and parameter icons, composited on the real statement slot.

Every icon is drawn into the 18x18 slot from textures/gui/misc_slots.png (the slot the GUI paints behind it,
icon at +1,+1) and shown at 1x and at --scale (default 6x), labelled, on the GUI panel grey. A 1x strip per folder
puts neighbours in adjacent slots the way the game does (patterns in the selection popup's rows, parameters one
family per run), which is where "can I tell these apart" gets judged.

With --compare-rev, icons that differ from that git revision also get an old-vs-new block (old pixels read
with `git show REV:path`). --compare limits/forces which icons are compared.

Each icon is also linted against docs/filler-icon-style.md: wrong size, pixels inside the 1px margin,
semi-transparent pixels, and colours outside the guide's ```palette block are noted under its label in red.

Usage:
  python scripts/art/filler-icon-sheet.py OUT.png
  python scripts/art/filler-icon-sheet.py OUT.png --compare-rev HEAD            old-vs-new for every changed icon
  python scripts/art/filler-icon-sheet.py OUT.png --compare-rev HEAD --compare patterns/clear parameters/face_north
  python scripts/art/filler-icon-sheet.py OUT.png --patterns DIR --parameters DIR --exclude NAME
Exit status is 0 even when lint notes are drawn; --strict makes any note exit 1.
"""

from __future__ import annotations

import argparse
import io
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[2]
TEXTURES = REPO / "src" / "main" / "resources" / "assets" / "buildcraftunofficial" / "textures"
DEFAULT_PATTERNS = TEXTURES / "filler" / "patterns"
DEFAULT_PARAMETERS = TEXTURES / "filler" / "parameters"
DEFAULT_SLOT = TEXTURES / "gui" / "misc_slots.png"
DEFAULT_GUIDE = REPO / "docs" / "filler-icon-style.md"

STATEMENTS = REPO / "src" / "main" / "java" / "buildcraft" / "builders" / "BCBuildersStatements.java"
PATTERN_SOURCES = STATEMENTS.parent / "snapshot" / "pattern"

# The 1x strips put neighbours side by side the way the game does. Patterns follow the selection popup's rows
# (BCBuildersStatements.PATTERN_POPUP, read by pattern_popup()); a parameter popup shows one parameter type at a time,
# so parameters are grouped by family. Icons outside every group (orphans, new files) end the strip as their own group.
PARAMETER_FAMILIES = [("face_",), ("axis_",), ("arrow_",), ("center_",), ("rotation_",), ("stairs_",),
                      ("filled_", "hollow")]

PANEL = (198, 198, 198, 255)  # vanilla GUI panel #C6C6C6 - what surrounds the slots in game
TEXT = (64, 64, 64, 255)  # vanilla GUI label grey #404040
WARN = (190, 20, 20, 255)
SLOT_BOX = (0, 0, 18, 18)  # GuiElementStatement.SLOT_COLOUR
GAP = 10


def font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.load_default(size=size)
    except TypeError:  # Pillow without FreeType: fixed bitmap font
        return ImageFont.load_default()


def load_palette(guide: Path) -> set[tuple[int, int, int]] | None:
    """Colours from the guide's ```palette fenced block (lines '<letter> #RRGGBB role'); None if absent."""
    if not guide.is_file():
        return None
    m = re.search(r"```palette\n(.*?)```", guide.read_text(encoding="utf-8"), re.S)
    if not m:
        return None
    cols = set()
    for line in m.group(1).splitlines():
        h = re.search(r"#([0-9A-Fa-f]{6})\b", line)
        if h:
            v = int(h.group(1), 16)
            cols.add((v >> 16 & 255, v >> 8 & 255, v & 255))
    return cols or None


def lint(icon: Image.Image, palette: set | None) -> list[str]:
    notes = []
    if icon.size != (16, 16):
        notes.append(f"size {icon.size[0]}x{icon.size[1]}")
    px = icon.convert("RGBA").load()
    w, h = icon.size
    margin = semi = 0
    off = set()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if x in (0, w - 1) or y in (0, h - 1):
                margin += 1
            if a != 255:
                semi += 1
            if palette is not None and (r, g, b) not in palette:
                off.add((r, g, b))
    if margin:
        notes.append(f"margin {margin}px")
    if semi:
        notes.append(f"semi-alpha {semi}px")
    if off:
        notes.append(f"off-palette {len(off)}")
    return notes


def git_show(rev: str, path: Path) -> Image.Image | None:
    """The icon at REV, or None when REV has no such file (REV itself is checked once in main)."""
    try:
        rel = path.resolve().relative_to(REPO).as_posix()
    except ValueError:
        return None
    try:
        data = subprocess.run(["git", "-C", str(REPO), "show", f"{rev}:{rel}"], capture_output=True, check=True).stdout
    except subprocess.CalledProcessError:
        return None
    return Image.open(io.BytesIO(data)).convert("RGBA")


def pattern_popup() -> list[list[str]]:
    """BCBuildersStatements.PATTERN_POPUP as rows of icon names (pattern tags), parsed from the Java source."""
    src = STATEMENTS.read_text(encoding="utf-8")
    popup = re.search(r"PATTERN_POPUP = List\.of\((.*?)\n\s*\);", src, re.S)
    if not popup:
        sys.exit(f"PATTERN_POPUP not found in {STATEMENTS}")
    rows = []
    for row in re.findall(r"List\.of\(([^)]*)\)", popup.group(1)):
        rows.append([pattern_tag(src, const.strip()) for const in row.split(",")])
    return rows


def pattern_tag(src: str, const: str) -> str:
    """The tag a PATTERN_* constant's class passes to super(), e.g. PATTERN_EIGHTH_SPHERE -> sphere_eighth."""
    decl = re.search(rf"\b{const} = new (\w+)\(([^)]*)\)", src)
    if not decl:
        sys.exit(f"{const} not declared in {STATEMENTS}")
    cls, arg = decl.groups()
    ctor = re.search(r'super\("([^"]*)"(\s*\+\s*type\.lowerCaseName)?\)',
                     (PATTERN_SOURCES / f"{cls}.java").read_text(encoding="utf-8"))
    if not ctor:
        sys.exit(f"no super(\"tag\") in {cls}.java")
    # PatternSpherePart appends its SpherePartType's lower-case name to the tag
    return ctor.group(1) + (arg.rsplit(".", 1)[-1].lower() if ctor.group(2) else "")


def strip_groups(folder: str, names: list[str]) -> list[list[str]]:
    """Neighbour groups for a folder's 1x strip, in game order; leftovers form a final group."""
    if folder == "patterns":
        groups = [[n for n in row if n in names] for row in pattern_popup()]
    else:
        groups = [[n for n in names if n.startswith(prefixes)] for prefixes in PARAMETER_FAMILIES]
    used = {n for g in groups for n in g}
    groups.append([n for n in names if n not in used])
    return [g for g in groups if g]


def strip_layout(groups: list[list[str]], width: int) -> tuple[dict[str, tuple[int, int]], int]:
    """Slot positions (x, y offset) for the groups, wrapping whole groups at the sheet edge; returns height too."""
    pos, x, y = {}, GAP, 0
    for g in groups:
        if x > GAP and x + len(g) * 18 > width - GAP:
            x, y = GAP, y + 18 + GAP // 2
        for k, n in enumerate(g):
            pos[n] = (x + k * 18, y)
        x += len(g) * 18 + GAP
    return pos, y + 18


def in_slot(slot: Image.Image, icon: Image.Image | None, scale: int) -> Image.Image:
    out = slot.copy()
    if icon is not None:
        out.alpha_composite(icon.convert("RGBA").crop((0, 0, 16, 16)), (1, 1))
    return out if scale == 1 else out.resize((18 * scale, 18 * scale), Image.NEAREST)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out", type=Path, help="PNG to write")
    ap.add_argument("--patterns", type=Path, default=DEFAULT_PATTERNS)
    ap.add_argument("--parameters", type=Path, default=DEFAULT_PARAMETERS)
    ap.add_argument("--slot", type=Path, default=DEFAULT_SLOT, help="misc_slots.png (slot read from 0,0 18x18)")
    ap.add_argument("--guide", type=Path, default=DEFAULT_GUIDE, help="style guide holding the ```palette block")
    ap.add_argument("--scale", type=int, default=6)
    ap.add_argument("--columns", type=int, default=8)
    ap.add_argument("--compare-rev", metavar="REV", help="show old-vs-new against this git revision")
    ap.add_argument("--compare", nargs="+", metavar="FOLDER/NAME",
                    help="icons to compare (e.g. patterns/clear); default: every icon that differs from REV")
    ap.add_argument("--exclude", nargs="+", default=[], metavar="NAME", help="icon basenames to leave out")
    ap.add_argument("--strict", action="store_true", help="exit 1 if any icon has a lint note")
    args = ap.parse_args()
    if args.compare and not args.compare_rev:
        ap.error("--compare needs --compare-rev")
    for d in (args.patterns, args.parameters):
        if not d.is_dir():
            ap.error(f"not a directory: {d}")
    if args.compare_rev and subprocess.run(["git", "-C", str(REPO), "rev-parse", "--verify", "--quiet",
                                            args.compare_rev + "^{commit}"], capture_output=True).returncode:
        ap.error(f"unknown revision: {args.compare_rev}")

    slot = Image.open(args.slot).convert("RGBA").crop(SLOT_BOX)
    palette = load_palette(args.guide)
    s = args.scale
    big = 18 * s
    f_title, f_label, f_note = font(16), font(11), font(10)

    folders = [("patterns", args.patterns), ("parameters", args.parameters)]
    icons: list[tuple[str, str, Path, Image.Image]] = []  # (folder, name, path, image)
    for folder, d in folders:
        for p in sorted(d.glob("*.png"), key=natural_key):
            if p.stem not in args.exclude:
                icons.append((folder, p.stem, p, Image.open(p).convert("RGBA")))

    compare: list[tuple[str, Path, Image.Image | None, Image.Image]] = []
    if args.compare_rev:
        wanted = set(args.compare) if args.compare else None
        for folder, name, p, im in icons:
            key = f"{folder}/{name}"
            if wanted is not None and key not in wanted:
                continue
            old = git_show(args.compare_rev, p)
            if wanted is None and old is not None and old.tobytes() == im.tobytes() and old.size == im.size:
                continue
            compare.append((key, p, old, im))
        if wanted:
            missing = wanted - {c[0] for c in compare}
            if missing:
                ap.error("not found: " + ", ".join(sorted(missing)))

    cell_w = big + GAP + 18 + GAP
    cell_h = big + 32
    cols = args.columns
    width = max(cols * cell_w + GAP, 2 * (2 * big + 3 * GAP) + GAP)
    pair_w = 2 * big + 3 * GAP
    pair_cols = max(1, (width - GAP) // pair_w)

    strips = {folder: strip_layout(strip_groups(folder, [i[1] for i in icons if i[0] == folder]), width)
              for folder, _ in folders}

    # Height: title, compare block, strips, grids.
    height = GAP + 24
    if compare:
        rows = (len(compare) + pair_cols - 1) // pair_cols
        height += 22 + rows * (18 + GAP + big + 36)
    for folder, _ in folders:
        n = sum(1 for i in icons if i[0] == folder)
        height += 22 + strips[folder][1] + GAP + 22 + ((n + cols - 1) // cols) * cell_h
    height += GAP

    sheet = Image.new("RGBA", (width, height), PANEL)
    d = ImageDraw.Draw(sheet)
    y = GAP
    d.text((GAP, y), f"Filler icons  |  slot = misc_slots.png 0,0  |  1x and {s}x", fill=TEXT, font=f_title)
    y += 24
    any_notes = False

    if compare:
        d.text((GAP, y), f"Old ({args.compare_rev}) vs new  -  1x pair on top, {s}x pair below", fill=TEXT,
               font=f_label)
        y += 22
        for i, (key, p, old, new) in enumerate(compare):
            cx = GAP + (i % pair_cols) * pair_w
            cy = y + (i // pair_cols) * (18 + GAP + big + 36)
            sheet.alpha_composite(in_slot(slot, old, 1), (cx, cy))
            sheet.alpha_composite(in_slot(slot, new, 1), (cx + 18 + 4, cy))
            sheet.alpha_composite(in_slot(slot, old, s), (cx, cy + 18 + GAP))
            sheet.alpha_composite(in_slot(slot, new, s), (cx + big + GAP, cy + 18 + GAP))
            ly = cy + 18 + GAP + big + 2
            d.text((cx, ly), "old" if old is not None else "old: (none)", fill=TEXT, font=f_note)
            d.text((cx + big + GAP, ly), "new", fill=TEXT, font=f_note)
            d.text((cx, ly + 13), key, fill=TEXT, font=f_label)
        y += ((len(compare) + pair_cols - 1) // pair_cols) * (18 + GAP + big + 36)

    for folder, _ in folders:
        group = [i for i in icons if i[0] == folder]
        pos, strip_h = strips[folder]
        order = "selection-popup rows" if folder == "patterns" else "one family per run"
        d.text((GAP, y), f"{folder}  -  1x neighbours as in game ({order}; ungrouped icons last)", fill=TEXT,
               font=f_label)
        y += 22
        for _, name, _, im in group:
            px, py = pos[name]
            sheet.alpha_composite(in_slot(slot, im, 1), (px, y + py))
        y += strip_h + GAP
        d.text((GAP, y), f"{folder}  -  {s}x with 1x beside", fill=TEXT, font=f_label)
        y += 22
        for k, (_, name, _, im) in enumerate(group):
            cx = GAP + (k % cols) * cell_w
            cy = y + (k // cols) * cell_h
            sheet.alpha_composite(in_slot(slot, im, s), (cx, cy))
            sheet.alpha_composite(in_slot(slot, im, 1), (cx + big + GAP, cy))
            d.text((cx, cy + big + 2), name, fill=TEXT, font=f_label)
            notes = lint(im, palette)
            if notes:
                any_notes = True
                d.text((cx, cy + big + 15), ", ".join(notes), fill=WARN, font=f_note)
        y += ((len(group) + cols - 1) // cols) * cell_h

    args.out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.out)
    print(f"wrote {args.out} ({width}x{height}); {len(icons)} icons, {len(compare)} compared"
          + ("" if palette is not None else "; no palette block found, colour lint skipped"))
    return 1 if (args.strict and any_notes) else 0


def natural_key(p: Path):
    return [int(t) if t.isdigit() else t for t in re.split(r"(\d+)", p.stem)]


if __name__ == "__main__":
    sys.exit(main())
