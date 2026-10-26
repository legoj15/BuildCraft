---
navigation:
  title: "FE Engine"
  parent: contents/power/engine.md
  position: 9
  icon: buildcraftunofficial:engine_rf
item_ids:
  - buildcraftunofficial:engine_rf
---

# FE Engine

<ItemImage id="buildcraftunofficial:engine_rf" scale="2" float="right" />

Perhaps you've come into possession of a form of power incompatible with your machines. This engine will take it, and convert it to MJ.

## Information

The FE Engine actively pulls FE from any block on its sides or back that exposes the Energy capability, and stores up to **10,000 FE** internally before converting it.

By default, it outputs at a rate of **4 MJ/t** through the piston face. Exact FE-to-MJ ratios are governed by the current game configuration.

<Row>
<RecipesFor id="buildcraftunofficial:engine_rf" fallbackText="" />
</Row>

## Upgrades

Open the engine's interface (crouch-click, or click without a wrench/pipe in hand) to access four upgrade slots. Drop gears into the slots to raise the output rate:

- **Iron Gear**: +2 MJ/t
- **Gold Gear**: +3 MJ/t

Each filled slot adds independently, and the engine consumes more FE per tick as the output rises (it becomes faster; it doesn't generate more MJ from the same amount of FE).

## Engine Mechanics

BuildCraft engines have 5 temperature stages, which determines the speed the engine runs at: Blue, Green, Yellow, Red and Black.

FE Engines warm through these stages as they run but cap at Black and **cannot overheat**, so they never need to be cooled.

Engines will always connect to the nearest compatible MJ consumer.

You can use a Wrench to rotate it to change which block it is powering.

FE Engines can be "chained" in a line with up to 5 engines in total, passing MJ through each other to increase output.

As with all engines, it **requires a redstone signal to run.**

Gates can be used to detect the engines temperature stages to help you control them.
