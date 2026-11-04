###### Changes since 2026.3.1:

- **Fabric support for Minecraft 26.3 (in development).** BuildCraft now also builds for Fabric (with Fabric API and Forge Config API Port); the NeoForge versions are unchanged. Machines respect Fabric claim mods through the bundled Common Protection API, placements included. Known Fabric differences so far: no Curios/Accessories slot for the Robot Goggles, and fluid transfers smaller than 1/81 mB are ignored.
- Fixed mobs and players being stuck in place in crude oil and residue on Minecraft 26.1.2 and later: they now wade through slowly (residue the slowest, and slower again with `oilIsSticky`), sink gently, and can climb out at the edge.
