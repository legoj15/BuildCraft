###### Changes since 2026.2.0:

- Added Minecraft 26.3 support. Minimum required NeoForge version is 26.3.0.38.
- Robots now glide smoothly on clients instead of stepping.
- Robots, Stripes pipes, Builders, Fillers, Quarries, Mining Wells and Pumps now respect land-claim and protection mods the way players do; a refused spot is skipped and retried later.
- Stripes pipes now use buckets, bottles and thrown items on the block in front, extend their line when fed item pipes, and break minecarts in front of them, like 1.7.10. Robots and Stripes pipes keep the container after using up its fluid.
- Builders and Fillers no longer get stuck next to fluid they can't remove, and builds with fluid in their empty cells now finish.
- Quarries repair a frame gap once whatever blocked it is gone. Mining Wells and Pumps no longer replace waterlogged blocks with their tube or destroy ones that can't stay dry, and idle Pumps over huge pools and the Zone Planner map use far less work.
- Machines keep or drop their contents correctly when blown up or replaced by commands: engine fuel and gear upgrades now drop, and a command replace clears them instead of spilling. On Minecraft 1.21.1, /setblock, /fill and /clone no longer spill contents, and breaking a Distiller now drops the items in its container slots.
- The Electronic Library no longer rejects blueprints holding pistons or blocks with replaceable variants; older saved blueprints are repaired automatically.
- Guide book: no more text over page numbers, and links that wrap onto the next page are clickable on both pages.
- Fixed Fragile Fluid Shards showing the wrong colour for coloured fluids (lava shards looked blue) on Minecraft 1.21.10 and newer.
- The Wooden Diamond pipe's filter modes are now called Allowlist and Blocklist; the Programming Table pages through more than 24 boards.
