/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import buildcraft.api.core.BCLog;
import buildcraft.lib.nbt.NbtSquisher;

import buildcraft.core.BCCoreConfig;

import buildcraft.builders.snapshot.Snapshot;

/**
 * The server's side of a {@link ContainerElectronicLibrary} upload: collects the chunks a client sends for one
 * snapshot and hands back the whole once the final chunk arrives.
 * <p>
 * Everything about the stream is the client's to choose, so it is bounded here by {@link Limits}: at most
 * {@link Limits#maxUploadBytes} bytes in at most {@link Limits#maxChunks} chunks are kept, and an upload that goes
 * past either is dropped as a unit (buffer emptied, and the rest of that stream, up to its final chunk, ignored)
 * rather than left to grow. What the bytes unpack to is bounded too, by {@link #decode}: the transfer is compressed,
 * so its size on the wire says little about its size in memory.
 * <p>
 * The limits follow the largest scan the config allows ({@link Limits#current}), so a blueprint markers can frame is
 * never refused for its size; they only stop a payload built to be larger than any such scan.
 * <p>
 * Not thread-safe; a menu's messages arrive on the server thread one at a time.
 */
public final class LibraryUploadBuffer {
    /** What the sending client splits a snapshot into (it stays inside the per-packet limit). */
    public static final int CHUNK_BYTES = 32 * 1024;
    /** What the game's NBT accounter charges for one blueprint cell: an {@code IntTag} (12) plus its slot in the
     *  list (4). Blueprints store one per cell, so this dominates a large scan; pinned by a test on every node. */
    static final long EXPANDED_BYTES_PER_CELL = 16;
    /** Room past the cells for the palette, the entities and the header. */
    static final long EXPANDED_HEADROOM = 64L * 1024 * 1024;
    /** One cell's worst case on the wire: its four bytes of int when nothing about them compresses. */
    static final long UPLOAD_BYTES_PER_CELL = 4;
    /** Room past the cells for the palette, the entities and the header, compressed. */
    static final long UPLOAD_HEADROOM = 16L * 1024 * 1024;

    /** The cells in the largest box markers {@code markerMaxDistance} apart enclose: a marker connects to one up to
     *  that many blocks away, so the box is one longer than the distance on each side. */
    static long largestMarkerScanCells(int markerMaxDistance) {
        long side = markerMaxDistance + 1L;
        return side * side * side;
    }

    /**
     * How much one upload may hold.
     *
     * @param maxUploadBytes   compressed bytes on the wire
     * @param maxChunks        chunks they may come in (each costs an array, however small)
     * @param maxExpandedBytes NBT they may unpack to, by the game's own heap estimate
     */
    public record Limits(int maxUploadBytes, int maxChunks, long maxExpandedBytes) {
        /** The least the limits ever are: what they were before they followed the config. A volume box is not
         *  bounded by the marker range, so a scan of one that fit then keeps fitting. */
        public static final Limits FLOOR = of(16 * 1024 * 1024, 256L * 1024 * 1024);

        private static Limits of(long uploadBytes, long expandedBytes) {
            int upload = (int) Math.min(uploadBytes, Integer.MAX_VALUE - CHUNK_BYTES);
            // A full upload at the sender's chunk size, plus one of slack.
            return new Limits(upload, upload / CHUNK_BYTES + 1, expandedBytes);
        }

        /** Room for the largest marker scan at {@code markerMaxDistance}, and never less than {@link #FLOOR}. */
        public static Limits forMarkerDistance(int markerMaxDistance) {
            long cells = largestMarkerScanCells(markerMaxDistance);
            return of(Math.max(FLOOR.maxUploadBytes, cells * UPLOAD_BYTES_PER_CELL + UPLOAD_HEADROOM),
                Math.max(FLOOR.maxExpandedBytes, cells * EXPANDED_BYTES_PER_CELL + EXPANDED_HEADROOM));
        }

