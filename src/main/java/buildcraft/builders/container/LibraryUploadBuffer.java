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
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.lib.nbt.NbtSquisher;

/**
 * The server's side of a {@link ContainerElectronicLibrary} upload: collects the chunks a client sends for one
 * snapshot and hands back the whole once the final chunk arrives.
 * <p>
 * Everything about the stream is the client's to choose, so it is bounded here: at most {@link #MAX_UPLOAD_BYTES}
 * bytes in at most {@link #MAX_CHUNKS} chunks are kept, and an upload that goes past either is dropped as a unit
 * (buffer emptied, and the rest of that stream, up to its final chunk, ignored) rather than left to grow. What the
 * bytes unpack to is bounded too, by {@link #decode}: the transfer is compressed, so its size on the wire says
 * little about its size in memory.
 * <p>
 * Not thread-safe; a menu's messages arrive on the server thread one at a time.
 */
public final class LibraryUploadBuffer {
    /** What the sending client splits a snapshot into (it stays inside the per-packet limit). */
    public static final int CHUNK_BYTES = 32 * 1024;
    /** The most compressed bytes one upload may hold. A snapshot is a few KB to a few MB compressed; this leaves
     *  an order of magnitude over a large scan. */
    public static final int MAX_UPLOAD_BYTES = 16 * 1024 * 1024;
    /** The most chunks one upload may span: a full-size upload at the sender's chunk size, plus one of slack. */
    public static final int MAX_CHUNKS = MAX_UPLOAD_BYTES / CHUNK_BYTES + 1;
    /** The most NBT (by the game's own heap estimate) an upload may unpack to. A default-range scan is a few
     *  megabytes of it; this only stops a payload built to explode. */
    static final long MAX_EXPANDED_BYTES = 256L * 1024 * 1024;

    private final List<byte[]> chunks = new ArrayList<>();
    private int bytes;
    /** An upload overflowed: ignore chunks until its final one has gone by. */
    private boolean discarding;

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
        if (chunks.size() >= MAX_CHUNKS || (long) bytes + chunk.length > MAX_UPLOAD_BYTES) {
            BCLog.logger.warn("[library] Dropping an upload past the limit ({} chunks, {} bytes buffered, {} more "
                + "arriving)", chunks.size(), bytes, chunk.length);
            reset();
            discarding = !last;
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

    /** Unpack an assembled upload into a snapshot (its key recomputed from its content). */
    static Snapshot decode(byte[] assembled) throws IOException {
        return decode(assembled, MAX_EXPANDED_BYTES);
    }

    /**
     * @throws IOException if the data is not a snapshot, or would unpack past {@code maxExpandedBytes}
     */
    static Snapshot decode(byte[] assembled, long maxExpandedBytes) throws IOException {
        Snapshot snapshot;
        try {
            snapshot = Snapshot.readFromNBT(NbtSquisher.expand(new ByteArrayInputStream(assembled), maxExpandedBytes));
        } catch (RuntimeException e) {
            // A hand-built payload can be well-formed NBT that is not a snapshot; reading it can fail any way it likes.
            throw new IOException("Not a readable snapshot: " + e, e);
        }
        snapshot.computeKey();
        return snapshot;
    }
}
