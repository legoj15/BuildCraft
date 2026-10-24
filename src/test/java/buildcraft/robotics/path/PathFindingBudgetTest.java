/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * The wall-clock driving of the A* search: a finder driven to a time budget must reach the SAME result as
 * one driven by expansion counts, a search whose per-round budget is shrunk must merely spread its work
 * over more rounds (never change its answer or stall), and a time-slice must always make at least one step
 * of progress. All pure JUnit over {@link MockSoftGrid} — no live world.
 */
public class PathFindingBudgetTest {

    private static final int Y = 64;

    /** Drives a finder to completion with iterate(int), the count-driven path. */
    private static LinkedList<BlockPos> solveByCount(PathFinding pf, int step) {
        int guard = 0;
        while (!pf.isDone()) {
            pf.iterate(step);
            if (++guard > 200_000) {
                Assertions.fail("count-driven finder failed to terminate");
            }
        }
        return pf.getResult();
    }

    /** Drives a finder to completion one time-slice at a time. */
    private static LinkedList<BlockPos> solveByTime(PathFinding pf, long sliceNanos) {
        int guard = 0;
        while (!pf.isDone()) {
            int before = pf.expansions();
            pf.iterateForNanos(sliceNanos);
            if (pf.expansions() == before && !pf.isDone()) {
                Assertions.fail("a time slice made no progress at all (expansions stuck at " + before + ")");
            }
            if (++guard > 200_000) {
                Assertions.fail("time-driven finder failed to terminate");
            }
        }
        return pf.getResult();
    }

    /** The wall-detour map: ties abound among equal-cost cells, so any ordering drift shows up as a different
     *  path. */
    private static MockSoftGrid detourMap() {
        return MockSoftGrid.parse2D(Y,
                "S....",
                "####.",
                "....E");
    }

    @Test
    public void aTimeBudgetedFinderReachesTheSameResultAsACountDrivenOne() {
        MockSoftGrid grid = detourMap();

        LinkedList<BlockPos> byCount = solveByCount(new PathFinding(grid, grid.start(), grid.end()), 1000);
        LinkedList<BlockPos> byTime = solveByTime(new PathFinding(grid, grid.start(), grid.end()), 1_000_000L);

        Assertions.assertEquals(byCount, byTime,
                "the wall-clock budget only decides WHEN expansions happen, never which node expands next,"
                        + " so the result must be identical");
    }

    /** The tightest possible slice still advances: iterateForNanos always makes at least one step, so no
     *  caller can spin its rounds making no progress. */
    @Test
    public void theTightestSliceStillMakesAtLeastOneStepAndTheSamePath() {
        MockSoftGrid grid = detourMap();

        LinkedList<BlockPos> bySingles = solveByTime(new PathFinding(grid, grid.start(), grid.end()), 1L);

        Assertions.assertEquals(solveByCount(new PathFinding(grid, grid.start(), grid.end()), 64), bySingles,
                "one-expansion-at-a-time driving is the limit of time budgeting; the path must not notice");
    }

    /** The big wall fixture from the search tests — an 80x80 layer, a full wall at x=40 with its only gap at
     *  the far end, so reaching the target costs thousands of expansions (far more than one finder share,
     *  which is what makes the one-round assertions machine-speed independent) — behind a scan line of
     *  hundreds of non-candidate deltas, so the scan phase has real work the round budget can gate. */
    private static final class WallFixture {
        static final BlockPos START = new BlockPos(38, Y, 2);
        static final BlockPos TARGET = new BlockPos(42, Y, 2);
        static final Set<BlockPos> WALL = wall();

        private static Set<BlockPos> wall() {
            Set<BlockPos> wall = new HashSet<>();
            for (int z = 0; z < 79; z++) {
                wall.add(new BlockPos(40, Y, z));
            }
            return wall;
        }

        static SoftBlockAccess access(MockSoftGrid grid) {
            return new SoftBlockAccess() {
                @Override
                public boolean isSoft(BlockPos pos) {
                    return !WALL.contains(pos) && !pos.equals(TARGET) && grid.isSoft(pos);
                }
            };
        }

        /** Scan deltas: a westward line of 500 non-candidates, then the target — one scan step per delta. */
        static java.util.Iterator<BlockPos> scanLine() {
            List<BlockPos> deltas = new java.util.ArrayList<>();
            for (int i = 500; i >= 1; i--) {
                deltas.add(new BlockPos(-i, 0, 0));
            }
            deltas.add(TARGET.subtract(START));
            return deltas.iterator();
        }

        static PathFindingSearch search(SoftBlockAccess access) {
            return new PathFindingSearch(access, START, scanLine(), TARGET::equals, 0, 400f, null,
                    new HashSet<>());
        }
    }

    @Test
    public void aShrunkScanBudgetSpreadsTheSearchOverRoundsWithoutChangingItsAnswer() {
        MockSoftGrid grid = new MockSoftGrid().fill(0, Y, 0, 79, Y, 79);
        PathFindingSearch reference = WallFixture.search(WallFixture.access(grid));
        int guard = 0;
        while (!reference.isDone()) {
            reference.iterate();
            if (++guard > 100_000) {
                Assertions.fail("default-budget search failed to terminate");
            }
        }
        Assertions.assertEquals(WallFixture.TARGET, reference.getResultTarget(),
                "fixture: the search finds the target behind the wall");

        PathFindingSearch starved = WallFixture.search(WallFixture.access(grid));
        long realBudget = PathFindingSearch.TICK_BUDGET_NANOS;
        PathFindingSearch.TICK_BUDGET_NANOS = 100L; // 100ns: one scan step at most, then the gate closes
        try {
            starved.iterate();
            Assertions.assertFalse(starved.isDone(),
                    "a starved scan round gets nowhere near the target's delta");
            Assertions.assertNull(starved.getResultTarget(),
                    "nothing is locked onto after one starved round");
        } finally {
            PathFindingSearch.TICK_BUDGET_NANOS = realBudget;
        }

        guard = 0;
        while (!starved.isDone()) {
            starved.iterate();
            if (++guard > 100_000) {
                Assertions.fail("restored-budget search failed to terminate");
            }
        }

        Assertions.assertEquals(reference.getResultTarget(), starved.getResultTarget(),
                "the scan budget decides how many rounds a search takes, never which target it locks");
        Assertions.assertEquals(reference.getResult(), starved.getResult(),
                "...and never which path it returns");
        Assertions.assertEquals(WallFixture.START, starved.getResult().getFirst(), "the path starts at the robot");
        Assertions.assertEquals(WallFixture.TARGET, starved.getResult().getLast(), "the path ends on the target");
    }
}
