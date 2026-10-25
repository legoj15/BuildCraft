/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * One {@value #SIZE}&times;{@value #SIZE}-chunk region of the Zone Planner's far map ({@link ZoneSurveyMap}): for each
 * chunk the server has recorded, a {@value #CELLS}&times;{@value #CELLS} grid of samples, each the surface height and
 * vanilla map-colour id of the lowest-corner column of a {@value #CELL}&times;{@value #CELL}-block cell. The survey
 * zoom draws one sample per 4 or 8 blocks, so this is all it needs; at 48 bytes a chunk (50 on the wire) a whole
 * survey square goes to a client in at most about 600 kilobytes. A chunk never recorded is {@linkplain #has unknown}, never flat ground.
 *
 * <p>Pure Java, no Minecraft types: the file format and the cell layout are unit-tested on their own.
 */
public final class ZoneSurveyRegion {
    /** Chunks along each side of a region. */
    public static final int SIZE = 32;
    public static final int CHUNKS = SIZE * SIZE;
    /** Blocks along each side of a sample cell, and cells along each side of a chunk. */
    public static final int CELL = 4;
    public static final int CELLS = 16 / CELL;
    public static final int SAMPLES = CELLS * CELLS;
    /** Height of a sample whose column holds nothing with a map colour (void, an all-air column). */
    public static final short NO_DATA = Short.MIN_VALUE;
    /** Bumped when the file layout changes; a file of another version is read as empty and rebuilt as chunks load. */
    public static final int FILE_VERSION = 1;

    private final short[] heights = new short[CHUNKS * SAMPLES];
    private final byte[] colours = new byte[CHUNKS * SAMPLES];
    private final long[] present = new long[CHUNKS / 64];

    /** Changed since it was last written to its file. */
    boolean dirty;
    /** Server game time it was last read or written, for unloading idle regions. */
    long lastUsed;

    /** Index of world chunk {@code (chunkX, chunkZ)} within its region. */
    public static int chunkIndex(int chunkX, int chunkZ) {
        return ((chunkX & (SIZE - 1)) * SIZE) + (chunkZ & (SIZE - 1));
    }

    /** Index of the sample covering chunk-local column {@code (localX, localZ)}. */
    public static int sampleIndex(int localX, int localZ) {
        return ((localX & 15) / CELL) * CELLS + ((localZ & 15) / CELL);
    }

    public boolean has(int chunk) {
        return (present[chunk >> 6] & (1L << (chunk & 63))) != 0;
    }

    public short height(int chunk, int sample) {
        return heights[chunk * SAMPLES + sample];
    }

    public byte colour(int chunk, int sample) {
        return colours[chunk * SAMPLES + sample];
    }

    /** Records one chunk's samples (copied), marking the region dirty only when something changed. */
    public void put(int chunk, short[] chunkHeights, byte[] chunkColours) {
        int at = chunk * SAMPLES;
        boolean changed = !has(chunk);
        for (int s = 0; s < SAMPLES && !changed; s++) {
            changed = heights[at + s] != chunkHeights[s] || colours[at + s] != chunkColours[s];
        }
        if (!changed) {
            return;
        }
        System.arraycopy(chunkHeights, 0, heights, at, SAMPLES);
        System.arraycopy(chunkColours, 0, colours, at, SAMPLES);
        present[chunk >> 6] |= 1L << (chunk & 63);
        dirty = true;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(FILE_VERSION);
        for (long bits : present) {
            out.writeLong(bits);
        }
        for (short h : heights) {
            out.writeShort(h);
        }
        out.write(colours);
    }

    /** Reads a region written by {@link #write}; a file of another version comes back empty. */
    public static ZoneSurveyRegion read(DataInputStream in) throws IOException {
        ZoneSurveyRegion region = new ZoneSurveyRegion();
        if (in.readInt() != FILE_VERSION) {
            return region;
        }
        for (int i = 0; i < region.present.length; i++) {
            region.present[i] = in.readLong();
        }
        for (int i = 0; i < region.heights.length; i++) {
            region.heights[i] = in.readShort();
        }
        in.readFully(region.colours);
        return region;
    }
}
