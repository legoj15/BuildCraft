###### Changes since 2026.1.0-rc1:
- **Fixed a crash that stopped dedicated servers from starting.** A client-only texture lookup was being reached on the server thread moments after "Done", hard-crashing any real (non-dev) dedicated server. Servers now boot normally. A development `runServer` never hit this because it bundles the client classes a true server lacks.
- **Fixed multiplayer clients being unable to join dedicated servers.** Logging into a server warms the guide book's item index, which could name a facade before its block data finished initializing on the client — throwing an error that dropped the connection right after login. Facade naming and the guide indexer are now both hardened against it, and the load order is fixed.
- Stopped the five dev-only "decorated" blocks from spamming the server log with missing-item loot-table errors at every world load.
- **Fixed the in-game update notification.** The version manifest it reads was malformed, so the check silently failed for everyone; it now parses, and each supported Minecraft version reports the updates published for its own line.

###### 2026.1.0-rc1 — first release under the new CalVer scheme (changes since 26.1.x Beta release 6):
- New versioning scheme hopefully to prevent confusion with the true BuildCraft project since its still in active development
- **Old BuildCraft saves keep their machines and items.** Worlds and inventories from the 1.21.11 builds restore their placed pipes, engines, and tanks (with fluid contents) and full inventories despite two rounds of block/item ID changes; 1.12.2 worlds can reclaim some items that were in inventories, for what that's worth (the flattening was brutal). Migration is automatic. **Backup your worlds anyways!!!**
- **There are now two builds, one per Minecraft line.** A NeoForge API the mod relies on (the block-break event) was renamed between Minecraft 26.1.1 and 26.1.2, so a single jar can't serve both. This `+mc26.1.1` jar is built against NeoForge 26.1.1.15-beta and runs on **Minecraft 26.1 and 26.1.1**; a separate `+mc26.1.2` jar (NeoForge 26.1.2.21-beta or newer) covers **Minecraft 26.1.2**. Grab whichever matches your game version. (JEI is unavailable on 26.1; it works on 26.1.1 and 26.1.2.)
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