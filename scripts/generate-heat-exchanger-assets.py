#!/usr/bin/env python3
"""Generate the Heat Exchanger's block models, item model, blockstate and textures.

The shell-and-tube look: a shell in the other BC machines' casing grey with a sight slot
on all four long faces (the renderer's fluid flow shows through them), bolted steel flanges where two exchangers join,
blind dished caps on a lone segment's open ends, and colour-banded nozzles where the fluids
enter and leave a finished 3-5 block exchanger (START blue = cold side, END red = hot side,
the same colours as the GUI help). Everything is generated so the geometry can be changed
in one place and stay consistent with RenderHeatExchange's tank and flow boxes.

Run (stdlib only, rewrites the files in place):
    python3 scripts/generate-heat-exchanger-assets.py
    python3 scripts/generate-heat-exchanger-assets.py --out DIR   # an assets/<namespace> dir elsewhere

Canonical frame (blockstate y=0): FACING=north, chain axis = X, the clockwise ("right",
CONNECTED_RIGHT) neighbour is east (x 14..16), the counter-clockwise ("left") one west (x 0..2).
START sits at the clockwise end of a chain (outer end east), END at the counter-clockwise end
(outer end west); BlockHeatExchange/TileHeatExchange decide which block is which.

Every face uses the projected ("default") UV of its own position unless a rim needs one
specific texture column, so the textures are painted in block coordinates and no UV is ever
rotated. Left-side parts are their own mirrored models (not the right-side model turned 180
degrees), which keeps the two halves of a bolted joint pixel-aligned on the top and bottom.
All textures are fully opaque: the 26.x lines pick each face's chunk layer from its sprite's
pixels, the 1.21.x lines read render_type, and both land on SOLID.

Fluid geometry this must agree with (RenderHeatExchange, model pixels, canonical frame):
  START side tank (14,4,4)-(16,12,12)  -> axial nozzle bore, east
  END side tank   (0,4,4)-(2,12,12)    -> axial nozzle bore, west
  START bottom    (4,0,4)-(12,2,12)    -> bottom nozzle bore (TANK_BOTTOM)
  END top         (4,14,4)-(12,16,12)  -> top nozzle bore (TANK_TOP)
  every tank box is shrunk by 1/4 px when drawn
  flow: heatant box y/z 2.1..13.9, coolant box 4.1..11.9, from END x ~2.2 to START x ~13.7
        -> enclosed by the 2..14 shell, seen through the sight slots (front/back y 6..10, top/bottom
           z 6..10, each down to the 4.25..11.75 core); the flow ends sit inside the 1px cover plates.
"""
import json
import os
import struct
import sys
import zlib

NS = "buildcraftunofficial"
TEXDIR = NS + ":block/heat_exchange/"
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_OUT = os.path.join(REPO, "src", "main", "resources", "assets", NS)

# =========================================================================================
# Textures (16x16, opaque). Each is laid out for the projected UVs the models use, so a
# texel's place on the sheet is where it lands on the block.
# =========================================================================================

# shell plate: the BC machine casing ramp (filler, quarry, builder, architect...): plate
# #cbcbcb with its one-step mottles, and the 1px #a7a7a7 frame every casing face wears
G_MD = (0xcb, 0xcb, 0xcb)
G_M2 = (0xc6, 0xc6, 0xc6)  # plate mottling
G_M3 = (0xcd, 0xcd, 0xcd)  # lighter plate mottling
G_DK = (0xbd, 0xbd, 0xbd)
G_DD = (0xa7, 0xa7, 0xa7)
# window seal: one flat band in the old heat exchanger's dark green (its sprites' #24431b)
SEAL = (0x24, 0x43, 0x1b)
# steel (shares the casing ramp's light and frame)
S_HI = (0xd9, 0xd9, 0xd9)
S_LT = (0xc2, 0xc2, 0xc2)
S_MD = (0xa7, 0xa7, 0xa7)
S_DK = (0x84, 0x84, 0x84)
S_DD = (0x63, 0x63, 0x63)
S_XX = (0x45, 0x45, 0x45)
S_BK = (0x2f, 0x2f, 0x2f)
# interior: the casings' recess greys (the quarry and builder displays' #363636)
I_BK = (0x2b, 0x2b, 0x2b)
I_DK = (0x36, 0x36, 0x36)
# copper tube bundle seen through the sight slots
C_LT = (0xad, 0x6c, 0x3a)
C_MD = (0x86, 0x50, 0x2a)
C_DK = (0x5c, 0x36, 0x1b)
# nozzle colour bands (GUI help colours: hot_in/heated_out red-orange, cold_in/cooled_out blue)
HOT = {"hi": (0xff, 0x9a, 0x6a), "md": (0xd8, 0x4a, 0x2e), "dk": (0x96, 0x2c, 0x1a)}
COLD = {"hi": (0x9c, 0xc8, 0xff), "md": (0x3f, 0x80, 0xd8), "dk": (0x25, 0x4f, 0x92)}


