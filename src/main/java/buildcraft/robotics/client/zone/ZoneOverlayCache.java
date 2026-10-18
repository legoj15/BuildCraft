/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.BitSet;

import buildcraft.robotics.zone.ZoneChunk;
import buildcraft.robotics.zone.ZonePlan;

/**
 * What the Zone Planner's map overlay draws, worked out once and reused: for each dye layer, the painted cells inside
 * the visible chunk window, as world coordinates.
 *
 * <p>The overlay is drawn every frame, but a layer only changes when somebody paints, and the view only moves when
 * somebody pans or zooms. A layer painted across the whole survey square holds millions of columns, so walking it (let
 * alone emitting a quad per column) each frame is not affordable. Instead {@link #cells} reads only the layer's chunks
 * that reach into the window ({@link ZonePlan#forEachChunkIn}), turns their bit rows into cells without allocating per
 * column, and keeps the result for its layer slot keyed on the plan instance, its {@link ZonePlan#modCount()}, the
 * window, the zoom stride and the planner's position. While those match, the same {@link Cells} object comes back and
 * the per-frame cost is the number of cells in view.
 *
 * <p>At a zoom of less than one pixel per block the terrain is sampled on a {@code stride} grid, and the overlay
 * follows: a cell is one world-aligned {@code stride x stride} square, listed when any column in it is painted, so the
 * cell count tracks the viewport's pixel count and not the world area, and a thin stroke does not vanish between
 * samples.
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

    /** One layer slot's last answer and everything it was computed from. */
    private static final class Entry {
        final WeakReference<ZonePlan> plan;
        final int modCount;
        final int tileX;
        final int tileZ;
        final int minChunkX;
        final int minChunkZ;
        final int maxChunkX;
        final int maxChunkZ;
        final int stride;
        final Cells cells;

        Entry(ZonePlan plan, int modCount, int tileX, int tileZ, int minChunkX, int minChunkZ, int maxChunkX,
              int maxChunkZ, int stride, Cells cells) {
            this.plan = new WeakReference<>(plan);
            this.modCount = modCount;
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.minChunkX = minChunkX;
            this.minChunkZ = minChunkZ;
            this.maxChunkX = maxChunkX;
            this.maxChunkZ = maxChunkZ;
            this.stride = stride;
            this.cells = cells;
        }

        boolean matches(ZonePlan plan, int tileX, int tileZ, int minChunkX, int minChunkZ, int maxChunkX,
                        int maxChunkZ, int stride) {
            return this.plan.get() == plan && modCount == plan.modCount() && this.tileX == tileX
                    && this.tileZ == tileZ && this.minChunkX == minChunkX && this.minChunkZ == minChunkZ
                    && this.maxChunkX == maxChunkX && this.maxChunkZ == maxChunkZ && this.stride == stride;
        }
    }

    private Entry[] entries = new Entry[16];
    private int builds;

    /**
     * The painted cells of {@code plan} (a tile-relative layer of the planner at {@code tileX, tileZ}) inside the
     * world chunk window {@code [minChunkX, maxChunkX] x [minChunkZ, maxChunkZ]}, at {@code stride} blocks per cell
     * ({@code stride} must divide 16, as the terrain's does). {@code slot} names the layer being drawn (0..15): each
     * slot remembers its latest answer, so call it with the same slot for the same layer every frame.
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
        if (entry != null && entry.matches(plan, tileX, tileZ, minChunkX, minChunkZ, maxChunkX, maxChunkZ, stride)) {
            return entry.cells;
        }
        int modCount = plan.modCount();
        Cells built = build(plan, tileX, tileZ, minChunkX, minChunkZ, maxChunkX, maxChunkZ, stride);
        builds++;
        entries[slot] = new Entry(plan, modCount, tileX, tileZ, minChunkX, minChunkZ, maxChunkX, maxChunkZ, stride,
                built);
        return built;
    }

    /** How many times a layer's cells have been worked out (as opposed to reused), for tests. */
    public synchronized int builds() {
        return builds;
    }

    /** Forgets every layer's cells: the planner screen opening, or the world changing under it. */
    public synchronized void clear() {
        Arrays.fill(entries, null);
    }

    /** Collects cells: appended one by one at stride 1, marked on a grid of {@code stride}-block squares otherwise. */
    private static final class Builder {
        private final int stride;
        private final int originX;
        private final int originZ;
        private final int gridWidth;
        private int[] xz = Cells.NO_CELLS;
        private int count;
        private final BitSet grid;

        Builder(int stride, int originX, int originZ, int widthBlocks, int heightBlocks) {
            this.stride = stride;
            this.originX = originX;
            this.originZ = originZ;
            this.gridWidth = widthBlocks / stride;
            this.grid = stride == 1 ? null : new BitSet(gridWidth * (heightBlocks / stride));
        }

        void add(int wx, int wz) {
            if (grid != null) {
                grid.set(((wz - originZ) / stride) * gridWidth + (wx - originX) / stride);
                return;
            }
            int at = count << 1;
            if (at + 2 > xz.length) {
                xz = Arrays.copyOf(xz, Math.max(64, xz.length << 1));
            }
            xz[at] = wx;
            xz[at + 1] = wz;
            count++;
        }

        Cells finish() {
            if (grid == null) {
                return new Cells(1, count == 0 ? Cells.NO_CELLS : Arrays.copyOf(xz, count << 1), count);
            }
            int cells = grid.cardinality();
            int[] out = cells == 0 ? Cells.NO_CELLS : new int[cells << 1];
            int n = 0;
            for (int i = grid.nextSetBit(0); i >= 0; i = grid.nextSetBit(i + 1)) {
                out[n++] = originX + (i % gridWidth) * stride;
                out[n++] = originZ + (i / gridWidth) * stride;
            }
            return new Cells(stride, out, cells);
        }
    }

    private static Cells build(ZonePlan plan, int tileX, int tileZ, int minChunkX, int minChunkZ, int maxChunkX,
                               int maxChunkZ, int stride) {
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
            return new Cells(stride, Cells.NO_CELLS, 0);
        }
        // The window in world blocks, upper bounds exclusive.
        int x0 = minChunkX << 4;
        int z0 = minChunkZ << 4;
        int x1 = (maxChunkX + 1) << 4;
        int z1 = (maxChunkZ + 1) << 4;
        Builder out = new Builder(stride, x0, z0, x1 - x0, z1 - z0);
        // The same window in the layer's own (tile-relative) chunks; a relative chunk straddles two world chunks
        // unless the planner sits on a chunk boundary, so the edge chunks are read and then clipped to the window.
        plan.forEachChunkIn(Math.floorDiv(x0 - tileX, 16), Math.floorDiv(z0 - tileZ, 16),
                Math.floorDiv(x1 - 1 - tileX, 16), Math.floorDiv(z1 - 1 - tileZ, 16),
                (chunkX, chunkZ, chunk) -> addChunk(out, chunk, (chunkX << 4) + tileX, (chunkZ << 4) + tileZ,
                        x0, z0, x1, z1));
        return out.finish();
    }

    /** Adds the columns of one layer chunk (whose lowest corner is world {@code baseX, baseZ}) that lie in the window. */
    private static void addChunk(Builder out, ZoneChunk chunk, int baseX, int baseZ, int x0, int z0, int x1, int z1) {
        int lo = Math.max(0, x0 - baseX);
        int hi = Math.min(15, x1 - 1 - baseX);
        if (lo > hi) {
            return;
        }
        int clip = ((1 << (hi + 1)) - 1) & ~((1 << lo) - 1);
        for (int z = 0; z < 16; z++) {
            int wz = baseZ + z;
            if (wz < z0 || wz >= z1) {
                continue;
            }
            int row = chunk.rowBits(z) & clip;
            while (row != 0) {
                out.add(baseX + Integer.numberOfTrailingZeros(row), wz);
                row &= row - 1;
            }
        }
    }
}
