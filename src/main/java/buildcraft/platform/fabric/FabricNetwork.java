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
 * <p><b>Gaps against NeoForge.</b> Fabric has no per-payload version negotiation, so {@code version} is unused and a
 * mismatched peer misreads instead of being refused at login; and the payloads are registered without Fabric's
 * {@code registerLarge} splitting, so one above vanilla's packet size limit (a huge blueprint) fails to send where
 * NeoForge would split it.
 *^/
public final class FabricNetwork implements BCNetwork {

    @Override
    public <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
        if (Platform.loader().isPhysicalClient()) {
            FabricClientNetwork.registerReceiver(type, handler);
        }
    }

    @Override
    public <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        registerServerReceiver(type, handler);
    }

    @Override
    public <T extends CustomPacketPayload> void bidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
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
