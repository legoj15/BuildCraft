/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.net;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BCLog;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.net.PacketBufferBC;
import buildcraft.transport.pipe.Pipe;

/**
 * Network payload for sending pipe flow/behaviour messages from server to client.
 * Replaces the old 1.12.2 MessageUpdateTile / IPayloadReceiver system for pipes.
 * <p>
 * Each message carries:
 * <ul>
 *   <li>{@code pos} — the pipe's block position</li>
 *   <li>{@code receiverOrdinal} — which part of the pipe this is for
 *       ({@link PipeMessageReceiver} ordinal)</li>
 *   <li>{@code payload} — the raw bytes written by the sender's IWriter</li>
 * </ul>
 */
public record MessagePipePayload(
        BlockPos pos,
        int receiverOrdinal,
        byte[] payload
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessagePipePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:pipe_payload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MessagePipePayload> STREAM_CODEC =
            StreamCodec.of(MessagePipePayload::encode, MessagePipePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, MessagePipePayload msg) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.receiverOrdinal);
        buf.writeByteArray(msg.payload);
    }

    private static MessagePipePayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int receiverOrdinal = buf.readVarInt();
        byte[] payload = buf.readByteArray();
        return new MessagePipePayload(pos, receiverOrdinal, payload);
    }

    @Override
    public Type<MessagePipePayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler. Looks up the pipe at the given position and
     * dispatches the payload to the correct receiver (flow, behaviour, or pluggable).
     */
    public static void handle(MessagePipePayload message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level world = ctx.player().level();
            if (world == null) return;

            BlockEntity tile = world.getBlockEntity(message.pos);
            if (!(tile instanceof buildcraft.transport.tile.TilePipeHolder holder)) {
                return;
            }
            Pipe pipe = holder.getPipe();
            if (pipe == null) return;

            PipeMessageReceiver[] receivers = PipeMessageReceiver.VALUES;
            if (message.receiverOrdinal < 0 || message.receiverOrdinal >= receivers.length) {
                BCLog.logger.warn("[transport.net] Invalid pipe message receiver ordinal: "
                        + message.receiverOrdinal);
                return;
            }
            PipeMessageReceiver receiver = receivers[message.receiverOrdinal];

            PacketBufferBC buffer = new PacketBufferBC(Unpooled.wrappedBuffer(message.payload));
            try {
                switch (receiver) {
                    case FLOW -> {
                        PipeFlow flow = pipe.getFlow();
                        if (flow != null) {
                            // The sendCustomPayload format: boolean(true) + short(id) + payload
                            boolean hasId = buffer.readBoolean();
                            if (hasId) {
                                int id = buffer.readShort();
                                flow.readPayload(id, buffer, null);
                            }
                        }
                    }
                    case BEHAVIOUR -> {
                        if (pipe.getBehaviour() != null) {
                            // PipeBehaviour.readPayload takes (buffer, ctx), no integer ID
                            pipe.getBehaviour().readPayload(buffer, null);
                        }
                    }
                    default -> {
                        // Pluggable messages — receiver has a face
                        if (receiver.face != null) {
                            buildcraft.api.transport.pluggable.PipePluggable plug = holder.getPluggable(receiver.face);
                            if (plug != null) {
                                plug.readPayload(buffer, receiver.face, Boolean.TRUE);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                BCLog.logger.warn("[transport.net] Error handling pipe payload at " + message.pos
                        + " for " + receiver, e);
            } finally {
                buffer.release();
            }
        });
    }
}
