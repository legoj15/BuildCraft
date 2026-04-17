###### Changes since 26.1 Beta release 5:
- Updated NeoForge to 26.1.2.12-beta and JEI to 29.5.0.26 for Minecraft 26.1.2.
- Fixed missing GUI textures for MJ Dynamo and FE Engine.
- Added tooltips for Redstone, Stirling, Combustion, and FE engines, as well as MJ Dynamo.
- Restored missing engine and dynamo block item translation strings by migrating to a centralized NeoForge ItemTooltipEvent registry.
- Added usage tooltips to the Land Mark and Path Mark blocks.
- Ported tooltips for the Filler, Builder, Architect Table, and Electronic Library from 1.12.2.
- Added tooltip to unpainted Paintbrushes to clarify they can strip paint from pipes.
- Added "Not usable in survival" tooltip to the Replacer block.
- Added usage tooltips to the Quarry and Mining Well blocks.Fix: Registered NeoForge Item capabilities for Filtered Buffer to allow transport pipes to connect
Fix: Restored GUI slot background textures for the Filtered Buffer
Fix: Added gui.json atlas to correctly stitch slot sprite backgrounds
Fix: Corrected GUI atlas JSON path to merge with the vanilla minecraft namespace instead of creating a custom atlas
Fix: Corrected GUI icon placement and restored translucency/handling for the NOTHING filter icon in the Filtered Buffer
Fix: Restored Filtered Buffer ghost item rendering and dynamic ghost slot assignments
Fix: Re-calibrated opacity levels for Filtered Buffer ghost items and ?? icon to match legacy 30% presence thresholds
Fix: Restored Help and Ownership letgers to the Filtered Buffer GUI layout
