/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.SchematicBlockAir;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.nbt.NbtSquisher;

/**
 * The server's side of a library upload: chunks come from a client, so how many it keeps, and how big the result
 * may grow when it is unpacked, are decided here and not by the sender.
 */
public class LibraryUploadBufferTester {

    /** The smallest limits the buffer ever runs with; the size tests feed a full upload of these. */
    private static final LibraryUploadBuffer.Limits LIMITS = LibraryUploadBuffer.Limits.FLOOR;

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private static Template build() {
        Template template = new Template();
        template.size = new BlockPos(2, 1, 1);
        template.facing = Direction.NORTH;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet();
        template.data.set(0);
        template.computeKey();
        return template;
    }

    /** Feed {@code count} chunks of {@code chunk} (the same array each time, so a big stream costs no memory in
     *  the test), none of them final. Returns the last result. */
    private static byte[] feed(LibraryUploadBuffer buffer, byte[] chunk, int count) {
        byte[] result = null;
        for (int i = 0; i < count; i++) {
            result = buffer.accept(false, chunk);
        }
        return result;
    }

    @Test
    public void chunksAssembleInOrderWhenTheLastOneArrives() {
        LibraryUploadBuffer buffer = new LibraryUploadBuffer(LIMITS);
        Assertions.assertNull(buffer.accept(false, bytes(1, 2)), "more to come");
        Assertions.assertNull(buffer.accept(false, bytes(3)), "more to come");
        Assertions.assertArrayEquals(bytes(1, 2, 3, 4), buffer.accept(true, bytes(4)));
        Assertions.assertEquals(0, buffer.bufferedBytes(), "an assembled upload leaves nothing buffered");
        Assertions.assertEquals(0, buffer.bufferedChunks());
    }

    @Test
    public void anEmptyFinalChunkStillCompletesAnUpload() {
        // The client sends one when the snapshot serialised to nothing.
        LibraryUploadBuffer buffer = new LibraryUploadBuffer(LIMITS);
        Assertions.assertArrayEquals(new byte[0], buffer.accept(true, new byte[0]));
    }

    @Test
    public void anUploadExactlyAtTheByteLimitIsAccepted() {
        LibraryUploadBuffer buffer = new LibraryUploadBuffer(LIMITS);
        byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
        int chunks = LIMITS.maxUploadBytes() / LibraryUploadBuffer.CHUNK_BYTES;
        Assertions.assertNull(feed(buffer, chunk, chunks - 1));
        byte[] whole = buffer.accept(true, chunk);
        Assertions.assertNotNull(whole, "the limit itself is allowed");
        Assertions.assertEquals(LIMITS.maxUploadBytes(), whole.length);
    }

    @Test
    public void anUploadPastTheByteLimitIsDroppedAndTheBufferReset() {
        LibraryUploadBuffer buffer = new LibraryUploadBuffer(LIMITS);
        byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
        int atLimit = LIMITS.maxUploadBytes() / LibraryUploadBuffer.CHUNK_BYTES;
        feed(buffer, chunk, atLimit);
        Assertions.assertEquals(LIMITS.maxUploadBytes(), buffer.bufferedBytes());

        Assertions.assertNull(buffer.accept(false, bytes(1)), "one byte past the limit");
        Assertions.assertEquals(0, buffer.bufferedBytes(), "the overflowing upload is dropped, not kept");
        Assertions.assertEquals(0, buffer.bufferedChunks());

        // The rest of the dropped stream (and its final chunk) neither grows the buffer nor completes anything.
        Assertions.assertNull(feed(buffer, chunk, 10));
        Assertions.assertEquals(0, buffer.bufferedBytes());
        Assertions.assertNull(buffer.accept(true, chunk), "a dropped upload never completes");
        Assertions.assertEquals(0, buffer.bufferedBytes());

        // The next upload starts clean.
        Assertions.assertNull(buffer.accept(false, bytes(1, 2)));
        Assertions.assertArrayEquals(bytes(1, 2, 3), buffer.accept(true, bytes(3)));
    }

    @Test
    public void aSingleChunkPastTheByteLimitIsDropped() {
        // Whatever the transport allows per packet, one chunk cannot carry the upload past the limit by itself.
        LibraryUploadBuffer buffer = new LibraryUploadBuffer(LIMITS);
        Assertions.assertNull(buffer.accept(true, new byte[LIMITS.maxUploadBytes() + 1]));
        Assertions.assertEquals(0, buffer.bufferedBytes());
        Assertions.assertArrayEquals(bytes(9), buffer.accept(true, bytes(9)), "and the next upload is unaffected");
    }

    @Test
    public void anUploadPastTheChunkLimitIsDroppedEvenWhenTheBytesAreFew() {
        // One-byte chunks cost an array header each: the count is bounded as well as the total.
        LibraryUploadBuffer buffer = new LibraryUploadBuffer(LIMITS);
        byte[] chunk = bytes(1);
        feed(buffer, chunk, LIMITS.maxChunks());
        Assertions.assertEquals(LIMITS.maxChunks(), buffer.bufferedChunks());

        Assertions.assertNull(buffer.accept(false, chunk));
        Assertions.assertEquals(0, buffer.bufferedChunks(), "dropped and reset");
        Assertions.assertEquals(0, buffer.bufferedBytes());
        Assertions.assertNull(buffer.accept(true, chunk), "the tail of the dropped upload completes nothing");
        Assertions.assertArrayEquals(bytes(5), buffer.accept(true, bytes(5)));
    }

