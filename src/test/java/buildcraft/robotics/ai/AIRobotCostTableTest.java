/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import buildcraft.api.mj.MjAPI;

/** Pins the per-leaf power costs (in micro-MJ, the unit {@code AIRobot.getPowerCost} is charged in) and their
 *  relative ordering. Each leaf is 7.1.x's RF cost converted at BuildCraft's canonical 1 MJ = 10 RF bridge —
 *  1 RF = 100_000 micro-MJ ({@code MjRfConversion.DEFAULT_MJ_PER_RF}), so fetch's 15 RF is 1_500_000. */
public class AIRobotCostTableTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void fetchItemIsTheMostExpensiveLeaf() {
        Assertions.assertEquals(1_500_000, new AIRobotFetchItem(robot).getPowerCost(),
                "picking up a dropped item is the priciest single action (7.1.x charged 15 RF)");
    }

    @Test
    public void unloadCostsMoreThanLoad() {
        Assertions.assertEquals(1_000_000, new AIRobotUnload(robot).getPowerCost(),
                "unload is 7.1.x's 10 RF");
        Assertions.assertEquals(800_000, new AIRobotLoad(robot).getPowerCost(),
                "load is 7.1.x's 8 RF");
    }

    @Test
    public void theGotoFamilySharesAModestCost() {
        // AIRobotGoto is abstract, so its cost is pinned through its concrete subclasses.
        Assertions.assertEquals(300_000, new AIRobotGotoBlock(robot).getPowerCost(),
                "movement is 7.1.x's 3 RF — the shared AIRobotGoto base cost");
        Assertions.assertEquals(300_000, new AIRobotStraightMoveTo(robot).getPowerCost(),
                "StraightMoveTo inherits the Goto cost");
    }

    @Test
    public void controlAndIdleAIsAreFree() {
        Assertions.assertEquals(0, new AIRobotMain(robot).getPowerCost(),
                "the controller declares no cost of its own");
        Assertions.assertEquals(0, new AIRobotRecharge(robot).getPowerCost(),
                "recharging is free — the station pays");
        Assertions.assertEquals(0, new AIRobotShutdown(robot).getPowerCost(),
                "shutting down is free — a flat robot must not be denied its own shutdown");
    }

    @Test
    public void sleepCostsADeclaredIdleRate() {
        AIRobotSleep sleep = new AIRobotSleep(robot);
        // The 0.1 RF/tick trick: MJ/10 whenever sleptTime % 10 == 0, 0 otherwise. The freshly-created AI has
        // sleptTime == 0, so the very first tick already pays.
        Assertions.assertEquals(MjAPI.MJ / 10, sleep.getPowerCost(),
                "tick 0 (sleptTime 0) pays MJ/10");
        sleep.update(); // sleptTime 1
        Assertions.assertEquals(0, sleep.getPowerCost(),
                "the tick after a pay tick is free");
        for (int i = 0; i < 9; i++) {
            sleep.update(); // sleptTime 10
        }
        Assertions.assertEquals(MjAPI.MJ / 10, sleep.getPowerCost(),
                "the tenth tick pays MJ/10 again — the 0.1 RF/tick idle rate");
    }

    @Test
    public void orderingIsFetchThenUnloadThenLoadThenGoto() {
        long fetch = new AIRobotFetchItem(robot).getPowerCost();
        long unload = new AIRobotUnload(robot).getPowerCost();
        long load = new AIRobotLoad(robot).getPowerCost();
        long gotoCost = new AIRobotGotoBlock(robot).getPowerCost();
        Assertions.assertTrue(fetch > unload && unload > load && load > gotoCost,
                "FetchItem (1.5M) > Unload (1M) > Load (0.8M) > Goto (0.3M)");
    }

    // ── Ph5 (the board AIs) ─────────────────────────────────────────────────
    // Same 1 RF = 100_000 µMJ bridge. 7.1.x's BREAK_ENERGY (160 RF) has no BuilderAPI in the modern tree,
    // so break/attack hardcode the converted constants.

    @Test
    public void theSearchAIsCostTwoRf() {
        Assertions.assertEquals(200_000, new AIRobotSearchBlock(robot).getPowerCost(),
                "searching for a block is 7.1.x's 2 RF");
        Assertions.assertEquals(200_000, new AIRobotSearchEntity(robot, e -> true, 250f, null).getPowerCost(),
                "searching for an entity is 7.1.x's 2 RF");
    }

    @Test
    public void breakCostsElevenRf() {
        Assertions.assertEquals(1_100_000, new AIRobotBreak(robot, BlockPos.ZERO).getPowerCost(),
                "breaking is 7.1.x's ceil(160*2/30) = 11 RF");
    }

    @Test
    public void attackCostsSixteenRf() {
        Assertions.assertEquals(1_600_000, new AIRobotAttack(robot, null).getPowerCost(),
                "attacking is 7.1.x's 160*2/20 = 16 RF");
    }

    @Test
    public void pumpAndToolUseCostFiveAndEightRf() {
        Assertions.assertEquals(500_000, new AIRobotPumpBlock(robot, BlockPos.ZERO).getPowerCost(),
                "pumping a fluid source block is 7.1.x's 5 RF");
        Assertions.assertEquals(800_000, new AIRobotUseToolOnBlock(robot, BlockPos.ZERO).getPowerCost(),
                "using a tool on a block is 7.1.x's 8 RF");
    }

    @Test
    public void theFluidLoadUnloadAIsCostEightAndTenRf() {
        Assertions.assertEquals(800_000, new AIRobotLoadFluids(robot, f -> true).getPowerCost(),
                "loading fluid is 7.1.x's 8 RF");
        Assertions.assertEquals(1_000_000, new AIRobotUnloadFluids(robot).getPowerCost(),
                "unloading fluid is 7.1.x's 10 RF");
    }

    @Test
    public void thePh5AIsWithoutAnOverrideShareTheDefault() {
        // 7.1.x gave these no cost override — the default 1 RF (MjAPI.MJ/10) is the faithful pin.
        Assertions.assertEquals(MjAPI.MJ / 10, new AIRobotHarvest(robot, BlockPos.ZERO).getPowerCost(),
                "harvesting inherits the default 1 RF");
        Assertions.assertEquals(MjAPI.MJ / 10, new AIRobotPlant(robot, BlockPos.ZERO).getPowerCost(),
                "planting inherits the default 1 RF");
        Assertions.assertEquals(MjAPI.MJ / 10, new AIRobotFetchAndEquipItemStack(robot, s -> true).getPowerCost(),
                "fetch-and-equip inherits the default 1 RF");
        Assertions.assertEquals(MjAPI.MJ / 10, new AIRobotSearchAndGotoBlock(robot).getPowerCost(),
                "search-and-goto inherits the default 1 RF");
        Assertions.assertEquals(MjAPI.MJ / 10, new AIRobotGotoStationToLoadFluids(robot, f -> true).getPowerCost(),
                "goto-station-to-load-fluids inherits the default 1 RF");
        Assertions.assertEquals(MjAPI.MJ / 10, new AIRobotGotoStationToUnloadFluids(robot).getPowerCost(),
                "goto-station-to-unload-fluids inherits the default 1 RF");
        Assertions.assertEquals(MjAPI.MJ / 10, new AIRobotGotoStationAndLoadFluids(robot, f -> true).getPowerCost(),
                "goto-station-and-load-fluids inherits the default 1 RF");
        Assertions.assertEquals(MjAPI.MJ / 10, new AIRobotGotoStationAndUnloadFluids(robot).getPowerCost(),
                "goto-station-and-unload-fluids inherits the default 1 RF");
    }
}
