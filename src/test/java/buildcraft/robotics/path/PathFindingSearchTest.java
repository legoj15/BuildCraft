/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;

/**
 * Pure-JUnit characterization of {@link PathFindingSearch}: the nearest-matching-block scanner that drives a
 * pool of {@link PathFinding} instances and reserves the block it locks onto.
 */
public class PathFindingSearchTest {

    private static final int Y = 64;
    private static final double NO_END_SLACK = 0;
    private static final float MAX_DIST = 64f;

    /** Deltas (relative to start) covering a flat {@code [-r, r]} square in the y=start plane, nearest first. */
    private static Iterator<BlockPos> squareDeltas(int r) {
        List<BlockPos> deltas = new ArrayList<>();
        for (int ring = 0; ring <= r; ring++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        deltas.add(new BlockPos(dx, 0, dz));
                    }
                }
            }
        }
        return deltas.iterator();
    }

    private static PathFindingSearch search(MockSoftGrid grid, BlockPos start, Set<BlockPos> reservations, IZone zone) {
        return new PathFindingSearch(grid, start, squareDeltas(6), grid.targetFilter(), NO_END_SLACK, MAX_DIST, zone,
                reservations);
    }

    private static void run(PathFindingSearch s) {
        int guard = 0;
        while (!s.isDone()) {
            s.iterate();
            if (++guard > 100_000) {
                Assertions.fail("search failed to terminate");
            }
        }
    }

    @Test
    public void testFindsReachableTargetAndReservesIt() {
        MockSoftGrid grid = MockSoftGrid.parse2D(Y,
                "S....",
                ".....",
                "..T..");
        BlockPos target = new BlockPos(2, Y, 2);
        Set<BlockPos> reservations = new HashSet<>();

        PathFindingSearch s = search(grid, grid.start(), reservations, null);
        run(s);

        Assertions.assertEquals(target, s.getResultTarget(), "search should lock onto the reachable target");
        Assertions.assertFalse(s.getResult().isEmpty(), "a path to the target should exist");
        Assertions.assertEquals(grid.start(), s.getResult().getFirst(), "path starts at the robot");
        Assertions.assertEquals(target, s.getResult().getLast(), "path ends at the target");
        Assertions.assertTrue(reservations.contains(target), "the found target must be reserved");
    }

    @Test
    public void testSkipsTargetWithNoSoftNeighbour() {
        // T is encased in solid in-plane and void above/below: nothing can stand next to it.
        MockSoftGrid grid = MockSoftGrid.parse2D(Y,
                "S......",
                ".......",
                "..###..",
                "..#T#..",
                "..###..");

        PathFindingSearch s = search(grid, grid.start(), new HashSet<>(), null);
        run(s);

        Assertions.assertNull(s.getResultTarget(), "an unreachable (no soft neighbour) target must be skipped");
    }

    @Test
    public void testSkipsTargetInUnloadedChunk() {
        BlockPos start = new BlockPos(0, Y, 0);
        BlockPos target = new BlockPos(2, Y, 0);
        // Reachable in principle (soft neighbour at x=1) but its chunk is unloaded.
        MockSoftGrid grid = new MockSoftGrid()
                .soft(start).soft(new BlockPos(1, Y, 0))
                .target(target).unloaded(target);

        PathFindingSearch s = search(grid, start, new HashSet<>(), null);
        run(s);
        Assertions.assertNull(s.getResultTarget(), "a target in an unloaded chunk must be skipped");

        // Control: the very same layout with the chunk loaded IS found, proving the skip was the load check.
        MockSoftGrid loaded = new MockSoftGrid()
                .soft(start).soft(new BlockPos(1, Y, 0))
                .target(target);
        PathFindingSearch s2 = search(loaded, start, new HashSet<>(), null);
        run(s2);
        Assertions.assertEquals(target, s2.getResultTarget(), "control: a loaded target is found");
    }

    @Test
    public void testPreReservedTargetIsExcluded() {
        MockSoftGrid grid = MockSoftGrid.parse2D(Y,
                "S....",
                ".....",
                "..T..");
        BlockPos target = new BlockPos(2, Y, 2);

        Set<BlockPos> reservations = new HashSet<>();
        reservations.add(target);

        PathFindingSearch s = search(grid, grid.start(), reservations, null);
        run(s);

        Assertions.assertNull(s.getResultTarget(), "a target already reserved by another robot must be skipped");
    }

    @Test
    public void testZoneRestrictionExcludesOutOfZoneTargets() {
        MockSoftGrid grid = MockSoftGrid.parse2D(Y,
                "S....",
                ".....",
                "..T..");
        BlockPos target = new BlockPos(2, Y, 2);

        // Zone that excludes the target -> skipped.
        PathFindingSearch excluded = search(grid, grid.start(), new HashSet<>(), new BoxZone(0, Y, 0, 1, Y, 1));
        run(excluded);
        Assertions.assertNull(excluded.getResultTarget(), "a target outside the work zone must be skipped");

        // Zone that includes the target -> found.
        PathFindingSearch included = search(grid, grid.start(), new HashSet<>(), new BoxZone(0, Y, 0, 4, Y, 4));
        run(included);
        Assertions.assertEquals(target, included.getResultTarget(), "a target inside the work zone is found");
    }

    /** Minimal axis-aligned-box {@link IZone} for zone-filtering tests; only {@link #contains(Vec3)} is exercised. */
    private static final class BoxZone implements IZone {
        private final double x0;
        private final double y0;
        private final double z0;
        private final double x1;
        private final double y1;
        private final double z1;

        BoxZone(int x0, int y0, int z0, int x1, int y1, int z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            // +1 so the integer block at (x1,y1,z1) is fully contained (block centre at +0.5).
            this.x1 = x1 + 1;
            this.y1 = y1 + 1;
            this.z1 = z1 + 1;
        }

        @Override
        public boolean contains(Vec3 p) {
            return p.x >= x0 && p.x <= x1 && p.y >= y0 && p.y <= y1 && p.z >= z0 && p.z <= z1;
        }

        @Override
        public double distanceTo(BlockPos pos) {
            return 0;
        }

        @Override
        public double distanceToSquared(BlockPos pos) {
            return 0;
        }

        @Override
        public BlockPos getRandomBlockPos(Random rand) {
            return BlockPos.ZERO;
        }
    }
}
