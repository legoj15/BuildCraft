/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.chunkload;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ChunkPos;

import buildcraft.api.core.BCLog;

/**
 * Chunk loading manager stub for NeoForge 1.21.
 * The old ForgeChunkManager TicketHelper API was removed.
 * TODO: Re-implement using NeoForge forced chunk loading API.
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
        // TODO: Re-implement chunk release with NeoForge 1.21 forced chunk API
        BCLog.logger.debug("[lib.chunkloading] releaseChunksFor called (stub) for {}", tile.getClass().getName());
    }

    private static <T extends BlockEntity & IChunkLoadingTile> void updateChunksFor(T tile) {
        // TODO: Re-implement chunk forcing with NeoForge 1.21 forced chunk API
        BCLog.logger.debug("[lib.chunkloading] updateChunksFor called (stub) for {}", tile.getClass().getName());
    }

    public static <T extends BlockEntity & IChunkLoadingTile> Set<ChunkPos> getChunksToLoad(T tile) {
        Set<ChunkPos> chunksToLoad = tile.getChunksToLoad();
        Set<ChunkPos> chunkPoses = new HashSet<>(chunksToLoad != null ? chunksToLoad : Collections.emptyList());
        chunkPoses.add(new ChunkPos(tile.getBlockPos()));
        return chunkPoses;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean canLoadFor(IChunkLoadingTile tile) {
        // BCLibConfig is not ported yet — always allow chunk loading for now
        return tile.getLoadType() != null;
    }
}
