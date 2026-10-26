---
navigation:
  title: "Mining Well"
  parent: contents/mining_fluids/mining.md
  position: 15
  icon: buildcraftunofficial:mining_well
item_ids:
  - buildcraftunofficial:mining_well
---

# Mining Well

<ItemImage id="buildcraftunofficial:mining_well" scale="2" float="right" />

On a quest to dig deeper, you start to research into some new machines and begin to assemble a machine that can automatically dig for you.

It has one caveat; It can only dig straight down.

## Information

It will mine directly downwards below where it is placed and will automatically output the blocks it breaks into any inventories or pipes placed next to it. Failing to find any pipes or inventories, it will throw the items out into the world.

The harder the block it is trying to break is, the longer it takes to mine.

<Row>
<RecipesFor id="buildcraftunofficial:mining_well" fallbackText="" />
</Row>

**Used to craft:** <ItemLink id="buildcraftunofficial:filler" />

## Machine Mechanics

As with most machines, it requires power. The more power you supply it, the faster it will dig.

If you give it a redstone signal, the Mining Well will stop running.

## Mining Tier

The Mining Well harvests blocks as if it were holding an iron pickaxe. Anything an iron pickaxe can mine drops the same items it would when broken by a player (cobblestone from stone, raw iron from iron ore, diamond from diamond ore, and so on). Experience orbs awarded by these breaks spawn at the Mining Well itself, so you can collect XP at the machine you placed without walking down the shaft.

Blocks that need a higher-tier pickaxe (such as obsidians and netherite related blocks) make the well stop. Should the miner encounter one of these blocks, or a dense liquid (such as lava or oil), the well will halt until the obstruction is cleared.

A Pump placed on top of the Mining Well clears a dense liquid for it: the pump drains the pool the well stopped at, drawing on the well's power once its own runs out, and the well digs on once it is gone. While it sits on the well, the pump's status light glows blue.
