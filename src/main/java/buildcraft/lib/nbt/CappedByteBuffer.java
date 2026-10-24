/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.nbt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An in-memory output stream that refuses to hold more than a limit: the write that would pass it throws, whole and
 * before anything of it is kept, so a caller measuring a large stream stops at the limit instead of buffering the rest.
 * <p>
 * The bytes live in fixed-size chunks. A {@link java.io.ByteArrayOutputStream} doubles its array as it grows (a
 * transient of up to three times the data) and copies it once more out of {@code toByteArray}; this does neither, so
 * what it holds is the data and a partly used last chunk, and {@link #openRead} reads it in place. It is for a stream
 * that is written once and then read back, which is all the NBT size check does.
 */
final class CappedByteBuffer extends OutputStream {
    static final int DEFAULT_CHUNK_BYTES = 64 * 1024;

    private final long limit;
    private final int chunkBytes;
    private final List<byte[]> chunks = new ArrayList<>();
    private long size;
    /** Bytes used in the last chunk; 0 when there is none yet. */
    private int lastUsed;

    CappedByteBuffer(long limit) {
        this(limit, DEFAULT_CHUNK_BYTES);
    }

    /** @param chunkBytes the size of each chunk (a small one is for tests that want to cross chunk borders) */
    CappedByteBuffer(long limit, int chunkBytes) {
        if (limit < 0 || chunkBytes <= 0) {
            throw new IllegalArgumentException("limit " + limit + ", chunkBytes " + chunkBytes);
        }
        this.limit = limit;
        this.chunkBytes = chunkBytes;
    }

    /** How many bytes are held. Never more than the limit. */
    long size() {
        return size;
    }

    @Override
    public void write(int b) throws IOException {
        reserve(1);
        chunkWithRoom()[lastUsed++] = (byte) b;
        size++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        reserve(len);
        while (len > 0) {
            byte[] chunk = chunkWithRoom();
            int n = Math.min(len, chunk.length - lastUsed);
            System.arraycopy(b, off, chunk, lastUsed, n);
            lastUsed += n;
            off += n;
            len -= n;
            size += n;
        }
    }

    private void reserve(int more) throws IOException {
        if (more > limit - size) {
            throw new Overflow();
        }
    }

    /**
     * Thrown by the write that would pass the limit. It names no figure: the limit is the caller's own derived cap,
     * not a number anyone configured, so the caller reports the overflow against the limit it was given.
     */
    static final class Overflow extends IOException {
        private Overflow() {
            super("the stream passed the buffer's limit");
        }
    }

    /** The last chunk, or a new one when it is full (or there is none yet); {@code lastUsed} indexes its free part. */
    private byte[] chunkWithRoom() {
        if (chunks.isEmpty() || lastUsed == chunkBytes) {
            chunks.add(new byte[chunkBytes]);
            lastUsed = 0;
        }
        return chunks.get(chunks.size() - 1);
    }

    /** A reader over what has been written so far; the buffer must not be written to while it is read. */
    InputStream openRead() {
        return new Reader();
    }

    private final class Reader extends InputStream {
        private int chunk;
        private int pos;
        private long remaining = size;

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            byte[] current = chunks.get(chunk);
            int b = current[pos++] & 0xFF;
            if (pos == current.length) {
                chunk++;
                pos = 0;
            }
            remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            int total = (int) Math.min(len, remaining);
            int left = total;
            while (left > 0) {
                byte[] current = chunks.get(chunk);
                int n = Math.min(left, current.length - pos);
                System.arraycopy(current, pos, b, off, n);
                pos += n;
                off += n;
                left -= n;
                if (pos == current.length) {
                    chunk++;
                    pos = 0;
                }
            }
            remaining -= total;
            return total;
        }

        @Override
        public int available() {
            return (int) Math.min(remaining, Integer.MAX_VALUE);
        }
    }
}
