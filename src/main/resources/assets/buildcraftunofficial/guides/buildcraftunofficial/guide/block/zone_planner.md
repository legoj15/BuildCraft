---
navigation:
  title: "Zone Planner"
  parent: contents/robots/robot.md
  position: 14
  icon: buildcraftunofficial:zone_planner
item_ids:
  - buildcraftunofficial:zone_planner
---

# Zone Planner

<ItemImage id="buildcraftunofficial:zone_planner" scale="2" float="right" />

A map table for drawing on the world. Paint the fields the robots may farm, the forest they may cut, the pit they may mine, and hand each painting to a gate.

## Drawing zones

Open the planner and drag with an empty hand to pan the map, scroll to zoom; zoom all the way out for a survey view of the loaded area. Hold a [Paintbrush](../item/paintbrush.md) and drag across the map to paint that colour's zone, right-drag to erase. The planner keeps sixteen zones, one per colour, and remembers them when you close it. Zones are columns: a painted square covers every height.

## Copying zones

Put a <ItemLink id="buildcraftunofficial:map_location" /> in the output slot together with a paintbrush of the zone's colour and the planner writes that zone onto the map. The input slot does the reverse, pulling a zone off a Map Location into the planner, with a paintbrush beside it choosing which colour to overwrite. A Map Location that holds a zone goes into the parameter slot of a Work in Area action on a Quartz or Diamond gate; the robot then works only inside the painting.

<Row>
<RecipesFor id="buildcraftunofficial:zone_planner" fallbackText="" />
</Row>

## See also

- [Getting Started with Robots](../concept/robots.md)
