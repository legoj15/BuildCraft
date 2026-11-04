/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.chunkload;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.lib.misc.PositionUtil;
import buildcraft.platform.Platform;

/**
 * Keeps chunks force-loaded for tiles implementing {@link IChunkLoadingTile} (e.g. the Quarry),
 * through the platform's chunk tickets. Tickets are owned by the tile's {@link BlockPos}
 * (and persist across save/load where the loader supports it); {@link #releaseChunksFor} drops them when the tile is
 * removed.
 */
public class ChunkLoaderManager {

    public static <T extends BlockEntity & IChunkLoadingTile> void loadChunksForTile(T tile) {
        if (!canLoadFor(tile)) {
            releaseChunksFor(tile);
            return;
        }
        updateChunksFor(tile);
    }

    public static <T extends BlockEntity & IChunkLoadingTile> void releaseChunksFor(T tile) {
        forceChunks(tile, false);
    }

    private static <T extends BlockEntity & IChunkLoadingTile> void updateChunksFor(T tile) {
        forceChunks(tile, true);
    }

    private static <T extends BlockEntity & IChunkLoadingTile> void forceChunks(T tile, boolean add) {
        if (!(tile.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos owner = tile.getBlockPos();
        for (ChunkPos chunk : getChunksToLoad(tile)) {
            Platform.chunkTickets().forceChunk(serverLevel, owner, PositionUtil.chunkX(chunk), PositionUtil.chunkZ(chunk), add);
        }
    }

    public static <T extends BlockEntity & IChunkLoadingTile> Set<ChunkPos> getChunksToLoad(T tile) {
        Set<ChunkPos> chunksToLoad = tile.getChunksToLoad();
        Set<ChunkPos> chunkPoses = new HashSet<>(chunksToLoad != null ? chunksToLoad : Collections.emptyList());
        chunkPoses.add(buildcraft.lib.misc.PositionUtil.chunkContaining(tile.getBlockPos()));
        return chunkPoses;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean canLoadFor(IChunkLoadingTile tile) {
        return tile.getLoadType() != null;
    }
}
