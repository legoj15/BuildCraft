/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.net;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Collects travelling item data per-player per-tick and sends batched packets at end of each server tick.
 * This ensures that item movements are sent efficiently — one packet per player per tick instead
 * of one per item per pipe.
 */
public class PipeItemMessageQueue {

    private static final Map<ServerPlayer, MessageMultiPipeItem> cachedPlayerPackets = new WeakHashMap<>();

    /**
     * Called at end of each server tick to flush all accumulated item data to players.
     */
    public static void serverTick() {
        for (var entry : cachedPlayerPackets.entrySet()) {
            PacketDistributor.sendToPlayer(entry.getKey(), entry.getValue());
        }
        cachedPlayerPackets.clear();
    }

    /**
     * Appends an item's travelling data to be sent to all players watching the containing chunk.
     *
     * @param world      the server world
     * @param pos        the pipe's block position
     * @param stack      the item stack (will be serialized directly)
     * @param stackCount the stack count to display on client
     * @param toCenter   true if travelling toward center, false if toward exit
     * @param side       the side the item is coming from (if toCenter) or going to (if !toCenter)
     * @param colour     the dye colour tag, or null
     * @param timeToDest ticks until the item reaches its destination (center or exit)
     */
    public static void appendTravellingItem(Level world, BlockPos pos, ItemStack stack, int stackCount,
            boolean toCenter, Direction side, @Nullable DyeColor colour, int timeToDest) {
        if (!(world instanceof ServerLevel server)) {
            return;
        }

        // Find all players tracking this chunk
        ChunkPos chunkPos = ChunkPos.containing(pos);
        var chunkMap = server.getChunkSource().chunkMap;
        var players = chunkMap.getPlayers(chunkPos, false);

        for (ServerPlayer player : players) {
            cachedPlayerPackets.computeIfAbsent(player, pl -> new MessageMultiPipeItem())
                    .append(pos, stack, stackCount, toCenter, side, colour, timeToDest);
        }
    }
}
