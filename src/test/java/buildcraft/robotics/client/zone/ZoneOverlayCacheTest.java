/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.robotics.zone.ZoneChunk;
import buildcraft.robotics.zone.ZonePlan;

/**
 * Pure-JUnit pins for {@link ZoneOverlayCache}, which the Zone Planner's map overlay draws from every frame: the
 * painted cells of a layer inside the visible chunk window, worked out only for chunks in view and only again
 * when the layer is edited or the window, zoom or planner position moves. The cache returns the same cells while
 * nothing has changed, so the per-frame cost is the visible cells and not the layer.
 */
public class ZoneOverlayCacheTest {

    /** Window (world chunks) used by most tests: chunks 0..3 on both axes, i.e. blocks 0..63. */
    private static final int[] WIN = {0, 0, 3, 3};

    private static ZoneOverlayCache.Cells cells(ZoneOverlayCache cache, int slot, ZonePlan plan, int tileX, int tileZ,
                                                int[] win, int stride) {
        return cache.cells(slot, plan, tileX, tileZ, win[0], win[1], win[2], win[3], stride);
    }

    private static Set<Long> asSet(ZoneOverlayCache.Cells cells) {
        Set<Long> out = new HashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            Assertions.assertTrue(out.add(pack(cells.x(i), cells.z(i))), "no cell is listed twice");
        }
        return out;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    /** The plain meaning of the cells: every stride x stride world cell of the window with a painted column. */
    private static Set<Long> naive(ZonePlan plan, int tileX, int tileZ, int[] win, int stride) {
        Set<Long> out = new HashSet<>();
        int x0 = win[0] << 4;
        int z0 = win[1] << 4;
        for (int wx = x0; wx < (win[2] + 1) << 4; wx++) {
            for (int wz = z0; wz < (win[3] + 1) << 4; wz++) {
                if (plan.get(wx - tileX, wz - tileZ)) {
                    out.add(pack(x0 + Math.floorDiv(wx - x0, stride) * stride, z0 + Math.floorDiv(wz - z0, stride) * stride));
                }
            }
        }
        return out;
    }

    // ── What the cells are ───────────────────────────────────────────────────────────────

    @Test
    public void testCellsAreTheWorldColumnsOfTheTileRelativeLayer() {
        ZonePlan plan = new ZonePlan();
        plan.set(3, 4, true);
        plan.set(-2, 0, true);
        ZoneOverlayCache.Cells cells = cells(new ZoneOverlayCache(), 0, plan, 10, 20, WIN, 1);
        Assertions.assertEquals(Set.of(pack(13, 24), pack(8, 20)), asSet(cells),
                "tile-relative (3, 4) and (-2, 0) with the planner at (10, 20)");
        Assertions.assertEquals(1, cells.stride());
    }

    @Test
    public void testCellsMatchThePlainMeaningAtEveryStrideAndAnyPlannerPosition() {
        ZonePlan plan = new ZonePlan();
        plan.paintRect(-40, -40, -5, 3, true);
        plan.paintRect(10, 10, 12, 50, true);
        plan.set(33, -1, true);
        for (int[] tile : new int[][] {{0, 0}, {1, 0}, {5, 13}, {-3, 7}, {16, -16}, {37, 41}}) {
            for (int stride : new int[] {1, 2, 4, 8}) {
                ZoneOverlayCache.Cells cells = cells(new ZoneOverlayCache(), 0, plan, tile[0], tile[1], WIN, stride);
                Assertions.assertEquals(naive(plan, tile[0], tile[1], WIN, stride), asSet(cells),
                        "planner (" + tile[0] + ", " + tile[1] + "), stride " + stride);
                Assertions.assertEquals(stride, cells.stride());
            }
        }
    }