def new(fill):
    return [[fill for _ in range(16)] for _ in range(16)]


def hline(px, y, x0, x1, c):
    for x in range(x0, x1 + 1):
        px[y][x] = c


def vline(px, x, y0, y1, c):
    for y in range(y0, y1 + 1):
        px[y][x] = c


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[y][x] = c


def mottle(px, x0, y0, x1, y1, base, alt, seed):
    """Sparse deterministic paint noise (a few texels one shade off)."""
    s = seed
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            s = (s * 1103515245 + 12345) & 0x7FFFFFFF
            if px[y][x] == base and (s >> 16) % 7 == 0:
                px[y][x] = alt


def tex_shell_side():
    """Front/back of the shell: rows 2-4 upper band, 5/10 green seal, 6-9 sight slot, 11-13 lower
    band, rows 14-15 the slot walls (explicit UV), the same green as the seal. Rows 2 and 13,
    the shell's outer edges, are the casing frame."""
    px = new(G_MD)
    mottle(px, 0, 0, 15, 15, G_MD, G_M2, 7)
    mottle(px, 0, 0, 15, 15, G_MD, G_M3, 29)
    hline(px, 2, 0, 15, G_DD)
    # green seal above and below the slot
    hline(px, 5, 0, 15, SEAL)
    hline(px, 10, 0, 15, SEAL)
    # tube bundle through the glass: one copper tube on a dark ground
    hline(px, 6, 0, 15, I_BK)
    hline(px, 7, 0, 15, C_MD)
    hline(px, 8, 0, 15, C_DK)
    hline(px, 9, 0, 15, I_DK)
    for x in (2, 6, 9, 13):
        px[7][x] = C_LT
    hline(px, 11, 0, 15, G_MD)
    hline(px, 13, 0, 15, G_DD)
    mottle(px, 0, 11, 15, 11, G_MD, G_M2, 3)
    # slot walls: the seal's depth
    hline(px, 14, 0, 15, SEAL)
    hline(px, 15, 0, 15, SEAL)
    hline(px, 0, 0, 15, G_DK)
    hline(px, 1, 0, 15, G_DK)
    return px


def tex_shell_top():
    """Top/bottom of the shell (u = x, v = z): the same slot layout as the sides turned flat.
    Rows 2-4 and 11-13 plate, 5/10 bezel, 6-9 what the slot sees (the core's face), rows 14-15
    the slot walls (explicit UV). Rows 5 and 10 are both seal and rows 2 and 13 (the outer
    edges) both frame, so the bottom (which samples the sheet flipped) keeps both on the
    same edges as the top; only the mottling differs."""
    px = new(G_MD)
    mottle(px, 0, 0, 15, 15, G_MD, G_M2, 11)
    mottle(px, 0, 0, 15, 15, G_MD, G_M3, 31)
    hline(px, 2, 0, 15, G_DD)
    hline(px, 13, 0, 15, G_DD)
    hline(px, 5, 0, 15, SEAL)
    hline(px, 10, 0, 15, SEAL)
    hline(px, 6, 0, 15, I_BK)
    hline(px, 7, 0, 15, C_MD)
    hline(px, 8, 0, 15, C_MD)
    hline(px, 9, 0, 15, I_BK)
    for x in (2, 6, 9, 13):
        px[7][x] = C_LT
        px[8][x] = C_DK
    hline(px, 14, 0, 15, SEAL)
    hline(px, 15, 0, 15, SEAL)
    hline(px, 0, 0, 15, G_DK)
    hline(px, 1, 0, 15, G_DK)
    return px


