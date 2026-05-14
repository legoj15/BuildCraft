<lore>
A blueprint by itself only describes a structure. To raise it, you need something that can read the snapshot, request the right materials, and lay every block down in order. The Builder is that machine.
</lore>
<no_lore>
The Builder consumes a snapshot (a Blueprint or Template captured by an Architect Table) and reconstructs the captured area at a new location, drawing items and fluids from its internal inventory and tanks.
</no_lore>
<chapter name="Information"/>
Place the Builder next to a Land Mark path (or with a Land Mark Connector adjacent) to define where it should build. Drop a snapshot into its top slot and the Builder starts working as soon as it has power and the resources the snapshot still needs.
The 27 resource slots on the left of the GUI are where you stage items for the build. Pipes or hoppers can feed those slots from any side, and the Builder will only accept items still required by the snapshot. The display on the right of the GUI is a read-only preview of what is still outstanding — empty cells mean that material is fully covered.
<recipes_usages stack="buildcraftunofficial:builder"/>
<chapter name="Destruction Lasers"/>
Before the Builder can place schematic blocks, anything already occupying the build area needs to come out. The Builder fires destruction lasers at those positions, harvesting them as if it were wielding an iron pickaxe — matching the Mining Well nested in its recipe.
Drops are inserted directly into the Builder's own resource inventory, so cobblestone you sweep out of the area immediately stacks with any cobblestone you supplied for the build. If a slot is full, the overflow spills as items at the position that was cleared.
Anything an iron pickaxe couldn't harvest (obsidian, ancient debris, and similar diamond-tier blocks) is destroyed instead of mined: the block is removed so the build can continue, but no drops are produced for that position. Experience awarded by harvestable blocks (redstone ore, lapis ore, diamond ore, and so on) spawns at the Builder itself.
<chapter name="Fluid Handling"/>
The fluid-mode button cycles between three behaviours for fluid blocks that fall inside the build area:
- <bold>Skip Fluids</bold> (Barrier icon) keeps the world as-is — fluid positions are left untouched and the snapshot is built around them.
- <bold>Replace Fluids</bold> (Bricks icon) places schematic blocks on top of fluids. Vanilla waterloggable blocks preserve the source water "for free"; other blocks destroy the fluid first.
- <bold>Clear Fluids</bold> (Bucket icon) sweeps every fluid out of the build area before placement. Source blocks the Builder breaks are absorbed into its four internal tanks (one bucket per source) so a CLEAR-mode build over a small lake gives you the water back. When the tanks are full, additional sources are discarded; plumb a fluid output pipe if you want to keep collecting.
<chapter name="Fluid Tanks"/>
The Builder has four internal tanks, each holding up to eight buckets. They serve two roles: storing the fluid the snapshot needs when it captures fluid blocks (filled by pipes, by clicking with a bucket, or by the CLEAR-mode absorption described above), and acting as a buffer the place-phase pulls from when it needs to set a fluid block from the schematic.
