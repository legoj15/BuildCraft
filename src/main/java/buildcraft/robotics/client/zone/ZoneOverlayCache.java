/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import buildcraft.robotics.zone.ZoneChunk;
import buildcraft.robotics.zone.ZonePlan;

/**
 * What the Zone Planner's map overlay draws, worked out once and reused: for each dye layer, the painted cells inside
 * the visible chunk window, as world coordinates.
 *
 * <p>The overlay is drawn every frame, but a layer only changes when somebody paints, and the view only moves when
 * somebody pans or zooms. A layer painted across the whole survey square holds millions of columns, so walking it (let
 * alone emitting a quad per column) each frame is not affordable. Two levels of reuse keep the cost down:
 *
 * <ul>
 *   <li><b>The answer.</b> While a layer slot is asked about the same plan instance, {@link ZonePlan#modCount()},
 *   window, zoom stride and planner position, the same {@link Cells} object comes back and the per-frame cost is the
 *   number of cells in view.</li>
 *   <li><b>The world chunk.</b> Panning moves the window a chunk at a time (every step at the survey zoom), which
 *   changes the answer but not the chunks that stay in view. Each layer slot therefore also keeps the cells of every
 *   world chunk it has worked out, for as long as the plan, its modification count, the zoom stride and the planner
 *   position stay the same, and a new window costs only the chunks that entered it (plus copying the cells of the
 *   rest). A chunk is worked out from the layer's bit rows alone: the layer is stored relative to the planner, so
 *   a world chunk is a 16-bit-row shift of at most four layer chunks, and no column is visited singly.</li>
 * </ul>
 *
 * <p>At a zoom of less than one pixel per block the terrain is sampled on a {@code stride} grid, and the overlay
 * follows: a cell is one world-aligned {@code stride x stride} square, listed when any column in it is painted, so the
 * cell count tracks the viewport's pixel count and not the world area, and a thin stroke does not vanish between
 * samples. The rows of a chunk are folded into stride-wide masks (the {@code stride} rows of a band ORed together,
 * then tested a stride-wide group of bits at a time) rather than marking columns one by one.
 *
 * <p>Cells come out one world chunk at a time, chunk after chunk down the window, at every stride, so a consumer that
 * looks up per-chunk data (the terrain under the cell) can keep the last chunk it looked up.
 *
 * <p>Single-threaded like the rest of the map code (the client render thread); the methods are {@code synchronized}
 * only so a stray second thread cannot see a half-built entry. Plans are held weakly, so a closed planner's layers are
 * not kept alive.
 */
public final class ZoneOverlayCache {

    /** The painted cells of one layer in view: the world coordinates of each cell's lowest corner. */
    public static final class Cells {
        private static final int[] NO_CELLS = new int[0];

        private final int stride;
        private final int[] xz;
        private final int count;

        private Cells(int stride, int[] xz, int count) {
            this.stride = stride;
            this.xz = xz;
            this.count = count;
        }

        /** Blocks along each side of a cell ({@code 1} when the view is at least one pixel per block). */
        public int stride() {
            return stride;
        }

        public int size() {
            return count;
        }

        public int x(int index) {
            return xz[index << 1];
        }

        public int z(int index) {
            return xz[(index << 1) + 1];
        }
    }

    /** One layer slot: what its cells are worked out from, the world chunks worked out so far, and the last answer. */
    private static final class Entry {
        final WeakReference<ZonePlan> plan;
        final int modCount;
        final int tileX;
        final int tileZ;
        final int stride;
        /** The cells of each world chunk worked out so far (x,z pairs; a shared empty array for a chunk with none). */
        final Map<Long, int[]> chunks = new HashMap<>();

        /** The window of {@link #cells}, once there is one. */
        int minChunkX;
        int minChunkZ;
        int maxChunkX;
        int maxChunkZ;
        Cells cells;

        Entry(ZonePlan plan, int tileX, int tileZ, int stride) {
            this.plan = new WeakReference<>(plan);
            this.modCount = plan.modCount();
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.stride = stride;
        }

        boolean sameSource(ZonePlan plan, int tileX, int tileZ, int stride) {
            return this.plan.get() == plan && modCount == plan.modCount() && this.tileX == tileX
                    && this.tileZ == tileZ && this.stride == stride;
        }

