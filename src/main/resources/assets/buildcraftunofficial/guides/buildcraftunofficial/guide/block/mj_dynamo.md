---
navigation:
  title: "MJ Dynamo"
  parent: contents/power/engine.md
  position: 10
  icon: buildcraftunofficial:mj_dynamo
item_ids:
  - buildcraftunofficial:mj_dynamo
---

# MJ Dynamo

<ItemImage id="buildcraftunofficial:mj_dynamo" scale="2" float="right" />

In the scenario where you've come across machines or contraptions that don't accept MJ, this "engine" will convert power to a format more familiar to them.

## Information

The MJ Dynamo accepts MJ on any non-output side and stores up to **1,000 MJ** in its internal buffer, converting it to FE on the fly.

It generates a base **4 MJ/t worth of FE** through the piston face. Exact FE-to-MJ ratios are governed by the current game configuration.

<Row>
<RecipesFor id="buildcraftunofficial:mj_dynamo" fallbackText="" />
</Row>

## Upgrades

Interact with the dynamo to see its interface to access four upgrade slots. Drop gears into the slots to raise the output rate:

- **Iron Gear**: +2 MJ/t worth of FE
- **Gold Gear**: +3 MJ/t worth of FE

Each filled slot adds independently, and the dynamo consumes more MJ per tick as the output rises (it becomes faster; it doesn't generate more FE from the same amount of MJ).

## Engine Mechanics

BuildCraft engines have 5 temperature stages, which determines the speed the engine runs at: Blue, Green, Yellow, Red and Black.

MJ Dynamos warm through these stages as they run but cap at Black and **cannot overheat**, so they never need to be cooled.

The dynamo will always push FE to the receiver on its piston face.

You can use a Wrench to rotate it to change which block it is powering.

MJ Dynamos can be "chained" in a line with up to 4 dynamos in total additively to improve throughput.

As with all engines, it **requires a redstone signal to run.**

Gates can be used to detect the dynamo's temperature stages to help you control them.
