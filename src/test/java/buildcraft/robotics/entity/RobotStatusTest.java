/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.robots.AIRobot;
import buildcraft.robotics.ai.AIRobotBreak;
import buildcraft.robotics.ai.AIRobotDisposeItems;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotMain;
import buildcraft.robotics.ai.AIRobotSearchBlock;
import buildcraft.robotics.ai.AIRobotShutdown;
import buildcraft.robotics.ai.AIRobotWaitForHome;
import buildcraft.robotics.ai.MockRobotAccess;
import buildcraft.robotics.boards.BoardRobotCarrier;
import buildcraft.robotics.boards.BoardRobotEmpty;
import buildcraft.robotics.boards.BoardRobotKnight;

/** The Robot Goggles status label: what {@link RobotStatus#derive} makes of an AI chain, and the reason each board
 *  gives when it sends a robot to sleep. The chains are built by hand (AIs whose {@code start} does nothing, so they
 *  stay where they are put) for the precedence rules, and by running real boards against a robot with no stations for
 *  the sleep reasons - the sites a wrong reason would hide at, since each is a one-line choice among twelve. */
public class RobotStatusTest extends VanillaSetupBaseTester {

    private final MockRobotAccess robot = new MockRobotAccess();

    /** A controller whose delegates are the given AIs, each the delegate of the one before; none is cycled. */
    private AIRobotMain chain(AIRobot... ais) {
        AIRobotMain main = new AIRobotMain(robot);
        AIRobot parent = main;
        for (AIRobot ai : ais) {
            parent.startDelegateAI(ai);
            parent = ai;
        }
        return main;
    }

    /** Stands in for a board: an AI that names no activity of its own. */
    private AIRobot board() {
        return new BoardRobotEmpty(robot) {
            @Override
            public void update() {}
        };
    }

    private static RobotActivity activity(AIRobotMain main) {
        return RobotStatus.derive(main, false).activity();
    }

    // ── Precedence ──────────────────────────────────────────────────────────

    @Test
    public void aRobotWithNoAiOrNoDelegateIsIdle() {
        Assertions.assertEquals(RobotActivity.IDLE, RobotStatus.derive(null, false).activity());
        Assertions.assertEquals(RobotActivity.IDLE, activity(chain()), "the controller between two cycles");
    }

    @Test
    public void aShutdownIsOutOfPowerOnlyWhenTheBatteryIsEmpty() {
        AIRobotMain main = chain(new AIRobotShutdown(robot));

        Assertions.assertEquals(RobotActivity.OUT_OF_POWER, RobotStatus.derive(main, true).activity());
        Assertions.assertEquals(RobotActivity.SHUT_DOWN, RobotStatus.derive(main, false).activity(),
                "a shutdown with charge left (an orphaned robot) must not claim the battery is empty");
    }

    @Test
    public void theControllersOwnRungsAndAGatesRedirectTakeTheWholeRobotOver() {
        Assertions.assertEquals(RobotActivity.WAITING_FOR_HOME, activity(chain(new AIRobotWaitForHome(robot))));

        AIRobot redirect = new AIRobot(robot);
        AIRobotMain main = new AIRobotMain(robot);
        main.setOverridingAI(redirect);
        main.startDelegateAI(redirect);
        Assertions.assertEquals(RobotActivity.FOLLOWING_GATE, activity(main));

        // The same AI as an ordinary delegate (not the tracked override) is not a gate order.
        Assertions.assertEquals(RobotActivity.WORKING, activity(chain(new AIRobot(robot))));
    }

    @Test
    public void aSleepAnywhereInTheChainNamesItsReasonInsteadOfTheFlightHome() {
        AIRobotGotoSleep sleep = new AIRobotGotoSleep(robot, RobotSleepReason.TARGET_FULL) {
            @Override
            public void start() {}
        };
        AIRobotGotoBlock flightHome = new AIRobotGotoBlock(robot) {
            @Override
            public void start() {}
        };

        RobotStatus status = RobotStatus.derive(chain(board(), sleep, flightHome), false);

        Assertions.assertEquals(RobotActivity.SLEEPING, status.activity());
        Assertions.assertEquals(RobotSleepReason.TARGET_FULL, status.sleepReason());
    }

