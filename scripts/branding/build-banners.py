#!/usr/bin/env python3
# Copyright (c) 2026 the BuildCraftUnofficial contributors
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
# distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
"""Build the BuildCraft Redux banner art from the sources in misc/branding/.

Outputs (deterministic; a file is rewritten only when its pixels change, so a rerun leaves it byte-identical):
  src/main/resources/logo.png          in-game mod-list banner: 250x50 logical pixels, each a 4x4 block
                                       (1000x200 RGBA, transparent); NeoForge 26.2+ samples it NEAREST,
                                       the older lines LINEAR (logoBlur defaults to true)
  docs/store/img/banner.png            store-page hero, 2000x500, opaque
  docs/store/img/banner-wide.png       gallery / social preview, 1920x1080, opaque, mark inside the 10% safe area
  docs/store/img/logo-transparent.png  the mark alone, 2000x400 RGBA (the 250x50 mark at x8)

Sources:
  misc/branding/lockup.png             hand-pixelled master: crane + "BuildCraft" + REDUX band (1 px = 1 logical px)
  misc/branding/font/girder7.txt       pixel font for the credit line
  misc/branding/backgrounds/*.png      in-game screenshots behind the web versions
  the mod's own gear textures          src/main/resources/assets/buildcraftunofficial/textures/item/gear_*.png

Usage:
  python scripts/branding/build-banners.py                rebuild every output
  python scripts/branding/build-banners.py --check        exit 1 unless every output is current and logo.png is
                                                          grid-aligned (250x50 nearest round trip is lossless)
  python scripts/branding/build-banners.py --preview DIR  also write review renders (nearest-scaled logo over dark
                                                          and light panels, web banners on light/dark pages) to DIR
"""

from __future__ import annotations

import argparse
import io
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

REPO = Path(__file__).resolve().parents[2]
BRAND = REPO / "misc" / "branding"
LOCKUP = BRAND / "lockup.png"
FONT = BRAND / "font" / "girder7.txt"
BACKGROUNDS = BRAND / "backgrounds"
GEAR_TEXTURES = REPO / "src" / "main" / "resources" / "assets" / "buildcraftunofficial" / "textures" / "item"
GEARS = ("wood", "stone", "iron", "gold", "diamond")  # no obsidian gear in this mod

OUT_LOGO = REPO / "src" / "main" / "resources" / "logo.png"
OUT_HERO = REPO / "docs" / "store" / "img" / "banner.png"
OUT_WIDE = REPO / "docs" / "store" / "img" / "banner-wide.png"
OUT_MARK = REPO / "docs" / "store" / "img" / "logo-transparent.png"

# The credit line, as runs of (text, tone). Every version carries it; edit here and rebuild.
CREDIT = (
    (("based on ", "dim"), ("BuildCraft", "bright")),
    (("by ", "dim"), ("SpaceToad", "bright")),
)

MARK_W, MARK_H = 250, 50  # NeoForge ModListScreen: INFO_PANEL_WIDTH x BANNER_HEIGHT
LOGO_SCALE = 4            # logo.png = mark x4: crisp at any whole GUI scale (NEAREST), and LINEAR up to scale 4
LOCKUP_X = 3              # lockup.png's left edge inside the 250x50 mark
COLUMN_CX = 199           # centre of the gears + nameplate column inside the 250x50 mark
LOCKUP_BAND_CX = 75       # centre of the "BuildCraft"/REDUX band inside lockup.png (stacked layout aligns to it)

INK = {"dim": (150, 150, 150, 255), "bright": (236, 236, 236, 255)}
EDGE = (18, 18, 18, 255)
PLATE = (38, 38, 38, 255)
RIVET = (96, 96, 96, 255)
CLEAR = (0, 0, 0, 0)
HAZARD = ((246, 208, 59, 255), (246, 196, 0, 255), (28, 28, 28, 255))  # the REDUX band's yellow, shade, black


