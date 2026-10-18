/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit pins for the windowed reads the Zone Planner's map overlay is built on:
 * {@link ZonePlan#forEachChunkIn} (only the chunks in view are visited, whichever way the window compares with
 * the plan's size) and {@link ZoneChunk}'s allocation-free column/row readers.
 */
public class ZonePlanWindowTest {

    /** A chunk that records that it was looked at; the plan must not touch chunks outside the window. */
    private static final class WatchedChunk extends ZoneChunk {
        int looks;

        @Override
        public boolean isEmpty() {
            looks++;
            return super.isEmpty();
        }

        @Override
        public int rowBits(int zChunk) {
            looks++;
            return super.rowBits(zChunk);
        }

        @Override
        public void forEachSet(ColumnVisitor visitor) {
            looks++;
            super.forEachSet(visitor);
        }
    }

    private static WatchedChunk watched(int x, int z) {
        WatchedChunk chunk = new WatchedChunk();
        chunk.set(x & 15, z & 15, true);
        return chunk;
    }

    private static Set<Long> visited(ZonePlan plan, int minCX, int minCZ, int maxCX, int maxCZ) {
        Set<Long> seen = new HashSet<>();
        plan.forEachChunkIn(minCX, minCZ, maxCX, maxCZ, (cx, cz, chunk) -> {
            Assertions.assertSame(plan.getChunkMapping().get(ZonePlan.packChunkKey(cx, cz)), chunk,
                    "the visitor is handed the plan's own chunk for those coordinates");
            Assertions.assertTrue(seen.add(ZonePlan.packChunkKey(cx, cz)), "each chunk is visited once");
        });
        return seen;
    }

    @Test
    public void testSmallWindowVisitsOnlyTheChunksInsideIt() {
        ZonePlan plan = new ZonePlan();
        List<WatchedChunk> all = new ArrayList<>();
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                WatchedChunk chunk = watched(cx, cz);
                all.add(chunk);
                plan.getChunkMapping().put(ZonePlan.packChunkKey(cx, cz), chunk);
            }
        }
        // A 3x2 window against 441 occupied chunks: probed by coordinate.
        Set<Long> seen = visited(plan, 2, -1, 4, 0);
        Set<Long> expected = new HashSet<>();
        for (int cx = 2; cx <= 4; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                expected.add(ZonePlan.packChunkKey(cx, cz));
            }
        }
        Assertions.assertEquals(expected, seen);
        for (WatchedChunk chunk : all) {
            Assertions.assertEquals(0, chunk.looks, "forEachChunkIn hands chunks over; it never reads their contents");
        }
    }

    @Test
    public void testWideWindowAgainstASparsePlanVisitsEveryOccupiedChunkInsideAndNoOther() {
        ZonePlan plan = new ZonePlan();
        plan.set(0, 0, true);          // chunk (0, 0)
        plan.set(-17, 33, true);       // chunk (-2, 2)
        plan.set(100_000, 0, true);    // chunk (6250, 0): outside
        plan.set(0, -100_000, true);   // chunk (0, -6250): outside

        // 4001 x 4001 window against four chunks: filtered from the plan's own chunks.
        Set<Long> seen = visited(plan, -2000, -2000, 2000, 2000);
        Assertions.assertEquals(Set.of(ZonePlan.packChunkKey(0, 0), ZonePlan.packChunkKey(-2, 2)), seen);
    }

    @Test
    public void testWindowEdgesAreInclusive() {
        ZonePlan plan = new ZonePlan();
        for (int c = -1; c <= 3; c++) {
            plan.set(c << 4, c << 4, true);
        }
        Assertions.assertEquals(3, visited(plan, 0, 0, 2, 2).size(), "chunks 0, 1 and 2");
        Assertions.assertEquals(1, visited(plan, 3, 3, 3, 3).size(), "a one-chunk window");
        Assertions.assertEquals(0, visited(plan, 2, 1, 1, 5).size(), "an inverted window is empty, not an error");
        Assertions.assertEquals(0, visited(new ZonePlan(), -5, -5, 5, 5).size(), "an empty plan has nothing to visit");
    }

    @Test
    public void testBothStrategiesAgree() {
        ZonePlan plan = new ZonePlan();
        for (int i = 0; i < 40; i++) {
            plan.set(i * 37 - 500, i * 91 - 900, true);
        }
        // Same chunks, one window small enough to probe and one wide enough to filter.
        Set<Long> probed = visited(plan, -3, -6, 3, 6);
        Set<Long> filtered = new HashSet<>();
        for (long key : plan.getChunkKeys()) {
            int cx = ZonePlan.unpackChunkX(key);
            int cz = ZonePlan.unpackChunkZ(key);
            if (cx >= -3 && cx <= 3 && cz >= -6 && cz <= 6) {
                filtered.add(key);
            }
        }
        Assertions.assertEquals(filtered, probed);
        Assertions.assertEquals(plan.getChunkKeys(), visited(plan, -1_000_000, -1_000_000, 1_000_000, 1_000_000),
                "a window past every chunk visits them all");
    }

    // ── ZoneChunk readers ────────────────────────────────────────────────────────────────

    private static ZoneChunk sample() {
        ZoneChunk chunk = new ZoneChunk();
        chunk.set(0, 0, true);
        chunk.set(15, 0, true);
        chunk.set(3, 7, true);
        chunk.set(4, 7, true);
        chunk.set(15, 15, true);
        return chunk;
    }

    @Test
    public void testForEachSetVisitsExactlyGetAllInTheSameOrder() {
        for (ZoneChunk chunk : new ZoneChunk[] {sample(), ZoneChunk.full(), new ZoneChunk()}) {
            List<String> viaVisitor = new ArrayList<>();
            chunk.forEachSet((x, z) -> viaVisitor.add(x + "," + z));
            List<String> viaList = new ArrayList<>();
            for (int[] p : chunk.getAll()) {
                viaList.add(p[0] + "," + p[1]);
            }
            Assertions.assertEquals(viaList, viaVisitor);
        }
    }

    @Test
    public void testRowBitsReadsOneRowAsAMask() {
        ZoneChunk chunk = sample();
        Assertions.assertEquals(0b1000_0000_0000_0001, chunk.rowBits(0));
        Assertions.assertEquals(0, chunk.rowBits(1));
        Assertions.assertEquals((1 << 3) | (1 << 4), chunk.rowBits(7));
        Assertions.assertEquals(1 << 15, chunk.rowBits(15));
        Assertions.assertEquals(0xFFFF, ZoneChunk.full().rowBits(9));
        Assertions.assertEquals(0, new ZoneChunk().rowBits(3));
    }

    @Test
    public void testFromRowsRebuildsTheChunkAndRecognisesFullAndEmpty() {
        ZoneChunk source = sample();
        int[] rows = new int[16];
        for (int z = 0; z < 16; z++) {
            rows[z] = source.rowBits(z);
        }
        Assertions.assertTrue(source.sameContent(ZoneChunk.fromRows(rows)));

        int[] full = new int[16];
        java.util.Arrays.fill(full, 0xFFFF);
        Assertions.assertTrue(ZoneChunk.fromRows(full).isFull(), "16 full rows are the full flag, as set() would promote");
        Assertions.assertTrue(ZoneChunk.fromRows(new int[16]).isEmpty());
        full[5] = 0xFFFE;
        ZoneChunk almost = ZoneChunk.fromRows(full);
        Assertions.assertFalse(almost.isFull());
        Assertions.assertFalse(almost.get(0, 5));
        Assertions.assertEquals(255, almost.getAll().size());
    }
}
