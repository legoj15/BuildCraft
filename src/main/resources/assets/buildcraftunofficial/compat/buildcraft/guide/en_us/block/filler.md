<lore>
Sometimes you don't want a structure — you want a flat floor, a sphere of cobblestone, or a hollowed-out room. The Filler picks a geometric pattern instead of a snapshot and stamps it into a marked area.
</lore>
<no_lore>
The Filler builds geometric patterns (boxes, spheres, walls, etc.) inside a marker box and can be configured to invert the pattern or excavate the interior before filling.
</no_lore>
<chapter name="Information"/>
Place the Filler adjacent to a Land Mark volume box, drop a pattern into the Filler's pattern slot, and supply it with power and materials. The Filler reads the pattern, optionally takes a few parameters (corner offsets, sizes, percentages), and places blocks from its resource inventory into the matching positions inside the marker volume.
The <bold>Excavate</bold> toggle clears the interior of the shape before filling. The <bold>Invert</bold> toggle builds blocks outside the defined shape rather than inside. The <bold>Control mode</bold> button (set by gates) cycles between ON, OFF, and LOOP.
<recipes_usages stack="buildcraftunofficial:filler"/>
<chapter name="Destruction Lasers"/>
When Excavate is enabled (and for any block that occupies a position the pattern wants filled), the Filler fires destruction lasers at those positions. The Filler harvests as if wielding an iron pickaxe — matching the Mining Well nested in its recipe — so anything an iron pickaxe could mine drops the appropriate items.
Drops are routed into adjacent inventory blocks placed against the Filler. Pipes are explicitly skipped: the Filler wants to feed its own collection station next to it, not have the items shuttled away. If no adjacent inventory exists, drops fall as items at the position that was cleared, where you can collect them by walking through the area or with a hopper-based pickup line.
Blocks an iron pickaxe couldn't harvest (obsidian, ancient debris, similar diamond-tier blocks) are destroyed without dropping anything — the Filler removes them so the pattern can be filled, but no items come back. Experience awarded by harvestable blocks spawns at the Filler itself, so XP collects at the machine you placed.