def tex_flange():
    """Joint-flange halves, cap-plate and cover rims, and every shell-facing flange face.
    cols 0/15: joint-side rim (bolt heads); cols 1/14: shell-side rim; rows/cols 1 and 14:
    the ring a shell-facing face shows around the shell; centre: what the sight slots
    see at their ends. Cols 0/15 and 1/14 are painted alike, so either side may sample them."""
    px = new(I_DK)
    hline(px, 1, 1, 14, S_LT)
    hline(px, 14, 1, 14, S_DK)
    for c in (0, 15):
        vline(px, c, 0, 15, S_LT)
        px[1][c] = S_HI
        px[14][c] = S_DK
        for r in (3, 6, 9, 12):  # bolt heads straddling the joint
            px[r][c] = S_HI
            px[r + 1][c] = S_XX if r < 12 else S_DD
    for c in (1, 14):
        vline(px, c, 0, 15, S_MD)
        px[1][c] = S_LT
        px[14][c] = S_DK
    rect(px, 2, 2, 13, 13, I_DK)
    rect(px, 2, 6, 13, 9, S_DD)
    return px


def tex_cap():
    """Blind end of an unconnected segment: end view (cols 1-14) + dome rim (cols 0/15)."""
    px = new(S_MD)
    rect(px, 1, 1, 14, 14, S_LT)
    hline(px, 1, 1, 14, S_HI)
    vline(px, 1, 1, 14, S_HI)
    hline(px, 14, 1, 14, S_DK)
    vline(px, 14, 2, 14, S_DK)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 1), (8, 14), (1, 8), (14, 7)):
        px[y][x] = S_XX
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[y][x] = S_BK
    # dome 3..12: dished head shading and a centre boss
    rect(px, 3, 3, 12, 12, S_MD)
    hline(px, 3, 3, 12, S_LT)
    vline(px, 3, 3, 12, S_LT)
    hline(px, 12, 3, 12, S_DK)
    vline(px, 12, 3, 12, S_DK)
    rect(px, 5, 5, 10, 10, S_LT)
    rect(px, 6, 6, 9, 9, S_HI)
    rect(px, 7, 7, 8, 8, S_DD)
    px[7][7] = S_MD
    for c in (0, 15):
        vline(px, c, 0, 15, S_MD)
        vline(px, c, 3, 12, S_LT)
        px[3][c] = S_HI
        px[12][c] = S_DK
    return px


def tex_cover():
    """START/END channel-head plate, end view: the cap's bolted ring around a dark bore."""
    px = tex_cap()
    rect(px, 3, 3, 12, 12, S_DD)
    rect(px, 4, 4, 11, 11, I_BK)
    rect(px, 5, 5, 10, 10, I_DK)
    return px


def tex_nozzle(col):
    """10x10 collar, 2px long, around the 8x8 bore. End view: ring texels 3/12 (colour band),
    bore 4..11 dark. Rims: cols 0/15 and rows 0/15 are the mouth half (colour), cols 1/14 and
    rows 1/14 the body half (steel)."""
    px = new(S_MD)
    rect(px, 3, 3, 12, 12, col["md"])
    hline(px, 3, 3, 12, col["hi"])
    vline(px, 3, 3, 12, col["hi"])
    hline(px, 12, 4, 12, col["dk"])
    vline(px, 12, 4, 12, col["dk"])
    rect(px, 4, 4, 11, 11, I_BK)
    rect(px, 5, 5, 10, 10, I_DK)
    for c in (0, 15):
        vline(px, c, 0, 15, col["md"])
        px[3][c] = col["hi"]
        px[12][c] = col["dk"]
    for c in (1, 14):
        vline(px, c, 0, 15, S_LT)
        px[3][c] = S_HI
        px[12][c] = S_DK
    for r in (0, 15):
        hline(px, r, 0, 15, col["md"])
        px[r][3] = col["hi"]
        px[r][12] = col["dk"]
    for r in (1, 14):
        hline(px, r, 0, 15, S_LT)
        px[r][3] = S_HI
        px[r][12] = S_DK
    return px


