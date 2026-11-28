###### Changes since 2026.4.0:

- **MinecraftForge support**, for those of you stuck with legacy modpacks. 1.21.10 is not supported by the Forge team, so I didn't bother with that version.
- When in creative mode, clicking a tank's gauge with a bucket on the cursor now works like using a bucket in the world instead of voiding the fluid.
- Minecraft 1.21.10 and later (NeoForge): right-clicking a tank with a filled bucket no longer duplicates the bucket (you used to get the empty bucket and keep the full one), and holding a stack of buckets now fills or empties just one of them. Thank you for reporting, [user_pf5hyk9zed69zy64](https://www.curseforge.com/members/user_pf5hyk9zed69zy64)!

Other than that, there are some minor bug fixes.

- The description of the "Mine Player Protected Blocks" setting is more descriptive
- Speaking of which, with "Mine Player Protected Blocks" off, a Builder, Quarry, Mining Well or Stripes pipe that a claim mod stops from breaking a block now skips it and carries on, instead of retrying it forever or stalling. The Builder's status now says the block was refused.
- Robots now actually report and acknowledge when an action was rejected due to being within a claim instead of retrying forever.
- Robots no longer fly back and forth forever when their target sits inside a block they can't enter, such as a Picker going after an item lying on a pipe. A Picker or a fighting robot now does its job from beside it, and other robots give up on it. Robots in general no longer get stuck on blocks that get placed after they've chosen a path.
- Improved and fixed rendering of pipes that are being placed or in blueprints.
- With ModernFix's dynamic resources option on, gates, facades and lenses show their proper item icons again, and pipe items get their colors back. Thank you for reporting, [SteamMarionette](https://www.curseforge.com/members/steammarionette)!
- (NeoForge) With Sodium installed, gates, lenses, facade and painted pipe item icons are no longer drawn too dark.
- Fixed a slew of visual bugs involving pipe wires when pipes are broken and replaced/moved.
- Made it so that pipe wires visual state line up with their actual state, which includes being chopped by chunks, or modified by /data.
- Improved performance of gates and pipe wire networks.
- A blueprint built rotated now turns everything in it (best effort).
- A machine built from a blueprint no longer carries on with the working area of the machine it was copied from. This means that Fillers, Quarries, and Architect Tables now need their associated markers placed in their blueprint to get a specific volume.
- A machine or pipe built from a blueprint now belongs to whoever built it (i.e. the owner of the Builder), not to whoever made the blueprint, so a copy of someone's blueprint no longer digs, places or asks claim mods as that player.
- Buildings will no longer break/adopt markers claimed by someone else.
- The "Logic transportation" advancement is no longer granted for a wire placed beside one it does not connect to.
- A Quarry placed against a marker area that was resized with a wrench now uses the resized area. Quarries now enforce a hard minimum 3x3 footprint (that would leave a 1x1 space for the drill rig).
