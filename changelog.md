###### Changes since 2026.4.0:

- **MinecraftForge support** (not released yet): BuildCraft now also builds for MinecraftForge on Minecraft 1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3, with the whole mod on each. There is no MinecraftForge build for 1.21.10.
- On MinecraftForge, BuildCraft's settings can be changed in game from the mod list's Config button. JEI is supported on the MinecraftForge 1.21.1 build.
- The description of the "Mine Player Protected Blocks" setting now says what it covers: besides the mining machines it also lets Pumps, Flood Gates, Stripes pipes and robots skip asking claim mods.
- With "Mine Player Protected Blocks" off, a Builder, Quarry, Mining Well or Stripes pipe that a claim mod stops from breaking a block now skips it and carries on, instead of retrying it forever or stalling. The Builder's status now says the block was refused.
- Minecraft 1.21.11 (MinecraftForge): the cracks on a pipe being mined are no longer drawn twice, which showed as bright specks on the pipe.
- Minecraft 1.21.1 (NeoForge): emptying a creative-mode bucket into a tank no longer replaces the bucket.
