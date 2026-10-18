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
        /** Whether the unload the board started was asked to empty the hand: that is what makes it fail when the
         *  tool stays in it, and so what sends the board to sleep when a station that is there will not take it. */
        boolean unloadAskedForTheHand;
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
        Assertions.assertTrue(run.unloadAskedForTheHand,
                "the " + name + "'s unload of its broken sword asks for the hand to be empty, so a sword left in it "
                        + "fails the unload");
    }

    @Test
    public void knightSleepsWhenItCannotUnloadItsBrokenSword() {
        Run run = new Run();
        cycle(new BoardRobotKnight(robotHoldingAWornSword()) {
            @Override
            public void delegateAIEnded(AIRobot ai) {
                if (ai instanceof AIRobotGotoStationAndUnload unload) {
                    run.unloadsEnded++;
                    run.unloadAskedForTheHand = unload.emptiesHand();
                }
                super.delegateAIEnded(ai);
            }
        }, run);
        requireSleptAfterOneFailedUnload("knight", run);
    }

    /** The break boards (lumberjack, miner, shovelman, leave cutter) share one base that unloads a worn tool. */
    @Test
    public void breakBoardsSleepWhenTheyCannotUnloadTheirBrokenTool() {
        Run run = new Run();
        MockRobotAccess robot = new MockRobotAccess();
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        axe.setDamageValue(axe.getMaxDamage());
        robot.setHeldItem(axe);
        cycle(new BoardRobotLumberjack(robot) {
            @Override
            public void delegateAIEnded(AIRobot ai) {
                if (ai instanceof AIRobotGotoStationAndUnload unload) {
                    run.unloadsEnded++;
                    run.unloadAskedForTheHand = unload.emptiesHand();
                }
                super.delegateAIEnded(ai);
            }
        }, run);
        requireSleptAfterOneFailedUnload("lumberjack", run);
    }

    @Test
    public void butcherSleepsWhenItCannotUnloadItsBrokenSword() {
        Run run = new Run();
        cycle(new BoardRobotButcher(robotHoldingAWornSword()) {
            @Override
            public void delegateAIEnded(AIRobot ai) {
                if (ai instanceof AIRobotGotoStationAndUnload unload) {
                    run.unloadsEnded++;
                    run.unloadAskedForTheHand = unload.emptiesHand();
                }
                super.delegateAIEnded(ai);
            }
        }, run);
        requireSleptAfterOneFailedUnload("butcher", run);
    }
}
