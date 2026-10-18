/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.path.SoftBlockAccess;

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

    // ---- candidates a search could not reach are remembered ----

    /** A robot standing in a small room with twelve candidates it can never reach (sealed pockets above the ceiling)
     *  nearer than the one it can (in the wall). Every search scans from the centre outwards, so every search starts
     *  its finders on the same sealed candidates first, and a search whose round budget runs out on them reports
     *  nothing - the next one spends its rounds the same way and nothing ever reaches the block in the wall. */
    private final class SealedCandidatesWorld {
        final BlockPos reachable = new BlockPos(6, 64, 0);
        final Set<BlockPos> candidates = new HashSet<>();
        final Set<BlockPos> pockets = new HashSet<>();
        final SoftBlockAccess access;

        SealedCandidatesWorld() {
            candidates.add(reachable);
            for (int x = -5; x <= 5; x++) {
                sealed(new BlockPos(x, 68, 0));
            }
            sealed(new BlockPos(0, 68, 2));
            // The room: eleven by eleven, three high. The wall block `reachable` is outside it, next to its edge.
            access = pos -> pockets.contains(pos)
                    || (Math.abs(pos.getX()) <= 5 && Math.abs(pos.getZ()) <= 5 && pos.getY() >= 63 && pos.getY() <= 65);
        }

        /** A solid candidate with one soft cell beside it (so a robot could stand there) that nothing connects to. */
        private void sealed(BlockPos pos) {
            candidates.add(pos);
            pockets.add(pos.above());
        }

        /** One search of the world, run to its end by hand; the rounds it gets are far fewer than production's. */
        AIRobotSearchBlock search() {
            AIRobotSearchBlock search = new AIRobotSearchBlock(robot, false, candidates::contains, 0);
            search.blockAccess = access;
            search.maxRounds = 4;
            RecordingParent parent = new RecordingParent(robot);
            parent.startDelegateAI(search);
            for (int i = 0; i < search.maxRounds + 20 && parent.ended == null; i++) {
                search.update();
            }
            Assertions.assertSame(search, parent.ended, "fixture: the search ends");
            return search;
        }
    }

    @Test
    public void aSearchThatRanOutOnUnreachableCandidatesDoesNotSpendTheNextOneOnThemAgain() {
        robot.setPosition(new Vec3(0.5, 64.5, 0.5));
        SealedCandidatesWorld world = new SealedCandidatesWorld();

        AIRobotSearchBlock first = world.search();
        Assertions.assertNull(first.blockFound,
                "fixture: the reachable block is behind more sealed candidates than four rounds can give up on");

        AIRobotSearchBlock second = world.search();
        Assertions.assertEquals(world.reachable, second.blockFound,
                "the sealed candidates the first search gave up on are skipped, so the second one gets past them");
    }

    /** The memory is the robot's, not the search's: a robot with none (the default) searches as it always did. */
    @Test
    public void aRobotThatRemembersNothingSearchesAsBefore() {
        MockRobotAccess forgetful = new MockRobotAccess() {
            @Override
            public void unreachableBlockDetected(BlockPos pos) {
            }

            @Override
            public boolean isKnownUnreachableBlock(BlockPos pos) {
                return false;
            }
        };
        forgetful.setPosition(new Vec3(0.5, 64.5, 0.5));
        SealedCandidatesWorld world = new SealedCandidatesWorld();
        for (int attempt = 0; attempt < 3; attempt++) {
            AIRobotSearchBlock search = new AIRobotSearchBlock(forgetful, false, world.candidates::contains, 0);
            search.blockAccess = world.access;
            search.maxRounds = 4;
            RecordingParent parent = new RecordingParent(forgetful);
            parent.startDelegateAI(search);
            for (int i = 0; i < 24 && parent.ended == null; i++) {
                search.update();
            }
            Assertions.assertNull(search.blockFound, "search " + attempt + " ran out on the sealed candidates again");
        }
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
