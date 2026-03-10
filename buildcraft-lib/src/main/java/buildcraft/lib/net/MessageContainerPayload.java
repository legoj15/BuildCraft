/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import io.netty.buffer.Unpooled;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BCLog;
import buildcraft.lib.gui.ContainerBC_Neptune;

/**
 * Network payload for sending arbitrary container-scoped messages between client and server.
 * Replaces the old 1.12.2 MessageContainer / MessageManager system.
 * <p>
 * Each message carries:
 * <ul>
 *   <li>{@code containerId} — must match the player's currently open container</li>
 *   <li>{@code messageId} — dispatched by {@link ContainerBC_Neptune#readMessage}</li>
 *   <li>{@code payload} — arbitrary bytes written by the sender</li>
 * </ul>
 */
public record MessageContainerPayload(
        int containerId,
        int messageId,
        byte[] payload
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessageContainerPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftlib:container"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MessageContainerPayload> STREAM_CODEC =
            StreamCodec.of(MessageContainerPayload::encode, MessageContainerPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MessageContainerPayload msg) {
        buf.writeVarInt(msg.containerId);
        buf.writeVarInt(msg.messageId);
        buf.writeByteArray(msg.payload);
    }

    private static MessageContainerPayload decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int messageId = buf.readVarInt();
        byte[] payload = buf.readByteArray();
        return new MessageContainerPayload(containerId, messageId, payload);
    }

    @Override
    public Type<MessageContainerPayload> type() {
        return TYPE;
    }

    /**
     * Bidirectional handler — works on both client and server.
     * Validates the player's open container matches, then dispatches to
     * {@link ContainerBC_Neptune#readMessage}.
     */
    public static void handle(MessageContainerPayload message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            AbstractContainerMenu openContainer = player.containerMenu;
            if (openContainer == null) {
                BCLog.logger.warn("[lib.net] Received container message but player has no open container");
                return;
            }
            if (openContainer.containerId != message.containerId) {
                // Stale or mismatched packet — silently discard
                return;
            }
            if (!(openContainer instanceof ContainerBC_Neptune bcContainer)) {
                BCLog.logger.warn("[lib.net] Received container message but open container is not a ContainerBC_Neptune"
                    + " (got " + openContainer.getClass().getName() + ")");
                return;
            }
            PacketBufferBC buffer = new PacketBufferBC(Unpooled.wrappedBuffer(message.payload));
            try {
                boolean isClient = player.level().isClientSide();
                bcContainer.readMessage(message.messageId, buffer, isClient, ctx);
            } catch (Exception e) {
                BCLog.logger.warn("[lib.net] Error handling container message (id=" + message.messageId + ")", e);
            } finally {
                buffer.release();
            }
        });
    }
}
