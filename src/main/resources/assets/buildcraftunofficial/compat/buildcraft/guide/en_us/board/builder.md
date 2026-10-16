<lore>
The site foreman's runner: it keeps the Builder's bins full so the building never stalls for want of a block.
</lore>
<no_lore>
The Builder board makes a robot supply a Builder machine with the materials its blueprint still needs.
</no_lore>
<chapter name="Behaviour"/>
The robot finds the nearest <link inline="buildcraftunofficial:block/builder"/> inside its zone, within about 110 blocks, that is missing materials. It fetches one missing stack at a time from a station whose gate has Provide Items active, flies to the machine and puts it in. When the Builder has everything, or its storage is full, the robot sleeps. It only serves blueprints; a Builder working from a template needs nothing delivered. Costs 51,200 MJ to program, the most expensive board.
<chapter name="See also"/>
<link to="buildcraftunofficial:block/builder"/>
<link to="buildcraftunofficial:concept/robots"/>
