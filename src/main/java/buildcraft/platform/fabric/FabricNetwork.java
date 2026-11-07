/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import io.netty.buffer.ByteBuf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import buildcraft.platform.Platform;
import buildcraft.platform.fabric.client.FabricClientNetwork;
import buildcraft.platform.network.BCNetwork;
import buildcraft.platform.network.PacketContext;
import buildcraft.platform.network.PacketHandler;

/^*
 * {@link BCNetwork} on Fabric: {@link PayloadTypeRegistry} and {@link ServerPlayNetworking}, with every
 * client-only call in {@link FabricClientNetwork}.
 *
 * <p><b>Registration</b> runs at once, as Fabric expects from a mod initializer: the payload type is registered for
 * its direction(s) on both sides, the server receiver globally on both sides (an integrated server shares the JVM),
 * and the client receiver only on the physical client, through {@link FabricClientNetwork}. Doing the client part
 * here, behind {@link buildcraft.platform.LoaderServices#isPhysicalClient()}, means no client entrypoint is needed:
 * registering a global receiver before the client has connected is what Fabric's own client initializers do.
 *
 * <p><b>Versions.</b> Fabric has no per-payload version negotiation, so {@link FabricNetworkVersions} adds one: each
 * payload's version is recorded here and compared with the client's in a configuration-phase handshake, which
 * refuses a mismatched client at login as NeoForge does.
 *
 * <p><b>Size.</b> Every payload is registered with {@code registerLarge}, so Fabric splits one above its 1 MiB split
 * threshold (under vanilla's 2 MiB frame limit; a huge blueprint in a snapshot response) and reassembles it, as NeoForge's packet splitter does for every
 * payload; a small payload goes out unsplit. The reassembled cap is {@link #MAX_PAYLOAD_BYTES} towards a client and
 * {@link #MAX_SERVERBOUND_PAYLOAD_BYTES} towards the server.
 *^/
public final class FabricNetwork implements BCNetwork {

    /^* The largest server-to-client payload a client reassembles: NeoForge's default {@code splitPacketMaxSizeLimitMb}. *^/
    static final int MAX_PAYLOAD_BYTES = 128 * 1024 * 1024;
    /^*
     * The largest client-to-server payload the server reassembles. Every serverbound payload is small, so this stays
     * far under {@link #MAX_PAYLOAD_BYTES}: an untrusted client cannot make the server buffer 128 MB.
     *^/
    static final int MAX_SERVERBOUND_PAYLOAD_BYTES = 8 * 1024 * 1024;

    private final FabricNetworkVersions versions = new FabricNetworkVersions();

    @Override
    public <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        versions.record(type, version);
        PayloadTypeRegistry.clientboundPlay().registerLarge(type, codec, MAX_PAYLOAD_BYTES);
        if (Platform.loader().isPhysicalClient()) {
            FabricClientNetwork.registerReceiver(type, handler);
        }
    }

    @Override
    public <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        versions.record(type, version);
        PayloadTypeRegistry.serverboundPlay().registerLarge(type, codec, MAX_SERVERBOUND_PAYLOAD_BYTES);
        registerServerReceiver(type, handler);
    }

    @Override
    public <T extends CustomPacketPayload> void bidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        versions.record(type, version);
        PayloadTypeRegistry.clientboundPlay().registerLarge(type, codec, MAX_PAYLOAD_BYTES);
        PayloadTypeRegistry.serverboundPlay().registerLarge(type, codec, MAX_SERVERBOUND_PAYLOAD_BYTES);
        registerServerReceiver(type, handler);
        if (Platform.loader().isPhysicalClient()) {
            FabricClientNetwork.registerReceiver(type, handler);
        }
    }

    private static <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type,
            PacketHandler<T> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type,
                (payload, ctx) -> handler.handle(payload, new ServerContext(ctx)));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNetwork.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(level, chunkPos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToDimension(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.level(level)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public RegistryFriendlyByteBuf registryBuf(ByteBuf buf, RegistryAccess registries) {
        return new RegistryFriendlyByteBuf(buf, registries);
    }

    /^* {@link PacketContext} over a server-side receive: the sender, the server's thread, the sender's connection. *^/
    private record ServerContext(ServerPlayNetworking.Context ctx) implements PacketContext {
        @Override
        public Player player() {
            return ctx.player();
        }

        @Override
        public void runOnMain(Runnable work) {
            ctx.server().execute(work);
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            ctx.responseSender().sendPacket(payload);
        }
    }
}
*///?}
