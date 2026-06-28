###### Changes since 2026.1.1:

- The Zone Planner now has a working interactive 3D map. Open it to see an isometric view of the surrounding terrain: drag with an empty hand to pan, scroll to zoom, and drag a coloured paintbrush across the map to mark (or right-drag to erase) that colour's zone. Zones can be copied to and from a Map Location using the side slots.
- The Map Location is now a survival item (craftable from 8 paper around a yellow dye) and the Zone Planner has a crafting recipe (iron, redstone, gold & diamond gears, and a map). Both were previously creative/dev only.
- Fixed quarries draining server performance (TPS) long after they finish mining. A completed quarry now goes fully idle: it stops re-scanning its frame every tick and releases the chunks it was force-loading, so a forgotten quarry no longer keeps its work area loaded and ticking. The drill rig also no longer rebuilds its collision shape every tick while stationary.
