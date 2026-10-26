---
navigation:
  title: "Quarry"
  parent: contents/mining_fluids/mining.md
  position: 16
  icon: buildcraftunofficial:quarry
item_ids:
  - buildcraftunofficial:quarry
---

# Quarry

<ItemImage id="buildcraftunofficial:quarry" scale="2" float="right" />

A [Mining Well](./mining_well.md) drills a single column. A Quarry chews entire chunks out of the world. With the right power supply and a large enough [Land Mark](./marker_volume.md) frame, it strip-mines down to bedrock all by itself.

## Information

Surround the area you want mined with Land Marks (up to 64×64 by default), place the Quarry next to one of the marks, and supply it with power. The Quarry builds a frame around the area first, then a head travels inside that frame to mine each block in turn. Output flows into any adjacent inventory or pipe; if there's nowhere to put a drop, the Quarry will eject the item into the world.

The Quarry continues until it reaches bedrock or runs out of unmineable blocks. A red <Color color="#FF5555">LED</Color> on its sides indicates it has work but no power; a green <Color color="#55FF55">LED</Color> indicates it is actively mining; both <Color color="#FF5555">LE</Color><Color color="#55FF55">Ds</Color> together mean it's idle or finished.

<Row>
<RecipesFor id="buildcraftunofficial:quarry" fallbackText="" />
</Row>

## Mining Tier

The Quarry's head harvests blocks as if wielding a diamond pickaxe. It will only skip mining if it encounters an impassible fluid (such as lava or oil), or a block that cannot be successfully mined by a diamond pick.

Experience orbs from breaks (redstone ore, lapis, diamond, coal, etc.) spawn at the Quarry block itself, not in the pit, so you can collect XP at the machine without rappelling down to bedrock.

## Power

As with the Mining Well, more power means faster mining. Redstone signal pauses the Quarry without resetting its progress.
