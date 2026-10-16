<lore>
A map table for drawing on the world. Paint the fields the robots may farm, the forest they may cut, the pit they may mine, and hand each painting to a gate.
</lore>
<no_lore>
The Zone Planner shows a 3D map of the surrounding terrain and lets you paint zones onto it with paintbrushes. A painted zone is copied onto a Map Location item, which gates use to tell robots where to work.
</no_lore>
<chapter name="Drawing zones"/>
Open the planner and drag with an empty hand to pan the map, scroll to zoom; zoom all the way out for a survey view of the loaded area. Hold a <link inline="buildcraftunofficial:item/paintbrush"/> and drag across the map to paint that colour's zone, right-drag to erase. The planner keeps sixteen zones, one per colour, and remembers them when you close it. Zones are columns: a painted square covers every height.
<chapter name="Copying zones"/>
Put a <link inline="buildcraftunofficial:map_location" type="item_stack"/> in the output slot together with a paintbrush of the zone's colour and the planner writes that zone onto the map. The input slot does the reverse, pulling a zone off a Map Location into the planner, with a paintbrush beside it choosing which colour to overwrite. A Map Location that holds a zone goes into the parameter slot of a Work in Area action on a Quartz or Diamond gate; the robot then works only inside the painting.
<recipes_usages stack="buildcraftunofficial:zone_planner"/>
<chapter name="See also"/>
<link to="buildcraftunofficial:concept/robots"/>
