<lore>
The courier. It reads the order forms and fills them.
</lore>
<no_lore>
The Delivery board makes a robot fulfil item requests: the orders in a Requester, and Request Items actions on station gates.
</no_lore>
<chapter name="Behaviour"/>
The robot finds the nearest open request, reserves it, loads the requested item at a station whose gate has Provide Items active, flies to the requesting station and hands the items over. If it cannot find the item anywhere it drops that request until its next search. Cargo it can no longer deliver is unloaded at an accepting station, or dropped on the ground as a last resort. Costs 12,800 MJ to program.
<chapter name="See also"/>
<link to="buildcraftunofficial:block/requester"/>
<link to="buildcraftunofficial:action/station_request_items"/>
<link to="buildcraftunofficial:concept/robots"/>
