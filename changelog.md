###### Changes since 2026.1.1:

- Fixed a crash when breaking a Distiller. Thank you for your report, [Ryk7039](https://github.com/Ryk7039)!
- **Items travelling through pipes no longer lose their enchantments, custom names, or damage when the chunk is saved and reloaded**
- Builders and Fillers now reach their full building speed once their internal power buffer is half full, instead of permanently topping out at half speed even on a completely full buffer
- Fixed three of BuildCraft's four JEI plugins sharing one identifier, which could cause JEI to drop some of the mod's recipes
- Fixed FE/RF pipes not rendering their power sometimes (MJ was fixed the same way previous update)
- Fixed engines occasionally forgetting their recorded owner if they were placed and then never interacted with before their area unloaded
- Fixed the collapsible info/help ledgers flying up off the top of the screen when collapsed
- Fixed a "Network Protocol Error" disconnect when right-clicking certain machines while in spectator mode
- Blocks with status lights now stop rendering those lights when obscured by another block
- Improvements to the 3D preview of blueprints
- "Map Location" item and "Zone Planner" block are now available in survival. They don't really do all that much yet.
- Idle quarries no longer are a performance hog