TEXTURE_PAINTERS = {
    "shell_side": tex_shell_side,
    "shell_top": tex_shell_top,
    "flange": tex_flange,
    "cap": tex_cap,
    "cover": tex_cover,
    "nozzle_cold": lambda: tex_nozzle(COLD),
    "nozzle_hot": lambda: tex_nozzle(HOT),
}


def write_png(px, path):
    """Minimal RGBA PNG writer (no Pillow needed). Pixel-deterministic; the bytes follow the local zlib build, so a regeneration on another machine can rewrite unchanged PNGs."""
    raw = b"".join(b"\x00" + bytes(v for (r, g, b) in row for v in (r, g, b, 255)) for row in px)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", len(px[0]), len(px), 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


# =========================================================================================
# Models
# =========================================================================================

TEXTURES = {
    "side": TEXDIR + "shell_side",
    "top": TEXDIR + "shell_top",
    "flange": TEXDIR + "flange",
    "cap": TEXDIR + "cap",
    "cover": TEXDIR + "cover",
    "cold": TEXDIR + "nozzle_cold",
    "hot": TEXDIR + "nozzle_hot",
    "particle": TEXDIR + "shell_top",
}

SH0, SH1 = 2, 14            # shell cross-section
GL0, GL1 = 6, 10            # sight slot width (y on the front/back, z on the top/bottom)
CORE0, CORE1 = 4.25, 11.75  # slot floor depth: inside the coolant flow box (4.1..11.9)
FL0, FL1 = 1, 15            # flange / cover / cap-plate cross-section
DOME0, DOME1 = 3, 13        # cap dome
NK0, NK1 = 3, 13            # nozzle collar (outer)
BR0, BR1 = 4, 12            # nozzle bore (= the BER port tanks' cross-section)
# vertical-port bore backs: 1/8 px off the shell faces (2, 14) and off the shrunk tank boxes
# (1.75, 14.25), so nothing is ever coplanar with them
BORE_CEILING_Y, BORE_FLOOR_Y = 1.875, 14.125

RIM_COL = [14, FL0, 15, FL1]  # a flange-texture rim column (cols 1 and 14 are painted alike)
DARK_STRIP = [5, 3, 6, 13]    # a dark 1x10 strip inside the nozzle sheets' bore area

INWARD = {"east": "west", "west": "east"}


def default_uv(fr, to, face):
    if face == "down":
        return [fr[0], 16 - to[2], to[0], 16 - fr[2]]
    if face == "up":
        return [fr[0], fr[2], to[0], to[2]]
    if face == "north":
        return [16 - to[0], 16 - to[1], 16 - fr[0], 16 - fr[1]]
    if face == "south":
        return [fr[0], 16 - to[1], to[0], 16 - fr[1]]
    if face == "west":
        return [fr[2], 16 - to[1], to[2], 16 - fr[1]]
    if face == "east":
        return [16 - to[2], 16 - to[1], 16 - fr[2], 16 - fr[1]]
    raise ValueError(face)


def F(tex, uv="proj", cull=None):
    return {"tex": tex, "uv": uv, "cull": cull}


def box(name, fr, to, faces):
    return {"name": name, "from": list(fr), "to": list(to), "faces": faces}


def span(side, length, offset=0):
    """x range of a part `length` long whose outer face is `offset` in from the east/west block face."""
    if side == "east":
        return 16 - offset - length, 16 - offset
    return offset, offset + length


