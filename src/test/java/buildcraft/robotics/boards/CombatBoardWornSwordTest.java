/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.robots.AIRobot;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;
import buildcraft.robotics.ai.MockRobotAccess;

/** The two combat boards unload a WORN sword at a station before they fight on. When there is no station to take
 *  it, that unload fails - and a board with no handler for the failure started the same unload again on the next
 *  tick, forever, never sleeping (the break-board base and the fetch leg already send a failed leg to sleep). The
 *  robot here has no station anywhere, so the unload can only fail. */
public class CombatBoardWornSwordTest extends VanillaSetupBaseTester {

    /** Far fewer than the 1200-tick sleep, far more than the unload's own start-up and search cycles. */
    private static final int CYCLES = 60;

    /** What a board did over {@link #CYCLES} cycles of holding a worn sword with nowhere to unload it. */
    private static final class Run {
        int unloadsEnded;
        boolean sawGotoSleep;
    }

    private static MockRobotAccess robotHoldingAWornSword() {
        MockRobotAccess robot = new MockRobotAccess();
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(sword.getMaxDamage());
        robot.setHeldItem(sword);
        return robot;
    }

    private static void cycle(RedstoneBoardRobot board, Run run) {
        for (int i = 0; i < CYCLES; i++) {
            board.cycle();
            if (board.getDelegateAI() instanceof AIRobotGotoSleep) {
                run.sawGotoSleep = true;
            }
        }
    }

    private static void requireSleptAfterOneFailedUnload(String name, Run run) {
        Assertions.assertTrue(run.sawGotoSleep, "a " + name + " that cannot unload its broken sword goes to sleep");
        Assertions.assertEquals(1, run.unloadsEnded,
                "...after ONE failed unload, not a retry every tick (" + run.unloadsEnded + " ended in " + CYCLES
                        + " cycles)");
    }

    @Test
    public void knightSleepsWhenItCannotUnloadItsBrokenSword() {
        Run run = new Run();
        cycle(new BoardRobotKnight(robotHoldingAWornSword()) {
            @Override
            public void delegateAIEnded(AIRobot ai) {
                if (ai instanceof AIRobotGotoStationAndUnload) {
                    run.unloadsEnded++;
                }
                super.delegateAIEnded(ai);
            }
        }, run);
        requireSleptAfterOneFailedUnload("knight", run);
    }

    @Test
    public void butcherSleepsWhenItCannotUnloadItsBrokenSword() {
        Run run = new Run();
        cycle(new BoardRobotButcher(robotHoldingAWornSword()) {
            @Override
            public void delegateAIEnded(AIRobot ai) {
                if (ai instanceof AIRobotGotoStationAndUnload) {
                    run.unloadsEnded++;
                }
                super.delegateAIEnded(ai);
            }
        }, run);
        requireSleptAfterOneFailedUnload("butcher", run);
    }
}
