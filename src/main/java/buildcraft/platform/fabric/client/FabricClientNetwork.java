/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.function.Supplier;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import buildcraft.platform.network.PacketContext;
import buildcraft.platform.network.PacketHandler;

/^*
 * The client-only half of {@code FabricNetwork}: client receivers and client-to-server sends. Only the physical
 * client reaches this class, so a dedicated server never loads Fabric's client networking.
 *^/
public final class FabricClientNetwork {
    private FabricClientNetwork() {}

    /^* Registers the client-side receiver of a server-to-client (or bidirectional) payload. *^/
    public static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type,
            PacketHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type,
                (payload, ctx) -> handler.handle(payload, new ClientContext(ctx)));
    }

    /^*
     * Answers the server's configuration-phase version handshake ({@code FabricNetworkVersions}) with this client's
     * own table; the server compares the two and decides.
     *^/
    public static <T extends CustomPacketPayload> void registerVersionReply(CustomPacketPayload.Type<T> type,
            Supplier<? extends CustomPacketPayload> ownTable) {
        ClientConfigurationNetworking.registerGlobalReceiver(type,
                (serverTable, ctx) -> ctx.responseSender().sendPacket(ownTable.get()));
    }

    /^* Sends a payload to the server the client is connected to. *^/
    public static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    /^* {@link PacketContext} over a client-side receive: the local player, the client's thread, the connection. *^/
    private record ClientContext(ClientPlayNetworking.Context ctx) implements PacketContext {
        @Override
        public Player player() {
            return ctx.player();
        }

        @Override
        public void runOnMain(Runnable work) {
            ctx.client().execute(work);
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            ctx.responseSender().sendPacket(payload);
        }
    }
}
*///?}
