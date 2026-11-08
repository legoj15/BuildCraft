/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

//? if >=26.1 {
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
//?} else {
/^import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
^///?}
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;

import buildcraft.platform.Platform;
import buildcraft.platform.fabric.client.FabricClientNetwork;

/^*
 * The Fabric stand-in for NeoForge's per-payload version negotiation: a configuration-phase handshake that refuses a
 * client whose BuildCraft payloads differ from the server's, before either side can misread a play packet.
 *
 * <p>{@link FabricNetwork} records each payload's id and version here as it registers it. When a client configures,
 * the server queues {@link Task}, which sends the server's table; the client answers with its own table
 * ({@link FabricClientNetwork#registerVersionReply}), and the server compares the two. A payload missing on either
 * side or carrying a different version disconnects the client with the list; matching tables finish the task and the
 * login goes on. A client that cannot receive the handshake at all (no BuildCraft, or a jar from before it) is refused
 * the same way NeoForge refuses a client missing a required payload. The server decides; the client only answers.
 *^/
public final class FabricNetworkVersions {

    private static final String MOD_ID = FabricConfigRegistrar.MOD_ID;
    /^* How many mismatched payloads the disconnect screen names before it summarises the rest. *^/
    private static final int LISTED_MISMATCHES = 5;

    /^* Payload id to version, sorted so the table goes over the wire in a stable order. *^/
    private final Map<String, String> versions = new TreeMap<>();
    /^* Connections whose check has started (the table is sent) and is unanswered; any other reply is dropped. *^/
    private final Set<ServerConfigurationPacketListenerImpl> awaiting =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    FabricNetworkVersions() {
        FabricNetwork.clientboundConfiguration().register(VersionTable.TYPE, VersionTable.STREAM_CODEC);
        FabricNetwork.serverboundConfiguration().register(VersionTable.TYPE, VersionTable.STREAM_CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> configure(handler));
        ServerConfigurationNetworking.registerGlobalReceiver(VersionTable.TYPE,
                //? if >=26.1 {
                (reply, ctx) -> check(ctx.packetListener(), reply));
                //?} else {
                /^(reply, ctx) -> check(ctx.networkHandler(), reply));
                ^///?}
        if (Platform.loader().isPhysicalClient()) {
            FabricClientNetwork.registerVersionReply(VersionTable.TYPE, this::table);
        }
    }

    /^* Records a payload's version; a payload registered for both directions is recorded once. *^/
    synchronized void record(CustomPacketPayload.Type<?> type, String version) {
        String id = type.id().toString();
        String previous = versions.putIfAbsent(id, version);
        if (previous != null && !previous.equals(version)) {
            throw new IllegalStateException("Payload " + id + " registered at versions " + previous + " and "
                    + version);
        }
    }

    /^* This side's table, as sent to the peer. *^/
    synchronized VersionTable table() {
        return new VersionTable(new TreeMap<>(versions));
    }

    private void configure(ServerConfigurationPacketListenerImpl handler) {
        if (!ServerConfigurationNetworking.canSend(handler, VersionTable.TYPE)) {
            handler.disconnect(Component.translatableWithFallback(MOD_ID + ".network.missing",
                    "This server needs the same version of BuildCraft Redux installed on your game."));
            return;
        }
        //? if >=26.1 {
        ((FabricServerConfigurationPacketListenerImpl) handler)
                .addTask(new Task(table(), () -> awaiting.add(handler)));
        //?} else {
        /^((FabricServerConfigurationNetworkHandler) handler)
                .addTask(new Task(table(), () -> awaiting.add(handler)));
        ^///?}
    }

    private void check(ServerConfigurationPacketListenerImpl handler, VersionTable client) {
        if (!awaiting.remove(handler)) {
            return; // not answering a started check: completing a task that is not current would throw
        }
        List<String> mismatches = mismatches(table().versions(), client.versions());
        if (mismatches.isEmpty()) {
            //? if >=26.1 {
            ((FabricServerConfigurationPacketListenerImpl) handler).completeTask(Task.TYPE);
            //?} else {
            /^((FabricServerConfigurationNetworkHandler) handler).completeTask(Task.TYPE);
            ^///?}
            return;
        }
        String listed = String.join(", ", mismatches.subList(0, Math.min(LISTED_MISMATCHES, mismatches.size())));
        if (mismatches.size() > LISTED_MISMATCHES) {
            listed += " (+" + (mismatches.size() - LISTED_MISMATCHES) + ")";
        }
        handler.disconnect(Component.translatableWithFallback(MOD_ID + ".network.version_mismatch",
                "Your BuildCraft Redux does not match the server's. Install the server's version. Differing: %s",
                listed));
    }

    /^*
     * Every payload id whose version differs between the two tables, or that only one side has, as
     * {@code id (server v, client v)} with {@code none} for a missing side; empty when the tables match.
     *^/
    private static List<String> mismatches(Map<String, String> server, Map<String, String> client) {
        TreeSet<String> ids = new TreeSet<>(server.keySet());
        ids.addAll(client.keySet());
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            String s = server.get(id);
            String c = client.get(id);
            if (!Objects.equals(s, c)) {
                out.add(id + " (server " + (s == null ? "none" : s) + ", client " + (c == null ? "none" : c) + ")");
            }
        }
        return out;
    }

    /^* The configuration task the check runs under: the login waits on it until the client's table arrives. *^/
    private record Task(VersionTable server, Runnable onStart) implements ConfigurationTask {
        static final ConfigurationTask.Type TYPE =
                new ConfigurationTask.Type(MOD_ID + ":network_versions");

        @Override
        public void start(Consumer<Packet<?>> sender) {
            onStart.run();
            //? if >=26.1 {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(server));
            //?} else {
            /^sender.accept(ServerConfigurationNetworking.createS2CPacket(server));
            ^///?}
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }

    /^* The handshake payload, the same both ways: one side's payload id to version table. *^/
    public record VersionTable(Map<String, String> versions) implements CustomPacketPayload {
        public static final Type<VersionTable> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(MOD_ID, "network_versions"));
        static final StreamCodec<ByteBuf, VersionTable> STREAM_CODEC = ByteBufCodecs
                .<ByteBuf, String, String, Map<String, String>>map(size -> new TreeMap<>(), ByteBufCodecs.STRING_UTF8,
                        ByteBufCodecs.STRING_UTF8)
                .map(VersionTable::new, VersionTable::versions);

        @Override
        public Type<VersionTable> type() {
            return TYPE;
        }
    }
}
*///?}
