/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit pins for {@link ZonePlan#getWithOffset}: the tile-relative &lt;-&gt; world translation the Zone Planner
 * runs on every transfer. It shifts whole chunks (rows of 16 bits) rather than re-marking every column, so these
 * compare it with the plain per-column meaning across aligned, unaligned and negative offsets, and trap any use of
 * the per-column enumeration.
 */
public class ZonePlanOffsetTest {

    /** Fails the test the moment anything enumerates the plan column by column. */
    private static final class NoEnumerationPlan extends ZonePlan {
        @Override
        public List<int[]> getAll() {
            throw new AssertionError("getWithOffset must shift chunks, not enumerate every column");
        }
    }

    private static Set<Long> cells(ZonePlan plan) {
        Set<Long> out = new HashSet<>();
        for (int[] p : plan.getAll()) {
            out.add(pack(p[0], p[1]));
        }
        return out;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    /** The per-column meaning of an offset: every marked column moved by (dx, dz). */
    private static Set<Long> shiftedCells(ZonePlan plan, int dx, int dz) {
        Set<Long> out = new HashSet<>();
        for (int[] p : plan.getAll()) {
            out.add(pack(p[0] + dx, p[1] + dz));
        }
        return out;
    }

    private static final int[][] OFFSETS = {
            {0, 0}, {16, 0}, {0, -16}, {-48, 32}, {1, 0}, {0, 1}, {-1, -1}, {15, 15}, {16, 1}, {5, 13}, {-7, -23},
            {-1000, 777}, {123_456, -654_321}, {31, -17}, {8, 8}, {-8, 8}
    };

    private static ZonePlan randomPlan(long seed) {
        Random rand = new Random(seed);
        ZonePlan plan = new ZonePlan();
        // A full chunk, a nearly full one, a sparse one and some strays, around and across the origin.
        plan.paintRect(-20, -20, 20, 20, true);
        plan.set(0, 0, false);
        plan.set(-1, -1, false);
        for (int i = 0; i < 600; i++) {
            plan.set(rand.nextInt(400) - 200, rand.nextInt(400) - 200, true);
        }
        for (int i = 0; i < 300; i++) {
            plan.set(rand.nextInt(400) - 200, rand.nextInt(400) - 200, false);
        }
        return plan;
    }

    @Test
    public void testOffsetMatchesShiftingEveryColumn() {
        ZonePlan plan = randomPlan(1234L);
        Assertions.assertFalse(plan.isEmpty(), "baseline: the sample plan marks something");
        for (int[] off : OFFSETS) {
            ZonePlan shifted = plan.getWithOffset(off[0], off[1]);
            Assertions.assertEquals(shiftedCells(plan, off[0], off[1]), cells(shifted),
                    "offset (" + off[0] + ", " + off[1] + ")");
        }
    }

    @Test
    public void testOffsetOfASingleColumnLandsOnTheRightColumnAtEveryChunkEdge() {
        for (int x : new int[] {-17, -16, -1, 0, 1, 15, 16}) {
            for (int z : new int[] {-17, -16, -1, 0, 1, 15, 16}) {
                ZonePlan plan = new ZonePlan();
                plan.set(x, z, true);
                for (int[] off : OFFSETS) {
                    ZonePlan shifted = plan.getWithOffset(off[0], off[1]);
                    Assertions.assertEquals(Set.of(pack(x + off[0], z + off[1])), cells(shifted),
                            "column (" + x + ", " + z + ") by (" + off[0] + ", " + off[1] + ")");
                }
            }
        }
    }

    @Test
    public void testOffsetNeverEnumeratesColumns() {
        NoEnumerationPlan plan = new NoEnumerationPlan();
        plan.paintRect(-300, -300, 300, 300, true);
        plan.set(7, 7, false);
        for (int[] off : OFFSETS) {
            Assertions.assertDoesNotThrow(() -> plan.getWithOffset(off[0], off[1]),
                    "offset (" + off[0] + ", " + off[1] + ")");
        }
    }

    @Test
    public void testChunkAlignedOffsetMovesTheChunkKeysAndKeepsFullChunksFull() {
        ZonePlan plan = new ZonePlan();
        plan.paintRect(0, 0, 31, 15, true); // two full chunks
        plan.set(-5, 40, true);              // one sparse chunk

        ZonePlan shifted = plan.getWithOffset(160, -32);
        Assertions.assertEquals(3, shifted.getChunkKeys().size());
        Assertions.assertTrue(shifted.getChunkMapping().get(ZonePlan.packChunkKey(10, -2)).isFull());
        Assertions.assertTrue(shifted.getChunkMapping().get(ZonePlan.packChunkKey(11, -2)).isFull());
        Assertions.assertTrue(shifted.hasChunk(-1 + 10, 2 - 2), "the sparse chunk moved by whole chunks too");
        Assertions.assertTrue(shifted.get(-5 + 160, 40 - 32));
    }

    @Test
    public void testUnalignedOffsetOfFullChunksPromotesWhereTheyOverlapAWholeChunk() {
        // A 3x3 block of full chunks shifted by (8, 8): the middle chunk of the result is wholly covered.
        ZonePlan plan = new ZonePlan();
        plan.paintRect(0, 0, 47, 47, true);
        ZonePlan shifted = plan.getWithOffset(8, 8);
        ZoneChunk middle = shifted.getChunkMapping().get(ZonePlan.packChunkKey(1, 1));
        Assertions.assertNotNull(middle);
        Assertions.assertTrue(middle.isFull(), "a fully covered result chunk is stored as full, as set() would");
        Assertions.assertEquals(shiftedCells(plan, 8, 8), cells(shifted));
        Assertions.assertEquals(16, shifted.getChunkKeys().size(), "a 4x4 grid of chunks, and none that holds no column");
        Assertions.assertFalse(shifted.hasChunk(4, 4));
        Assertions.assertFalse(shifted.hasChunk(-1, 0));
    }

    @Test
    public void testOffsetResultIsIndependentOfTheSource() {
        ZonePlan plan = new ZonePlan();
        plan.set(3, 3, true);
        ZonePlan same = plan.getWithOffset(0, 0);
        ZonePlan moved = plan.getWithOffset(16, 16);

        plan.set(4, 4, true);
        Assertions.assertFalse(same.get(4, 4), "a zero offset still copies");
        Assertions.assertFalse(moved.get(20, 20), "so does an aligned one");
        same.set(9, 9, true);
        moved.set(30, 30, true);
        Assertions.assertFalse(plan.get(9, 9) || plan.get(30, 30), "and edits to a result never reach the source");
    }

    @Test
    public void testOffsetOfAnEmptyPlanIsEmpty() {
        Assertions.assertTrue(new ZonePlan().getWithOffset(5, 5).isEmpty());
        Assertions.assertTrue(new ZonePlan().getWithOffset(16, 0).isEmpty());
    }

    @Test
    public void testOffsetRoundTripRestoresThePlan() {
        ZonePlan plan = randomPlan(99L);
        ZonePlan there = plan.getWithOffset(-1234, 4321);
        ZonePlan back = there.getWithOffset(1234, -4321);
        Assertions.assertTrue(plan.sameContent(back), "tile-relative -> world -> tile-relative is the identity");
    }

    @Test
    public void testOffsetResultCountsAsModified() {
        ZonePlan shifted = randomPlan(5L).getWithOffset(3, 3);
        Assertions.assertTrue(shifted.modCount() > 0,
                "a built plan reads as edited, like one made by set(): watchers key their caches on the counter");
    }
}
