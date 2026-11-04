/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * BuildCraft's play-phase networking: registering payloads and sending them. Payload records, their
 * {@link CustomPacketPayload.Type}s and {@link StreamCodec}s are vanilla and stay in common code; only the
 * registration and the routing go through here.
 *
 * <p><b>Registration</b> is a plain call made during BuildCraft's init (mod construction), Fabric-shaped: NeoForge
 * queues the calls and replays them, in call order, inside its payload-registration event; Fabric registers them
 * at once. A handler runs on the receiving side's main thread (both loaders hand it over already there); one that
 * needs the game state settled still goes through {@link PacketContext#runOnMain}, exactly as before the seam.
 *
 * <p><b>Versions.</b> Every payload carries a protocol version, {@link #DEFAULT_VERSION} unless one is given; NeoForge
 * refuses a connection whose payload versions differ. Change a payload's version when its codec changes.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 */
public interface BCNetwork {

    /** The protocol version a payload registered without one carries. */
    String DEFAULT_VERSION = "1";

    /** Registers a server-to-client payload; {@code handler} runs on the client. */
    <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version);

    /** Registers a client-to-server payload; {@code handler} runs on the server. */
    <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version);

    /** Registers a payload either side may send; the same {@code handler} runs on whichever side receives it. */
    <T extends CustomPacketPayload> void bidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version);

    /** {@link #clientbound(CustomPacketPayload.Type, StreamCodec, PacketHandler, String)} at the default version. */
    default <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler) {
        clientbound(type, codec, handler, DEFAULT_VERSION);
    }

    /** {@link #serverbound(CustomPacketPayload.Type, StreamCodec, PacketHandler, String)} at the default version. */
    default <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler) {
        serverbound(type, codec, handler, DEFAULT_VERSION);
    }

    /** {@link #bidirectional(CustomPacketPayload.Type, StreamCodec, PacketHandler, String)} at the default version. */
    default <T extends CustomPacketPayload> void bidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler) {
        bidirectional(type, codec, handler, DEFAULT_VERSION);
    }

    /** Sends a payload from the client to the server it is connected to. Call only on the physical client. */
    void sendToServer(CustomPacketPayload payload);

    /** Sends a payload to one player. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Sends a payload to every player that has the chunk at {@code chunkPos} of {@code level} loaded. */
    void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload);

    /** Sends a payload to every player in {@code level}. */
    void sendToDimension(ServerLevel level, CustomPacketPayload payload);

    /**
     * Wraps {@code buf} as a {@link RegistryFriendlyByteBuf} over {@code registries}, so registry-backed codecs
     * ({@code ItemStack.OPTIONAL_STREAM_CODEC} and friends) can run on a buffer that is not a connection's own
     * (a container message's inner bytes, a test). On NeoForge it is marked a NeoForge-to-NeoForge connection, the
     * marking BuildCraft's own payloads carry.
     */
    RegistryFriendlyByteBuf registryBuf(ByteBuf buf, RegistryAccess registries);
}
