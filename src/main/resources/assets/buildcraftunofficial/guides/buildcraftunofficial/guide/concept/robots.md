---
navigation:
  title: "Getting Started with Robots"
  parent: contents/robots/robot.md
  position: 2
item_ids:
  - buildcraftunofficial:map_location
---

# Getting Started with Robots

Pipes move things and gates make decisions, but somebody still has to walk out to the forest with an axe... until now. These flying robots are controlled and powered entirely by the pipe network and can get various jobs done.

## How a robot comes to be

Building a working robot takes four steps, each on its own page:

- [Redstone Board](../item/redstone_board.md)
- [Programming Table](../block/programming_table.md)
- [Robot](../item/robot.md)
- [Integration Table](../block/integration_table.md)

First craft a blank Redstone Board and give it a job in the Programming Table, which needs laser power. Then craft a blank Robot and combine it with the programmed board in the Integration Table, which also needs laser power. A blank robot refuses to be placed and only says "Not programmed", so the board step is never optional.

## Docking Stations

A programmed robot is placed by right-clicking a [Docking Station](../item/robot_station.md) on a pipe. That station becomes the robot's **home**: it is linked to it, returns to it to sleep, and reads its orders from the gate on that pipe. Robots use other stations too, to pick up and drop off cargo, to fetch tools, and to recharge. Whether a station hands out or takes in cargo is decided by the gate on its pipe, so a station with no gate is only a parking spot and, on a kinesis pipe, a charger.

Recharging needs no gate: any station attached to a kinesis (power) pipe charges whatever docks on it. A robot flies off to recharge when its battery drops below a fifth; if it finds nowhere to charge it keeps working until it is flat, then shuts down where it stands.

## Giving orders

Robots have no control screen. Every order is a gate action on a station's pipe, and the gate must actually be firing for the action to count, so the usual trick is a trigger that is always true, such as Redstone Signal Off with no redstone nearby.

- [Work in Area](../action/robot_work_in_area.md)

Work in Area is the order most boards need: it names the zone the robot may work in. The zone comes from a <ItemLink id="buildcraftunofficial:map_location" /> set in the action's parameter slot, and only Quartz and Diamond gates have action parameter slots at all. Draw the zone with a paintbrush in the [Zone Planner](../block/zone_planner.md) and copy it onto the Map Location there, or record a plain box with the Map Location directly.

- [Provide Items](../action/station_provide_items.md)
- [Accept Items](../action/station_accept_items.md)

Provide Items lets robots take items out of the inventory behind the station, and Accept Items lets them hand cargo in. Taking only works through a **Wooden item pipe** whose extraction face points at the inventory; handing in works through any item pipe. The fluid actions work the same way for tanks, through a Wooden fluid pipe. Boards that need a tool (axes, pickaxes, hoes, swords, shears, shovels) look for a Provide Items station *inside their work zone*; a tool chest outside the zone is invisible to them unless a Load/Unload in Area action points at it.

## Keeping it running

A robot with nothing to do, or nothing to do it with, parks on its home station and sleeps for a minute before looking again; the Wake Up action rouses it at once. The Docking Station page lists the remaining station actions (forbidding or forcing robots, requesting items) and the robot triggers a gate can read, such as whether a robot is in the station or asleep.

## Boards

Every board has a page of its own under Boards. The cheap ones (Picker, Carrier, Tank) move things between stations. The middle tier works a zone: Lumberjack, Miner, Farmer, Planter, Leaf Cutter and Shovelman with a tool from a station, Harvester and Pump with none, and the Butcher with a sword. The expensive ones fight monsters (Knight), deliver on request, drop TNT, run items like a Stripes pipe, or feed a Builder. The Empty board is the blank one, and a robot carrying it cannot be placed.
