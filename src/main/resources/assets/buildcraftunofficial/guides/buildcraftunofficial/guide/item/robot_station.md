---
navigation:
  title: "Docking Station"
  parent: contents/robots/station.md
  position: 37
  icon: buildcraftunofficial:robot_station
item_ids:
  - buildcraftunofficial:robot_station
---

# Docking Station

<ItemImage id="buildcraftunofficial:robot_station" scale="2" float="right" />

A perch for a robot, bolted onto the side of a pipe. Which pipe you bolt it to decides what the robot can do there.

## Placing

Right-click any face of a pipe to attach a station there. Then right-click the station with a programmed [Robot](./robot.md) to place the robot; that station becomes the robot's home and the robot is **linked** to it. A robot's home station must have two clear blocks in front of it. Sneak and right-click the robot with a wrench to take it back (a robot holding a tool gives up the tool first).

## What the pipe decides

On a kinesis (power) pipe the station charges a docked robot from the pipe's power. On a **Wooden item pipe** the station can hand out items from the inventory on the pipe's extraction face, and on a Wooden fluid pipe it can hand out fluid the same way. On any item pipe a station can take cargo from a robot and push it into the pipe. A station on a cobblestone pipe, say, can only accept deliveries.

## What the gate decides

Charging needs no gate, but none of the item and fluid transfers above happen without a gate on the station's pipe saying so. The station actions are:

- [Provide Items](../action/station_provide_items.md)
- [Accept Items](../action/station_accept_items.md)
- [Provide Fluids](../action/station_provide_fluids.md)
- [Accept Fluids](../action/station_accept_fluids.md)
- [Request Items](../action/station_request_items.md)
- [Request Needed Items](../action/station_request_needed_items.md)
- [Forbid Robot](../action/station_forbid_robot.md)
- [Force Robot](../action/station_force_robot.md)

Forbid Robot and Force Robot take a kind of robot as a parameter (click the slot with a programmed robot, or with an empty hand to cycle through the boards) and turn away, or reserve the station for, robots of that kind. Request Items asks passing Delivery robots for an item, like a one-slot [Requester](../block/requester.md). The gate can also read the station's state:

- [Robot In Station](../trigger/robot_in_station.md)
- [Station Linked](../trigger/station_linked.md)
- [Station Reserved](../trigger/station_reserved.md)
- [Robot Sleep](../trigger/robot_sleep.md)

And the orders a home station gives its own robot are the robot actions:

- [Work in Area](../action/robot_work_in_area.md)
- [Goto Station](../action/robot_goto_station.md)
- [Wake Up](../action/robot_wake_up.md)
- [Robot Filter](../action/robot_filter.md)
- [Robot Filter Tool](../action/robot_filter_tool.md)

<Row>
<RecipesFor id="buildcraftunofficial:robot_station" fallbackText="" />
</Row>

## See also

- [Getting Started with Robots](../concept/robots.md)
