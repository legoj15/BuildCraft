---
navigation:
  title: "Delivery Board"
  parent: contents/robots/board.md
  position: 20
  icon: buildcraftunofficial:redstone_board
---

# Delivery Board

The courier. It reads the order forms and fills them.

## Behaviour

The robot finds the nearest open request, reserves it, loads the requested item at a station whose gate has Provide Items active, flies to the requesting station and hands the items over. If it cannot find the item anywhere it drops that request until its next search. Cargo it can no longer deliver is unloaded at an accepting station, or dropped on the ground as a last resort. Costs 12,800 MJ to program.

## See also

- [Requester](../block/requester.md)
- [Request Items](../action/station_request_items.md)
- [Getting Started with Robots](../concept/robots.md)
