/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

import buildcraft.platform.network.BCNetwork;
import buildcraft.platform.network.PacketContext;
import buildcraft.platform.network.PacketHandler;

/^*
 * {@link BCNetwork} on MinecraftForge: a {@link ChannelBuilder} payload channel per payload, and
 * {@link PacketDistributor} for the routing.
 *
 * <p><b>One channel per payload.</b> Forge negotiates one integer protocol version per <i>channel</i> at login, and
 * refuses a connection when either side lacks a channel or the versions differ, which is NeoForge's per-payload rule
 * only if each payload has a channel of its own. So each registration builds its own single-payload channel, named
 * after the payload's id and carrying that payload's version: a payload missing on the other side, or at another
 * version, fails the same login it fails on NeoForge, and Forge's mismatch screen names the payload. (The channel
 * name and the payload id may be the same {@link Identifier}: Forge keeps channels and payload ids in separate maps.)
 * Forge needs no queue-and-replay either: a channel is built the moment it is registered, and Forge only refuses
 * new channels once its loading is over, which fails a late registration here exactly as NeoForge's replay does.
 *
 * <p><b>Versions.</b> BuildCraft's version is a string, Forge's an {@code int}: a version of plain digits is that
 * number, any other string maps to its non-negative hash. Equal strings give equal numbers on both sides, which is all
 * the comparison needs.
 *
 * <p><b>Threads.</b> Handlers are added with {@code addMain}, which hands the payload to the receiving side's main
 * thread (Forge's plain {@code add} runs on the network thread), so a {@link PacketHandler} starts on the main thread
 * as on the other two loaders.
 *
 * <p><b>Size.</b> Forge 26.3's packet splitter (the one that lifts vanilla's caps on a custom payload) is commented out
 * in its sources, so a payload is held to vanilla's own limits, 1 MiB towards a client and 32 KiB towards the server,
 * where NeoForge splits every payload and Fabric API splits above 1 MiB. BuildCraft's largest payload (a blueprint
 * snapshot response) has to be checked against the clientbound cap before this node ships.
 *
 * <p><b>Client reference.</b> The one client class this file names, {@code Minecraft} (for the local player of a
 * clientbound handler), is read in {@link ClientSide}, a nested class that only a physical client ever loads.
 *^/
public final class ForgeNetwork implements BCNetwork {

    /^* Every registered payload's channel by payload id, for sending and replying. ^/
    private final Map<Identifier, Channel<CustomPacketPayload>> channels = new ConcurrentHashMap<>();

    @Override
    public <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        register(type, codec, handler, version, PacketFlow.CLIENTBOUND);
    }

    @Override
    public <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        register(type, codec, handler, version, PacketFlow.SERVERBOUND);
    }

    @Override
    public <T extends CustomPacketPayload> void bidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        register(type, codec, handler, version, null);
    }

    @SuppressWarnings("unchecked") // a codec that reads any supertype of the play buffer reads the play buffer
    private <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version,
            PacketFlow flow) {
        Identifier id = type.id();
        Channel<CustomPacketPayload> channel = ChannelBuilder.named(id)
                .networkProtocolVersion(protocolVersion(version))
                .payloadChannel()
                .play()
                .flow(flow)
                .addMain(type, (StreamCodec<RegistryFriendlyByteBuf, T>) codec,
                        (payload, ctx) -> handler.handle(payload, new Context(ctx, this)))
                .build();
        channels.put(id, channel);
    }

    /^* The Forge protocol number of a BuildCraft version string: its digits when it is a plain number, else its hash. ^/
    static int protocolVersion(String version) {
        if (version.length() <= 9 && !version.isEmpty() && version.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Integer.parseInt(version);
        }
        return version.hashCode() & Integer.MAX_VALUE;
    }

    private Channel<CustomPacketPayload> channelOf(CustomPacketPayload payload) {
        Channel<CustomPacketPayload> channel = channels.get(payload.type().id());
        if (channel == null) {
            throw new IllegalArgumentException("Payload " + payload.type().id() + " was never registered with "
                    + "BCNetwork");
        }
        return channel;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        channelOf(payload).send(payload, PacketDistributor.SERVER.noArg());
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        channelOf(payload).send(payload, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload) {
        // Forge's own TRACKING_CHUNK target wants a loaded LevelChunk; the position is enough here, the same lookup
        // NeoForge's sendToPlayersTrackingChunk makes. One packet is built for all the connections.
        List<Connection> connections = new ArrayList<>();
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            connections.add(player.connection.getConnection());
        }
        if (!connections.isEmpty()) {
            channelOf(payload).send(payload, PacketDistributor.NMLIST.with(connections));
        }
    }

    @Override
    public void sendToDimension(ServerLevel level, CustomPacketPayload payload) {
        channelOf(payload).send(payload, PacketDistributor.DIMENSION.with(level.dimension()));
    }

    @Override
    public RegistryFriendlyByteBuf registryBuf(ByteBuf buf, RegistryAccess registries) {
        return new RegistryFriendlyByteBuf(buf, registries);
    }

    /^*
     * {@link PacketContext} over Forge's payload context. Forge's context has no client player, and gives a server
     * handler the sender through {@code getSender()}.
     *^/
    private record Context(CustomPayloadEvent.Context ctx, ForgeNetwork network) implements PacketContext {
        @Override
        public Player player() {
            return ctx.isClientSide() ? ClientSide.player() : ctx.getSender();
        }

        @Override
        public void runOnMain(Runnable work) {
            ctx.enqueueWork(work);
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            network.channelOf(payload).reply(payload, ctx);
        }
    }

    /^* The client-only half of {@link Context#player()}, in a class of its own so a server never loads {@code Minecraft}. ^/
    private static final class ClientSide {
        static Player player() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }
}
*///?}
