/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.LinkedList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * Pure-JUnit characterization of the ported {@link PathFinding} A* (no game bootstrap — {@link BlockPos} is a
 * plain holder). Because the cost function is squared Euclidean (non-metric), these assert structural path
 * invariants and loose length bounds, never an exact optimal path.
 */
public class PathFindingTest {

    private static final int Y = 64;
    /** Generous slack factor for the "no wildly long path" bound; the real paths are far tighter. */
    private static final int LEN_SLACK = 4;

    /** Drives a finder to completion with a hard iteration cap so a regression can't hang the suite. */
    private static LinkedList<BlockPos> solve(PathFinding pf) {
        int guard = 0;
        while (!pf.isDone()) {
            pf.iterate(64);
            if (++guard > 100_000) {
                Assertions.fail("pathfinder failed to terminate");
            }
        }
        return pf.getResult();
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
                Math.abs(a.getZ() - b.getZ()));
    }

    /** Asserts the path is a contiguous chain of soft cells from start to end. */
    private static void assertValidPath(SoftBlockAccess access, BlockPos start, BlockPos end,
            LinkedList<BlockPos> path) {
        Assertions.assertFalse(path.isEmpty(), "expected a non-empty path");
        Assertions.assertEquals(start, path.getFirst(), "path must begin at the start");
        Assertions.assertEquals(end, path.getLast(), "path must end at the end");
        for (int i = 1; i < path.size(); i++) {
            BlockPos prev = path.get(i - 1);
            BlockPos cur = path.get(i);
            Assertions.assertEquals(1, chebyshev(prev, cur),
                    "consecutive nodes must be a single step apart: " + prev + " -> " + cur);
            if (i < path.size() - 1) {
                Assertions.assertTrue(access.isSoft(cur), "every intermediate node must be soft: " + cur);
            }
        }
        Assertions.assertTrue(path.size() - 1 <= LEN_SLACK * manhattan(start, end),
                "path length " + (path.size() - 1) + " unreasonably exceeds manhattan " + manhattan(start, end));
    }

    @Test
    public void testStraightLineAcrossOpenField() {
        MockSoftGrid grid = new MockSoftGrid().fill(0, Y, 0, 9, Y, 9);
        BlockPos start = new BlockPos(0, Y, 0);
        BlockPos end = new BlockPos(9, Y, 9);

        LinkedList<BlockPos> path = solve(new PathFinding(grid, start, end));

        assertValidPath(grid, start, end, path);
        // An open diagonal should be walked in exactly chebyshev(=9) steps.
        Assertions.assertEquals(chebyshev(start, end) + 1, path.size(), "open diagonal should be the chebyshev path");
    }

    @Test
    public void testRoutesAroundWall() {
        // A wall spans z=1 with a single gap at x=4; the robot must detour through it.
        MockSoftGrid grid = MockSoftGrid.parse2D(Y,
                "S....",
                "####.",
                "....E");

        LinkedList<BlockPos> path = solve(new PathFinding(grid, grid.start(), grid.end()));

        assertValidPath(grid, grid.start(), grid.end(), path);
        // The detour must be strictly longer than the blocked straight shot.
        Assertions.assertTrue(path.size() - 1 > chebyshev(grid.start(), grid.end()),
                "a wall must force a path longer than the unobstructed chebyshev distance");
    }

    @Test
    public void testUnreachableEnclosedTargetYieldsNoPath() {
        // E is sealed in-plane by '#', and the single layer means nothing is soft above/below it either.
        MockSoftGrid grid = MockSoftGrid.parse2D(Y,
                "S....",
                ".###.",
                ".#E#.",
                ".###.",
                ".....");

        LinkedList<BlockPos> path = solve(new PathFinding(grid, grid.start(), grid.end()));

        Assertions.assertTrue(path.isEmpty(), "an enclosed target must be unreachable (empty path)");
    }

    @Test
    public void testMaxTotalDistanceCapPrunesFarTargets() {
        MockSoftGrid grid = new MockSoftGrid().fill(0, Y, 0, 10, Y, 0);
        BlockPos start = new BlockPos(0, Y, 0);
        BlockPos end = new BlockPos(10, Y, 0);

        // Cap below the start heuristic (10² = 100) prunes everything: no path.
        LinkedList<BlockPos> capped = solve(new PathFinding(grid, start, end, 0, 3f));
        Assertions.assertTrue(capped.isEmpty(), "a tight maxTotalDistance must forbid a path to a far target");

        // Generous cap reaches it.
        LinkedList<BlockPos> reached = solve(new PathFinding(grid, start, end, 0, 20f));
        assertValidPath(grid, start, end, reached);
    }

    @Test
    public void testResultIsDeterministicRegardlessOfIterationGranularity() {
        // Same map, two finders driven at different batch sizes; the tie-break must make them agree exactly.
        MockSoftGrid grid = MockSoftGrid.parse2D(Y,
                "S....",
                "####.",
                "....E");

        PathFinding coarse = new PathFinding(grid, grid.start(), grid.end());
        coarse.iterate(1000);
        while (!coarse.isDone()) {
            coarse.iterate(1000);
        }

        PathFinding fine = new PathFinding(grid, grid.start(), grid.end());
        int guard = 0;
        while (!fine.isDone()) {
            fine.iterate(5);
            if (++guard > 100_000) {
                Assertions.fail("fine-grained finder failed to terminate");
            }
        }

        Assertions.assertEquals(coarse.getResult(), fine.getResult(),
                "path must not depend on how iterations are batched");
    }

    @Test
    public void testStartEqualsEndIsCharacterizedAsOutAndBack() {
        // Known 7.1.x quirk preserved by the port: the centre self-move is forced to 0, so the start cell is
        // never recognised as the end while expanding the start itself. Instead the first neighbour expanded
        // sees the start (== end) as an adjacent "end reached" cell, yielding a degenerate out-and-back path
        // [start, neighbour, start] of length two. A robot told to path to the cell it already occupies thus
        // jiggles out and back rather than standing still — callers must short-circuit "already there" before
        // invoking the finder. Pinned so any future change to this is a conscious one (revisit in Ph4 when the
        // goto AIs actually call this).
        MockSoftGrid grid = new MockSoftGrid().fill(0, Y, 0, 4, Y, 4);
        BlockPos here = new BlockPos(2, Y, 2);

        LinkedList<BlockPos> path = solve(new PathFinding(grid, here, here));

        Assertions.assertEquals(3, path.size(), "start == end is characterized as a 3-cell out-and-back (7.1.x quirk)");
        Assertions.assertEquals(here, path.getFirst(), "out-and-back begins at start");
        Assertions.assertEquals(here, path.getLast(), "out-and-back returns to start (== end)");
        Assertions.assertEquals(1, chebyshev(here, path.get(1)), "the detour cell is adjacent to start");
    }
}
