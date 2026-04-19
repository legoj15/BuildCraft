###### Changes since 26.1 Beta release 5:
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