def shell(x0, x1):
    """Shell body between x0 and x1: four corner bars and a core, leaving a sight slot on each
    long face (front/back y GL0..GL1, top/bottom z GL0..GL1) down to the core."""
    lo, hi = GL0, GL1
    # slot walls (zero-thickness): the green seal's depth, sheet rows 14..16
    mouth_lo, mouth_hi = [x0, 14, x1, 16], [x0, 16, x1, 14]
    return [
        box("shell_lower_front", (x0, SH0, SH0), (x1, lo, lo), {"north": F("side"), "down": F("top")}),
        box("shell_lower_back", (x0, SH0, hi), (x1, lo, SH1), {"south": F("side"), "down": F("top")}),
        box("shell_upper_front", (x0, hi, SH0), (x1, SH1, lo), {"north": F("side"), "up": F("top")}),
        box("shell_upper_back", (x0, hi, hi), (x1, SH1, SH1), {"south": F("side"), "up": F("top")}),
        box("core", (x0, CORE0, CORE0), (x1, CORE1, CORE1), {
            "north": F("side"), "south": F("side"), "up": F("top"), "down": F("top")}),
        box("sill_front_floor", (x0, lo, SH0), (x1, lo, CORE0), {"up": F("side", mouth_lo)}),
        box("sill_front_ceiling", (x0, hi, SH0), (x1, hi, CORE0), {"down": F("side", mouth_hi)}),
        box("sill_back_floor", (x0, lo, CORE1), (x1, lo, SH1), {"up": F("side", mouth_hi)}),
        box("sill_back_ceiling", (x0, hi, CORE1), (x1, hi, SH1), {"down": F("side", mouth_lo)}),
        box("sill_top_front", (x0, CORE1, lo), (x1, SH1, lo), {"south": F("top", mouth_lo)}),
        box("sill_top_back", (x0, CORE1, hi), (x1, SH1, hi), {"north": F("top", mouth_lo)}),
        box("sill_bottom_front", (x0, SH0, lo), (x1, CORE0, lo), {"south": F("top", mouth_hi)}),
        box("sill_bottom_back", (x0, SH0, hi), (x1, CORE0, hi), {"north": F("top", mouth_hi)}),
    ]


def flange(side):
    """One half of a bolted flange pair on a joint side; its joint face is never drawn."""
    x0, x1 = span(side, 2)
    return [box("flange", (x0, FL0, FL0), (x1, FL1, FL1), {
        "north": F("flange"), "south": F("flange"), "up": F("flange"), "down": F("flange"),
        INWARD[side]: F("flange")})]


def cap(side):
    """Blind end of an unconnected segment side: bolted plate + dished dome, 2px deep."""
    px0, px1 = span(side, 1, 1)
    dx0, dx1 = span(side, 1)
    return [
        box("cap_plate", (px0, FL0, FL0), (px1, FL1, FL1), {
            "north": F("flange"), "south": F("flange"), "up": F("flange"), "down": F("flange"),
            INWARD[side]: F("flange"), side: F("cap")}),
        box("cap_dome", (dx0, DOME0, DOME0), (dx1, DOME1, DOME1), {
            "north": F("cap"), "south": F("cap"), "up": F("cap"), "down": F("cap"),
            side: F("cap", cull=side)}),
    ]


def cover(side):
    """Channel-head cover plate, 1px, between the shell and the axial nozzle."""
    x0, x1 = span(side, 1, 2)
    return [box("cover", (x0, FL0, FL0), (x1, FL1, FL1), {
        "north": F("flange", RIM_COL), "south": F("flange", RIM_COL),
        "up": F("flange", RIM_COL), "down": F("flange", RIM_COL),
        INWARD[side]: F("flange"), side: F("cover")})]


def ring_x(prefix, x0, x1, o0, o1, tex):
    """Square ring around the x axis (walls between o0..o1 outer and BR0..BR1 bore), x0..x1 long."""
    d = F(tex, DARK_STRIP)
    return [
        box(prefix + "_top", (x0, BR1, o0), (x1, o1, o1), {
            "up": F(tex), "down": d, "north": F(tex), "south": F(tex), "east": F(tex), "west": F(tex)}),
        box(prefix + "_bottom", (x0, o0, o0), (x1, BR0, o1), {
            "down": F(tex), "up": d, "north": F(tex), "south": F(tex), "east": F(tex), "west": F(tex)}),
        box(prefix + "_north", (x0, BR0, o0), (x1, BR1, BR0), {
            "north": F(tex), "south": d, "east": F(tex), "west": F(tex)}),
        box(prefix + "_south", (x0, BR0, BR1), (x1, BR1, o1), {
            "south": F(tex), "north": d, "east": F(tex), "west": F(tex)}),
    ]


