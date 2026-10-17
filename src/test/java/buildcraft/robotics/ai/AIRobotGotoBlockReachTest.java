/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.ai;

import java.util.LinkedList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import buildcraft.robotics.path.MockSoftGrid;
import buildcraft.robotics.path.PathFinding;

/** Pins {@link AIRobotGotoBlock#withinFlightReach} against the finder the flight actually runs: boards use the
 *  predicate to decide which targets to send a robot to, so it must say "reachable" exactly where the
 *  flight's {@code PathFinding} (8-block arrival radius, {@link AIRobotGotoBlock#FLIGHT_REACH} total) finds a
 *  path along an open line, and "unreachable" just past it. */
public class AIRobotGotoBlockReachTest {

    private static final int Y = 64;

    private static LinkedList<BlockPos> fly(int distance) {
        MockSoftGrid corridor = new MockSoftGrid().fill(0, Y, 0, distance, Y, 0);
        PathFinding finder = new PathFinding(corridor, new BlockPos(0, Y, 0), new BlockPos(distance, Y, 0), 8,
                AIRobotGotoBlock.FLIGHT_REACH);
        int guard = 0;
        while (!finder.isDone()) {
            finder.iterate(64);
            if (++guard > 100_000) {
                Assertions.fail("pathfinder failed to terminate");
            }
        }
        return finder.getResult();
    }

    @Test
    public void predicateMatchesTheFinderAtTheEdge() {
        int reach = AIRobotGotoBlock.FLIGHT_REACH;
        BlockPos origin = new BlockPos(0, Y, 0);

        Assertions.assertFalse(fly(reach).isEmpty(), "the flight finds a path at exactly its reach");
        Assertions.assertTrue(AIRobotGotoBlock.withinFlightReach(origin, new BlockPos(reach, Y, 0)),
                "…and the predicate agrees");

        Assertions.assertTrue(fly(reach + 1).isEmpty(), "one block past its reach the flight fails outright");
        Assertions.assertFalse(AIRobotGotoBlock.withinFlightReach(origin, new BlockPos(reach + 1, Y, 0)),
                "…and the predicate agrees");
    }

    @Test
    public void reachIsSevenOneXVerbatim() {
        Assertions.assertEquals(96, AIRobotGotoBlock.FLIGHT_REACH, "7.1.x passed 96 as maxTotalDistance");
        Assertions.assertFalse(AIRobotGotoBlock.withinFlightReach(BlockPos.ZERO, new BlockPos(100, 0, 0)),
                "a target 100 blocks away is out of reach");
    }
}
