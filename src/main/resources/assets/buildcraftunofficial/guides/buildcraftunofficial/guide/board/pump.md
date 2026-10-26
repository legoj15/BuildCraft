---
navigation:
  title: "Pump Board"
  parent: contents/robots/board.md
  position: 30
  icon: buildcraftunofficial:redstone_board
---

# Pump Board

A pump that comes to the puddle instead of waiting for the puddle to come to it.

## Behaviour

The robot finds a fluid source block in its zone, flies to it and pumps it into its tank, then carries each bucket to a station whose gate has Accept Fluids active. That station must stand inside the work zone, or inside a Load/Unload in Area zone if the home gate sets one; the robot's own home station counts too, even outside the zone. It needs no tool. Costs 3,200 MJ to program.

## See also

- [Tank Board](./tank.md)
- [Getting Started with Robots](../concept/robots.md)
