###### Changes since 26.1 Beta release 4:
- Gates, lenses, filters, wires,the pulsar, timer, and light sensor all now look more correct, are placeable, and actually work
- Overhauled underlying mod structure; some assets or code may still be missing
- Fluid physics: dense oils (Heavy / Dense / Residue) now sink through water, and lighter oils spread evenly across ocean surfaces. Player interaction with fluids now actually exists as well
- Fixed wrong neighbor-texture bleed while fluids scroll through pipes (like with the Kinesis/FE pipes)
- Fixed Kinesis flow overlays rendering as solid opaque boxes instead of translucent
- Fixed Wooden Kinesis Pipes showing the wood extraction plug when connected to FE Engines
- Fixed MJ Dynamo and FE Engine deleting FE during transfers
- Fixed MJ Dynamo stalling permanently once its 10,000 FE buffer filled up
- Fixed MJ Dynamo piston animating indefinitely when no valid FE consumer was attached
- Fixed MJ Dynamo FE-generation readout being erased every tick
- Fixed double-title rendering on engine blocks (e.g. one title left-adjacent, another centered)
- Fixed MJ Dynamo and FE Engine GUI
- Fixed Kinesis Pipes accumulating energy past capacity when pushed without demand
- Fixed an energy accumulation bug in Redstone Flux kinesis pipes
- Fixed the Quarry's client-side battery readout ballooning exponentially due to additive packet sync
- Volume / Land Markers can once again float freely in the air, matching 1.12.2 behavior
- Path Marker now correctly drops its item when its anchor is broken
- Quarry Frames visually connect to the Quarry block again, including frames placed by the Quarry itself
- Gate redstone trigger/action icons now use vanilla's redstone torch textures so they match the current art style and respect resource packs
- Fixed a save/shutdown hang on any world containing pipes
- Fixed a 1px gap near the left ledger border when it moves in or out (was visible with help ledgers)

**The following are changes that are not user facing:**

- Eliminated deprecation warnings, wrapped legacy `FluidUtil.getFilledBucket`
- Added `GameTest` validation testing
