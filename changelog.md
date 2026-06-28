###### Changes since 2026.1.1:

- Fixed quarries draining server performance (TPS) long after they finish mining. A completed quarry now goes fully idle: it stops re-scanning its frame every tick and releases the chunks it was force-loading, so a forgotten quarry no longer keeps its work area loaded and ticking. The drill rig also no longer rebuilds its collision shape every tick while stationary.
