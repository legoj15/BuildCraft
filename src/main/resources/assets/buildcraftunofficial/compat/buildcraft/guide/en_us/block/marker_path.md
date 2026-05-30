<lore>
A Land Mark fences off a region; a Path Mark draws a line through it. Lay down a trail of them, link them in order, and you have told your machines not just where to work, but which way to walk.
</lore>
<no_lore>
Path Marks are linked together with a Marker Connector into an ordered, directional route. The usual use is to feed that route to a Builder, which works its blueprint or template along the whole line.
</no_lore>
<chapter name="Information"/>
Place two or more Path Marks, then take a <link inline="buildcraftunofficial:item/marker_connector"/> in hand. While the connector is held, a faint preview laser is drawn between every pair of marks that are close enough to link. Aim along one of those previews and click to join the two marks; the beam turns solid to show the route, and clicking onward from either end extends the chain mark by mark.
Unlike a <link inline="buildcraftunofficial:block/marker_volume"/>, a Path Mark is not restricted to a single world axis — a route may turn and climb freely, so long as each hop stays within the marker distance limit (64 blocks by default, set by <bold>Marker Max Distance</bold> in the config).
A path has a direction: a definite first mark and last mark. Right-click any mark in the chain with an <bold>empty hand</bold> to reverse the whole route. Linking the last mark back onto the first closes the path into a repeating loop.
Breaking a Path Mark takes it out of the chain — remove one from the middle and the route splits in two; remove an end and the route simply shortens.
<recipes_usages stack="buildcraftunofficial:marker_path"/>
<chapter name="Putting a path to use"/>
The usual consumer is the <link inline="buildcraftunofficial:block/builder"/>. Build the path first, then set a Builder directly behind one end: on placement it copies the route, pops the marks back as items, and works its blueprint or template incrementally along the whole line — see the Builder's <italic>Continuous Building</italic> section for how it smears a snapshot from one end to the other. The build follows the path's direction, so if it would run the wrong way, flip the route before placing the Builder by right-clicking any mark with an empty hand.
A Map Location also reads a path: right-click a connected Path Mark with a clean one to copy the route into the item, stored as a repeating route if the path loops.
