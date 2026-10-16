<lore>
A perch for a robot, bolted onto the side of a pipe. Which pipe you bolt it to decides what the robot can do there.
</lore>
<no_lore>
The Docking Station is a pipe pluggable that robots dock on. The pipe it sits on and the gate on that pipe together decide what a docked robot may do: recharge, take or deliver items and fluids, or merely rest.
</no_lore>
<chapter name="Placing"/>
Right-click any face of a pipe to attach a station there. Then right-click the station with a programmed <link inline="buildcraftunofficial:item/robot"/> to place the robot; that station becomes the robot's home and the robot is <bold>linked</bold> to it. A robot's home station must have two clear blocks in front of it. Sneak and right-click the robot with a wrench to take it back (a robot holding a tool gives up the tool first).
<chapter name="What the pipe decides"/>
On a kinesis (power) pipe the station charges a docked robot from the pipe's power. On a <bold>Wooden item pipe</bold> the station can hand out items from the inventory on the pipe's extraction face, and on a Wooden fluid pipe it can hand out fluid the same way. On any item pipe a station can take cargo from a robot and push it into the pipe. A station on a cobblestone pipe, say, can only accept deliveries.
<chapter name="What the gate decides"/>
Charging needs no gate, but none of the item and fluid transfers above happen without a gate on the station's pipe saying so. The station actions are:
<link to="buildcraftunofficial:action/station_provide_items"/>
<link to="buildcraftunofficial:action/station_accept_items"/>
<link to="buildcraftunofficial:action/station_provide_fluids"/>
<link to="buildcraftunofficial:action/station_accept_fluids"/>
<link to="buildcraftunofficial:action/station_request_items"/>
<link to="buildcraftunofficial:action/station_request_needed_items"/>
<link to="buildcraftunofficial:action/station_forbid_robot"/>
<link to="buildcraftunofficial:action/station_force_robot"/>
Forbid Robot and Force Robot take a kind of robot as a parameter (click the slot with a programmed robot, or with an empty hand to cycle through the boards) and turn away, or reserve the station for, robots of that kind. Request Items asks passing Delivery robots for an item, like a one-slot <link inline="buildcraftunofficial:block/requester"/>. The gate can also read the station's state:
<link to="buildcraftunofficial:trigger/robot_in_station"/>
<link to="buildcraftunofficial:trigger/station_linked"/>
<link to="buildcraftunofficial:trigger/station_reserved"/>
<link to="buildcraftunofficial:trigger/robot_sleep"/>
And the orders a home station gives its own robot are the robot actions:
<link to="buildcraftunofficial:action/robot_work_in_area"/>
<link to="buildcraftunofficial:action/robot_goto_station"/>
<link to="buildcraftunofficial:action/robot_wake_up"/>
<link to="buildcraftunofficial:action/robot_filter"/>
<link to="buildcraftunofficial:action/robot_filter_tool"/>
<recipes_usages stack="buildcraftunofficial:robot_station"/>
<chapter name="See also"/>
<link to="buildcraftunofficial:concept/robots"/>
