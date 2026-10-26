---
navigation:
  title: "Stirling Engine"
  parent: contents/power/engine.md
  position: 12
  icon: buildcraftunofficial:engine_stone
item_ids:
  - buildcraftunofficial:engine_stone
---

# Stirling Engine

<ItemImage id="buildcraftunofficial:engine_stone" scale="2" float="right" />

After experimenting with what can be done with a basic redstone engine, you discover a way to burn furnace fuels to to actually produce a decent amount of power at a rate of 1MJ/t.

## Information

A Stirling Engines are just as efficient as a regular furnace when it comes to power production.

<Row>
<RecipesFor id="buildcraftunofficial:engine_stone" fallbackText="" />
</Row>

## Engine Mechanics

BuildCraft engines have 5 temperature stages, which determines the speed the engine runs at: Blue, Green, Yellow, Red and Black.

Stirling engines will only turn black if the engines internal power buffer fills up as the power has nowhere to go, thus the engine will overheat and needs to be wrenched to be cooled.

Engines will always connect to the nearest compatible MJ consumer.

You can use a Wrench to rotate it to change which block it is powering.

Stirling engines can be "chained" in a line with up to 3 engines in total.

As with all engines, it **requires a redstone signal to run.**

Gates can be used to detect the engines temperature stages to help you control them.
