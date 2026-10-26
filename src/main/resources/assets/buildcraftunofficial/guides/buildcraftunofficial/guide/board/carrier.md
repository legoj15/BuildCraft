---
navigation:
  title: "Carrier Board"
  parent: contents/robots/board.md
  position: 19
  icon: buildcraftunofficial:redstone_board
---

# Carrier Board

A flying hopper. Load here, unload there, repeat forever.

## Behaviour

With empty cargo the robot finds a station whose gate has Provide Items active and loads whatever the station's Wooden item pipe can pull from the inventory behind it; with cargo it finds a station with Accept Items active and unloads into that pipe. The Filter action on the home station's gate narrows what it will carry. It sleeps when it finds neither kind of station. Costs 800 MJ to program.

## See also

- [Tank Board](./tank.md)
- [Getting Started with Robots](../concept/robots.md)
