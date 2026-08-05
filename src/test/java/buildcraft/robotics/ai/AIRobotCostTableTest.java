/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Pins the per-leaf power costs (in micro-MJ, the unit {@code AIRobot.getPowerCost} is charged in) and their
 *  relative ordering. The MJ constants are decisions, chosen not derived — each cost is 7.1.x's RF value at
 *  BuildCraft's canonical 1 MJ = 10 RF bridge, except the Goto family which 7.1.x charged 30 RF (3 MJ). */
public class AIRobotCostTableTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void fetchItemIsTheMostExpensiveLeaf() {
        Assertions.assertEquals(15, new AIRobotFetchItem(robot).getPowerCost(),
                "picking up a dropped item is the priciest single action (7.1.x 150 RF / 10)");
    }

    @Test
    public void unloadCostsMoreThanLoad() {
        Assertions.assertEquals(10, new AIRobotUnload(robot).getPowerCost(),
                "unload is 7.1.x 100 RF / 10");
        Assertions.assertEquals(8, new AIRobotLoad(robot).getPowerCost(),
                "load is 7.1.x 80 RF / 10");
    }

    @Test
    public void theGotoFamilySharesAModestCost() {
        // AIRobotGoto is abstract, so its cost is pinned through its concrete subclasses.
        Assertions.assertEquals(3, new AIRobotGotoBlock(robot).getPowerCost(),
                "movement is 7.1.x 30 RF / 10 — the shared AIRobotGoto base cost");
        Assertions.assertEquals(3, new AIRobotStraightMoveTo(robot).getPowerCost(),
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
        Assertions.assertEquals(buildcraft.api.mj.MjAPI.MJ / 10, sleep.getPowerCost(),
                "tick 0 (sleptTime 0) pays MJ/10");
        sleep.update(); // sleptTime 1
        Assertions.assertEquals(0, sleep.getPowerCost(),
                "the tick after a pay tick is free");
        for (int i = 0; i < 9; i++) {
            sleep.update(); // sleptTime 10
        }
        Assertions.assertEquals(buildcraft.api.mj.MjAPI.MJ / 10, sleep.getPowerCost(),
                "the tenth tick pays MJ/10 again — the 0.1 RF/tick idle rate");
    }

    @Test
    public void orderingIsFetchThenUnloadThenLoadThenGoto() {
        long fetch = new AIRobotFetchItem(robot).getPowerCost();
        long unload = new AIRobotUnload(robot).getPowerCost();
        long load = new AIRobotLoad(robot).getPowerCost();
        long gotoCost = new AIRobotGotoBlock(robot).getPowerCost();
        Assertions.assertTrue(fetch > unload && unload > load && load > gotoCost,
                "FetchItem (15) > Unload (10) > Load (8) > Goto (3)");
    }
}
