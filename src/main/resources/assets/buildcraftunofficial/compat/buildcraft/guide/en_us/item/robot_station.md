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
None of the above happens without a gate on the station's pipe saying so. The station actions are:
<link to="buildcraft:station.provide_items" type="statement"/>
<link to="buildcraft:station.accept_items" type="statement"/>
<link to="buildcraft:station.provide_fluids" type="statement"/>
<link to="buildcraft:station.accept_fluids" type="statement"/>
<link to="buildcraft:station.request_items" type="statement"/>
<link to="buildcraft:station.provide_machine_request" type="statement"/>
<link to="buildcraft:station.drop_in_pipe" type="statement"/>
<link to="buildcraft:station.forbid_robot" type="statement"/>
<link to="buildcraft:station.force_robot" type="statement"/>
Forbid Robot and Force Robot take a robot or board as a parameter and turn away, or reserve the station for, robots of that kind. Request Items asks passing Delivery robots for an item, like a one-slot <link inline="buildcraftunofficial:block/requester"/>. The gate can also read the station's state:
<link to="buildcraft:robot.in.station" type="statement"/>
<link to="buildcraft:robot.linked" type="statement"/>
<link to="buildcraft:robot.reserved" type="statement"/>
<link to="buildcraft:robot.sleep" type="statement"/>
And the orders a home station gives its own robot are the robot actions:
<link to="buildcraft:robot.work_in_area" type="statement"/>
<link to="buildcraft:robot.goto_station" type="statement"/>
<link to="buildcraft:robot.wakeup" type="statement"/>
<link to="buildcraft:robot.work_filter" type="statement"/>
<link to="buildcraft:robot.work_filter_tool" type="statement"/>
<recipes_usages stack="buildcraftunofficial:robot_station"/>
<chapter name="See also"/>
<link to="buildcraftunofficial:concept/robots"/>