def ring_y(prefix, y0, y1, o0, o1, tex):
    """Square ring around the y axis, y0..y1 tall."""
    d = F(tex, DARK_STRIP)
    return [
        box(prefix + "_north", (o0, y0, o0), (o1, y1, BR0), {
            "north": F(tex), "south": d, "up": F(tex), "down": F(tex), "east": F(tex), "west": F(tex)}),
        box(prefix + "_south", (o0, y0, BR1), (o1, y1, o1), {
            "south": F(tex), "north": d, "up": F(tex), "down": F(tex), "east": F(tex), "west": F(tex)}),
        box(prefix + "_west", (o0, y0, BR0), (BR0, y1, BR1), {
            "west": F(tex), "east": d, "up": F(tex), "down": F(tex)}),
        box(prefix + "_east", (BR1, y0, BR0), (o1, y1, BR1), {
            "east": F(tex), "west": d, "up": F(tex), "down": F(tex)}),
    ]


def drop_faces(elements, faces):
    for el in elements:
        for f in faces:
            el["faces"].pop(f, None)
    return elements


def axial_nozzle(side, tex):
    """Tube-side nozzle on a chain end: a 10x10 collar, 2px long, around the 8x8 bore that the
    BER's side tank fills; it stands on the cover plate, so its inner end is never drawn."""
    x0, x1 = span(side, 2)
    collar = drop_faces(ring_x("nozzle", x0, x1, NK0, NK1, tex), [INWARD[side]])
    for el in collar:
        el["faces"][side]["cull"] = side
    return collar


def bottom_nozzle(tex):
    """Shell-side inlet under START: a 10x10 collar y0..2 around the 8x8 bore (BER bottom tank)."""
    collar = drop_faces(ring_y("port", 0, 2, NK0, NK1, tex), ["up"])
    for el in collar:
        el["faces"]["down"]["cull"] = "down"
    # dark bore back just below the shell's underside, so the shell plate never shows in the hole
    ceiling = box("port_bore_ceiling", (BR0, BORE_CEILING_Y, BR0), (BR1, BORE_CEILING_Y, BR1), {"down": F(tex)})
    return collar + [ceiling]


def top_nozzle(tex):
    """Shell-side outlet on END: a 10x10 collar y14..16 around the 8x8 bore (BER top tank)."""
    collar = drop_faces(ring_y("port", 14, 16, NK0, NK1, tex), ["down"])
    for el in collar:
        el["faces"]["up"]["cull"] = "up"
    floor = box("port_bore_floor", (BR0, BORE_FLOOR_Y, BR0), (BR1, BORE_FLOOR_Y, BR1), {"up": F(tex)})
    return collar + [floor]


def models():
    # START (cold end): joint flange west, cover + tube-side nozzle east, shell-side inlet below.
    start = flange("west") + shell(2, 13) + cover("east") + axial_nozzle("east", "cold") + bottom_nozzle("cold")
    # END (hot end): cover + tube-side nozzle west, joint flange east, shell-side outlet on top.
    end = cover("west") + axial_nozzle("west", "hot") + shell(3, 14) + flange("east") + top_nozzle("hot")
    return {
        "block/heat_exchange_middle": (shell(2, 14), None),
        "block/heat_exchange_flange_left": (flange("west"), None),
        "block/heat_exchange_flange_right": (flange("east"), None),
        "block/heat_exchange_cap_left": (cap("west"), None),
        "block/heat_exchange_cap_right": (cap("east"), None),
        "block/heat_exchange_start": (start, None),
        "block/heat_exchange_end": (end, None),
        # the item is a lone placed exchanger: the shell with a blind cap on both ends
        "item/heat_exchange": (cap("west") + shell(2, 14) + cap("east"), "minecraft:block/block"),
    }


