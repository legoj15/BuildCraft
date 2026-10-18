/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** {@link AIRobotSearchBlock}'s round budget, driven over an in-memory world against a {@link MockRobotAccess}.
 *  7.1.x ran EVERY search on an {@code IterableAlgorithmRunner} bounded to 1000 rounds, the expanding scan
 *  (Farmer, the break boards, Harvester, Pump) included; the port had kept that bound for the random scanners only,
 *  so an expanding scan that kept finding candidates it could not reach ran on for as long as its cube lasted. */
public class AIRobotSearchBlockTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    /** An expanding search is held to its round budget like a random one: with the budget shrunk to a few rounds,
     *  a scan that has not got anywhere near the end of its 64-block cube still ends, with nothing found. */
    @Test
    public void anExpandingSearchEndsWhenItsRoundsRunOut() {
        robot.setPosition(new Vec3(0.5, 64.5, 0.5));
        AIRobotSearchBlock search = new AIRobotSearchBlock(robot, false, pos -> false, 0);
        search.blockAccess = pos -> false;
        search.maxRounds = 3;
        RecordingParent parent = new RecordingParent(robot);
        parent.startDelegateAI(search);

        // Each round scans 10 000 cells of a cube of about two million: three rounds come nowhere near its end.
        for (int i = 0; i < search.maxRounds + 2 && parent.ended == null; i++) {
            search.update();
        }

        Assertions.assertSame(search, parent.ended, "the expanding search ends once its rounds are spent");
        Assertions.assertFalse(search.success(), "with nothing found");
    }

    /** The budget is every search's, but an ordinary expanding search still finds a reachable target well inside it. */
    @Test
    public void anExpandingSearchStillFindsAReachableTarget() {
        robot.setPosition(new Vec3(0.5, 64.5, 0.5));
        BlockPos target = new BlockPos(4, 64, 0);
        AIRobotSearchBlock search = new AIRobotSearchBlock(robot, false, target::equals, 0);
        // Open air in a 9-block cube around the robot, the target itself solid.
        search.blockAccess = pos -> !pos.equals(target) && Math.abs(pos.getX()) <= 8
                && Math.abs(pos.getY() - 64) <= 8 && Math.abs(pos.getZ()) <= 8;
        RecordingParent parent = new RecordingParent(robot);
        parent.startDelegateAI(search);

        for (int i = 0; i < AIRobotSearchBlock.SEARCH_ROUNDS + 2 && parent.ended == null; i++) {
            search.update();
        }

        Assertions.assertSame(search, parent.ended, "the search ends");
        Assertions.assertEquals(target, search.blockFound, "on the reachable target");
    }

    /** Records the delegate that terminated. */
    private static class RecordingParent extends AIRobot {
        AIRobot ended;

        RecordingParent(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            ended = ai;
        }
    }
}