    @Test
    public void testOnlyTheWindowIsListed() {
        ZonePlan plan = new ZonePlan();
        plan.paintRect(-200, -200, 200, 200, true);
        ZoneOverlayCache.Cells cells = cells(new ZoneOverlayCache(), 0, plan, 0, 0, new int[] {1, 2, 2, 2}, 1);
        for (int i = 0; i < cells.size(); i++) {
            Assertions.assertTrue(cells.x(i) >= 16 && cells.x(i) < 48 && cells.z(i) >= 32 && cells.z(i) < 48,
                    "cell (" + cells.x(i) + ", " + cells.z(i) + ") lies in chunks x 1..2, z 2..2");
        }
        Assertions.assertEquals(32 * 16, cells.size());
    }

    @Test
    public void testAFullSurveyLayerStaysBoundedByTheViewAtSurveyZoom() {
        ZonePlan plan = new ZonePlan();
        plan.paintRect(ZonePlan.SURVEY_MIN_BLOCK, ZonePlan.SURVEY_MIN_BLOCK,
                ZonePlan.SURVEY_MAX_BLOCK, ZonePlan.SURVEY_MAX_BLOCK, true);
        int[] win = {-60, -60, 59, 59}; // 120 x 120 chunks: 1920 blocks across
        ZoneOverlayCache.Cells cells = cells(new ZoneOverlayCache(), 0, plan, 5, 9, win, 8);
        int cellsAcross = 120 * 16 / 8;
        Assertions.assertTrue(cells.size() <= cellsAcross * cellsAcross,
                "one cell per 8x8 blocks, not per block: " + cells.size());
        Assertions.assertTrue(cells.size() > cellsAcross * cellsAcross * 3 / 4, "and the square is mostly covered");
    }

    // ── Cost: what is visited, what is reused ─────────────────────────────────────────────

    /** A chunk that records that it was read; chunks outside the window must never be. */
    private static final class WatchedChunk extends ZoneChunk {
        int reads;

        @Override
        public int rowBits(int zChunk) {
            reads++;
            return super.rowBits(zChunk);
        }
    }

    @Test
    public void testChunksOutsideTheWindowAreNeverRead() {
        ZonePlan plan = new ZonePlan();
        WatchedChunk[] outside = new WatchedChunk[400];
        int n = 0;
        for (int cx = -10; cx < 10; cx++) {
            for (int cz = -10; cz < 10; cz++) {
                WatchedChunk chunk = new WatchedChunk();
                chunk.set(1, 1, true);
                plan.getChunkMapping().put(ZonePlan.packChunkKey(cx, cz), chunk);
                if (cx < 0 || cx > 3 || cz < 0 || cz > 3) {
                    outside[n++] = chunk;
                }
            }
        }
        ZoneOverlayCache.Cells cells = cells(new ZoneOverlayCache(), 0, plan, 0, 0, WIN, 1);
        Assertions.assertEquals(16, cells.size(), "the 4x4 chunks in view each hold one column");
        for (int i = 0; i < n; i++) {
            Assertions.assertEquals(0, outside[i].reads, "a chunk out of view was read");
        }
    }

    @Test
    public void testChunksNextToTheWindowAreReadOnlyWhenTheirColumnsReachIn() {
        // Planner at (8, 8): tile-relative chunk -1 straddles world blocks -8..7, so it is read; relative chunk -2
        // ends at world block -9 and cannot reach a window starting at block 0, so it is not.
        ZonePlan plan = new ZonePlan();
        WatchedChunk straddling = new WatchedChunk();
        straddling.set(15, 15, true); // relative (-1, -1) block (-1, -1) -> world (7, 7)
        WatchedChunk beyond = new WatchedChunk();
        beyond.set(15, 15, true);
        plan.getChunkMapping().put(ZonePlan.packChunkKey(-1, -1), straddling);
        plan.getChunkMapping().put(ZonePlan.packChunkKey(-2, -2), beyond);
        ZoneOverlayCache.Cells cells = cells(new ZoneOverlayCache(), 0, plan, 8, 8, WIN, 1);
        Assertions.assertEquals(Set.of(pack(7, 7)), asSet(cells));
        Assertions.assertTrue(straddling.reads > 0);
        Assertions.assertEquals(0, beyond.reads);
    }

