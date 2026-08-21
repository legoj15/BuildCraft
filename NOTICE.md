# Licensing notice

This project ships code under three licences. Which one applies to a given file is decided by
**that file's own copyright header**, not by this repository's root `LICENSE`.

- **Most files** — Mozilla Public License 2.0, see [LICENSE](LICENSE). This is the default for every
  file that does not carry a notice of its own.
- **Most of `buildcraft/api/`** — MIT, see [LICENSE.API](LICENSE.API). Those files say so in their own
  header, so other mods can compile against the API freely. Seven files that live in the
  `buildcraft.api` packages are *not* MIT — they are in the MMPL list below. The package is genuinely
  mixed; the header is authoritative, never the directory.
- **65 files carried over from BuildCraft for Minecraft 1.7.10 / 1.12.2** — Minecraft Mod Public
  License 1.0.1, see [LICENSE.MMPL](LICENSE.MMPL). Each one states this in its own header.

Upstream BuildCraft has never relicensed these files away from MMPL — every upstream branch, up to and
including `master`, still ships MMPL as its root licence — so this fork does not either. The root
MPL-2.0 grant does not override a file's own header.

## Files under the Minecraft Mod Public License 1.0.1

Two of them carry **two** notices, upstream's first, because they are original work built on top of
carried-over code:

- `src/main/java/buildcraft/api/robots/DockingStation.java`
- `src/main/java/buildcraft/robotics/item/ItemRedstoneBoard.java`

The remaining 63 carry the upstream notice alone:

- `src/main/java/buildcraft/api/core/IFluidFilter.java`
- `src/main/java/buildcraft/api/core/IStackFilter.java`
- `src/main/java/buildcraft/api/robots/ResourceIdBlock.java`
- `src/main/java/buildcraft/api/robots/ResourceIdRequest.java`
- `src/main/java/buildcraft/api/robots/RobotManager.java`
- `src/main/java/buildcraft/api/statements/StatementSlot.java`
- `src/main/java/buildcraft/builders/snapshot/pattern/PatternBox.java`
- `src/main/java/buildcraft/builders/snapshot/pattern/PatternClear.java`
- `src/main/java/buildcraft/builders/snapshot/pattern/PatternFill.java`
- `src/main/java/buildcraft/builders/snapshot/pattern/PatternFrame.java`
- `src/main/java/buildcraft/builders/snapshot/pattern/PatternPyramid.java`
- `src/main/java/buildcraft/builders/snapshot/pattern/PatternStairs.java`
- `src/main/java/buildcraft/core/properties/WorldPropertyIsSoft.java`
- `src/main/java/buildcraft/robotics/IEntityFilter.java`
- `src/main/java/buildcraft/robotics/IStationFilter.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotAttack.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotBreak.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotFetchAndEquipItemStack.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotFetchItem.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGoto.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoBlock.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoSleep.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStation.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStationAndLoad.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStationAndLoadFluids.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStationAndUnload.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStationAndUnloadFluids.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStationToLoad.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStationToLoadFluids.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStationToUnload.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotGotoStationToUnloadFluids.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotHarvest.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotLoad.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotLoadFluids.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotMain.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotPlant.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotPumpBlock.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotRecharge.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotSearchAndGotoBlock.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotSearchAndGotoStation.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotSearchBlock.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotSearchEntity.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotSearchStation.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotShutdown.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotSleep.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotStraightMoveTo.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotUnload.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotUnloadFluids.java`
- `src/main/java/buildcraft/robotics/ai/AIRobotUseToolOnBlock.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotButcher.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotCarrier.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotFarmer.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotGenericBreakBlock.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotGenericSearchBlock.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotHarvester.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotKnight.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotLumberjack.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotMiner.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotPicker.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotPlanter.java`
- `src/main/java/buildcraft/robotics/boards/BoardRobotPump.java`
- `src/main/java/buildcraft/robotics/entity/EntityRobot.java`
- `src/main/java/buildcraft/robotics/item/ItemRobot.java`

This list is not maintained by hand: `CopyrightHeaderTester.noticeFileListsEveryMmplFile` fails the
build if it drifts from the headers actually present in the tree.
