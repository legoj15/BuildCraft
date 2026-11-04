/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import buildcraft.platform.network.BCNetwork;
import buildcraft.platform.network.PacketContext;
import buildcraft.platform.network.PacketHandler;

/**
 * {@link BCNetwork} on NeoForge: {@link PayloadRegistrar} and {@link PacketDistributor}.
 *
 * <p><b>Registration replay.</b> Register calls are queued, and the first one subscribes a single
 * {@link RegisterPayloadHandlersEvent} listener on the mod bus of the mod being constructed (BuildCraft: the calls
 * are made from its init). The listener replays the queue in call order, each entry on a registrar of its own
 * version with the registrar's defaults ({@code HandlerThread.MAIN}, not optional), so every payload is registered
 * exactly as a hand-written {@code event.registrar(version).playToClient(...)} would. A call after the event has
 * fired is refused: the payload could never be registered.
 */
public final class NeoForgeNetwork implements BCNetwork {

    private final List<Consumer<RegisterPayloadHandlersEvent>> pending = new ArrayList<>();
    private boolean subscribed;
    private boolean replayed;

    @Override
    public <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        IPayloadHandler<T> h = adapt(handler);
        queue(event -> event.registrar(version).playToClient(type, codec, h));
    }

    @Override
    public <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        IPayloadHandler<T> h = adapt(handler);
        queue(event -> event.registrar(version).playToServer(type, codec, h));
    }

    @Override
    public <T extends CustomPacketPayload> void bidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PacketHandler<T> handler, String version) {
        IPayloadHandler<T> h = adapt(handler);
        queue(event -> {
            //? if >=1.21.10 {
            // The 3-arg overload leaves the client handler to RegisterClientPayloadHandlersEvent: pass it here.
            event.registrar(version).playBidirectional(type, codec, h, h);
            //?} else {
            /*event.registrar(version).playBidirectional(type, codec, h);
            *///?}
        });
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> adapt(PacketHandler<T> handler) {
        return (payload, ctx) -> handler.handle(payload, new Context(ctx));
    }

    private synchronized void queue(Consumer<RegisterPayloadHandlersEvent> registration) {
        if (replayed) {
            throw new IllegalStateException("Payload registered after RegisterPayloadHandlersEvent fired; "
                    + "register payloads during mod construction");
        }
        pending.add(registration);
        if (!subscribed) {
            subscribed = true;
            ModLoadingContext.get().getActiveContainer().getEventBus()
                    .addListener(RegisterPayloadHandlersEvent.class, this::replay);
        }
    }

    private synchronized void replay(RegisterPayloadHandlersEvent event) {
        replayed = true;
        for (Consumer<RegisterPayloadHandlersEvent> registration : pending) {
            registration.accept(event);
        }
        pending.clear();
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        //? if >=1.21.10 {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
        //?} else {
        /*PacketDistributor.sendToServer(payload);
        *///?}
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, payload);
    }

    @Override
    public void sendToDimension(ServerLevel level, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }

    @Override
    public RegistryFriendlyByteBuf registryBuf(ByteBuf buf, RegistryAccess registries) {
        return new RegistryFriendlyByteBuf(buf, registries, ConnectionType.NEOFORGE);
    }

    /** {@link PacketContext} over NeoForge's {@link IPayloadContext}. */
    private record Context(IPayloadContext ctx) implements PacketContext {
        @Override
        public Player player() {
            return ctx.player();
        }

        @Override
        public void runOnMain(Runnable work) {
            ctx.enqueueWork(work);
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            ctx.reply(payload);
        }
    }
}
//?}
