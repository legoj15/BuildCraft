---
navigation:
  title: "Builder Board"
  parent: contents/robots/board.md
  position: 17
  icon: buildcraftunofficial:redstone_board
---

# Builder Board

The site foreman's runner: it keeps the Builder's bins full so the building never stalls for want of a block.

## Behaviour

The robot finds the nearest [Builder](../block/builder.md) inside its zone, within about 110 blocks, that is missing materials. It fetches one missing stack at a time from a station whose gate has Provide Items active, flies to the machine and puts it in. When the Builder has everything, or its storage is full, the robot sleeps. It only serves blueprints; a Builder working from a template needs nothing delivered. Costs 51,200 MJ to program, the most expensive board.

## See also

- [Builder](../block/builder.md)
- [Getting Started with Robots](../concept/robots.md)
