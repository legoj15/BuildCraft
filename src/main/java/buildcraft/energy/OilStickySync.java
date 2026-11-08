/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import buildcraft.platform.Platform;
import buildcraft.platform.network.PacketContext;

/**
 * Server -> client: the server's {@code oilIsSticky}, so a client predicts the same fluid the server simulates.
 *
 * <p>{@link BCEnergyConfig#oilIsSticky} is on the COMMON spec, which neither loader sends to a remote client. The
 * option scales how hard a current pushes and how fast a ridden boat may go, and both run on the client for the local
 * player ({@code BCEnergyFluids}, {@code BoatFluidMotion}), so a client whose own file said {@code false} against a
 * server that said {@code true} was never slowed and was pulled back by the server every tick. The server sends the
 * flag to a player as they join and to everyone when the COMMON file is reloaded (an edit on disk); the client keeps it
 * in {@link BCEnergyConfig#setSyncedOilIsSticky} until it leaves the world.
 *
 * <p>The server sends its {@linkplain BCEnergyConfig#localOilIsSticky() local} value, never
 * {@link BCEnergyConfig#isOilSticky()}, which would answer a synced copy if this JVM held one.
 *
 * @param sticky the server's {@code oilIsSticky}
 */
public record OilStickySync(boolean sticky) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OilStickySync> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:oil_sticky"));

    public static final StreamCodec<FriendlyByteBuf, OilStickySync> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.sticky),
            buf -> new OilStickySync(buf.readBoolean()));

    /** Registers the payload and the two server events that send it. Called once, from {@link BCEnergy#init()}. */
    public static void register() {
        Platform.network().clientbound(TYPE, STREAM_CODEC, OilStickySync::handle);
        Platform.gameHooks().onPlayerLoggedIn(OilStickySync::sendTo);
        Platform.configHooks().onReload(buildcraft.core.BCUnifiedConfig.SPEC, OilStickySync::onConfigReloaded);
    }

    private static void sendTo(ServerPlayer player) {
        Platform.network().sendToPlayer(player, new OilStickySync(BCEnergyConfig.localOilIsSticky()));
    }

    /**
     * The COMMON file was reloaded: tell every connected player, from the server thread (the file watcher is not
     * on it). A reload on a client with no integrated server finds no server and does nothing.
     */
    private static void onConfigReloaded() {
        MinecraftServer server = Platform.loader().currentServer();
        if (server == null) return;
        server.execute(() -> {
            OilStickySync message = new OilStickySync(BCEnergyConfig.localOilIsSticky());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Platform.network().sendToPlayer(player, message);
            }
        });
    }

    /**
     * Client-side handler: keeps the server's value until the player leaves the world. Touches no client-only class.
     * A client that is also the server (single-player, or the host of a LAN world) already holds the server's file, so
     * it keeps nothing: a copy there would be read by the server's own simulation and could lag a reload.
     */
    static void handle(OilStickySync message, PacketContext ctx) {
        ctx.runOnMain(() -> {
            if (Platform.loader().currentServer() == null) {
                BCEnergyConfig.setSyncedOilIsSticky(message.sticky);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
