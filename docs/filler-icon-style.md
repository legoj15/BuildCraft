# Filler icon style

Spec for every icon in `src/main/resources/assets/buildcraftunofficial/textures/filler/patterns/` and
`.../filler/parameters/` (issue #44). All icons below are drawn to this spec; this file is enough to redraw or add
one without further design decisions. Where a rule and a reference grid disagree, the grid wins.

Exemplars the rest were drawn from: `patterns/sphere_quarter`, `patterns/2d_hexagon`, `patterns/clear`, `parameters/face_north`.
Every sprite is in this style. Horizon's old art was deleted; issue #87 draws one if the pattern comes back.

The gate's pipe-direction actions use the `face_*` icons directly (`BCTransportSprites.ACTION_PIPE_DIRECTION`);
there are no separate `triggers/trigger_dir_*` files, so a face icon change reaches both GUIs.

## Workflow

1. Draw each icon as 16 rows of 16 characters: palette letters below, `.` = transparent.
2. Write it 1:1 into a 16x16 RGBA PNG (PIL `putpixel`, no scaling, no smoothing).
3. Preview: `python scripts/art/filler-icon-sheet.py OUT.png --compare-rev HEAD`. Every icon sits in the real
   18x18 slot at 1x and 6x; changed icons get an old-vs-new block; lint notes print in red under the label.
4. Judge at 1x, in the strips, not in the 6x cells. The strips lay patterns out in the selection popup's rows and
   parameters one family per run; an icon must differ from its neighbours there (see "Popup neighbours").
5. Done when `... --strict` exits 0. Old art fails the colour lint until redrawn.

## Palette

The sheet script reads this block; keep one colour per line.

```palette
K #222222 ink: every silhouette and outline, concave step lines, the rotation wedge edge
L #E6E6E6 light grey: top faces, 2D fills, the selected element of a parameter (axis edges and faces excepted), Frame front beams, the hollow family's checker
M #B3B3B3 mid grey: left (south) faces, curved surface body, Frame back beams, the hollow family's checker
D #6E6E6E dark grey: right (east) faces, curved shadow, hidden edges of glass cubes, unselected centre dots
G #C8C8C8 ghost: dotted outline of space the pattern leaves alone (None, sphere-part ghosts)
a #F58A6E accent light: Clear and Flatten, top face
A #DD4B32 accent mid: Clear and Flatten, left face
B #9C2B1E accent dark: Clear and Flatten, right face
X #FF3352 axis red: the highlighted edges of axis_x (Blender's X)
Y #8BDC00 axis green: the highlighted edges of axis_y (Blender's Y)
Z #2890FF axis blue: the highlighted edges of axis_z (Blender's Z)
N #FF5555 north red: face_north's face (gate tooltips' side colours, ColourUtil.FACE_TO_FORMAT)
S #5555FF south blue: face_south's face
E #FFFF55 east yellow: face_east's face
W #55FF55 west green: face_west's face
T #FFFFFF top white: face_up's face (face_down's face is K: 1.12.2's side colour was black, which tooltips show as DARK_GRAY #555555 and which would read as the block tone D in an icon)
```

- Nothing outside the block. Every pixel is fully opaque or fully transparent.
- L, M and D are the only placed-block tones. G never means a block, only "untouched".
- The accent hue means removal and appears on Clear and Flatten only. A selected element in a parameter is L, never accent
  (the hollow family shows its placed region as the L/M checker instead); the axis and face icons are the exceptions: axis edges take the 3D-editor axis colours X, Y and Z, and a
  face takes the colour gate tooltips give that side (N, S, E, W, T; the bottom is K, not the tooltip's dark grey). X is
  Blender's pink-leaning red, kept apart from the accent's orange-red so it does not read as removal.
- The accent ramp is darker than the grey ramp at every step (luma 167/116/75 against 230/179/110), so Clear and
  Fill still differ in greyscale and for red-blind players.

## Canvas

- 16x16, transparent background. Row and column 0 and 15 stay empty: the drawing area is x, y = 1..14.
- The GUI draws the icon at +1,+1 inside the 18x18 slot from `gui/misc_slots.png` (interior #8B8B8B, dark top-left
  rim, white bottom-right rim). Tones were picked against #8B8B8B: K and L carry the silhouette, M and D sit either
  side of the slot grey, G is visible but quiet.
- Coordinates are (x, y), 0-based, y down. "Row n" means y = n.

## Lines

- Silhouette: 1px K on every filled pixel that has a transparent pixel on any of its four sides. Exceptions:
  Frame (the beams are the drawing) and the ghost drawings (G only). At a concave corner that rule leaves the outline
  joined only diagonally; K the corner pixel as well (the specs name those pixels).
- Iso edges are 2:1: 2px horizontal runs, one row per run. Vertical edges are 1px columns. No 1:1 diagonals in iso art.
- Convex edges inside a solid (cube top/left/right): tone change only, no line.
- Concave edges (the Stairs inner step): 1px K.
- Glass cubes (face and axis parameters): K silhouette, inner edges in D (three for a face icon, all six for an
  axis icon), drawn 2:1 like the silhouette.
- Ghost outlines: G, dotted, never continuous. Drop any ghost pixel that is 4-adjacent to a K pixel of the drawing.
- 2D shapes: K outline, L fill, no shading.
- Curves use round runs: on a 14px circle the outline runs from the top flat round to the side flat are
  4-2-1-1-2-4 (DISC14). A 6-1-1-1-6 rhythm is an octagon (OCTAGON); a Bresenham circle at 14px comes out as that
  octagon, so never use one for Circle.

## Projection

- 3D patterns (Fill, Clear, None, Box, Frame, Pyramid, Stairs, the sphere family): 2:1 pixel isometric from the
  south-east, above. World +X (east) goes screen down-right, +Z (south) down-left, +Y up. Visible faces:
  top = UP = L, left = SOUTH = M, right = EAST = D. North (back-right), west (back-left) and down are hidden.
- 2D patterns (`2d_*`): flat, top-down, north up, east right. The Axis parameter chooses the plane, so the shape
  itself carries no iso cue; the projection alone says "2D".
- Parameters: top-down (north up) for arrows and the centre grid; the iso glass cube for faces and axes; a clock
  face for rotation; side elevation for the Y-direction pair.
- New solid, if ever needed: a point (X, Y, Z) with each axis in 0..7 cube units lands at pixel-edge coordinates
  sx = 8 + X - Z, sy = 1 + X/2 + Z/2 + (7 - Y). Fill a pixel when its centre (x + 0.5, y + 0.5) lies inside the
  projected face, then apply the K silhouette rule. CUBE is exactly this cube.

## Templates

Named grids. The fence word `grid` plus a name makes them extractable (`` ```grid NAME ``).

CUBE: Fill, and the base of Clear, Box and None. Apex pair x = 7,8 on row 1; side vertices at x = 1 and x = 14,
rows 4-5; front vertex pair rows 7-8; 7-row sides; bottom pair on row 14.

```grid CUBE
................
.......KK.......
.....KKLLKK.....
...KKLLLLLLKK...
.KKLLLLLLLLLLKK.
.KMLLLLLLLLLLDK.
.KMMMLLLLLLDDDK.
.KMMMMMLLDDDDDK.
.KMMMMMMDDDDDDK.
.KMMMMMMDDDDDDK.
.KMMMMMMDDDDDDK.
.KKMMMMMDDDDDKK.
...KKMMMDDDKK...
.....KKMDKK.....
.......KK.......
................
```

OUT and INS: the glass cube split into its six faces. OUT is the cube seen from outside (the three near faces meet
at the front corner), INS from inside (the three far faces meet at the hidden back corner). Region tags:
OUT `u` up, `s` south, `e` east; INS `f` floor (down), `w` west, `n` north; `+` inner edge; `K` silhouette.
Tags are lowercase on purpose and never survive into a finished icon.

```grid OUT
................
.......KK.......
.....KKuuKK.....
...KKuuuuuuKK...
.KKuuuuuuuuuuKK.
.Ks++uuuuuu++eK.
.Ksss++uu++eeeK.
.Ksssss++eeeeeK.
.Ksssss++eeeeeK.
.Ksssss++eeeeeK.
.Ksssss++eeeeeK.
.KKssss++eeeeKK.
...KKss++eeKK...
.....KK++KK.....
.......KK.......
................
```

```grid INS
................
.......KK.......
.....KK++KK.....
...KKww++nnKK...
.KKwwww++nnnnKK.
.Kwwwww++nnnnnK.
.Kwwwww++nnnnnK.
.Kwwwww++nnnnnK.
.Kwwwww++nnnnnK.
.Kwww++ff++nnnK.
.Kw++ffffff++nK.
.KKffffffffffKK.
...KKffffffKK...
.....KKffKK.....
.......KK.......
................
```

BALL: Sphere, and the base of every sphere part. Highlight L upper-left, body M, shadow D lower-right.
Ghost ring (G dots for the sphere parts): (6,1) (4,2) (2,4) (1,6) (1,8) (2,10) (3,12) (5,13) (7,14) and their
mirrors (15 - x, y).

```grid BALL
................
......KKKK......
....KKLLLMKK....
...KLLLLLMMMK...
..KLLLLLMMMMMK..
..KLLLLMMMMMMK..
.KLLLLMMMMMMMDK.
.KLLLMMMMMMMMDK.
.KLMMMMMMMMMMDK.
.KMMMMMMMMMMDDK.
..KMMMMMMMMDDK..
..KMMMMMMMDDDK..
...KMMMMMDDDK...
....KKDDDDKK....
......KKKK......
................
```

DISC14: Circle, the semicircle, and the rotation clock face (its K ring).

```grid DISC14
................
......KKKK......
....KKLLLLKK....
...KLLLLLLLLK...
..KLLLLLLLLLLK..
..KLLLLLLLLLLK..
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
..KLLLLLLLLLLK..
..KLLLLLLLLLLK..
...KLLLLLLLLK...
....KKLLLLKK....
......KKKK......
................
```

RING12: the Hollow-parameter family's shape, the pre-redraw icons' ring (2px K, rows 2-13). Its L cells are the
inside; every family icon draws only the K ring and fills the region it places with the L/M checker
(L where x + y is even, M where odd), echoing the old icons' dither.

```grid RING12
................
................
......KKKK......
....KKKKKKKK....
...KKKKLLKKKK...
...KKLLLLLLKK...
..KKKLLLLLLKKK..
..KKLLLLLLLLKK..
..KKLLLLLLLLKK..
..KKKLLLLLLKKK..
...KKLLLLLLKK...
...KKKKLLKKKK...
....KKKKKKKK....
......KKKK......
................
................
```

## Families

Fill / Clear / None / Flatten: one form, four meanings. Fill is CUBE; Clear is CUBE with L->a, M->A, D->B (removal); None is
CUBE's silhouette as G dashes and nothing else (nothing happens); Flatten is Clear with its bottom slab left grey
(cleared above a floor), the one icon besides Clear that uses the accent.

Sphere parts are drawn in place, at BALL's size, never rescaled to fill the canvas, so all four sphere icons read as
fractions of one ball:
1. Take the part's screen footprint (the projection of the volume it keeps).
2. Copy BALL's pixels inside the footprint, then re-apply the K silhouette rule (cut edges become K too).
3. Add each ghost-ring dot whose pixel is still transparent and not 4-adjacent to a K pixel.

The footprints (half and eighth are given as rows below, the quarter is the exemplar):
- Hemisphere keeps Y >= centre: the dome. Matches the pattern default (Facing Down).
- Quarter-sphere keeps the south and east halves: a vertical wedge pointing at the viewer, an egg. This is NOT the
  pattern default (top-east quarter); the default reads as a lopsided copy of Hemisphere, its popup neighbour.
- Eighth-Sphere keeps up + south + east: the top of the egg, a bell. Matches the pattern default.

2D shapes are drawn filled: the fill is the shape's identity. Hollow versus filled is the Hollow parameter's job
(the 2D default is Hollow). Arc is the exception: it is drawn as its curve, which is what it builds by default and
keeps it apart from `rotation_1`. Orientation follows the generators with default parameters (Axis Y, no
rotation; the icon's top is north there, and up on Axis X and Z, where the shape stands): hexagon points west and east, pentagon point north and flat side south, triangle apex north, semicircle
flat side south, arc centred on the south-east corner and bulging north-west.

Parameters: the selected element is L; the frame or structure around it is K; secondary hints are D.
- Face: glass cube, the named face is the only filled region, in its side colour, plus the side's letter in K
  (N, S, E, W, and T/B after BuildCraft's own Top and Bottom) in the empty half of the cube.
- Axis: glass cube, the four edges parallel to the axis are in that axis's colour (X, Y or Z), not L.
- XZ direction: a top-down arrow.
- Centre: a 3x3 grid, the chosen cell solid.
- Rotation: a clock face, L wedge clockwise from 12 o'clock, one quarter per step, its leading edge capped by a K arrowhead pointing clockwise.
- Hollow family: one ring (RING12) in every icon; the L/M checker shows where blocks go: nowhere, inside, or
  outside to the box's edge (x, y = 1..14). The checker is this family's alone.
- Y direction: a side-elevation staircase; ascending the right way up, descending upside down.

No letters except the face icons' side letters. Every other meaning is carried by shape, position or tone; the tooltip names it.

## Patterns

| File | In game | Status | Drawing spec |
|---|---|---|---|
| `2d_arc` | Arc | draw | Grid ARC: a 1px L quarter-circle curve, K on both sides, radius about 10 around the SE corner, ends at (3,12) and (12,3). |
| `2d_circle` | Circle | draw | Grid DISC14. |
| `2d_hexagon` | Hexagon | exemplar | Regular flat-top hexagon 14 wide x 12 tall: K flats x = 4..11 on rows 2 and 13, points at x = 1 and x = 14 on rows 7-8, sides step 1px per 2 rows. |
| `2d_octagon` | Octagon | draw | Grid OCTAGON: 6px flats (x = 5..10 on rows 1 and 14, rows 5..10 at x = 1 and 14), 45-degree corners of single steps. |
| `2d_pentagon` | Pentagon | draw | Grid PENTAGON, copied from the generator's 14x14 output: K point pair x = 7,8 on row 1, widest x = 1..14 on rows 6-8, K flat x = 3..12 on row 14. |
| `2d_semi_circle` | Semi-Circle | draw | DISC14 rows 1-8 moved down to rows 3-10, then row 11 = K across x = 1..14 (flat side south). |
| `2d_square` | Square | draw | K ring on x, y = 2..13 (12x12), L inside. |
| `2d_triangle` | Triangle | draw | Apex K pair x = 7,8 on rows 1-2; rows 3-12 in pairs with the left K edge at x = 6, 5, 4, 3, 2 (right edge mirrored) and L between; rows 13-14 = K across x = 1..14. |
| `box` | Box | draw | CUBE with rows 3-7 replaced by grid BOX_TOP: open top showing the inside (west wall inside D, north wall inside M). The real pattern is a closed shell; closed, it would be Fill. |
| `clear` | Clear | exemplar | CUBE with L->a, M->A, D->B. |
| `fill` | Fill | draw | Grid CUBE. |
| `flatten` | Flatten | draw | Clear's CUBE with the bottom 3 of 7 cube units of the side faces left grey (M, D): the red/grey seam (topmost grey pixel per column) is row 9 at x = 2 and 13, row 10 at x = 3,4,11,12, row 11 at x = 5,6,9,10, row 12 at x = 7,8; everything below it inside the silhouette is grey, down to row 13 at x = 7,8 (columns 1 and 14 stay K). Red volume cleared over a grey floor. |
| `frame` | Frame | draw | Wireframe, no K, no fill: OUT's `K` and `+` pixels become L (silhouette and front corner); INS's `+` pixels become M where not already L (hidden back corner); everything else transparent. |
| `none` | None | draw | Grid NONE: CUBE's silhouette as G dashes only. |
| `pyramid` | Pyramid | draw | Grid PYRAMID: smooth square pyramid on CUBE's footprint, apex pair x = 7,8 on row 2, south face M, east face D, split between x = 7 and 8. The pattern builds 1-block steps; at 14px steps are noise. |
| `sphere` | Sphere | draw | Grid BALL, no ghost dots. |
| `sphere_eighth` | Eighth-Sphere | draw | Rows 0-8 of the `sphere_quarter` exemplar (its ghost dots included), then rows 9-11 = `...KKMMMMMMKK...`, `.....KKMMKK.....`, `.......KK.......`, rows 12-15 empty; then the ghost-ring dots by the rule. |
| `sphere_half` | Hemisphere | draw | BALL rows 0-8, then rows 9-11 = `.KKMMMMMMMMMDKK.`, `...KKKMMMMKKK...`, `......KKKK......`, rows 12-15 empty; then the ghost-ring dots by the rule. |
| `sphere_quarter` | Quarter-sphere | exemplar | South+east vertical wedge of BALL in place, ghost ring around it. |
| `stairs` | Stairs | draw | Grid STAIRS: one step, two levels, like the vanilla stairs item; tall back half, low front slab, rising north (away from the viewer); K on the concave edge where the low tread meets the riser. The pattern default rises east, toward the viewer, which hides every tread. |

## Parameters

| File | In game | Status | Drawing spec |
|---|---|---|---|
| `arrow_up` | North (XZ direction) | draw | Apex K pair x = 7,8 on row 1; the head widens 1px per side per row, K edges reaching x = 2 and 13 on row 6, L inside; row 7 = K x = 2..5 and 10..13 with L x = 6..9; rows 8-13 = K x = 5, L x = 6..9, K x = 10; row 14 = K x = 5..10. |
| `arrow_down` | South | draw | `arrow_up` mirrored top to bottom (y -> 15 - y). |
| `arrow_left` | West | draw | `arrow_up` transposed (x <-> y). |
| `arrow_right` | East | draw | `arrow_left` mirrored left to right (x -> 15 - x). |
| `axis_x` | X Axis | draw | Glass cube (OUT with region tags transparent and `+` -> D, plus INS `+` -> D), then X on the four east-west edges: 2px pairs stepping +2 x per row, four pairs each, starting at (7,1), (1,4), (7,8) and (1,11). |
| `axis_y` | Y Axis | draw | Same glass cube, then Y on x = 1 and x = 14 for rows 4-11 and on x = 7,8 for rows 1-14. |
| `axis_z` | Z Axis | draw | `axis_x` mirrored left to right, its X pixels in Z. |
| `center_0` | North-west (`center_i` = enum ordinal, reading order) | draw | K square ring on x, y = 1..14, no fill. Cells are 4x4 with origins x, y in {2, 6, 10}; cell i sits at column i % 3, row i / 3. Cell i is solid L; every other cell gets a 2x2 D dot at its origin + (1,1). Here cell 0, top left. |
| `center_1` | North | draw | As `center_0`, cell 1 lit (top middle). |
| `center_2` | North-east | draw | As `center_0`, cell 2 lit (top right). |
| `center_3` | West | draw | As `center_0`, cell 3 lit (middle left). |
| `center_4` | Centre (the default) | draw | As `center_0`, cell 4 lit (middle). |
| `center_5` | East | draw | As `center_0`, cell 5 lit (middle right). |
| `center_6` | South-west | draw | As `center_0`, cell 6 lit (bottom left). |
| `center_7` | South | draw | As `center_0`, cell 7 lit (bottom middle). |
| `center_8` | South-east | draw | As `center_0`, cell 8 lit (bottom right). |
| `face_down` | Down | draw | INS: K stays, `+` -> D, `f` -> K, other tags transparent. Letter B in K, 3x5 rows `XX. X.X XX. X.X XX.`, top left at (3,4). |
| `face_east` | East | draw | OUT: K stays, `+` -> D, `e` -> E, other tags transparent. Letter E in K, 3x5 rows `XXX X.. XX. X.. XXX`, top left at (3,7). |
| `face_north` | North | exemplar | INS with `n` -> N. Letter N in K, 3x5 rows `XX. X.X X.X X.X X.X`, top left at (3,4). |
| `face_south` | South | draw | OUT with `s` -> S. Letter S in K, 3x5 rows `XXX X.. XXX ..X XXX`, top left at (11,6). |
| `face_up` | Up | draw | OUT with `u` -> T. Letter T in K, 3x5 rows `XXX .X. .X. .X. .X.`, top left at (3,7). |
| `face_west` | West | draw | INS with `w` -> W. Letter W in K, 3x5 rows `X.X X.X XXX XXX X.X`, top left at (10,4). |
| `filled_inner` | Filled | draw | RING12's K ring, its L cells as the L/M checker. |
| `filled_outer` | Surrounded | draw | RING12's K ring; every other cell on x, y = 1..14 outside it as the L/M checker, its inside transparent. |
| `hollow` | Hollow | draw | RING12's K ring only. |
| `rotation_0` | No rotation | hand-drawn | DISC14's K ring, interior transparent; an L-cored hand at x = 7,8 down to row 7 under a K arrowhead at 12 o'clock. The PNG is the reference. |
| `rotation_1` | 90 degree rotation | hand-drawn | DISC14's K ring; L wedge over the first quarter clockwise from 12 o'clock, K edges at 12 and 3 o'clock, K arrowhead at the 3 o'clock edge pointing clockwise. The PNG is the reference. |
| `rotation_2` | 180 degree rotation | hand-drawn | As `rotation_1` with the wedge over half the face and the arrowhead at 6 o'clock. |
| `rotation_3` | 270 degree rotation | hand-drawn | As `rotation_1` with the wedge over three quarters and the arrowhead at 9 o'clock. |
| `stairs_ascend` | Up (Y direction; Pyramid and Stairs) | draw | Side elevation: L columns x = 2..5 (rows 8-13), x = 6..9 (rows 5-13), x = 10..13 (rows 2-13), so three 4px steps rise to the right on a flat base; K on the silhouette plus the two concave corners (6,8) and (10,5). |
| `stairs_descend` | Down | draw | `stairs_ascend` mirrored top to bottom: the inverted pyramid, stairs hung from the top. |

## Popup neighbours

Patterns popup, row by row: None, Box, Clear, Fill / Frame, Pyramid, Sphere, Eighth-Sphere / Hemisphere,
Quarter-sphere, Stairs, Flatten / Arc, Circle, Hexagon, Octagon / Pentagon, Semi-Circle, Square, Triangle (the pattern
groups in `ContainerFiller`; the sheet script copies them as `PATTERN_POPUP`, so change both together). Pairs that
need the most care: Circle-Octagon, Hemisphere-Quarter-sphere, Sphere-Eighth-Sphere, Box-Fill, None-Frame.
A parameter popup shows one type at a time (all six faces, all nine centres, and so on), so those icons only need
to differ within their family.

## Reference grids

Pixels that a one-line rule cannot pin down. Exemplars are repeated for the families built on them.

```grid sphere_quarter
................
......G..G......
....G..KK..G....
......KLLK......
..G..KLLMMK..G..
....KLLMMMMK....
.G..KLMMMMMK..G.
....KMMMMMMK....
.G.KMMMMMMMMK.G.
...KMMMMMMMMK...
...KMMMMMMMDK...
...KMMMMMMDDK...
...KMMMMMDDDK...
....KKDDDDKK....
......KKKK......
................
```

```grid 2d_hexagon
................
................
....KKKKKKKK....
...KLLLLLLLLK...
...KLLLLLLLLK...
..KLLLLLLLLLLK..
..KLLLLLLLLLLK..
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
..KLLLLLLLLLLK..
..KLLLLLLLLLLK..
...KLLLLLLLLK...
...KLLLLLLLLK...
....KKKKKKKK....
................
................
```

```grid NONE
................
.......GG.......
.....G....G.....
...GG......GG...
.G............G.
................
.G............G.
.G............G.
................
.G............G.
.G............G.
................
...GG......GG...
.....G....G.....
.......GG.......
................
```

BOX_TOP replaces CUBE rows 3-7 (five rows).

```grid BOX_TOP
...KKLLKKLLKK...
.KKLLKKDDKKLLKK.
.KMLKDDDDMMKLDK.
.KMMMKKDDMKKDDK.
.KMMMMMKKKDDDDK.
```

```grid PYRAMID
................
................
.......KK.......
......KMDK......
......KMDK......
.....KMMDDK.....
.....KMMDDK.....
....KMMMDDDK....
...KMMMMDDDDK...
...KMMMMDDDDK...
..KMMMMMDDDDDK..
.KKMMMMMDDDDDKK.
...KKMMMDDDKK...
.....KKMDKK.....
.......KK.......
................
```

```grid STAIRS
................
.......KK.......
.....KKLLKK.....
.....KMLLLLKK...
.....KMMMLLLLKK.
.....KMMMMMLLDK.
.....KMMMMMMDDK.
...KKKKMMMMMDDK.
.KKLLLLKKMMMDDK.
.KMLLLLLLKKMDDK.
.KMMMLLLLLLDDDK.
.KKMMMMLLDDDDKK.
...KKMMMDDDKK...
.....KKMDKK.....
.......KK.......
................
```

```grid ARC
................
................
..........KKKK..
........KKLLLK..
......KKLLLKKK..
.....KLLLKK.....
....KLLKK.......
...KLLK.........
...KLK..........
..KLLK..........
..KLK...........
..KLK...........
..KLK...........
..KKK...........
................
................
```

```grid PENTAGON
................
.......KK.......
......KLLK......
.....KLLLLK.....
....KLLLLLLK....
...KLLLLLLLLK...
.KKLLLLLLLLLLKK.
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
..KLLLLLLLLLLK..
..KLLLLLLLLLLK..
..KLLLLLLLLLLK..
...KLLLLLLLLK...
...KLLLLLLLLK...
...KKKKKKKKKK...
................
```

```grid OCTAGON
................
.....KKKKKK.....
....KLLLLLLK....
...KLLLLLLLLK...
..KLLLLLLLLLLK..
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
.KLLLLLLLLLLLLK.
..KLLLLLLLLLLK..
...KLLLLLLLLK...
....KLLLLLLK....
.....KKKKKK.....
................
```
