---
navigation:
  title: "Debugger"
  parent: contents/building/tool.md
  position: 18
  icon: buildcraftunofficial:debugger
item_ids:
  - buildcraftunofficial:debugger
---

# Debugger

<ItemImage id="buildcraftunofficial:debugger" scale="2" float="right" />

## Information

A tool only accessible via cheats. When held in a hand in survival mode, the F3 debug menu will show the additional data that is available to creative mode users. Some blocks have extra debug functionality when interacted with.

**Right-click action.** Right-clicking a BuildCraft tile that has a debug overlay registered will pop up a message describing what is being highlighted and starts drawing that overlay on top of the tile. Right-clicking a different supported tile moves the overlay to the new target. The overlay clears itself once the recorded tile is broken, unloaded, or replaced with something else.

**F3 overlay enhancement.** Open the F3 debug screen, look at any BuildCraft tile, and (if you are either holding a Debugger in either hand or are in Creative mode) extra **[BC]** lines appear at the bottom corners of the screen listing whatever internal state that is registered, typically things like energy stored, work progress, fluid contents, etc.

## Overlay coverage

Two BuildCraft tiles currently draw an in-world overlay when right-clicked with the Debugger:

**[Quarry](../block/quarry.md):** a translucent green box covers every chunk the quarry is keeping force-loaded, inset slightly from each chunk boundary so neighbouring chunks read as separate volumes. Useful for confirming what and how many chunks a quarry is keeping loaded during its process.

**[Laser](../block/laser.md):** small cubes mark every block inside the laser's six-block targeting cone. Green cubes are reachable along an unobstructed line back to the laser; red cubes are blocked by an intervening block. Useful for diagnosing why a laser is failing to feed its target.

At this time, no other blocks have additional debugging features such as these.

*Creative-only: there is no survival recipe.*
