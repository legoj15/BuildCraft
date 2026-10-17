/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.network.FriendlyByteBuf;

import buildcraft.robotics.client.zone.ZoneMapCamera;

/**
 * Pure-JUnit pins for a Zone Planner layer's edit and bounds: the stroke rectangle ({@link ZonePlan#paintRect},
 * the one edit a client can ask the server for) and its clip to the survey square, the chunk-at-once fast path
 * matching cell-by-cell painting, the bounded chunk reader, content comparison and the modification counter the
 * container uses to skip re-serialising untouched layers.
 */
public class ZonePlanBoundsTest {

    /** A block x/z, far outside the survey square in chunk terms (chunk 1000). */
    private static final int FAR = 16_000;

    private static ZonePlan cells(int... xz) {
        ZonePlan plan = new ZonePlan();
        for (int i = 0; i < xz.length; i += 2) {
            plan.set(xz[i], xz[i + 1], true);
        }
        return plan;
    }

    private static FriendlyByteBuf wire(ZonePlan plan) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        plan.writeToByteBuf(buf);
        return buf;
    }

    /** The plain cell-by-cell meaning of a stroke, clip included: what paintRect's fast path must match. */
    private static void naiveRect(ZonePlan plan, int x0, int z0, int x1, int z1, boolean val) {
        for (int x = Math.max(Math.min(x0, x1), ZonePlan.SURVEY_MIN_BLOCK);
             x <= Math.min(Math.max(x0, x1), ZonePlan.SURVEY_MAX_BLOCK); x++) {
            for (int z = Math.max(Math.min(z0, z1), ZonePlan.SURVEY_MIN_BLOCK);
                 z <= Math.min(Math.max(z0, z1), ZonePlan.SURVEY_MAX_BLOCK); z++) {
                plan.set(x, z, val);
            }
        }
    }

    // ── The survey square ───────────────────────────────────────────────────────────────

    @Test
    public void testSurveySquareCoversTheViewportAtFullZoomOut() {
        // GuiZonePlanner's map window is 213 px wide; at the zoom floor that is the "~1,700 blocks across" the
        // help text promises. The square must reach at least half of it from the planner in every direction.
        double spanBlocks = 213 / ZoneMapCamera.MIN_PX_PER_BLOCK;
        Assertions.assertTrue(ZonePlan.MAX_CHUNK_RADIUS * 16 >= spanBlocks / 2,
                "the square reaches the edge of the survey view");
        Assertions.assertTrue(ZonePlan.MAX_CHUNK_RADIUS * 16 <= spanBlocks / 2 + 16,
                "and no more than one chunk beyond it");
        Assertions.assertEquals(-ZonePlan.MAX_CHUNK_RADIUS * 16, ZonePlan.SURVEY_MIN_BLOCK);
        Assertions.assertEquals(ZonePlan.MAX_CHUNK_RADIUS * 16 - 1, ZonePlan.SURVEY_MAX_BLOCK,
                "block offsets floor into chunks, so the square is chunks [-r, r - 1]");
    }

    // ── The stroke rectangle ────────────────────────────────────────────────────────────

    @Test
    public void testPaintRectMarksAndClearsTheRectangle() {
        ZonePlan plan = cells(50, 50);
        Assertions.assertTrue(plan.paintRect(2, 3, -1, 0, true));
        Assertions.assertEquals(4 * 4 + 1, plan.getAll().size(), "corners in either order, inclusive");
        Assertions.assertTrue(plan.get(-1, 0) && plan.get(2, 3) && plan.get(50, 50));

        Assertions.assertTrue(plan.paintRect(0, 0, 2, 3, false));
        Assertions.assertFalse(plan.get(1, 1));
        Assertions.assertTrue(plan.get(-1, 0) && plan.get(50, 50));
    }

    @Test
    public void testWithRectLeavesTheBaseAlone() {
        ZonePlan base = cells(50, 50);
        ZonePlan painted = ZonePlan.withRect(base, 0, 0, 3, 3, true);
        Assertions.assertTrue(painted.get(3, 3));
        Assertions.assertTrue(base.sameContent(cells(50, 50)), "the base layer is not touched");
    }

    @Test
    public void testPaintRectIsClippedToTheSurveySquare() {
        int edge = ZonePlan.MAX_CHUNK_RADIUS * 16;
        ZonePlan painted = new ZonePlan();
        painted.paintRect(edge - 2, 0, edge + 500, 1, true);
        Assertions.assertEquals(2 * 2, painted.getAll().size(), "only the two columns inside the edge are painted");
        Assertions.assertTrue(painted.get(edge - 1, 0) && !painted.get(edge, 0));

        ZonePlan negative = new ZonePlan();
        negative.paintRect(-edge - 500, 0, -edge + 1, 0, true);
        Assertions.assertEquals(2, negative.getAll().size(), "and on the negative side, [-edge, -edge + 1]");

        ZonePlan outside = cells(1, 1);
        Assertions.assertFalse(outside.paintRect(FAR, FAR, FAR + 10, FAR + 10, true), "a stroke wholly outside");
        Assertions.assertTrue(outside.sameContent(cells(1, 1)), "paints nothing");

        // A far zone the layer was given (read from a map location) cannot be erased by a stroke either.
        ZonePlan far = cells(FAR, 0, 1, 1);
        far.paintRect(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, false);
        Assertions.assertTrue(far.get(FAR, 0), "the far chunk survives an erase that spans it");
        Assertions.assertFalse(far.get(1, 1), "while the square is cleared");
    }

    @Test
    public void testAWholeSquareStrokeSetsWholeChunks() {
        ZonePlan plan = cells(FAR, 0);
        plan.paintRect(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
        int side = 2 * ZonePlan.MAX_CHUNK_RADIUS;
        Assertions.assertEquals(side * side + 1, plan.getChunkKeys().size(), "every chunk of the square, plus the far one");
        for (ZoneChunk chunk : plan.getChunkMapping().values()) {
            if (chunk != plan.getChunkMapping().get(ZonePlan.packChunkKey(FAR >> 4, 0))) {
                Assertions.assertTrue(chunk.isFull(), "covered chunks are stored whole");
            }
        }
    }

    /** The chunk-at-once fast path must mean exactly what painting each column would, for strokes aligned and
     *  unaligned to chunk edges, negative coordinates, and erasing through full and partial chunks. */
    @Test
    public void testPaintRectMatchesCellByCellPainting() {
        int[][] strokes = {
                {0, 0, 15, 15, 1}, {-16, -16, -1, -1, 1}, {-20, 5, 40, 60, 1}, {3, -40, 3, 40, 1},
                {-8, -8, 8, 8, 0}, {10, 10, 47, 47, 1}, {16, 16, 31, 20, 0}, {-33, -1, -17, 64, 0},
                {5, 5, 5, 5, 1}, {32, 32, 47, 47, 0},
        };
        ZonePlan fast = cells(7, 7, 100, -100);
        ZonePlan slow = cells(7, 7, 100, -100);
        for (int[] s : strokes) {
            fast.paintRect(s[0], s[1], s[2], s[3], s[4] == 1);
            naiveRect(slow, s[0], s[1], s[2], s[3], s[4] == 1);
            Assertions.assertTrue(fast.sameContent(slow), "after stroke " + java.util.Arrays.toString(s));
        }
        for (ZoneChunk chunk : fast.getChunkMapping().values()) {
            Assertions.assertFalse(chunk.isEmpty(), "no empty chunk is left behind");
        }
    }

    // ── The bounded chunk reader ────────────────────────────────────────────────────────

    @Test
    public void testAnOversizedBitSetIsRefused() {
        // One chunk whose "bit set" is a megabyte: ZoneChunk bits are 0..255, 32 bytes at most.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(1);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeByte(1); // flags: has a bit set
        buf.writeByteArray(new byte[1 << 20]);
        Assertions.assertThrows(RuntimeException.class, () -> new ZonePlan().readFromByteBuf(buf));
    }

    // ── Content comparison ──────────────────────────────────────────────────────────────

    @Test
    public void testSameContentIgnoresInsertionOrder() {
        ZonePlan a = cells(1, 1, 40, 40, -50, 7);
        ZonePlan b = cells(-50, 7, 40, 40, 1, 1);
        Assertions.assertTrue(a.sameContent(b));
        Assertions.assertTrue(b.sameContent(a));
    }

    @Test
    public void testSameContentSeesAnyDifference() {
        ZonePlan a = cells(1, 1, 40, 40);
        Assertions.assertFalse(a.sameContent(cells(1, 1)), "missing chunk");
        Assertions.assertFalse(a.sameContent(cells(1, 1, 40, 41)), "different cell in a shared chunk");
        Assertions.assertFalse(a.sameContent(cells(1, 1, 80, 80)), "different chunk");
        Assertions.assertTrue(new ZonePlan().sameContent(new ZonePlan()), "two empty plans match");
    }

    @Test
    public void testSameContentTreatsAFullChunkAsFull() {
        ZonePlan setCellByCell = new ZonePlan();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                setCellByCell.set(x, z, true);
            }
        }
        ZonePlan rebuilt = new ZonePlan(setCellByCell);
        Assertions.assertTrue(setCellByCell.sameContent(rebuilt));
        rebuilt.set(3, 3, false);
        Assertions.assertFalse(setCellByCell.sameContent(rebuilt));
    }

    // ── Modification counter ────────────────────────────────────────────────────────────

    @Test
    public void testModCountMovesWithEveryMutation() {
        ZonePlan plan = new ZonePlan();
        int m0 = plan.modCount();
        plan.get(1, 1);
        plan.isEmpty();
        Assertions.assertEquals(m0, plan.modCount(), "reads do not count");

        plan.set(1, 1, true);
        int m1 = plan.modCount();
        Assertions.assertNotEquals(m0, m1, "a set counts");

        plan.set(1, 1, false);
        Assertions.assertNotEquals(m1, plan.modCount(), "a clear counts");

        int m2 = plan.modCount();
        plan.readFromByteBuf(wire(cells(2, 2)));
        Assertions.assertNotEquals(m2, plan.modCount(), "a wire read counts");

        int m3 = plan.modCount();
        plan.paintRect(0, 0, 40, 40, true);
        int m4 = plan.modCount();
        Assertions.assertNotEquals(m3, m4, "a stroke counts");
        Assertions.assertFalse(plan.paintRect(0, 0, 40, 40, true), "repeating it changes nothing");
        Assertions.assertEquals(m4, plan.modCount(), "and does not count");
    }
}