    @Test
    public void theOutermostJobNamesTheWholeStretchAndTheInnermostLegFillsInOtherwise() {
        AIRobotDisposeItems dispose = new AIRobotDisposeItems(robot) {
            @Override
            public void start() {}
        };
        AIRobotGotoBlock flight = new AIRobotGotoBlock(robot) {
            @Override
            public void start() {}
        };
        AIRobotSearchBlock search = new AIRobotSearchBlock(robot) {
            @Override
            public void start() {}
        };
        AIRobotBreak breaking = new AIRobotBreak(robot) {
            @Override
            public void start() {}
        };

        Assertions.assertEquals(RobotActivity.DISPOSING, activity(chain(board(), dispose, flight)),
                "the flight to the station that takes the cargo is part of disposing of it");
        Assertions.assertEquals(RobotActivity.TRAVELLING, activity(chain(board(), flight)));
        Assertions.assertEquals(RobotActivity.SEARCHING, activity(chain(board(), search)));
        Assertions.assertEquals(RobotActivity.BREAKING, activity(chain(board(), breaking)));
        Assertions.assertEquals(RobotActivity.WORKING, activity(chain(board(), new AIRobot(robot))),
                "a chain nothing names reads as the fallback");
        Assertions.assertEquals(RobotActivity.IDLE, activity(chain(board())),
                "a board with nothing under it is between two passes");
    }

    // ── What each board gives as its reason ─────────────────────────────────

    /** Runs {@code board} under a controller until the robot is asleep, and returns why. The robot has no stations, no
     *  home and no world, so every station search a board starts fails at once. */
    private RobotStatus statusOnceAsleep(RedstoneBoardRobot board) {
        robot.setBoard(board);
        AIRobotMain main = new AIRobotMain(robot);
        for (int i = 0; i < 300; i++) {
            main.cycle();
            RobotStatus status = RobotStatus.derive(main, false);
            if (status.activity() == RobotActivity.SLEEPING) {
                return status;
            }
        }
        throw new AssertionError("the board never put the robot to sleep; it ended doing "
                + RobotStatus.derive(main, false));
    }

    @Test
    public void aBoardThatCannotFindItsToolSleepsForNoTool() {
        Assertions.assertEquals(RobotSleepReason.NO_TOOL, statusOnceAsleep(new BoardRobotKnight(robot)).sleepReason());
    }

    @Test
    public void aBoardThatCannotPutAWornToolAwaySleepsForNowhereToUnload() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(sword.getMaxDamage());
        robot.setHeldItem(sword);

        Assertions.assertEquals(RobotSleepReason.NOWHERE_TO_UNLOAD,
                statusOnceAsleep(new BoardRobotKnight(robot)).sleepReason(),
                "the same branch as the missing tool, told apart by which leg failed");
    }

    @Test
    public void aCarrierWithNothingToLoadSleepsForIt() {
        Assertions.assertEquals(RobotSleepReason.NOTHING_TO_LOAD,
                statusOnceAsleep(new BoardRobotCarrier(robot)).sleepReason());
    }

    @Test
    public void aRobotWithTheEmptyBoardSleepsForNoProgram() {
        Assertions.assertEquals(RobotSleepReason.NO_PROGRAM,
                statusOnceAsleep(new BoardRobotEmpty(robot)).sleepReason());
    }

    // ── Wire format and text ────────────────────────────────────────────────

    @Test
    public void everyStatusSurvivesTheSyncedIds() {
        for (RobotActivity activity : RobotActivity.values()) {
            RobotStatus plain = new RobotStatus(activity, null);
            Assertions.assertEquals(plain, RobotStatus.ofIds(activity.ordinal(), plain.sleepReasonId()));
        }
        for (RobotSleepReason reason : RobotSleepReason.values()) {
            RobotStatus asleep = new RobotStatus(RobotActivity.SLEEPING, reason);
            Assertions.assertEquals(asleep,
                    RobotStatus.ofIds(RobotActivity.SLEEPING.ordinal(), asleep.sleepReasonId()));
        }
        Assertions.assertEquals(new RobotStatus(RobotActivity.IDLE, null), RobotStatus.ofIds(999, 999),
                "an id from a newer server reads as idle with no reason, not as a crash");
        Assertions.assertNull(new RobotStatus(RobotActivity.WORKING, RobotSleepReason.NO_WORK).sleepReason(),
                "a reason belongs to a sleep only");
    }
}
