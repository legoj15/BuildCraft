###### Changes since 2026.4.0:

- **MinecraftForge support**, for those of you stuck with legacy modpacks. 1.21.10 is not supported by the Forge team, so I didn't bother with that version.
- When in creative mode, clicking a tank's gauge with a bucket on the cursor now works like using a bucket in the world instead of voiding the fluid.
- Minecraft 1.21.10 and later (NeoForge): right-clicking a tank with a filled bucket no longer duplicates the bucket (you used to get the empty bucket and keep the full one), and holding a stack of buckets now fills or empties just one of them. Thank you for reporting, [user_pf5hyk9zed69zy64](https://www.curseforge.com/members/user_pf5hyk9zed69zy64)!

Other than that, there are some minor bug fixes.

- The description of the "Mine Player Protected Blocks" setting is more descriptive
- Speaking of which, with "Mine Player Protected Blocks" off, a Builder, Quarry, Mining Well or Stripes pipe that a claim mod stops from breaking a block now skips it and carries on, instead of retrying it forever or stalling. The Builder's status now says the block was refused.
- Robots now actually report and acknowledge when an action was rejected due to being within a claim instead of retrying forever.
- Robots no longer fly back and forth forever when their target sits inside a block they can't enter, such as a Picker going after an item lying on a pipe. A Picker or a fighting robot now does its job from beside it, and other robots give up on it.
- Improved and fixed rendering of pipes that are being placed or in blueprints.
- With ModernFix's dynamic resources option on, gates, facades and lenses show their proper item icons again, and pipe items get their colours back.
- With Sodium installed (NeoForge), gate, lens, facade and painted pipe item icons are no longer drawn too dark.
- Pipe wires now join up straight away when a pipe next to them is replaced, instead of leaving gaps or loose ends until the world is reloaded; a removed pipe also no longer leaves an invisible wire stub sticking out of its neighbour that you could bump into.
- A Quarry placed against a marker area that was resized with a wrench now uses the resized area, even when the drag left the bottom of the box below the Quarry or its top above it. Markers and boxes now share one minimum of 3x3; anything narrower gets the default area instead.
