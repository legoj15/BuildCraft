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
 * Pure-JUnit pins for the bounds a Zone Planner layer is held to when a client sends one: the survey-range
 * square ({@link ZonePlan#inSurveyRange}), the bounded wire reader, the server-side merge that keeps a layer's
 * own far chunks ({@link ZonePlan#clampToSurvey}), content comparison and the modification counter the
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
        Assertions.assertEquals((2 * ZonePlan.MAX_CHUNK_RADIUS) * (2 * ZonePlan.MAX_CHUNK_RADIUS),
                ZonePlan.MAX_CHUNKS, "MAX_CHUNKS is every chunk of the square");
    }

    @Test
    public void testInSurveyRangeEdges() {
        int r = ZonePlan.MAX_CHUNK_RADIUS;
        // Block offsets floor into chunks, so the square is [-r, r - 1] on each axis.
        Assertions.assertTrue(ZonePlan.inSurveyRange(0, 0));
        Assertions.assertTrue(ZonePlan.inSurveyRange(-r, -r));
        Assertions.assertTrue(ZonePlan.inSurveyRange(r - 1, r - 1));
        Assertions.assertFalse(ZonePlan.inSurveyRange(r, 0));
        Assertions.assertFalse(ZonePlan.inSurveyRange(0, r));
        Assertions.assertFalse(ZonePlan.inSurveyRange(-r - 1, 0));
        Assertions.assertFalse(ZonePlan.inSurveyRange(0, -r - 1));
        Assertions.assertFalse(ZonePlan.inSurveyRange(Integer.MAX_VALUE, 0));
        Assertions.assertFalse(ZonePlan.inSurveyRange(0, Integer.MIN_VALUE));
    }

    // ── The bounded wire reader ─────────────────────────────────────────────────────────

    @Test
    public void testBoundedReadRoundTripsAnOrdinaryLayer() {
        ZonePlan sent = cells(1, 1, -20, 40, 300, -300);
        ZonePlan received = new ZonePlan();
        Assertions.assertTrue(received.readFromByteBufBounded(wire(sent)));
        Assertions.assertTrue(received.sameContent(sent));
    }

    @Test
    public void testBoundedReadRejectsAnAbsurdDeclaredCount() {
        for (int declared : new int[] {Integer.MAX_VALUE, ZonePlan.MAX_CHUNKS + 1, -1}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeInt(declared); // and no entries behind it: honouring the count would run off the buffer
            ZonePlan plan = cells(7, 7);
            Assertions.assertFalse(plan.readFromByteBufBounded(buf), "declared " + declared);
            Assertions.assertTrue(plan.isEmpty(), "a rejected read leaves nothing behind (declared " + declared + ")");
        }
    }

    @Test
    public void testBoundedReadRejectsATruncatedMessage() {
        FriendlyByteBuf buf = wire(cells(1, 1, 40, 40));
        FriendlyByteBuf cut = new FriendlyByteBuf(Unpooled.buffer());
        cut.writeBytes(buf, buf.readableBytes() - 3);
        ZonePlan plan = new ZonePlan();
        Assertions.assertFalse(plan.readFromByteBufBounded(cut));
        Assertions.assertTrue(plan.isEmpty(), "a half-read layer is not kept");
    }

    @Test
    public void testBoundedReadRejectsAnOversizedBitSet() {
        // One chunk whose "bit set" is a megabyte: ZoneChunk bits are 0..255, 32 bytes at most.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(1);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeByte(1); // flags: has a bit set
        buf.writeByteArray(new byte[1 << 20]);
        ZonePlan plan = new ZonePlan();
        Assertions.assertFalse(plan.readFromByteBufBounded(buf));
        Assertions.assertTrue(plan.isEmpty());
    }

    // ── What a client sends: the survey square only ─────────────────────────────────────

    private static ZonePlan surveyWire(ZonePlan plan, int[] declared) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        plan.writeSurveyToByteBuf(buf);
        declared[0] = buf.getInt(buf.readerIndex());
        ZonePlan received = new ZonePlan();
        Assertions.assertTrue(received.readFromByteBufBounded(buf), "a survey-only message passes the bounded reader");
        return received;
    }

    @Test
    public void testSurveyWireCarriesOnlyTheSquare() {
        int[] declared = new int[1];
        ZonePlan received = surveyWire(cells(1, 1, FAR, 0, -FAR, 40, 2, 3), declared);
        Assertions.assertEquals(1, declared[0], "the declared count matches the chunks written");
        Assertions.assertTrue(received.sameContent(cells(1, 1, 2, 3)), "only the in-square chunk travels");
        Assertions.assertTrue(received.isWithinSurvey());
    }

    /** A layer can hold far chunks without limit (each map location read in adds its own zone); they never
     *  travel up, so they can never push a paint message past the cap and make the layer unpaintable. */
    @Test
    public void testFarChunksNeverCountAgainstThePaintCap() {
        ZonePlan full = new ZonePlan();
        int r = ZonePlan.MAX_CHUNK_RADIUS;
        for (int cx = -r; cx < r; cx++) {
            for (int cz = -r; cz < r; cz++) {
                full.set(cx << 4, cz << 4, true);
            }
        }
        for (int i = 0; i < ZonePlan.MAX_CHUNKS; i++) {
            full.set(FAR + ((i % 300) << 4), (i / 300) << 4, true);
        }
        int[] declared = new int[1];
        ZonePlan received = surveyWire(full, declared);
        Assertions.assertEquals(ZonePlan.MAX_CHUNKS, declared[0], "a full square is exactly the cap");
        Assertions.assertEquals(ZonePlan.MAX_CHUNKS, received.getChunkKeys().size());
    }

    @Test
    public void testIsWithinSurvey() {
        Assertions.assertTrue(new ZonePlan().isWithinSurvey());
        Assertions.assertTrue(cells(1, 1, -40, 300).isWithinSurvey());
        Assertions.assertFalse(cells(1, 1, FAR, 0).isWithinSurvey());
        int edge = ZonePlan.MAX_CHUNK_RADIUS * 16;
        Assertions.assertTrue(cells(edge - 1, -edge).isWithinSurvey(), "the last block inside each edge");
        Assertions.assertFalse(cells(edge, 0).isWithinSurvey(), "the first block past the positive edge");
        Assertions.assertFalse(cells(0, -edge - 1).isWithinSurvey(), "the first block past the negative edge");
    }

    // ── The viewport's paint rectangle ──────────────────────────────────────────────────

    @Test
    public void testPaintRectMarksTheRectangleOnACopy() {
        ZonePlan base = cells(50, 50);
        ZonePlan painted = ZonePlan.withRect(base, 2, 3, -1, 0, true);
        Assertions.assertEquals(4 * 4 + 1, painted.getAll().size(), "corners in either order, inclusive");
        Assertions.assertTrue(painted.get(-1, 0) && painted.get(2, 3) && painted.get(50, 50));
        Assertions.assertFalse(base.get(0, 0), "the base layer is not touched");

        ZonePlan erased = ZonePlan.withRect(painted, 0, 0, 2, 3, false);
        Assertions.assertFalse(erased.get(1, 1));
        Assertions.assertTrue(erased.get(-1, 0) && erased.get(50, 50));
    }

    @Test
    public void testPaintRectIsClippedToTheSurveySquare() {
        int edge = ZonePlan.MAX_CHUNK_RADIUS * 16;
        ZonePlan painted = ZonePlan.withRect(new ZonePlan(), edge - 2, 0, edge + 500, 1, true);
        Assertions.assertEquals(2 * 2, painted.getAll().size(), "only the two columns inside the edge are painted");
        Assertions.assertTrue(painted.isWithinSurvey());

        ZonePlan outside = ZonePlan.withRect(cells(1, 1), FAR, FAR, FAR + 10, FAR + 10, true);
        Assertions.assertTrue(outside.sameContent(cells(1, 1)), "a stroke wholly outside paints nothing");

        // A far zone the layer was given (read from a map location) cannot be erased from the viewport either.
        ZonePlan far = cells(FAR, 0);
        ZonePlan wiped = ZonePlan.withRect(far, -edge - 3000, -edge - 3000, FAR + 10, edge + 3000, false);
        Assertions.assertTrue(wiped.get(FAR, 0), "the far chunk survives an erase that spans it");
    }

    // ── The server-side merge ───────────────────────────────────────────────────────────

    @Test
    public void testClampDropsNewChunksOutsideTheSquare() {
        ZonePlan received = cells(1, 1, FAR, 0);
        ZonePlan merged = ZonePlan.clampToSurvey(received, new ZonePlan());
        Assertions.assertTrue(merged.get(1, 1), "the in-range stroke is kept");
        Assertions.assertFalse(merged.get(FAR, 0), "a chunk the layer did not already have, outside the square, is dropped");
        Assertions.assertFalse(merged.sameContent(received));
    }

    @Test
    public void testClampKeepsTheLayersOwnFarChunksWhateverTheClientSent() {
        // A zone read from a map location can sit anywhere; the viewport cannot show it, so a client's
        // paint message may legitimately carry it back, or (hostile or buggy) leave it out. Either way it
        // survives, and the client cannot move it.
        ZonePlan existing = cells(FAR, 0, 2, 2);
        ZonePlan receivedWithout = cells(1, 1);
        ZonePlan merged = ZonePlan.clampToSurvey(receivedWithout, existing);
        Assertions.assertTrue(merged.get(FAR, 0), "far chunk survives a message that omits it");
        Assertions.assertTrue(merged.get(1, 1), "the new in-range stroke lands");
        Assertions.assertFalse(merged.get(2, 2), "an in-range cell the client erased stays erased");

        ZonePlan receivedFarOverwrite = cells(FAR + 1, 5);
        ZonePlan merged2 = ZonePlan.clampToSurvey(receivedFarOverwrite, existing);
        Assertions.assertTrue(merged2.get(FAR, 0), "the existing far chunk is untouched");
        Assertions.assertFalse(merged2.get(FAR + 1, 5), "a client cannot rewrite a far chunk");
    }

    @Test
    public void testClampIsTransparentWhenTheClientSendsBackWhatItWasGiven() {
        ZonePlan existing = cells(FAR, 0, 2, 2);
        ZonePlan received = cells(FAR, 0, 2, 2, 3, 3);
        ZonePlan merged = ZonePlan.clampToSurvey(received, existing);
        Assertions.assertTrue(merged.sameContent(received), "nothing was altered, so nothing differs from the message");
    }

    @Test
    public void testClampDoesNotMutateItsInputs() {
        ZonePlan received = cells(1, 1, FAR, 0);
        ZonePlan existing = cells(FAR + 32, 0);
        ZonePlan.clampToSurvey(received, existing);
        Assertions.assertTrue(received.get(FAR, 0));
        Assertions.assertFalse(existing.get(1, 1));
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
        plan.readFromByteBufBounded(wire(cells(3, 3)));
        Assertions.assertNotEquals(m3, plan.modCount(), "a bounded wire read counts");
    }
}
