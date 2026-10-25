/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import buildcraft.robotics.zone.ZoneSurveyRegion;

/**
 * A top-down colour+height snapshot of one world chunk, used by the Zone Planner's 3D map viewport.
 * For every column in the 16&times;16 chunk it records the surface block's Y and its
 * {@link MapColor#col map colour} &mdash; the same data a vanilla cartographer map would show.
 *
 * <p>Usually built client-side from a chunk the client holds: the {@link Heightmap.Types#WORLD_SURFACE}
 * heightmap this reads is {@code Usage.CLIENT}, i.e. synced to the client, so the lookup is cheap and
 * accurate. Past the client's chunks the survey view draws chunks the server sampled instead
 * ({@code ZoneSurveyMap}), coarser: one sample per {@link ZoneSurveyRegion#CELL}-block cell.
 */
public class ZonePlannerMapChunk {
    /** Sentinel surface Y for a column with no recordable block (e.g. an all-air column). */
    public static final int NO_DATA = Integer.MIN_VALUE;

    /** How far below the heightmap surface to keep searching for a block with a real map colour, for
     *  the rare top blocks that map to {@link MapColor#NONE} (barriers, light blocks, …). */
    private static final int COLOUR_SEARCH_DEPTH = 32;

    /** log2 of the blocks along each side of one stored cell: 0 for a chunk scanned here (one cell per column),
     *  2 for one the server sampled ({@link ZoneSurveyRegion#CELL}-block cells), whose every column reads its cell. */
    private final int cellShift;
    private final int[] surfaceY;
    private final int[] colour;

    public ZonePlannerMapChunk(Level level, LevelChunk chunk, int chunkX, int chunkZ) {
        cellShift = 0;
        surfaceY = new int[256];
        colour = new int[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = (chunkX << 4) + x;
                int worldZ = (chunkZ << 4) + z;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int idx = index(x, z);

                int foundY = NO_DATA;
                int foundColour = 0;
                // Scanning a few blocks below the world floor is harmless: out-of-bounds getBlockState
                // returns air, so the loop simply finds nothing — no need for a node-specific min-Y getter.
                int floor = top - COLOUR_SEARCH_DEPTH;
                for (int y = top; y >= floor; y--) {
                    pos.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    int rgb = state.getMapColor(level, pos).col;
                    if (rgb != 0) {
                        foundY = y;
                        foundColour = rgb;
                        break;
                    }
                }
                surfaceY[idx] = foundY;
                colour[idx] = foundColour;
            }
        }
    }

    /**
     * A chunk of the server's far map ({@code ZoneSurveyMap}), from its {@link ZoneSurveyRegion#SAMPLES} samples:
     * heights ({@link ZoneSurveyRegion#NO_DATA} for none) and vanilla map-colour ids.
     */
    public ZonePlannerMapChunk(short[] heights, byte[] colourIds) {
        cellShift = Integer.numberOfTrailingZeros(ZoneSurveyRegion.CELL);
        surfaceY = new int[ZoneSurveyRegion.SAMPLES];
        colour = new int[ZoneSurveyRegion.SAMPLES];
        for (int s = 0; s < ZoneSurveyRegion.SAMPLES; s++) {
            boolean known = heights[s] != ZoneSurveyRegion.NO_DATA;
            surfaceY[s] = known ? heights[s] : NO_DATA;
            colour[s] = known ? MapColor.byId(colourIds[s] & 63).col : 0;
        }
    }

    private int index(int x, int z) {
        return ((x & 15) >> cellShift) * (16 >> cellShift) + ((z & 15) >> cellShift);
    }

    /** Surface Y for the column, or {@link #NO_DATA} if the column has no recordable block. */
    public int getSurfaceY(int localX, int localZ) {
        return surfaceY[index(localX, localZ)];
    }

    /** Packed RGB map colour for the column's surface block (0 when {@link #getSurfaceY} is {@link #NO_DATA}). */
    public int getColour(int localX, int localZ) {
        return colour[index(localX, localZ)];
    }

    public boolean hasData(int localX, int localZ) {
        return surfaceY[index(localX, localZ)] != NO_DATA;
    }
}
