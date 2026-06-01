###### 2026.1.0-rc1 — first release under the new CalVer scheme (changes since 26.1.x Beta release 6):
- New versioning scheme hopefully to prevent future confusion
- **Old BuildCraft saves keep their machines and items.** Worlds and inventories from 1.21.11 restore their placed pipes, engines, and tanks (with fluid contents) and full inventories despite two rounds of block/item ID changes; 1.12.2 worlds can reclaim some items that were in inventories, for what that's worth (the flattening was brutal). Migration is automatic. **Backup your worlds anyways!!!**
- Finalized the Guide Book (the GitHub Wiki is also now live)
- Made every advancement and recipe-book unlock obtainable
- Overhauled oil worldgen hopefully for the last time
- Mobs can now navigate pipes and pipes now have their correct maximum speeds/capacities. Pipes also drop when broken in scenarios where they didn't before.
- Restored Engine chaining, non-explosive overheating is default again, direct Forge-Energy intake under autoconversion
- Pipe Logic Gate pluggables now have their crafting recipes, and the Gate Copier now functions, with some added UX additions
- Quarries now chunk load again
- Finalized JEI integration
- Made sure all config options that are present actually do something
- The side-ledger things in GUIs now remember their last state kinda like they did in 1.12.2
- Added status LEDs to more blocks that had spots for them on their textures
- Rectified some major performance issues
- De-duplicated and removed unused assets to make the .jar as small as possible

The following changes are not user facing:
- For people trying to compile locally, you only need JDK 25 now, as JDK 21 should automatically resolve itself as a dependency for the NeoForge build system
- The jar is built against NeoForge 26.1.2.70-beta, but the minimum required is still 26.1.0.0-beta