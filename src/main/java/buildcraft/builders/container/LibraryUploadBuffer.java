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
 * past either is dropped as a unit rather than left to grow: the buffer is emptied and its request closed, so the rest
 * of that stream is refused chunk by chunk (it carries the closed request's id) without anything waiting for its final
 * chunk. Which stream a chunk belongs to is the request id the server sent with its request and the client echoes on
 * every chunk ({@link #beginRequest}): a chunk of any other request, however late it lands, is not part of the
 * answer, and a stream that never sends its final chunk cannot spill into the next one.
 * What the bytes unpack to is bounded too, by {@link #decode}: the transfer is compressed, so its size on the wire
 * says little about its size in memory.
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
    /** The request whose answer this buffer is taking ({@link #NO_REQUEST} while none is): only chunks the client
     *  tagged with it are kept. Ends when the answer completes, is dropped, or is {@link #release released}. */
    private int answering = NO_REQUEST;
    /** The tag the last {@link #beginRequest} handed out. */
    private int lastRequest = NO_REQUEST;
    /** Uploads dropped for their size, ever; the menu compares it across a chunk to see a refusal. */
    private int dropped;

    /** The tag no request carries. */
    static final int NO_REQUEST = 0;

    public LibraryUploadBuffer(Limits limits) {
        this.limits = limits;
    }

    /**
     * A fresh request for an upload is going out to the player this buffer belongs to: whatever an earlier one left
     * held is dropped, and only chunks tagged with the returned id (the client echoes it on every chunk of its
     * answer) are taken from now on. The tail of an earlier stream that is still on the wire carries the earlier
     * id, so it is told apart from the new stream however late it lands, and a stream that never sends its final
     * chunk cannot spill into the next answer either. Not counted as {@link #droppedUploads() dropped}.
     *
     * @return the id the request goes out with; never {@link #NO_REQUEST}, and never the previous one
     */
    int beginRequest() {
        reset();
        lastRequest = lastRequest == Integer.MAX_VALUE ? 1 : lastRequest + 1;
        answering = lastRequest;
        return answering;
    }

    /** The request whose answer this buffer is taking, or {@link #NO_REQUEST}. */
    int answeringRequest() {
        return answering;
    }

    /** Whether a chunk tagged {@code request} belongs to the answer this buffer is taking. */
    boolean isAnswering(int request) {
        return request != NO_REQUEST && request == answering;
    }

    /**
     * Take one chunk of an upload.
     *
     * @param request the request id the client tagged the chunk with
     * @param last    whether this is the final chunk
     * @return the whole upload when {@code last} completes an intact answer to the current request; null while more
     *         is expected, for a chunk of any other (or no) request, and for an answer that was or just became too
     *         large (that one never completes)
     */
    @Nullable
    byte[] accept(int request, boolean last, byte[] chunk) {
        if (!isAnswering(request)) return null;
        if (chunks.size() >= limits.maxChunks() || (long) bytes + chunk.length > limits.maxUploadBytes()) {
            BCLog.logger.warn("[library] Dropping an upload past the limit ({} chunks, {} bytes buffered, {} more "
                + "arriving)", chunks.size(), bytes, chunk.length);
            release();
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
        release(); // one request, one answer
        return assembled;
    }

    /**
     * Free what an upload in progress holds and take no more chunks until the next {@link #beginRequest}: the
     * request it answers is over (withdrawn, timed out, answered by someone else), or chunks arrived that no request
     * this player may answer asked for. Not counted as {@link #droppedUploads() dropped}.
     */
    void release() {
        reset();
        answering = NO_REQUEST;
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
