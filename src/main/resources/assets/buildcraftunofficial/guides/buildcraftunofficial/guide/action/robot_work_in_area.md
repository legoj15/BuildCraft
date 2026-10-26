---
navigation:
  title: "Work in Area"
  parent: contents/robots/robot.md
  position: 13
---

# Work in Area

Stay inside the lines.

## Information

Put a Map Location in the action's parameter slot. A zone painted in the Zone Planner, a two-corner area, or a path all work; the zone is a column, so height never matters. Only Quartz and Diamond gates have action parameter slots, so lower gates cannot give this order. The robot also looks for tool and cargo stations inside this zone unless a Load/Unload in Area action points elsewhere; it can always unload at its own home station too. The gate must be firing for the action to count; a trigger that is always true (Redstone Signal Off with no redstone nearby) is the usual way to keep it on.

## See also

- [Getting Started with Robots](../concept/robots.md)
- [Docking Station](../item/robot_station.md)
