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

import buildcraft.core.BCUnifiedConfig;

import buildcraft.builders.BCBuildersConfig;
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
 * One config option, {@link BCBuildersConfig#libraryMaxUploadMb}, sizes all of it ({@link Limits#current}): the bytes
 * on the wire, what they unpack to, and the cells a snapshot of any type may claim. Its default is a small multiple of
 * the largest scan the default config can make; a server that allows larger scans raises it.
 * <p>
 * Not thread-safe; a menu's messages arrive on the server thread one at a time.
 */
public final class LibraryUploadBuffer {
    /** What the sending client splits a snapshot into (it stays inside the per-packet limit). */
    public static final int CHUNK_BYTES = 32 * 1024;
    /** What the game's NBT accounter charges for one blueprint cell: an {@code IntTag} (12) plus its slot in the
     *  list (4). Blueprints store one per cell, so this dominates a large scan; pinned by a test on every node. */
    static final long EXPANDED_BYTES_PER_CELL = 16;

    /**
     * How much one upload may hold.
     *
     * @param maxUploadBytes   compressed bytes on the wire
     * @param maxChunks        chunks they may come in (each costs an array, however small)
     * @param maxExpandedBytes NBT they may unpack to, by the game's own heap estimate
     */
    public record Limits(int maxUploadBytes, int maxChunks, long maxExpandedBytes) {
        /**
         * The limits for an upload of {@code megabytes} MB: that many bytes on the wire and unpacked. Nothing a
         * snapshot holds is larger unpacked than packed by more than a blueprint's four wire bytes to sixteen
         * accounted ones a cell, so a size that fits the unpacked limit has room on the wire.
         */
        public static Limits ofMegabytes(int megabytes) {
            long bytes = megabytes * 1024L * 1024L;
            int upload = (int) Math.min(bytes, Integer.MAX_VALUE - CHUNK_BYTES);
            // A full upload at the sender's chunk size, plus one of slack.
            return new Limits(upload, upload / CHUNK_BYTES + 1, bytes);
        }

        /** The most cells a snapshot of any type may claim in its header: what {@link #maxExpandedBytes} pays for at
         *  the worst per-cell cost (a blueprint's). */
        public int maxCells() {
            return (int) Math.min(Integer.MAX_VALUE, maxExpandedBytes / EXPANDED_BYTES_PER_CELL);
        }

        /** The limits for the config as it is now. */
        public static Limits current() {
            // Not loaded before the common config is (a menu cannot open before then; this guards the tools that
            // build limits at class-load time).
            int megabytes = BCUnifiedConfig.SPEC.isLoaded() ? BCBuildersConfig.libraryMaxUploadMb.get()
                : BCBuildersConfig.LIBRARY_MAX_UPLOAD_MB_DEFAULT;
            return ofMegabytes(megabytes);
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

    /**
     * Free what an upload in progress holds because the request it answers is over (withdrawn, timed out, answered
     * by someone else), and ignore the rest of its stream up to its final chunk. A buffer holding nothing is left as
     * it is: no stream is in flight, so the next chunk starts a new upload and not the tail of an old one.
     */
    void release() {
        if (chunks.isEmpty()) return;
        skip(false);
    }

    /**
     * A fresh request for an upload is going out to the player this buffer belongs to: nothing left over from an
     * earlier request's stream is part of the answer to it, so the chunks held are dropped and so is any ignoring of
     * a stream's tail ({@link #release}, {@link #skip}, an overflow). Without this the ignoring lasts until a final
     * chunk goes by, and a client that never sends one (modified, or wedged) swallows the next honest upload whole.
     * Not counted as {@link #droppedUploads() dropped}.
     */
    void beginRequest() {
        reset();
        discarding = false;
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
