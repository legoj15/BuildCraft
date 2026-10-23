###### Changes since 2026.2.0:

- Added Minecraft 26.3 support. Minimum required NeoForge version is 26.3.0.38.
- Robots, Stripes pipes, Builders and Fillers now respect land-claim and protection mods just like how player interactions work; a refused spot in a build is retried later.
- Robots and Stripes pipes no longer lose fluid containers after consuming the fluid, it ejects it somewhere.
- Electronic Library no longer rejects blueprints that hold pistons/blocks with replaceable variants; older saved blueprints are repaired automatically when the library lists them.
- Fixed the guide book putting text over a page number in specific cases, and links that wrap onto the next page only being clickable on one of the two pages.
- Fixed Fragile Fluid Shards showing the wrong colour for coloured fluids (lava shards looked blue) on Minecraft 1.21.10 and newer.
- Robots now glide smoothly on clients instead of stepping.
- Builder no longer stays busy for ever next to fluid it can't remove (lava, oil or modded fluid outside the area in Clear mode; still pools in Replace mode), and builds with fluid in their empty cells now finish.
- Stripes pipes now use buckets, bottles and thrown items on the block in front, extend their line when fed item pipes, and break minecarts in front of them, like 1.7.10.
- On Minecraft 1.21.1, replacing a BuildCraft machine or pipe with /setblock, /fill or /clone no longer spills its contents; breaking a Distiller now drops the items in its container slots.
- The Wooden Diamond pipe's filter modes are now called Allowlist and Blocklist; the Programming Table pages through more than 24 boards.
- Mining wells and pumps no longer replace claimed or waterlogged blocks with their tube, or lay it where a claim refuses placement, and pumps no longer destroy waterlogged blocks that can't stay dry; idle pumps over huge pools and the Zone Planner map use far less work.
- A quarry now repairs a frame gap once whatever blocked it is gone, and leaves a gap where a claim refuses placing a frame block.
- Stirling engine fuel, and the gear upgrades in FE engines and MJ dynamos, now drop when the block is blown up or removed with /setblock destroy, and are cleared rather than spilled when a command replaces it.
