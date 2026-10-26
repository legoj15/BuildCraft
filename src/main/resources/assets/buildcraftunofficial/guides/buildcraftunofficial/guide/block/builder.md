---
navigation:
  title: "Builder"
  parent: contents/building/builders.md
  position: 3
  icon: buildcraftunofficial:builder
item_ids:
  - buildcraftunofficial:builder
---

# Builder

<ItemImage id="buildcraftunofficial:builder" scale="2" float="right" />

Blueprints and templates lay things out in such a way that even a machine could read them. The Builder is that machine; give it what it wants, and it'll do the building for you.

## Information

Insert your snapshot paper into the input slot in the top middle. The resource inventory is directly below. The builder will accept required items from any side of the block. To the right at the top is what is currently left in the queue to be placed, and at the bottom right are the 4 fluid tanks for builds that require the placement of fluid sources. The first stage of building will start as soon as the builder recieves power. If a protection or claim mod will not let the builder place a block in a spot, the builder skips that spot, carries on with the rest, and asks again about ten seconds later. The build is not finished until it has been able to place it.

## Continuous Building

Place the Builder behind a [Path Mark](./marker_path.md) that connects to 1 or more other path markers to have it build along a path. It will build incrementally, block by block; it does not take into account the size and shape of the snapshot, instead "smearing" it along the path.

<Row>
<RecipesFor id="buildcraftunofficial:builder" fallbackText="" />
</Row>

## Clearing Lasers

If the builder encounters blocks in the same volume as the build, it will remove via a deconstruction robot. Items from this will be deposited into the inventory of the builder at the same efficiency of an iron pickaxe. If the builder's inventory is full or it encounters blocks that iron picks can't mine, the items will not be collected and the builder will continue.

## Fluid Clearing

The fluid-mode button cycles between three behaviours for fluid blocks that fall inside the build area:

- **Skip Fluids** (Barrier icon) ignores existing fluids in a way that source blocks are left untouched and the snapshot is built around them.
- **Replace Fluids** (Bricks icon) places blocks from the snapshot on top of fluids. This can lead to blocks becoming waterlogged.
- **Clear Fluids** (Bucket icon) sweeps every fluid out of the build area before placement. Fluids are collected in the internal tanks. If there is nowhere for them to go, they are discarded. The one exception is fluid the builder can never remove, such as water that keeps flowing back in from a lake or river just outside the build area: it is left where it is, and the build still finishes around it.
