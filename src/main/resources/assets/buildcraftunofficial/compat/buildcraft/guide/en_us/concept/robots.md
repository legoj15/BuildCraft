<lore>
Pipes move things and gates make decisions, but somebody still has to walk out to the forest with an axe. Not any more. A Robot is a small flying worker that lives on your pipe network: give it a job, give it a place to charge, tell it where it may work, and it gets on with it while you do something else.
</lore>
<no_lore>
Robots are autonomous flying workers. A robot's job is set by the Redstone Board it was programmed with; it docks on Docking Stations attached to pipes, recharges from kinesis pipes, and takes its orders from the gate on its home station's pipe.
</no_lore>
<chapter name="How a robot comes to be"/>
Building a working robot takes four steps, each on its own page:
<link to="buildcraftunofficial:item/redstone_board"/>
<link to="buildcraftunofficial:block/programming_table"/>
<link to="buildcraftunofficial:item/robot"/>
<link to="buildcraftunofficial:block/integration_table"/>
First craft a blank Redstone Board and give it a job in the Programming Table, which needs laser power. Then craft a blank Robot and combine it with the programmed board in the Integration Table, which also needs laser power. A blank robot refuses to be placed and only says "Not programmed", so the board step is never optional.
<chapter name="Docking Stations"/>
A programmed robot is placed by right-clicking a <link inline="buildcraftunofficial:item/robot_station"/> on a pipe. That station becomes the robot's <bold>home</bold>: it is linked to it, returns to it to sleep, and reads its orders from the gate on that pipe. Robots use other stations too, to pick up and drop off cargo, to fetch tools, and to recharge. Whether a station allows any of that is decided by the gate on its pipe, so a station with no gate is a parking spot and nothing more.
Recharging works only on a station attached to a kinesis (power) pipe. A robot flies off to recharge when its battery drops below a fifth; if it finds nowhere to charge it keeps working until it is flat, then shuts down where it stands.
<chapter name="Giving orders"/>
Robots have no control screen. Every order is a gate action on a station's pipe, and the gate must actually be firing for the action to count, so the usual trick is a trigger that is always true, such as Redstone Signal Off with no redstone nearby.
<link to="buildcraftunofficial:action/robot_work_in_area"/>
Work in Area is the order most boards need: it names the zone the robot may work in. The zone comes from a <link inline="buildcraftunofficial:map_location" type="item_stack"/> set in the action's parameter slot, and only Quartz and Diamond gates have action parameter slots at all. Draw the zone with a paintbrush in the <link inline="buildcraftunofficial:block/zone_planner"/> and copy it onto the Map Location there, or record a plain box with the Map Location directly.
<link to="buildcraftunofficial:action/station_provide_items"/>
<link to="buildcraftunofficial:action/station_accept_items"/>
Provide Items lets robots take items out of the inventory behind the station, and Accept Items lets them hand cargo in. Taking only works through a <bold>Wooden item pipe</bold> whose extraction face points at the inventory; handing in works through any item pipe. The fluid actions work the same way for tanks, through a Wooden fluid pipe. Boards that need a tool (axes, pickaxes, hoes, swords, shears, shovels) look for a Provide Items station <italic>inside their work zone</italic>; a tool chest outside the zone is invisible to them unless a Load/Unload in Area action points at it.
<chapter name="Keeping it running"/>
A robot with nothing to do, or nothing to do it with, parks on its home station and sleeps for a minute before looking again; the Wake Up action rouses it at once. The Docking Station page lists the remaining station actions (forbidding or forcing robots, requesting items) and the robot triggers a gate can read, such as whether a robot is in the station or asleep.
<hint>
Sneak and right-click a robot with a wrench to take it apart one layer at a time: first anything it wears, then the tool it holds, then the robot itself, which keeps its board and charge.
</hint>
<chapter name="Boards"/>
Every board has a page of its own under Boards. The cheap ones (Picker, Carrier, Tank) move things between stations. The middle tier works a zone: Lumberjack, Miner, Farmer, Planter, Leaf Cutter and Shovelman with a tool from a station, Harvester and Pump with none, and the Butcher with a sword. The expensive ones fight monsters (Knight), deliver on request, drop TNT, run items like a Stripes pipe, or feed a Builder. The Empty board is the blank one, and a robot carrying it cannot be placed.