def model_json(elements, parent=None):
    model = {}
    if parent:
        model["parent"] = parent
    model["render_type"] = "minecraft:solid"
    used = sorted({spec["tex"] for el in elements for spec in el["faces"].values()})
    textures = {k: TEXTURES[k] for k in used}
    textures["particle"] = TEXTURES["particle"]
    model["textures"] = textures
    els = []
    for el in elements:
        faces = {}
        for face in ("north", "east", "south", "west", "up", "down"):
            if face not in el["faces"]:
                continue
            s = el["faces"][face]
            uv = default_uv(el["from"], el["to"], face) if s["uv"] == "proj" else s["uv"]
            fj = {"uv": uv, "texture": "#" + s["tex"]}
            if s["cull"]:
                fj["cullface"] = s["cull"]
            faces[face] = fj
        els.append({"name": el["name"], "from": el["from"], "to": el["to"], "faces": faces})
    model["elements"] = els
    return model


def dump_model(model, path):
    """One block per element, one line per face: readable diffs, compact files."""
    lines = ["{"]
    keys = list(model.keys())
    for i, k in enumerate(keys):
        tail = "" if i == len(keys) - 1 else ","
        if k == "elements":
            lines.append('    "elements": [')
            for j, el in enumerate(model["elements"]):
                lines.append("        {")
                lines.append('            "name": %s,' % json.dumps(el["name"]))
                lines.append('            "from": %s, "to": %s,' % (json.dumps(el["from"]), json.dumps(el["to"])))
                lines.append('            "faces": {')
                fk = list(el["faces"].keys())
                for m, f in enumerate(fk):
                    lines.append('                "%s": %s%s' % (f, json.dumps(el["faces"][f]),
                                                              "," if m < len(fk) - 1 else ""))
                lines.append("            }")
                lines.append("        }" + ("," if j < len(model["elements"]) - 1 else ""))
            lines.append("    ]" + tail)
        else:
            lines.append('    "%s": %s%s' % (k, json.dumps(model[k], indent=4).replace("\n", "\n    "), tail))
    lines.append("}")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


# =========================================================================================
# Blockstate
# =========================================================================================

ROT = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def blockstate():
    def apply(model, y):
        a = {"model": NS + ":block/" + model}
        if y:
            a["y"] = y
        return a

    parts = []
    for part in ("start", "end", "middle"):
        for facing, r in ROT:
            parts.append({"apply": apply("heat_exchange_" + part, r), "when": {"facing": facing, "part": part}})
    # MIDDLE segment sides: a flange half where a same-facing exchanger connects, a blind cap where not
    for prop, suffix in (("connected_left", "_left"), ("connected_right", "_right")):
        for connected, model in (("true", "heat_exchange_flange"), ("false", "heat_exchange_cap")):
            for facing, r in ROT:
                parts.append({"apply": apply(model + suffix, r),
                              "when": {"facing": facing, "part": "middle", prop: connected}})
    return {"multipart": parts}


def dump_blockstate(bs, path):
    lines = ["{", '    "multipart": [']
    n = len(bs["multipart"])
    prev = None
    for i, p in enumerate(bs["multipart"]):
        group = p["apply"]["model"]
        if prev is not None and group != prev:
            lines.append("")
        prev = group
        lines.append('        {"apply": %s, "when": %s}%s' % (
            json.dumps(p["apply"]), json.dumps(p["when"]), "," if i < n - 1 else ""))
    lines.append("    ]")
    lines.append("}")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def main():
    out = DEFAULT_OUT
    args = sys.argv[1:]
    if args[:1] == ["--out"] and len(args) == 2:
        out = args[1]
    elif args:
        sys.exit(__doc__)
    tex_dir = os.path.join(out, "textures", "block", "heat_exchange")
    os.makedirs(tex_dir, exist_ok=True)
    for name, painter in TEXTURE_PAINTERS.items():
        write_png(painter(), os.path.join(tex_dir, name + ".png"))
    for name, (elements, parent) in models().items():
        path = os.path.join(out, "models", name + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        dump_model(model_json(elements, parent), path)
    path = os.path.join(out, "blockstates", "heat_exchange.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    dump_blockstate(blockstate(), path)
    print("heat exchanger assets written to", out)


if __name__ == "__main__":
    main()
