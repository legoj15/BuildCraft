---
navigation:
  title: "Filler"
  parent: contents/building/builders.md
  position: 5
  icon: buildcraftunofficial:filler
item_ids:
  - buildcraftunofficial:filler
---

# Filler

<ItemImage id="buildcraftunofficial:filler" scale="2" float="right" />

Sometimes you don't want a structure. A flat floor, a sphere, wide stairs, or a hollowed-out room. The Filler allows for picking a geometric pattern without the need for a template in a defined volume.

## Information

Place the Filler at the corner of a [Land Mark](./marker_volume.md), drag and drop a pattern from the left of the GUI into the Filler's pattern slot and supply it with power and materials. The Filler optionally takes a few parameters (corner offsets, sizes, percentages), and places blocks from its resource inventory into the matching positions inside the marker volume. Fluids already inside the volume are left alone, and the Filler still finishes around them. If a protection or claim mod will not let the Filler place a block in a spot, it skips that spot, carries on with the rest, and asks again about ten seconds later.

The **Excavate** toggle clears the interior of the shape before filling. Excavated blocks will be ejected from the filler (assuming they are mineable by an Iron-tier pick).

The **Invert** toggle augments the pattern by affecting the blocks that *are not* a part of the pattern's shape.

The **Preview** toggle (the eye) draws see-through cubes over every spot the pattern still has to fill, for anyone near the Filler. It hides itself while the Filler is placing or breaking blocks.

The **Control mode** indicator (set by pipe gates) cycles between ON(default state), OFF, and LOOP. A padlock will appear if an adjacent gate is currently setting the pattern settings.

<Row>
<RecipesFor id="buildcraftunofficial:filler" fallbackText="" />
</Row>

**Used to craft:** <ItemLink id="buildcraftunofficial:builder" />