    @Test
    public void testPanningReadsOnlyTheChunksThatEnteredTheWindow() {
        // Panning moves the window a chunk at a time, usually mid-stroke of a drag: each step must cost the chunks that
        // came into view, not the whole window again.
        ZonePlan plan = new ZonePlan();
        WatchedChunk[][] grid = new WatchedChunk[8][8];
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                grid[cx][cz] = new WatchedChunk();
                grid[cx][cz].set(1, 1, true);
                plan.getChunkMapping().put(ZonePlan.packChunkKey(cx, cz), grid[cx][cz]);
            }
        }
        ZoneOverlayCache cache = new ZoneOverlayCache();
        Assertions.assertEquals(16, cells(cache, 0, plan, 0, 0, new int[] {0, 0, 3, 3}, 1).size());
        for (WatchedChunk[] column : grid) {
            for (WatchedChunk chunk : column) {
                chunk.reads = 0;
            }
        }

        ZoneOverlayCache.Cells panned = cells(cache, 0, plan, 0, 0, new int[] {1, 0, 4, 3}, 1);
        Assertions.assertEquals(16, panned.size(), "the 4x4 chunks now in view each hold one column");
        for (int cx = 1; cx <= 3; cx++) {
            for (int cz = 0; cz <= 3; cz++) {
                Assertions.assertEquals(0, grid[cx][cz].reads, "chunk " + cx + "," + cz + " was already worked out");
            }
        }
        for (int cz = 0; cz <= 3; cz++) {
            Assertions.assertTrue(grid[4][cz].reads > 0, "chunk 4," + cz + " came into view and is read");
        }
        Assertions.assertEquals(2, cache.builds(), "two answers were put together");
        Assertions.assertEquals(16 + 4, cache.chunkBuilds(), "but only 4 chunks were worked out for the second");
    }

    // ── Memory: what is kept ─────────────────────────────────────────────────────────────

    /** Marks the whole tile-relative chunk {@code (cx, cz)}, outside the survey clip a stroke is held to (a zone read
     *  in from a map location may sit anywhere), so a test can scatter painted chunks across a huge window. */
    private static void paintWholeChunk(ZonePlan plan, int cx, int cz) {
        plan.getChunkMapping().put(ZonePlan.packChunkKey(cx, cz), ZoneChunk.full());
    }

    @Test
    public void testChunksWithNothingPaintedAreNotRetained() {
        // The window is the chunks that can project into the viewport, hundreds across at the survey zoom of a deep
        // world, but a layer has paint on a handful of them. Remembering "this chunk has no cells" for every chunk of
        // the window held an entry per chunk (tens of thousands) per layer slot, for nothing.
        ZonePlan plan = new ZonePlan();
        paintWholeChunk(plan, 5, 5);
        paintWholeChunk(plan, 60, -40);
        ZoneOverlayCache cache = new ZoneOverlayCache();

        ZoneOverlayCache.Cells cells = cells(cache, 0, plan, 0, 0, new int[] {-100, -100, 99, 99}, 1); // 40,000 chunks

        Assertions.assertEquals(2 * 256, cells.size(), "the two painted chunks are listed whole");
        Assertions.assertEquals(2, cache.retainedChunks(), "and only they are kept, not the 39,998 empty ones");
    }

    @Test
    public void testTheLargestWindowOverASparseLayerRetainsOnlyThePaintedChunks() {
        // Whatever MAX_CHUNK_SPAN is, what is kept follows the paint, not the window the camera asks for.
        int half = ZoneMapCamera.MAX_CHUNK_SPAN / 2;
        ZonePlan plan = new ZonePlan();
        paintWholeChunk(plan, -half, -half);
        paintWholeChunk(plan, 0, 0);
        paintWholeChunk(plan, half - 1, half - 1);
        ZoneOverlayCache cache = new ZoneOverlayCache();

        ZoneOverlayCache.Cells cells = cells(cache, 0, plan, 0, 0, new int[] {-half, -half, half - 1, half - 1}, 8);

        Assertions.assertEquals(3 * 4, cells.size(), "an 8-block cell grid: four cells in each painted chunk");
        Assertions.assertEquals(3, cache.retainedChunks());
        Assertions.assertEquals(3, cache.chunkBuilds(), "and a chunk nothing reaches is not even worked out");
    }

    @Test
    public void testAnUnalignedPlannerRetainsAtMostFourWorldChunksPerPaintedChunk() {
        // The layer is stored relative to the planner, so off the chunk grid one layer chunk straddles four world
        // chunks: that, not the window, is what bounds what is kept.
        ZonePlan plan = new ZonePlan();
        paintWholeChunk(plan, 2, 3);
        ZoneOverlayCache cache = new ZoneOverlayCache();

        ZoneOverlayCache.Cells cells = cells(cache, 0, plan, 5, 9, new int[] {-80, -80, 80, 80}, 1);

        Assertions.assertEquals(256, cells.size(), "the 16x16 painted block, shifted by the planner's offset");
        Assertions.assertEquals(4, cache.retainedChunks());
    }

    @Test
    public void testRetentionFollowsThePaintedChunksInViewAsTheViewMoves() {
        // Ten clusters of 20 painted chunks, 30 chunks apart; the view visits them one by one. What is retained must
        // track the paint in view (with some slack, so panning over an edge does not thrash), and not pile up a
        // cluster at a time for as long as the view keeps wandering.
        ZonePlan plan = new ZonePlan();
        for (int cluster = 0; cluster < 10; cluster++) {
            for (int dx = 0; dx < 5; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    paintWholeChunk(plan, cluster * 30 + dx, dz);
                }
            }
        }
        ZoneOverlayCache cache = new ZoneOverlayCache();
        for (int round = 0; round < 3; round++) {
            for (int cluster = 0; cluster < 10; cluster++) {
                int[] win = {cluster * 30 - 1, -1, cluster * 30 + 5, 4}; // the cluster and a chunk around it
                Assertions.assertEquals(20 * 256, cells(cache, 0, plan, 0, 0, win, 1).size());
                Assertions.assertTrue(cache.retainedChunks() >= 20, "the cluster in view is kept");
                Assertions.assertTrue(cache.retainedChunks() <= 2 * 20 + 64,
                        "retained " + cache.retainedChunks() + " chunks after visiting cluster " + cluster
                                + " (round " + round + "): more than twice the painted chunks in view, plus slack");
            }
        }
    }

    @Test
    public void testSparseLayersMatchThePlainMeaningWhereverTheWindowCutsThem() {
        // Layers confined to a box, windows that miss it, clip one side of it or swallow it whole: the cells are the
        // same as the plain meaning in every case.
        java.util.Random rand = new java.util.Random(0xB0B);
        for (int round = 0; round < 80; round++) {
            ZonePlan plan = new ZonePlan();
            for (int stroke = 0; stroke < 4; stroke++) {
                int x = rand.nextInt(500) - 250;
                int z = rand.nextInt(500) - 250;
                plan.paintRect(x, z, x + rand.nextInt(60), z + rand.nextInt(60), true);
            }
            int tileX = rand.nextInt(200) - 100;
            int tileZ = rand.nextInt(200) - 100;
            int minCX = rand.nextInt(60) - 30;
            int minCZ = rand.nextInt(60) - 30;
            int[] win = {minCX, minCZ, minCX + rand.nextInt(10), minCZ + rand.nextInt(10)};
            int stride = 1 << rand.nextInt(4);
            Assertions.assertEquals(naive(plan, tileX, tileZ, win, stride),
                    asSet(cells(new ZoneOverlayCache(), 0, plan, tileX, tileZ, win, stride)),
                    "round " + round + ": planner (" + tileX + ", " + tileZ + "), window "
                            + java.util.Arrays.toString(win) + ", stride " + stride);
        }
    }

    @Test
    public void testCellsComeOutOneWorldChunkAtATimeAtEveryStride() {
        // The renderer looks the terrain chunk up once per run of cells, so a chunk's cells must be consecutive.
        ZonePlan plan = new ZonePlan();
        plan.paintRect(-40, -40, 60, 60, true);
        for (int stride : new int[] {1, 2, 4, 8}) {
            ZoneOverlayCache.Cells cells = cells(new ZoneOverlayCache(), 0, plan, 5, 3, WIN, stride);
            Assertions.assertTrue(cells.size() > 0);
            Set<Long> finished = new HashSet<>();
            long current = Long.MIN_VALUE;
            for (int i = 0; i < cells.size(); i++) {
                long chunk = pack(cells.x(i) >> 4, cells.z(i) >> 4);
                if (chunk != current) {
                    Assertions.assertTrue(finished.add(chunk), "chunk revisited at stride " + stride + ", cell " + i);
                    current = chunk;
                }
            }
        }
    }

    @Test
    public void testRandomLayersMatchThePlainMeaning() {
        java.util.Random rand = new java.util.Random(0x5EED);
        for (int round = 0; round < 60; round++) {
            ZonePlan plan = new ZonePlan();
            for (int stroke = 0; stroke < 6; stroke++) {
                int x = rand.nextInt(160) - 80;
                int z = rand.nextInt(160) - 80;
                plan.paintRect(x, z, x + rand.nextInt(50), z + rand.nextInt(50), rand.nextInt(5) != 0);
            }
            int tileX = rand.nextInt(200) - 100;
            int tileZ = rand.nextInt(200) - 100;
            int minCX = rand.nextInt(10) - 5;
            int minCZ = rand.nextInt(10) - 5;
            int[] win = {minCX, minCZ, minCX + rand.nextInt(6), minCZ + rand.nextInt(6)};
            int stride = 1 << rand.nextInt(4);
            Assertions.assertEquals(naive(plan, tileX, tileZ, win, stride),
                    asSet(cells(new ZoneOverlayCache(), 0, plan, tileX, tileZ, win, stride)),
                    "round " + round + ": planner (" + tileX + ", " + tileZ + "), window " + java.util.Arrays.toString(win)
                            + ", stride " + stride);
        }
    }

    @Test
    public void testSameCellsAreReusedWhileNothingChanges() {
        ZoneOverlayCache cache = new ZoneOverlayCache();
        ZonePlan plan = new ZonePlan();
        plan.paintRect(0, 0, 20, 20, true);

        ZoneOverlayCache.Cells first = cells(cache, 3, plan, 0, 0, WIN, 1);
        Assertions.assertEquals(1, cache.builds());
        for (int frame = 0; frame < 100; frame++) {
            Assertions.assertSame(first, cells(cache, 3, plan, 0, 0, WIN, 1), "frame " + frame);
        }
        Assertions.assertEquals(1, cache.builds(), "100 more frames of an unchanged view built nothing");
    }

    @Test
    public void testAnEditRebuilds() {
        ZoneOverlayCache cache = new ZoneOverlayCache();
        ZonePlan plan = new ZonePlan();
        plan.set(1, 1, true);
        ZoneOverlayCache.Cells before = cells(cache, 0, plan, 0, 0, WIN, 1);

        plan.set(2, 2, true);
        ZoneOverlayCache.Cells after = cells(cache, 0, plan, 0, 0, WIN, 1);
        Assertions.assertEquals(2, cache.builds());
        Assertions.assertNotSame(before, after);
        Assertions.assertEquals(Set.of(pack(1, 1), pack(2, 2)), asSet(after));

        plan.paintRect(0, 0, 5, 5, false);
        Assertions.assertEquals(0, cells(cache, 0, plan, 0, 0, WIN, 1).size(), "a stroke is an edit too");
        Assertions.assertEquals(3, cache.builds());
    }

    @Test
    public void testAReplacedPlanRebuildsEvenWhenItsCounterMatches() {
        ZoneOverlayCache cache = new ZoneOverlayCache();
        ZonePlan one = new ZonePlan();
        one.set(1, 1, true);
        ZonePlan two = new ZonePlan();
        two.set(5, 5, true);
        Assertions.assertEquals(one.modCount(), two.modCount(), "baseline: both plans have been edited once");

        cells(cache, 0, one, 0, 0, WIN, 1);
        ZoneOverlayCache.Cells cells = cells(cache, 0, two, 0, 0, WIN, 1);
        Assertions.assertEquals(2, cache.builds());
        Assertions.assertEquals(Set.of(pack(5, 5)), asSet(cells), "and shows the new plan, not the old one");
    }

    @Test
    public void testMovingTheViewRebuilds() {
        ZoneOverlayCache cache = new ZoneOverlayCache();
        ZonePlan plan = new ZonePlan();
        plan.paintRect(0, 0, 100, 100, true);
        cells(cache, 0, plan, 0, 0, WIN, 1);
        int builds = 1;

        cells(cache, 0, plan, 0, 0, new int[] {1, 0, 3, 3}, 1);
        Assertions.assertEquals(++builds, cache.builds(), "a different window");
        cells(cache, 0, plan, 0, 0, new int[] {1, 0, 3, 4}, 1);
        Assertions.assertEquals(++builds, cache.builds(), "a window that grew");
        cells(cache, 0, plan, 0, 0, new int[] {1, 0, 3, 4}, 2);
        Assertions.assertEquals(++builds, cache.builds(), "a different zoom");
        cells(cache, 0, plan, 4, 0, new int[] {1, 0, 3, 4}, 2);
        Assertions.assertEquals(++builds, cache.builds(), "a moved planner");
        cells(cache, 0, plan, 4, 0, new int[] {1, 0, 3, 4}, 2);
        Assertions.assertEquals(builds, cache.builds(), "and the same view again is free");
    }

    @Test
    public void testLayersAreCachedIndependently() {
        ZoneOverlayCache cache = new ZoneOverlayCache();
        ZonePlan[] layers = new ZonePlan[16];
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new ZonePlan();
            layers[i].set(i, i, true);
        }
        for (int frame = 0; frame < 5; frame++) {
            for (int i = 0; i < layers.length; i++) {
                Assertions.assertEquals(Set.of(pack(i, i)), asSet(cells(cache, i, layers[i], 0, 0, WIN, 1)));
            }
        }
        Assertions.assertEquals(16, cache.builds(), "one build per layer however many frames");

        layers[7].set(40, 40, true);
        for (int i = 0; i < layers.length; i++) {
            cells(cache, i, layers[i], 0, 0, WIN, 1);
        }
        Assertions.assertEquals(17, cache.builds(), "editing one layer rebuilds only that layer");
    }

    @Test
    public void testClearForcesARebuild() {
        ZoneOverlayCache cache = new ZoneOverlayCache();
        ZonePlan plan = new ZonePlan();
        plan.set(1, 1, true);
        cells(cache, 0, plan, 0, 0, WIN, 1);
        cache.clear();
        cells(cache, 0, plan, 0, 0, WIN, 1);
        Assertions.assertEquals(2, cache.builds());
    }

    @Test
    public void testAnEmptyOrAbsentLayerCostsNothingToDraw() {
        ZoneOverlayCache cache = new ZoneOverlayCache();
        Assertions.assertEquals(0, cells(cache, 0, new ZonePlan(), 0, 0, WIN, 1).size());
        Assertions.assertEquals(0, cells(cache, 1, new ZonePlan(), 0, 0, WIN, 8).size());
    }
}