        boolean sameWindow(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
            return cells != null && this.minChunkX == minChunkX && this.minChunkZ == minChunkZ
                    && this.maxChunkX == maxChunkX && this.maxChunkZ == maxChunkZ;
        }
    }

    private Entry[] entries = new Entry[16];
    private int builds;
    private int chunkBuilds;

    /**
     * The painted cells of {@code plan} (a tile-relative layer of the planner at {@code tileX, tileZ}) inside the
     * world chunk window {@code [minChunkX, maxChunkX] x [minChunkZ, maxChunkZ]}, at {@code stride} blocks per cell
     * ({@code stride} must divide 16, as the terrain's does). {@code slot} names the layer being drawn (0..15): each
     * slot remembers its latest answer and the chunks behind it, so call it with the same slot for the same layer
     * every frame.
     */
    public synchronized Cells cells(int slot, ZonePlan plan, int tileX, int tileZ,
                                    int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int stride) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot " + slot);
        }
        if (stride < 1 || 16 % stride != 0) {
            throw new IllegalArgumentException("stride " + stride + " does not divide a chunk");
        }
        if (slot >= entries.length) {
            entries = Arrays.copyOf(entries, slot + 1);
        }
        Entry entry = entries[slot];
        if (entry == null || !entry.sameSource(plan, tileX, tileZ, stride)) {
            entry = new Entry(plan, tileX, tileZ, stride);
            entries[slot] = entry;
        } else if (entry.sameWindow(minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
            return entry.cells;
        }
        Cells built = assemble(entry, plan, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        builds++;
        entry.minChunkX = minChunkX;
        entry.minChunkZ = minChunkZ;
        entry.maxChunkX = maxChunkX;
        entry.maxChunkZ = maxChunkZ;
        entry.cells = built;
        return built;
    }

    /** How many times a layer's cells have been put together for a new view or edit (as opposed to reused), for tests. */
    public synchronized int builds() {
        return builds;
    }

    /** How many world chunks' cells have been worked out from a layer's bits, for tests. */
    public synchronized int chunkBuilds() {
        return chunkBuilds;
    }

    /** Forgets every layer's cells: the planner screen opening, or the world changing under it. */
    public synchronized void clear() {
        Arrays.fill(entries, null);
    }

    /** The answer for a window: each chunk's cells in turn, taken from the entry or worked out and kept in it. */
    private Cells assemble(Entry entry, ZonePlan plan, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ || plan.getChunkMapping().isEmpty()) {
            return new Cells(entry.stride, Cells.NO_CELLS, 0);
        }
        long windowChunks = ((long) maxChunkX - minChunkX + 1) * ((long) maxChunkZ - minChunkZ + 1);
        int[][] parts = new int[(int) windowChunks][];
        int total = 0;
        int part = 0;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ZonePlan.packChunkKey(cx, cz);
                int[] chunkCells = entry.chunks.get(key);
                if (chunkCells == null) {
                    chunkCells = buildChunk(plan, entry.tileX, entry.tileZ, entry.stride, cx, cz);
                    entry.chunks.put(key, chunkCells);
                    chunkBuilds++;
                }
                parts[part++] = chunkCells;
                total += chunkCells.length;
            }
        }
        int[] xz = total == 0 ? Cells.NO_CELLS : new int[total];
        int at = 0;
        for (int[] chunkCells : parts) {
            System.arraycopy(chunkCells, 0, xz, at, chunkCells.length);
            at += chunkCells.length;
        }
        dropChunksFarFromWindow(entry, minChunkX, minChunkZ, maxChunkX, maxChunkZ, windowChunks);
        return new Cells(entry.stride, xz, total >> 1);
    }

    /** Keeps an entry's chunk map from growing as the view wanders: once it holds far more than the window needs, the
     *  chunks outside the window go (it is generous, so panning back and forth over an edge does not rebuild the
     *  chunks on either side of it). */
    private static void dropChunksFarFromWindow(Entry entry, int minChunkX, int minChunkZ, int maxChunkX,
                                                int maxChunkZ, long windowChunks) {
        if (entry.chunks.size() <= windowChunks * 2 + 64) {
            return;
        }
        for (Iterator<Long> it = entry.chunks.keySet().iterator(); it.hasNext(); ) {
            long key = it.next();
            int cx = ZonePlan.unpackChunkX(key);
            int cz = ZonePlan.unpackChunkZ(key);
            if (cx < minChunkX || cx > maxChunkX || cz < minChunkZ || cz > maxChunkZ) {
                it.remove();
            }
        }
    }

    /**
     * The cells of one world chunk {@code (cx, cz)}: the layer (stored relative to the planner at
     * {@code tileX, tileZ}) shifted into world coordinates a row of 16 bits at a time.
     *
     * <p>World column {@code cx * 16 + i} is layer column {@code (cx - qx) * 16 + (i - rx)}, where
     * {@code tileX = qx * 16 + rx}: so the world chunk's row takes the layer chunk {@code cx - qx} shifted up by
     * {@code rx} bits, and the top {@code rx} bits of the layer chunk {@code cx - qx - 1} to its left, which spill into
     * the low {@code rx} bits. Rows shift the same way against {@code tileZ}, so at most four layer chunks are read
     * (one when the planner sits on a chunk corner), and a layer chunk this window does not touch is not read at all.
     */
    private static int[] buildChunk(ZonePlan plan, int tileX, int tileZ, int stride, int cx, int cz) {
        int qx = Math.floorDiv(tileX, 16);
        int rx = Math.floorMod(tileX, 16);
        int qz = Math.floorDiv(tileZ, 16);
        int rz = Math.floorMod(tileZ, 16);
        Map<Long, ZoneChunk> layer = plan.getChunkMapping();
        // [0] this layer chunk, [1] the one above it (smaller z), [2] the one to its left (smaller x), [3] both.
        ZoneChunk[] src = new ZoneChunk[4];
        boolean any = false;
        for (int dz = 0; dz <= (rz == 0 ? 0 : 1); dz++) {
            for (int dx = 0; dx <= (rx == 0 ? 0 : 1); dx++) {
                ZoneChunk found = layer.get(ZonePlan.packChunkKey(cx - qx - dx, cz - qz - dz));
                src[dz * 2 + dx] = found;
                any |= found != null;
            }
        }
        if (!any) {
            return Cells.NO_CELLS;
        }
        int[] rows = new int[16];
        for (int j = 0; j < 16; j++) {
            int dz = j >= rz ? 0 : 1;
            int layerRow = j >= rz ? j - rz : j - rz + 16;
            int bits = 0;
            ZoneChunk here = src[dz * 2];
            if (here != null) {
                bits |= (here.rowBits(layerRow) << rx) & 0xFFFF;
            }
            ZoneChunk left = src[dz * 2 + 1];
            if (left != null) {
                bits |= left.rowBits(layerRow) >>> (16 - rx);
            }
            rows[j] = bits;
        }
        return stride == 1 ? cellsOfRows(rows, cx << 4, cz << 4) : cellsOfBands(rows, stride, cx << 4, cz << 4);
    }

    /** One cell per set bit: the chunk's rows as world columns. */
    private static int[] cellsOfRows(int[] rows, int baseX, int baseZ) {
        int count = 0;
        for (int row : rows) {
            count += Integer.bitCount(row);
        }
        if (count == 0) {
            return Cells.NO_CELLS;
        }
        int[] out = new int[count << 1];
        int n = 0;
        for (int j = 0; j < 16; j++) {
            int row = rows[j];
            while (row != 0) {
                out[n++] = baseX + Integer.numberOfTrailingZeros(row);
                out[n++] = baseZ + j;
                row &= row - 1;
            }
        }
        return out;
    }

    /**
     * One cell per {@code stride x stride} square with a set bit: each band of {@code stride} rows is ORed into one
     * row, whose stride-wide groups of bits are then each one cell. Cells come out row of squares by row of squares.
     */
    private static int[] cellsOfBands(int[] rows, int stride, int baseX, int baseZ) {
        int groups = 16 / stride;
        int groupMask = (1 << stride) - 1;
        int[] out = new int[groups * groups * 2];
        int n = 0;
        for (int band = 0; band < groups; band++) {
            int merged = 0;
            for (int r = band * stride; r < (band + 1) * stride; r++) {
                merged |= rows[r];
            }
            for (int group = 0; group < groups; group++) {
                if (((merged >>> (group * stride)) & groupMask) != 0) {
                    out[n++] = baseX + group * stride;
                    out[n++] = baseZ + band * stride;
                }
            }
        }
        return n == 0 ? Cells.NO_CELLS : Arrays.copyOf(out, n);
    }
}
