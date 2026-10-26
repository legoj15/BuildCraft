---
navigation:
  title: "Programming Table"
  parent: contents/robots/robot.md
  position: 5
  icon: buildcraftunofficial:programming_table
item_ids:
  - buildcraftunofficial:programming_table
---

# Programming Table

<ItemImage id="buildcraftunofficial:programming_table" scale="2" float="right" />

A laser table with a different job: instead of assembling chipsets it writes ideas onto redstone boards. Pick a job from the grid, feed it power, and out comes a board that knows what to do.

## Using it

Put a blank [Redstone Board](../item/redstone_board.md) in the input slot, pick a job from the grid of boards, and point a powered [Laser](./laser.md) at the table. The progress bar fills with the laser's power; when it reaches the board's cost the input is consumed and the written board drops into the output slot. If the output slot is blocked the table waits. A written board can go back in and be rewritten to a different job, at the new job's full cost.

Boards cost 800, 3,200, 12,800 or 51,200 MJ depending on the job, so a tier of Lasers that is fine for chipsets takes a while on a Builder board. Every board page under Boards names its cost.

<Row>
<RecipesFor id="buildcraftunofficial:programming_table" fallbackText="" />
</Row>

## See also

- [Integration Table](./integration_table.md)
- [Getting Started with Robots](../concept/robots.md)
