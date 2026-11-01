<!--
Copyright (c) 2026 the BuildCraftUnofficial contributors
This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
-->

# Branding sources

The BuildCraft Redux banners are built from the files in this folder. One command rebuilds every output:

```
python scripts/branding/build-banners.py                  # rebuild; a file is rewritten only if its pixels changed
python scripts/branding/build-banners.py --check          # exit 1 if an output is stale, the wrong size, or off-grid
python scripts/branding/build-banners.py --preview DIR    # also write review renders to DIR (keep DIR outside the repo)
```

Needs Python 3 and Pillow (last built with Python 3.14, Pillow 12.2). Rebuilding twice gives byte-identical files.

## Files

| File | What it is |
|---|---|
| `lockup.png` | Hand-pixelled master, 138x50, one pixel per logical pixel, transparent: the lattice crane, the "BuildCraft" wordmark and the hazard band carrying REDUX. Edit it 1:1 in any pixel editor. Keep it 50 px tall and keep alpha fully on or off. If the band's centre moves, update `LOCKUP_BAND_CX` in the script. |
| `font/girder7.txt` | Girder 7, the pixel font for the credit line (see below). Plain text: `[c]` starts a glyph, then 9 rows of `.` and `#`. |
| `backgrounds/filler-pyramid.png` | Behind the store hero. In-game screenshot `03-filler-pyramid.png` (2026-10-19 store-page set, 2560x1440), reduced to 960x540 and 256 colours. It is blurred and darkened at build time, so the reduction does not show. |
| `backgrounds/robots-farm.png` | Behind the 16:9 card. Screenshot `01-robots-farm.png` from the same set, reduced the same way. |

The build also reads the mod's own gear textures (`src/main/resources/assets/buildcraftunofficial/textures/item/gear_{wood,stone,iron,gold,diamond}.png`), so the banner always shows the gears the mod ships. There is no obsidian gear.

## Outputs

| Output | Size | What it is |
|---|---|---|
| `src/main/resources/logo.png` | 1000x200 RGBA | Mod-list banner. Drawn on a 250x50 grid (NeoForge's banner box) with every logical pixel a 4x4 block. NeoForge 26.2 and later sample it NEAREST, so it is sharp at every whole GUI scale; the older lines filter it LINEAR (`logoBlur` defaults to true), which only softens edges. Transparent. |
| `docs/store/img/banner.png` | 2000x500 | Store-page hero: the mark at x6 over the Filler build site, hazard tape top and bottom. Opaque. |
| `docs/store/img/banner-wide.png` | 1920x1080 | Gallery / social card: an upright arrangement at x8 over the robot farm, everything inside the central 80%. |
| `docs/store/img/logo-transparent.png` | 2000x400 RGBA | The mark alone (the 250x50 mark at x8), for placing anywhere. |

Pixel art is only ever scaled by whole numbers with nearest-neighbour; only the screenshots are resampled smoothly.

## Common edits

- **Credit text:** `CREDIT` near the top of `scripts/branding/build-banners.py`. Each line is a list of (text, tone) runs; `dim` is grey, `bright` is near-white. The nameplate resizes to fit, and every character must exist in Girder 7.
- **Layout:** `LOCKUP_X`, `COLUMN_CX` and `LOCKUP_BAND_CX` place the parts of the 250x50 mark. Hero and card settings (screenshot, focus, blur, darkness, scale) are in `build_hero` and `build_wide`.
- **Art:** edit `lockup.png`, rebuild, then look at the `--preview` renders: logo at 250x50 and 500x100 over a dark and a white panel, the LINEAR-filtered look of the older MC lines, and the web images on light and dark pages.

## Design notes

- The mark keeps the original BuildCraft logo's structure: crane on the left with its jib over the name, a heavy "Build" and a light "Craft", a hazard bar under the name, and the gears on the right (the material row and the obsidian column are gone).
- REDUX replaces the old bar's plain stripes: yellow capitals on a black plate set into the hazard band, 11 px tall against the wordmark's 18, so it reads as part of the name.
- The wordmark is light with a dark outline and a drop shadow, and the credit sits on a dark riveted plate, so the banner reads on NeoForge's dark panel and on white.
- Palette: hazard yellow `#F6C400` (bottom-row shade `#DEA800`) on band black `#1C1C1C`; crane steel `#E8E8E8`, `#9E9E9E`, `#686868`; outlines `#121212` (crane, band) and `#161616` (wordmark, over a `#000000` drop shadow); wordmark fill graded top to bottom, `#FFFFFF` to `#BEBEBE` for "Build" and `#C8C8C8` to `#949494` for "Craft". The nameplate is `#262626` with `#969696` and `#ECECEC` text.

## Girder 7 font

Girder 7 is an original pixel font drawn for this project, glyph by glyph on the grid in `font/girder7.txt`. It is not traced or copied from Mojang's Minecraft font or any other font. It is covered by the repository's MPL-2.0 licence, like the rest of this folder (the notice is at the top of the file).

Metrics: cap height 7, x-height 5, descenders 2 (9 rows per glyph). Capitals are mostly 5 px wide, lowercase mostly 4, with 1 px between letters. It covers printable ASCII plus `·`.
