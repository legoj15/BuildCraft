###### Changes since 2026.3.0:

This update serves as the second part of the 2026.3 feature update and brings some bug fixes I found along the way.
- **Completely redesigned the Heat Exchanger model.** It now is much more readable with color coded inputs and outputs. Heat Exchanger now requires 1 copper ingot and 1 less glass when crafting.
- **Revived cut content: The Robot Goggles.** These were a helmet-slot piece of equipment that let you see robots through walls, but never had a model or texture before now. These can count as curios/accessories for those who care.
- **New icons for the filler's parameters and settings.** Also fixed the pentagon shape, some axis alignment issues, and re-added the flatten pattern from 1.7.10.
- **New rare world gen: Abandoned Quarries.** If you explore a lot, you may now come across pits with damaged quarry frames around them. These can be disabled in the mod config. Read the wiki page for how they work.
- You can now see how far away you are from a marker after right-clicking on it with a Marker Wrench or Volume Wrench.
- Additionally, aiming at a potential marker connection will tell you the true length of the beam.
- Reduced mod size by deleting unused assets and optimizing all textures.
- Redesigned the Volume Wrench sprite because it looked too much like a short staff.
- Buffed wrench attach speed (lol) and increased the amount of damage the Volume Wrench deals.
- Fixed an oversight where the Marker Wrench could edit/create volumes.
- Added the resizing while in use feature to the creative mode Volume Box as well.
- Fixed an edge case where blocks that show dynamic fluid levels (such as Distillers and Heat Exchangers) would have the visual component of their fluids disappear completely when nearing empty.
- Lava can now light fuels and refined oils on fire. Naturally spawning oil is unaffected. This can be disabled.
- Fixed a rare crash when loading chunks that had certain pipe pluggables installed.
- Pipes and their pluggables now reload their textures when a resource pack is added, changed, or reloaded (such as color blind mode or high-contrast).
- Fixed a long standing bug that allowed engines to pass redstone through them. This was discovered when engines with levers on them turned off pumps and quarries, now that they can be instantly turned off with redstone as well.
- Gates now see cauldrons and composters, so inventory logic for those two blocks can now be used.
- New config option "Enable Detonation of Contained Fluids" (off by default). You can turn fluid tanks and pipe networks of gaseous fuels into a server owner's worst nightmare.
