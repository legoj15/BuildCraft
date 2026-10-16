<lore>
Stay inside the lines.
</lore>
<no_lore>
Work in Area is a gate action on the home station's pipe. It tells the linked robot the zone it may work in; most boards do nothing without one.
</no_lore>

<chapter name="Information"/>
Put a Map Location in the action's parameter slot. A zone painted in the Zone Planner, a two-corner area, or a path all work; the zone is a column, so height never matters. Only Quartz and Diamond gates have action parameter slots, so lower gates cannot give this order. The robot also looks for tool and cargo stations inside this zone unless a Load/Unload in Area action points elsewhere. The gate must be firing for the action to count; a trigger that is always true (Redstone Signal Off with no redstone nearby) is the usual way to keep it on.

<chapter name="See also"/>
<link to="buildcraftunofficial:concept/robots"/>
<link to="buildcraftunofficial:item/robot_station"/>