# --------------------------------------------------------------------------------------------- pixel font
def load_font(path: Path = FONT) -> dict[str, list[str]]:
    """Parse girder7.txt: '[c]' headers, 9 rows of '.'/'#' per glyph, ';' comments."""
    glyphs: dict[str, list[str]] = {}
    current, rows = None, []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if not line or line.startswith(";"):
            continue
        if line.startswith("[") and line.endswith("]") and (len(line) == 3 or line == "[space]"):
            if current is not None:
                glyphs[current] = rows
            current, rows = (" " if line == "[space]" else line[1]), []
            continue
        if set(line) - {".", "#"}:
            raise ValueError(f"{path.name}: bad row {line!r} in glyph {current!r}")
        rows.append(line)
    if current is not None:
        glyphs[current] = rows
    for ch, rows in glyphs.items():
        if len(rows) != 9 or len({len(r) for r in rows}) != 1:
            raise ValueError(f"{path.name}: glyph {ch!r} must be 9 rows of equal width")
    return glyphs


def text_runs(glyphs, runs) -> tuple[list[tuple[int, int, str]], int, int]:
    """Lay out runs of (text, tone): returns ink pixels (x, y, tone), width and height (7, or 9 with descenders)."""
    pixels, pen, deepest = [], 0, 6
    for text, tone in runs:
        for ch in text:
            if ch not in glyphs:
                raise ValueError(f"character {ch!r} is not in {FONT.name}; draw it there first")
            rows = glyphs[ch]
            for y, row in enumerate(rows):
                for x, cell in enumerate(row):
                    if cell == "#":
                        pixels.append((pen + x, y, tone))
                        deepest = max(deepest, y)
            pen += len(rows[0]) + 1
    return pixels, pen - 1, deepest + 1


# --------------------------------------------------------------------------------------------- mark parts
def load_rgba(path: Path) -> Image.Image:
    with Image.open(path) as img:
        return img.convert("RGBA")


def nameplate(glyphs, lines=CREDIT, pad_x: int = 3) -> Image.Image:
    """Dark riveted plate carrying the credit lines (centred). Reads on dark and light backgrounds alike."""
    laid = [text_runs(glyphs, runs) for runs in lines]
    inner_w = max(w for _, w, _ in laid)
    w = inner_w + 2 * pad_x + 2
    h = sum(hh for _, _, hh in laid) + (len(laid) - 1) + 3
    img = Image.new("RGBA", (w, h), PLATE)
    px = img.load()
    for x in range(w):
        px[x, 0] = px[x, h - 1] = EDGE
    for y in range(h):
        px[0, y] = px[w - 1, y] = EDGE
    for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        px[x, y] = RIVET
    y0 = 2
    for pixels, lw, lh in laid:
        x0 = 1 + pad_x + (inner_w - lw) // 2
        for x, y, tone in pixels:
            px[x0 + x, y0 + y] = INK[tone]
        y0 += lh + 1
    return img


def gear_row(gap: int = 2) -> Image.Image:
    row = Image.new("RGBA", (16 * len(GEARS) + gap * (len(GEARS) - 1), 16), CLEAR)
    for i, name in enumerate(GEARS):
        gear = load_rgba(GEAR_TEXTURES / f"gear_{name}.png")
        if gear.size != (16, 16):
            raise ValueError(f"gear_{name}.png is {gear.size}, the banner layout expects 16x16")
        row.alpha_composite(gear, (i * (16 + gap), 0))
    return row