    @Test
    public void aSnapshotThatFitsDecodesAndCarriesItsContentHash() throws IOException {
        Template template = build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtSquisher.squishVanilla(Snapshot.writeToNBT(template), out);

        Snapshot decoded = new LibraryUploadBuffer(LIMITS).decode(out.toByteArray());
        Assertions.assertEquals(template.key, decoded.key, "the key is recomputed from the content");
    }

    @Test
    public void aSmallUploadThatUnpacksPastTheLimitIsRefused() throws IOException {
        // A megabyte of zeros squashes to a few hundred bytes, so the byte limit on the wire cannot see it coming.
        CompoundTag nbt = Snapshot.writeToNBT(build());
        nbt.put("padding", new ByteArrayTag(new byte[1024 * 1024]));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtSquisher.squishVanilla(nbt, out);
        byte[] payload = out.toByteArray();
        Assertions.assertTrue(payload.length < 16 * 1024, "the payload is tiny on the wire: " + payload.length);

        Assertions.assertDoesNotThrow(() -> LibraryUploadBuffer.decode(payload, 64L * 1024 * 1024),
            "within a generous limit it is an ordinary snapshot");
        Assertions.assertThrows(IOException.class, () -> LibraryUploadBuffer.decode(payload, 64L * 1024),
            "past the limit it is refused as unreadable rather than unpacked");
    }

    @Test
    public void aTinyUploadClaimingAHugeBlueprintIsRefusedBeforeItsCellsAreMade() throws IOException {
        // A blueprint whose header claims ~2.1 billion cells and whose data list is empty. The claimed size used to
        // size the cell array before the list was checked against it: an 8 GiB allocation from a sub-kilobyte upload,
        // which the NBT accounter never sees (it counts the payload, not what the reader builds from it).
        CompoundTag nbt = Snapshot.writeToNBT(blueprint(1, i -> 0));
        nbt.put("size", NBTUtilBC.writeBlockPos(new BlockPos(1290, 1290, 1290)));
        nbt.put("data", new ListTag());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtSquisher.squishVanilla(nbt, out);
        byte[] payload = out.toByteArray();
        Assertions.assertTrue(payload.length < 1024, "the payload is tiny on the wire: " + payload.length);

        long started = System.nanoTime();
        Assertions.assertThrows(IOException.class, () -> new LibraryUploadBuffer(LIMITS).decode(payload));
        long tookMillis = (System.nanoTime() - started) / 1_000_000;
        Assertions.assertTrue(tookMillis < 2_000, "refused quickly, took " + tookMillis + " ms");
    }

    @Test
    public void garbageIsRefusedAsUnreadable() {
        Assertions.assertThrows(IOException.class, () -> new LibraryUploadBuffer(LIMITS).decode(bytes(1, 2, 3, 4)));
        Assertions.assertThrows(IOException.class, () -> new LibraryUploadBuffer(LIMITS).decode(new byte[0]));
    }

    // ── Drops and skips: what the menu tells the tile ───────────────────────

    @Test
    public void aDroppedUploadIsCountedOnceSoTheTileCanStopAsking() {
        LibraryUploadBuffer buffer = new LibraryUploadBuffer(LIMITS);
        Assertions.assertEquals(0, buffer.droppedUploads());
        Assertions.assertNull(buffer.accept(false, new byte[LIMITS.maxUploadBytes() + 1]));
        Assertions.assertEquals(1, buffer.droppedUploads(), "the overflow is one dropped upload");
        Assertions.assertNull(buffer.accept(false, bytes(1)));
        Assertions.assertNull(buffer.accept(true, bytes(1)));
        Assertions.assertEquals(1, buffer.droppedUploads(), "the ignored tail is not another one");
    }

    @Test
    public void aSkippedStreamIsIgnoredToItsEndAndClearsWhatCameBefore() {
        // An upload nobody asked for: none of it is kept, and a stale prefix cannot corrupt the next upload.
        LibraryUploadBuffer buffer = new LibraryUploadBuffer(LIMITS);
        Assertions.assertNull(buffer.accept(false, bytes(1, 2)));
        buffer.skip(false);
        Assertions.assertEquals(0, buffer.bufferedBytes(), "the earlier part is dropped");
        Assertions.assertNull(buffer.accept(false, bytes(3)), "the rest of the skipped stream is ignored");
        Assertions.assertNull(buffer.accept(true, bytes(4)), "including its final chunk");
        Assertions.assertEquals(0, buffer.droppedUploads(), "skipping is not refusing");
        Assertions.assertArrayEquals(bytes(5, 6), buffer.accept(true, bytes(5, 6)), "the next upload starts clean");

        buffer.skip(true);
        Assertions.assertArrayEquals(bytes(7), buffer.accept(true, bytes(7)), "skipping a final chunk skips no more");
    }

