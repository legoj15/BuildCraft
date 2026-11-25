###### Changes since 2026.4.0:

- **MinecraftForge support**, for those of you stuck with legacy modpacks. 1.21.10 is not supported by the Forge team, so I didn't bother with that version.
- When in creative mode, clicking a tank's gauge with a bucket on the cursor now works like using a bucket in the world instead of voiding the fluid.
- Minecraft 1.21.10 and later (NeoForge): right-clicking a tank while holding a stack of buckets now fills or empties just one of them.

Other than that, there are some minor bug fixes.

- The description of the "Mine Player Protected Blocks" setting is more descriptive
- Speaking of which, with "Mine Player Protected Blocks" off, a Builder, Quarry, Mining Well or Stripes pipe that a claim mod stops from breaking a block now skips it and carries on, instead of retrying it forever or stalling. The Builder's status now says the block was refused.
- Robots now actually report and acknowledge when an action was rejected due to being within a claim instead of retrying forever.
- Improved and fixed rendering of pipes that are being placed or in blueprints.