        /** The most cells a snapshot of any type may claim in its header: what {@link #maxExpandedBytes} pays for at
         *  the worst per-cell cost (a blueprint's). */
        public int maxCells() {
            return (int) Math.min(Integer.MAX_VALUE, maxExpandedBytes / EXPANDED_BYTES_PER_CELL);
        }

        /** The limits for the config as it is now. */
        public static Limits current() {
            return forMarkerDistance(BCCoreConfig.markerMaxDistance.get());
        }
    }

    private final Limits limits;
    private final List<byte[]> chunks = new ArrayList<>();
    private int bytes;
    /** An upload overflowed or was skipped: ignore chunks until its final one has gone by. */
    private boolean discarding;
    /** Uploads dropped for their size, ever; the menu compares it across a chunk to see a refusal. */
    private int dropped;

    public LibraryUploadBuffer(Limits limits) {
        this.limits = limits;
    }

    /**
     * Take one chunk of the current upload.
     *
     * @param last whether this is the final chunk
     * @return the whole upload when {@code last} completes an intact one; null while more is expected, and for
     *         an upload that was or just became too large (that one never completes)
     */
    @Nullable
    byte[] accept(boolean last, byte[] chunk) {
        if (discarding) {
            if (last) discarding = false;
            return null;
        }
        if (chunks.size() >= limits.maxChunks() || (long) bytes + chunk.length > limits.maxUploadBytes()) {
            BCLog.logger.warn("[library] Dropping an upload past the limit ({} chunks, {} bytes buffered, {} more "
                + "arriving)", chunks.size(), bytes, chunk.length);
            reset();
            discarding = !last;
            dropped++;
            return null;
        }
        chunks.add(chunk);
        bytes += chunk.length;
        if (!last) return null;

        byte[] assembled = new byte[bytes];
        int pos = 0;
        for (byte[] c : chunks) {
            System.arraycopy(c, 0, assembled, pos, c.length);
            pos += c.length;
        }
        reset();
        return assembled;
    }

    /**
     * Drop the upload in progress and ignore the rest of the stream this chunk belongs to (up to its final one,
     * unless {@code last} says this was it): for chunks that arrive when no upload was asked for. Not counted as
     * {@link #droppedUploads() dropped}.
     */
    void skip(boolean last) {
        if (discarding) {
            if (last) discarding = false;
            return;
        }
        reset();
        discarding = !last;
    }

    /** Uploads dropped for going past the limits since this buffer was made. */
    int droppedUploads() {
        return dropped;
    }

    Limits limits() {
        return limits;
    }

    private void reset() {
        chunks.clear();
        bytes = 0;
    }

    /** Bytes held for the upload in progress. */
    int bufferedBytes() {
        return bytes;
    }

    /** Chunks held for the upload in progress. */
    int bufferedChunks() {
        return chunks.size();
    }

    /** Unpack an assembled upload into a snapshot (its key recomputed from its content), within this buffer's
     *  {@link Limits#maxExpandedBytes}. */
    Snapshot decode(byte[] assembled) throws IOException {
        return decode(assembled, limits.maxExpandedBytes(), limits.maxCells());
    }

    /**
     * @throws IOException if the data is not a snapshot, would unpack past {@code maxExpandedBytes}, or claims a size
     *                     of more than {@code maxCells} cells
     */
    static Snapshot decode(byte[] assembled, long maxExpandedBytes, int maxCells) throws IOException {
        Snapshot snapshot;
        try {
            snapshot = Snapshot.readFromNBT(NbtSquisher.expand(new ByteArrayInputStream(assembled), maxExpandedBytes),
                maxCells);
        } catch (RuntimeException e) {
            // A hand-built payload can be well-formed NBT that is not a snapshot; reading it can fail any way it likes.
            throw new IOException("Not a readable snapshot: " + e, e);
        }
        snapshot.computeKey();
        return snapshot;
    }
}