def mark() -> Image.Image:
    """The 250x50 logical mark: lockup on the left; gears over the credit nameplate on the right, the plate's
    bottom level with the REDUX band's (row 47)."""
    glyphs = load_font()
    lockup = load_rgba(LOCKUP)
    if lockup.height != MARK_H:
        raise ValueError(f"lockup.png must be {MARK_H} px tall, got {lockup.height}")
    canvas = Image.new("RGBA", (MARK_W, MARK_H), CLEAR)
    canvas.alpha_composite(lockup, (LOCKUP_X, 0))
    gears = gear_row()
    plate = nameplate(glyphs)
    canvas.alpha_composite(gears, (COLUMN_CX - gears.width // 2, 10))
    canvas.alpha_composite(plate, (COLUMN_CX - plate.width // 2, 47 - plate.height + 1))
    return canvas


def stacked_mark() -> Image.Image:
    """Upright arrangement for the 16:9 card: lockup on top, then the gear row, then the credit nameplate,
    both centred under the REDUX band."""
    glyphs = load_font()
    lockup = load_rgba(LOCKUP)
    gears = gear_row()
    plate = nameplate(glyphs)
    gears_y = lockup.height + 6
    plate_y = gears_y + gears.height + 4
    canvas = Image.new("RGBA", (lockup.width, plate_y + plate.height), CLEAR)
    canvas.alpha_composite(lockup, (0, 0))
    canvas.alpha_composite(gears, (LOCKUP_BAND_CX - gears.width // 2, gears_y))
    canvas.alpha_composite(plate, (LOCKUP_BAND_CX - plate.width // 2, plate_y))
    return canvas


def hazard_tape(width: int, rows: int, k: int, edge_on_top: bool) -> Image.Image:
    """A strip of the band's diagonal hazard stripes with a dark edge line, scaled by k, cut to width."""
    lw = -(-width // k)
    tape = Image.new("RGBA", (lw, rows + 1), EDGE)
    px = tape.load()
    for y in range(rows):
        for x in range(lw):
            yellow = HAZARD[1] if y == rows - 1 else HAZARD[0]
            px[x, y + (1 if edge_on_top else 0)] = yellow if ((x + y) // 3) % 2 == 0 else HAZARD[2]
    return nearest(tape, k).crop((0, 0, width, (rows + 1) * k))


# --------------------------------------------------------------------------------------------- web helpers
def nearest(img: Image.Image, k: int) -> Image.Image:
    return img.resize((img.width * k, img.height * k), Image.Resampling.NEAREST)


def backdrop(name: str, size: tuple[int, int], focus: tuple[float, float], blur: float, dim: float) -> Image.Image:
    """Cover-fit a screenshot to size around focus (fractions), blur it, darken it, add a vignette."""
    src = load_rgba(BACKGROUNDS / name).convert("RGB")
    tw, th = size
    k = max(tw / src.width, th / src.height)
    sw, sh = round(src.width * k), round(src.height * k)
    img = src.resize((sw, sh), Image.Resampling.LANCZOS)
    left = min(max(round(focus[0] * sw - tw / 2), 0), sw - tw)
    top = min(max(round(focus[1] * sh - th / 2), 0), sh - th)
    img = img.crop((left, top, left + tw, top + th)).filter(ImageFilter.GaussianBlur(blur))
    img = img.point(lambda v: round(v * dim))
    vignette = Image.radial_gradient("L").resize(size, Image.Resampling.BICUBIC)
    vignette = vignette.point(lambda v: 255 - round(v * 0.55))  # centre 255, corners ~ 45% darker
    return ImageChops.multiply(img, Image.merge("RGB", (vignette,) * 3))


def halo(mark_img: Image.Image, spread: int, blur: float, strength: float) -> Image.Image:
    """Soft dark glow under a scaled mark so it separates from a busy backdrop."""
    alpha = mark_img.getchannel("A").point(lambda v: 255 if v else 0)
    alpha = alpha.filter(ImageFilter.MaxFilter(spread * 2 + 1)).filter(ImageFilter.GaussianBlur(blur))
    alpha = alpha.point(lambda v: round(v * strength))
    shadow = Image.new("RGBA", mark_img.size, (0, 0, 0, 255))
    shadow.putalpha(alpha)
    return shadow


def composite(base: Image.Image, overlay: Image.Image, x: int, y: int) -> None:
    """alpha_composite that tolerates an overlay hanging off any edge of base (it is clipped)."""
    sx, sy = max(0, -x), max(0, -y)
    w, h = min(overlay.width - sx, base.width - x - sx), min(overlay.height - sy, base.height - y - sy)
    if w > 0 and h > 0:
        base.alpha_composite(overlay.crop((sx, sy, sx + w, sy + h)), (x + sx, y + sy))


def place(base: Image.Image, overlay: Image.Image, xy: tuple[int, int], glow: tuple[int, float, float] | None = None):
    """Composite overlay at xy, optionally over a halo (spread, blur, strength)."""
    if glow:
        pad = glow[0] + int(glow[1] * 3)
        padded = Image.new("RGBA", (overlay.width + 2 * pad, overlay.height + 2 * pad), CLEAR)
        padded.alpha_composite(overlay, (pad, pad))
        composite(base, halo(padded, *glow), xy[0] - pad, xy[1] - pad)
    composite(base, overlay, *xy)


# --------------------------------------------------------------------------------------------- outputs
def build_logo() -> Image.Image:
    return nearest(mark(), LOGO_SCALE)


def build_mark_transparent() -> Image.Image:
    return nearest(mark(), 8)


def build_hero() -> Image.Image:
    """2000x500 page-top hero: the full mark at x6 on the Filler build site, framed top and bottom by hazard
    tape at the same pixel scale. Opaque, so it sits the same on light and dark page themes."""
    size, k = (2000, 500), 6
    base = backdrop("filler-pyramid.png", size, focus=(0.5, 0.45), blur=6, dim=0.55).convert("RGBA")
    m = nearest(mark(), k)
    place(base, m, ((size[0] - m.width) // 2, (size[1] - m.height) // 2), glow=(18, 24, 0.6))
    base.alpha_composite(hazard_tape(size[0], 4, k, edge_on_top=False), (0, 0))
    bottom = hazard_tape(size[0], 4, k, edge_on_top=True)
    base.alpha_composite(bottom, (0, size[1] - bottom.height))
    return base.convert("RGB")


def build_wide() -> Image.Image:
    """1920x1080 gallery / social card: the upright arrangement at x8 over the robot farm, everything that
    matters inside the central 80% (the 10% safe margin)."""
    size, k = (1920, 1080), 8
    base = backdrop("robots-farm.png", size, focus=(0.5, 0.5), blur=6, dim=0.5).convert("RGBA")
    m = nearest(stacked_mark(), k)
    # the text axis (wordmark, REDUX, gears, credit) sits on the frame's centre line; the crane hangs off to the left
    x, y = size[0] // 2 - LOCKUP_BAND_CX * k, (size[1] - m.height) // 2
    if x < size[0] * 0.1 or y < size[1] * 0.1 or x + m.width > size[0] * 0.9 or y + m.height > size[1] * 0.9:
        raise ValueError(f"wide banner mark {m.size} at {(x, y)} breaks the 10% safe margin")
    place(base, m, (x, y), glow=(20, 28, 0.6))
    return base.convert("RGB")


# path, builder, expected (size, mode), per-channel tolerance for --check. The pixel-art outputs must match
# exactly; the two photo-backed banners get a small slack so a different Pillow's resampling isn't "stale".
OUTPUTS = (
    (OUT_LOGO, build_logo, ((MARK_W * LOGO_SCALE, MARK_H * LOGO_SCALE), "RGBA"), 0),
    (OUT_HERO, build_hero, ((2000, 500), "RGB"), 3),
    (OUT_WIDE, build_wide, ((1920, 1080), "RGB"), 3),
    (OUT_MARK, build_mark_transparent, ((MARK_W * 8, MARK_H * 8), "RGBA"), 0),
)


def png_bytes(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)  # no metadata chunks, so the bytes depend only on the pixels
    return buf.getvalue()


def pixel_diff(path: Path, img: Image.Image) -> int | None:
    """Largest per-channel difference between the file on disk and img; None if sizes or modes differ."""
    with Image.open(path) as disk:
        disk.load()
        if disk.size != img.size or disk.mode != img.mode:
            return None
        return max(hi for _, hi in ImageChops.difference(disk, img).getextrema())


# --------------------------------------------------------------------------------------------- checks
def grid_aligned(path: Path) -> bool:
    """logo.png survives 250x50 nearest down and x4 nearest up unchanged: every logical pixel is a 4x4 block."""
    with Image.open(path) as img:
        img = img.convert("RGBA")
    small = img.resize((MARK_W, MARK_H), Image.Resampling.NEAREST)
    return nearest(small, LOGO_SCALE).tobytes() == img.tobytes()


def check() -> int:
    problems = []
    for path, build, (size, mode), tolerance in OUTPUTS:
        rel = path.relative_to(REPO).as_posix()
        if not path.exists():
            problems.append(f"{rel}: missing")
            continue
        with Image.open(path) as img:
            if img.size != size or img.mode != mode:
                problems.append(f"{rel}: {img.size} {img.mode}, expected {size} {mode}")
                continue
        diff = pixel_diff(path, build())
        if diff is None or diff > tolerance:
            problems.append(f"{rel}: stale, differs from a fresh render by {diff} (rebuild it)")
    if OUT_LOGO.exists() and not grid_aligned(OUT_LOGO):
        problems.append("src/main/resources/logo.png: not aligned to the 250x50 logical grid")
    for p in problems:
        print("FAIL", p)
    if not problems:
        print("OK: every banner output is current; logo.png is 1000x200 RGBA on the 250x50 logical grid")
    return 1 if problems else 0


# --------------------------------------------------------------------------------------------- previews
def write_previews(out_dir: Path) -> None:
    """Review renders: what each output looks like where it will be shown."""
    out_dir.mkdir(parents=True, exist_ok=True)
    logo = load_rgba(OUT_LOGO)
    for w, h in ((MARK_W, MARK_H), (MARK_W * 2, MARK_H * 2)):
        scaled = logo.resize((w, h), Image.Resampling.NEAREST)
        for name, colour in (("dark-1e1e1e", (30, 30, 30, 255)), ("light-ffffff", (255, 255, 255, 255))):
            panel = Image.new("RGBA", (w + 16, h + 16), colour)
            panel.alpha_composite(scaled, (8, 8))
            panel.convert("RGB").save(out_dir / f"logo-{w}x{h}-on-{name}.png")
            # the same at x4 so a reviewer sees the real pixels without a viewer's smoothing
            nearest(panel, 4 if w == MARK_W else 2).convert("RGB").save(out_dir / f"logo-{w}x{h}-on-{name}-zoom.png")
    # old MC lines: same file, LINEAR-filtered into the panel
    for w, h in ((MARK_W, MARK_H), (MARK_W * 2, MARK_H * 2), (MARK_W * 3, MARK_H * 3)):
        panel = Image.new("RGBA", (w + 16, h + 16), (30, 30, 30, 255))
        panel.alpha_composite(logo.resize((w, h), Image.Resampling.BILINEAR), (8, 8))
        panel.convert("RGB").save(out_dir / f"logo-{w}x{h}-linear-on-dark.png")
    for page_name, page in (("light", (255, 255, 255)), ("dark", (22, 24, 28))):
        for path in (OUT_HERO, OUT_WIDE):
            img = load_rgba(path).convert("RGB")
            shown = img.resize((900, round(img.height * 900 / img.width)), Image.Resampling.LANCZOS)
            sheet = Image.new("RGB", (900 + 80, shown.height + 80), page)
            sheet.paste(shown, (40, 40))
            sheet.save(out_dir / f"{path.stem}-on-{page_name}-page.png")
        mark_img = load_rgba(OUT_MARK)
        shown = mark_img.resize((1000, 200), Image.Resampling.LANCZOS)
        sheet = Image.new("RGBA", (1080, 280), page + (255,))
        sheet.alpha_composite(shown, (40, 40))
        sheet.convert("RGB").save(out_dir / f"logo-transparent-on-{page_name}-page.png")
    print(f"previews written to {out_dir}")


# --------------------------------------------------------------------------------------------- main
def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="verify outputs, write nothing")
    parser.add_argument("--preview", type=Path, metavar="DIR", help="also write review renders to DIR")
    args = parser.parse_args(argv)
    if args.check:
        return check()
    for path, build, _, _ in OUTPUTS:
        img = build()
        rel = path.relative_to(REPO).as_posix()
        if path.exists() and pixel_diff(path, img) == 0:
            print(f"unchanged {rel}")  # same pixels: keep the bytes (no churn from another Pillow's encoder)
            continue
        data = png_bytes(img)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        print(f"wrote {rel} ({len(data) // 1024} KB)")
    if args.preview:
        write_previews(args.preview)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
