###### Changes since 26.1 Beta release 5:
- Fix: Fixed a NullPointerException crash when placing a blueprint in the architect table with no owner (e.g., in creative with no explicit owner set). The snapshot header serialization now uses a zero UUID as a fallback.
- Fix: Addressed an issue causing the BuildCraft Filler to incorrectly render the flying block animation when unpowered. This was caused by the client-side battery artificially gaining massive amounts of power during unpowered network syncs due to task cancellation logic repeatedly refunding extrapolated block tasks to the client's local read-only battery mirror.
- Fix: Filler flying block animation no longer loops when power is cut; blocks freeze in place matching 1.12.2 behavior. Server task data is now merged with max(client, server) power to prevent backward animation resets while maintaining smooth client-side extrapolation between 5-tick server syncs.
- Updated NeoForge to 26.1.2.12-beta and JEI to 29.5.0.26 for Minecraft 26.1.2.
- Fixed missing GUI textures for MJ Dynamo and FE Engine.
- Added tooltips for Redstone, Stirling, Combustion, and FE engines, as well as MJ Dynamo.
- Restored missing engine and dynamo block item translation strings by migrating to a centralized NeoForge ItemTooltipEvent registry.
- Added usage tooltips to the Land Mark and Path Mark blocks.
- Ported tooltips for the Filler, Builder, Architect Table, and Electronic Library from 1.12.2.
- Added tooltip to unpainted Paintbrushes to clarify they can strip paint from pipes.
- Added "Not usable in survival" tooltip to the Replacer block.
- Added usage tooltips to the Quarry and Mining Well blocks.
- Fixed an issue where the Filtered Buffer would not allow transport pipes to connect.
- Fixed a regression in the BuildCraft Filler where the robot and lasers would fail to render when unpowered or idle.
- Fixed a critical progression wipe in the BuildCraft Filler that caused it to forget state upon world reload or chunk unloads.
- Fixed a crash on world load caused by saving uninitialized Filler target layouts.
- Restored persistence of active Filler patterns and parameters, resolving a block entity network desync on world reload.
- Disabled directional shadowing on Marker and robot lasers to restore full emissive visibility even in the dark.
- Fixed an issue where dragging a pattern into the Filler GUI would visually revert back to None upon reopening the interface.
- Resolved a 'Network Protocol Error' crash in the internal server loop when attempting to override an active filler configuration with an invalid PlaceTask queue.
- Restored the classic 1.12.2 block-throwing placement animation for the BuildCraft Filler using a SubmitCustomGeometryEvent integration, bypassing 1.21.11 ItemRender constraints, and disabled the robot visualization during filling tasks for legacy parity. Fixes also included mitigating animation stutter by implementing client-side target power extrapolation.
Fix: Restored GUI slot background textures for the Filtered Buffer
Fix: Added gui.json atlas to correctly stitch slot sprite backgrounds
Fix: Corrected GUI atlas JSON path to merge with the vanilla minecraft namespace instead of creating a custom atlas
Fix: Corrected GUI icon placement and restored translucency/handling for the NOTHING filter icon in the Filtered Buffer
Fix: Restored Filtered Buffer ghost item rendering and dynamic ghost slot assignments
Fix: Re-calibrated opacity levels for Filtered Buffer ghost items and ?? icon to match legacy 30% presence thresholds
Fix: Restored Help and Ownership letgers to the Filtered Buffer GUI layout
Fix: Propagated BlockFilteredBuffer placement events down to TileFilteredBuffer, resolving Unknown ownership displays on freshly placed Filtered Buffers
Fix: Resolved generic return type conflicts in TileBC_Neptune network sync implementation to restore client-side owner mapping
Fix: Dynamically drew the block title onto the Filtered Buffer GUI, applying standard ARGB hex formatting to solve transparent text rendering errors
Enhancement: Fleshed out Filtered Buffer's ledger Help integrations with dummy bounding box hover tags and custom en_us localized guidance
Fix: Restored Shift-Click functionality to the Filtered Buffer by dropping broken placeholder implementations and permitting the generalized container logic to inherently read from the ItemHandlerFiltered ruleset
Fix: Fixed gates and wire objects failing to drop when their parent pipe is broken in survival mode
Enhancement: Implemented fully functional Help ledgers for the MJ Dynamo and FE Engine GUI
Fix: Enforced max 1 gear per upgrade slot on MJ Dynamo and FE Engine (4 slots = 4 gear max)
Test: Added EnergyConverterTester gametest suite for upgrade slot filtering and upgrade effectiveness on both machines
Fix: MJ Dynamo power ledger now displays RF output and RF stored instead of MJ, matching 1.12.2 behavior and showing the actual benefit of gear upgrades
Enhancement: Battery help ledgers on MJ Dynamo and FE Engine now dynamically display the current conversion rate based on installed gears, matching 1.12.2
Fix: Fixed a GUI bug where shift-clicking a stack into a max-1 slot bypassed the stack limits, and shift-clicking into an occupied slot permanently deleted the items
Fix: Fixed quarry rig collision phasing glitch caused by vanilla position-sync resetting the custom AABB to default 1x1 dimensions every few seconds
Fix: Fixed oil well generation for modern world depth (minY=-64): sphere cavity no longer clips into the bedrock gradient, oil tube now actually generates between the cavity and bedrock (was broken by negative length), and the tube no longer replaces bedrock blocks with oil which could expose the void
Fix: Fixed pump oil spring detection searching at Y=0 instead of the actual bedrock layer (minY=-64)
Fixed an issue where Markers would not drop themselves as items when broken or washed away by fluids. 
Enhancement: Restored full Filler block functionality: volume box detection on placement, TemplateBuilder integration for automated block placement/breaking, persistent 27-slot resource inventory, and owner tracking. The Filler GUI now opens when placed adjacent to volume markers.
Feature: Completely rebuilt the Filler GUI from the deprecated 1.12.2 JSON framework to the modern GuiBC8 system, restoring Pattern Statement slots, parameter fields, toggles, and the custom progress ledger readout
Fix: Resolved a compile error in TileFiller where generic types for the client rendering task lists conflicted with the TemplateBuilder's inner classes
Fix: Reordered the patterns in the Filler GUI to perfectly match the 1.12.2 layout
Fix: Restored the missing Ownership ledger to the Filler GUI and repaired the clipped vertical spacing/overlapping within the Filler's Progress ledger
Fix: Restored visual rendering of Gate-controlled machine mode icons (Loop/Off) in the Filler GUI
Fix: Fixed an issue where the Filler's Invert and Excavate GUI buttons required a world reload to recalculate building tasks by explicitly forcing immediate statement evaluation and syncing.
Fix: Fixed an issue where filling a stalled Filler's inventory would not cause it to immediately resume. Inventory interactions now correctly clear the builder's required resources cache and unset the completion flag.
Fix: Fixed a visual bug where the Filler's internal inventory count would flicker and ghost items would appear in the GUI. The root cause was the client-side network sync (every 5 ticks) calling cancel() which refunded reserved items back into the visible inventory, and then the Container sync immediately correcting them. Fixed by guarding the item refund to server-only, skipping redundant buildingInfo rebuilds on the client, and stripping inventory data from the block entity update packet.
- Fix: Restored legacy 1.12.2 block breaking particles and sound effects for the Filler (and other Builder logic) by migrating from world.removeBlock back to world.destroyBlock.
- Fix: Fixed a bug where players could not top up existing item stacks in the Filler's resource inventory via the GUI. The root cause was SlotBase.getMaxStackSize(ItemStack) returning remaining insertion capacity instead of total slot capacity, causing vanilla's container logic to reject items when the slot was partially filled. This affected all BuildCraft containers using SlotBase.
- Test: Added bulk insertion gameTests to verify 4 gears can be distributed across FE Engine and MJ Dynamo upgrade slots automatically.
- Fix: Enforced maximum stack size of 1 on the FE Engine and MJ Dynamo GUI upgrade slots, preventing mouse dropping or shift-clicking an entire stack of gears into a single slot.
- Fix: Fixed a bug where the Filler would never stop operating after completing its task (e.g. clearing a volume), continuously destroying any blocks placed within the area. The missing isFinished() guard from a 1.12.2 TODO has been restored, so the Filler now correctly halts when mode is ON and the pattern is complete, while still looping when mode is LOOP.
- Fix: Fixed a bug where the Filler would falsely report itself as finished (is_finished = true) before it had actually scanned and processed the entire volume. The SnapshotBuilder only checks 10 blocks per tick, but the isDone logic was ignoring unchecked (UNKNOWN) blocks and in-progress tasks, causing it to prematurely declare completion after just a handful of blocks were processed.
- Fix: Fixed a legacy 1.12.2 bug where Pipe Gate actions that set the Filler's pattern (e.g., "Filler Action: Box") would lock the GUI visually, but failed to actually update the Filler's physical logic or prevent the player from forcefully overwriting the task in the interface. The GUI now strictly prevents interacting with the Pattern Statement slot while locked, the Server actively rejects forced manipulation packets, and the Builder correctly initiates the gate-assigned task upon receiving it.
- Fix: Fixed the Gate connector button (the vertical link between logic slots on Iron AND and higher gates) being a no-op. AbstractContainerScreen.mouseClicked() was swallowing the click before the connector hit-test could run. Reordered mouse handling to check connector buttons first and added optimistic client-side state update for instant visual feedback. Also fixed initial connection state sync when the Gate GUI is opened.

- Replaced deprecated AbstractContainerScreen GUI getter methods with their modern getters to fix compilation removal warnings.
- Enhancement: Ported the contextual Help Ledger text for the Filler block (Excavate, Invert, Mode, Locked) from 1.12.2.
- Fix: Restored the 1.12.2 Filler inventory filter that only accepts block-type items. Previously, any item (swords, sticks, ingots, etc.) could be placed into the Filler's resource slots, causing it to attempt building with unbuildable items. The filter now checks for BlockItem instances, matching the original ItemBlocks.getList() behavior.
- Test: Added FillerInventoryTester game test to verify both block items are accepted and non-block items are rejected by the Filler's inventory.
- Fix: Fixed a missing rendering implementation for the Architect Table where the "blue caution lasers" indicating the selected building volume bounds were not rendering on the client when placed next to volume markers. State synchronization and block entity lifecycle registration have been implemented, bringing back the volume visualizations.
