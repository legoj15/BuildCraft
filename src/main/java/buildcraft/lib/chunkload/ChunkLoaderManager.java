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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Keeps chunks force-loaded for tiles implementing {@link IChunkLoadingTile} (e.g. the Quarry),
 * using NeoForge's {@link TicketController} API. Tickets are owned by the tile's {@link BlockPos}
 * and persist across save/load; {@link #releaseChunksFor} drops them when the tile is removed.
 */
public class ChunkLoaderManager {

    /** Ticket controller for all BuildCraft chunk-loading tiles. Must be registered on the mod
     *  event bus (see {@link #registerTicketController}) or NeoForge discards its tickets on load. */
    public static final TicketController CONTROLLER =
        new TicketController(Identifier.fromNamespaceAndPath("buildcraftunofficial", "chunkloader"));

    public static void registerTicketController(IEventBus modEventBus) {
        modEventBus.addListener((RegisterTicketControllersEvent event) -> event.register(CONTROLLER));
    }

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
            CONTROLLER.forceChunk(serverLevel, owner, buildcraft.lib.misc.PositionUtil.chunkX(chunk), buildcraft.lib.misc.PositionUtil.chunkZ(chunk), add, false);
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