    // ── Limits: every scan the config allows fits ───────────────────────────

    @Test
    public void theLimitsNeverFallBelowTheFloor() {
        // What the limits were before they followed the config: a volume-box scan that fit then still fits.
        for (int distance : new int[] {1, 64, 256}) {
            LibraryUploadBuffer.Limits limits = LibraryUploadBuffer.Limits.forMarkerDistance(distance);
            Assertions.assertTrue(limits.maxUploadBytes() >= 16 * 1024 * 1024, "at " + distance);
            Assertions.assertTrue(limits.maxChunks() >= 513, "at " + distance);
            Assertions.assertTrue(limits.maxExpandedBytes() >= 256L * 1024 * 1024, "at " + distance);
        }
    }

    @Test
    public void theLimitsCoverTheLargestMarkerScanAtTheConfigMaximum() {
        // The review's case: a 256-cube blueprint is 16,777,216 cells at 16 accounted bytes each, exactly the old
        // 256 MiB quota before its palette was counted. A marker reaches markerMaxDistance blocks past itself, so
        // the largest box is one longer than that on each side.
        int distance = 256;
        long cells = LibraryUploadBuffer.largestMarkerScanCells(distance);
        Assertions.assertEquals(257L * 257 * 257, cells);
        LibraryUploadBuffer.Limits limits = LibraryUploadBuffer.Limits.forMarkerDistance(distance);
        Assertions.assertTrue(limits.maxExpandedBytes() >= cells * LibraryUploadBuffer.EXPANDED_BYTES_PER_CELL
            + LibraryUploadBuffer.EXPANDED_HEADROOM, "unpacked: " + limits.maxExpandedBytes());
        Assertions.assertTrue(limits.maxUploadBytes() >= cells * LibraryUploadBuffer.UPLOAD_BYTES_PER_CELL
            + LibraryUploadBuffer.UPLOAD_HEADROOM, "on the wire: " + limits.maxUploadBytes());
        Assertions.assertTrue((long) limits.maxChunks() * LibraryUploadBuffer.CHUNK_BYTES >= limits.maxUploadBytes(),
            "the chunk count admits a full upload at the sender's chunk size");
    }

    @Test
    public void theLimitsFollowTheLiveConfig() {
        Assertions.assertEquals(LibraryUploadBuffer.Limits.forMarkerDistance(
            buildcraft.core.BCCoreConfig.markerMaxDistance.get()), LibraryUploadBuffer.Limits.current());
    }

    /** A blueprint of {@code side}^3 cells, each holding {@code cell.applyAsInt(index)}. */
    private static Blueprint blueprint(int side, java.util.function.IntUnaryOperator cell) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(side, side, side);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(new SchematicBlockAir());
        blueprint.data = new int[side * side * side];
        for (int i = 0; i < blueprint.data.length; i++) blueprint.data[i] = cell.applyAsInt(i);
        blueprint.computeKey();
        return blueprint;
    }

    private static byte[] squish(Snapshot snapshot) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshot), out);
        return out.toByteArray();
    }

    /** The smallest quota {@code payload} unpacks under: what the game's accounter charges for it. */
    private static long accountedBytes(byte[] payload) {
        long fails = 0;
        long fits = 1L << 32;
        while (fits - fails > 1) {
            long mid = (fails + fits) >>> 1;
            try {
                NbtSquisher.expand(new ByteArrayInputStream(payload), mid);
                fits = mid;
            } catch (IOException e) {
                fails = mid;
            }
        }
        return fits;
    }

    @Test
    public void theUnpackedCostPerCellIsWhatTheGameCharges() throws IOException {
        // Measured against this node's own NBT accounter, so a line that charges more per tag fails here and not
        // on a player's upload. The slope between two sizes is the per-cell cost; the rest is fixed overhead.
        byte[] small = squish(blueprint(8, i -> 0));
        byte[] large = squish(blueprint(16, i -> 0));
        long cellsSmall = 8 * 8 * 8;
        long cellsLarge = 16 * 16 * 16;
        long perCell = (accountedBytes(large) - accountedBytes(small)) / (cellsLarge - cellsSmall);
        Assertions.assertTrue(perCell <= LibraryUploadBuffer.EXPANDED_BYTES_PER_CELL,
            "the game charges " + perCell + " bytes a cell");
        Assertions.assertTrue(accountedBytes(large) <= cellsLarge * LibraryUploadBuffer.EXPANDED_BYTES_PER_CELL
            + LibraryUploadBuffer.EXPANDED_HEADROOM);
    }

    @Test
    public void aCellNeverCostsMoreOnTheWireThanItsFourBytes() throws IOException {
        // The worst case: cells that do not compress at all.
        java.util.Random random = new java.util.Random(42);
        int side = 32;
        byte[] payload = squish(blueprint(side, i -> random.nextInt()));
        long cells = (long) side * side * side;
        Assertions.assertTrue(payload.length <= cells * LibraryUploadBuffer.UPLOAD_BYTES_PER_CELL * 101 / 100 + 4096,
            payload.length + " bytes for " + cells + " cells");
    }
}
