/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;

import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.lib.nbt.NbtSquisher;

/**
 * The server's side of a library upload: chunks come from a client, so how many it keeps, and how big the result
 * may grow when it is unpacked, are decided here and not by the sender.
 */
public class LibraryUploadBufferTester {

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
        LibraryUploadBuffer buffer = new LibraryUploadBuffer();
        Assertions.assertNull(buffer.accept(false, bytes(1, 2)), "more to come");
        Assertions.assertNull(buffer.accept(false, bytes(3)), "more to come");
        Assertions.assertArrayEquals(bytes(1, 2, 3, 4), buffer.accept(true, bytes(4)));
        Assertions.assertEquals(0, buffer.bufferedBytes(), "an assembled upload leaves nothing buffered");
        Assertions.assertEquals(0, buffer.bufferedChunks());
    }

    @Test
    public void anEmptyFinalChunkStillCompletesAnUpload() {
        // The client sends one when the snapshot serialised to nothing.
        LibraryUploadBuffer buffer = new LibraryUploadBuffer();
        Assertions.assertArrayEquals(new byte[0], buffer.accept(true, new byte[0]));
    }

    @Test
    public void anUploadExactlyAtTheByteLimitIsAccepted() {
        LibraryUploadBuffer buffer = new LibraryUploadBuffer();
        byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
        int chunks = LibraryUploadBuffer.MAX_UPLOAD_BYTES / LibraryUploadBuffer.CHUNK_BYTES;
        Assertions.assertNull(feed(buffer, chunk, chunks - 1));
        byte[] whole = buffer.accept(true, chunk);
        Assertions.assertNotNull(whole, "the limit itself is allowed");
        Assertions.assertEquals(LibraryUploadBuffer.MAX_UPLOAD_BYTES, whole.length);
    }

    @Test
    public void anUploadPastTheByteLimitIsDroppedAndTheBufferReset() {
        LibraryUploadBuffer buffer = new LibraryUploadBuffer();
        byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
        int atLimit = LibraryUploadBuffer.MAX_UPLOAD_BYTES / LibraryUploadBuffer.CHUNK_BYTES;
        feed(buffer, chunk, atLimit);
        Assertions.assertEquals(LibraryUploadBuffer.MAX_UPLOAD_BYTES, buffer.bufferedBytes());

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
        LibraryUploadBuffer buffer = new LibraryUploadBuffer();
        Assertions.assertNull(buffer.accept(true, new byte[LibraryUploadBuffer.MAX_UPLOAD_BYTES + 1]));
        Assertions.assertEquals(0, buffer.bufferedBytes());
        Assertions.assertArrayEquals(bytes(9), buffer.accept(true, bytes(9)), "and the next upload is unaffected");
    }

    @Test
    public void anUploadPastTheChunkLimitIsDroppedEvenWhenTheBytesAreFew() {
        // One-byte chunks cost an array header each: the count is bounded as well as the total.
        LibraryUploadBuffer buffer = new LibraryUploadBuffer();
        byte[] chunk = bytes(1);
        feed(buffer, chunk, LibraryUploadBuffer.MAX_CHUNKS);
        Assertions.assertEquals(LibraryUploadBuffer.MAX_CHUNKS, buffer.bufferedChunks());

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

        Snapshot decoded = LibraryUploadBuffer.decode(out.toByteArray());
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
    public void garbageIsRefusedAsUnreadable() {
        Assertions.assertThrows(IOException.class, () -> LibraryUploadBuffer.decode(bytes(1, 2, 3, 4)));
        Assertions.assertThrows(IOException.class, () -> LibraryUploadBuffer.decode(new byte[0]));
    }
}
