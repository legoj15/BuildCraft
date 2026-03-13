/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.item.ItemDebugger;

/**
 * Sent from client to server to request debug info for the block entity at the given position.
 * The server responds with a {@link MessageDebugResponse}.
 */
public record MessageDebugRequest(
        BlockPos pos,
        Direction side
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessageDebugRequest> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftlib:debug_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MessageDebugRequest> STREAM_CODEC =
            StreamCodec.of(MessageDebugRequest::encode, MessageDebugRequest::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MessageDebugRequest msg) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.side);
    }

    private static MessageDebugRequest decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction side = buf.readEnum(Direction.class);
        return new MessageDebugRequest(pos, side);
    }

    @Override
    public Type<MessageDebugRequest> type() {
        return TYPE;
    }

    /** Server-side handler. Looks up the tile entity and sends back debug info. */
    public static void handle(MessageDebugRequest message, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!ItemDebugger.isShowDebugInfo(player)) {
            // Player not authorized to see debug info - send empty response
            ctx.reply(new MessageDebugResponse(List.of(), List.of()));
            return;
        }
        BlockEntity tile = player.level().getBlockEntity(message.pos);
        if (tile instanceof IDebuggable debuggable) {
            List<String> left = new ArrayList<>();
            List<String> right = new ArrayList<>();
            debuggable.getDebugInfo(left, right, message.side);
            ctx.reply(new MessageDebugResponse(left, right));
        }
    }
}
