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

/**
 * A top-down colour+height snapshot of one world chunk, used by the Zone Planner's 3D map viewport.
 * For every column in the 16&times;16 chunk it records the surface block's Y and its
 * {@link MapColor#col map colour} &mdash; the same data a vanilla cartographer map would show.
 *
 * <p><b>Built entirely client-side.</b> The 1.12.2 original streamed these from the server because the
 * map could be panned into chunks the client hadn't loaded; here the viewport is anchored on the
 * planner the player is standing next to, so the client's own loaded chunks cover the visible area and
 * no networking is needed. The {@link Heightmap.Types#WORLD_SURFACE} heightmap this reads is
 * {@code Usage.CLIENT}, i.e. synced to the client, so the lookup is cheap and accurate.
 */
public class ZonePlannerMapChunk {
    /** Sentinel surface Y for a column with no recordable block (e.g. an all-air column). */
    public static final int NO_DATA = Integer.MIN_VALUE;

    /** How far below the heightmap surface to keep searching for a block with a real map colour, for
     *  the rare top blocks that map to {@link MapColor#NONE} (barriers, light blocks, …). */
    private static final int COLOUR_SEARCH_DEPTH = 32;

    private final int[] surfaceY = new int[256];
    private final int[] colour = new int[256];

    public ZonePlannerMapChunk(Level level, LevelChunk chunk, int chunkX, int chunkZ) {
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

    private static int index(int x, int z) {
        return (x & 15) * 16 + (z & 15);
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
