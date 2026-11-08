/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric && >=1.21.10 {
/*import static buildcraft.platform.fabric.BCFabricClientGameTest.LOGGER;
import static buildcraft.platform.fabric.BCFabricClientGameTest.NS;
import static buildcraft.platform.fabric.BCFabricClientGameTest.currentScreen;
import static buildcraft.platform.fabric.BCFabricClientGameTest.fillTank;
import static buildcraft.platform.fabric.BCFabricClientGameTest.openScreenOn;
import static buildcraft.platform.fabric.BCFabricClientGameTest.orientEngine;
import static buildcraft.platform.fabric.BCFabricClientGameTest.pipe;
import static buildcraft.platform.fabric.BCFabricClientGameTest.setHudHidden;
import static buildcraft.platform.fabric.BCFabricClientGameTest.worldShot;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
//? if >=26.2 {
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
//?} elif >=26.1 {
/^import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientLevelContext;
^///?} else {
/^import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext;
^///?}
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.ClientSnapshots;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.SnapshotResponsePayload;
import buildcraft.factory.tile.TileTank;
import buildcraft.transport.tile.TilePipeHolder;

/^*
 * The multiplayer phase of {@link BCFabricClientGameTest} ({@link #run}): a dedicated server Fabric API starts in the
 * client's JVM, joined over localhost TCP. Fabric API's client game-test module has no dedicated server before 1.21.4,
 * so the 1.21.1 node (which runs the singleplayer half through {@code gametest.ClientGameTestShim}) leaves it out.
 *^/
final class BCFabricMultiplayerChecks {

    private BCFabricMultiplayerChecks() {
    }

    /^* The multiplayer rig stands on its own floor, in the air above the server's flat world. *^/
    private static final int MP_Y = 100;
    private static final BlockPos MP_ENGINE = new BlockPos(0, MP_Y, 0);
    private static final BlockPos MP_PIPE = new BlockPos(1, MP_Y, 0);
    private static final BlockPos MP_TANK = new BlockPos(4, MP_Y, 0);
    /^*
     * Vanilla's largest frame (Varint21FrameDecoder: a 21-bit length). A payload above it reaches the client only if
     * FabricNetwork's splitter cut it up, so the blueprint must be larger than this for the check to mean anything.
     *^/
    private static final int VANILLA_FRAME_LIMIT = (1 << 21) - 1;
    /^* The payload id the mismatch check's client claims and the server does not have. *^/
    private static final String FAKE_PAYLOAD = NS + ":client_game_test_only";

    /^*
     * The multiplayer half: one dedicated server (in this JVM, its own thread, real TCP on localhost), a join that must
     * pass the version handshake, then two joins the handshake must refuse. A refused join leaves the client on a
     * {@link DisconnectedScreen}, whose reason is what a player reads.
     *^/
    static void run(ClientGameTestContext ctx, List<String> problems) {
        // Bound to 127.0.0.1, so nothing off this machine can join; the test server runs offline-mode (no account
        // check), which makes that bind the only thing keeping other machines out. A free port rather than vanilla's
        // 25565, which a dev server of the user's may already hold.
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("server-port", String.valueOf(freeLoopbackPort()));
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(properties)) {
            int port = server.computeOnServer(MinecraftServer::getPort);
            //? if >=26.2 {
            try (TestDedicatedServerConnection conn = server.connect()) {
            //?} elif >=26.1 {
            /^try (TestServerConnection link = server.connect()) {
                TestClientLevelContext conn = link.getClientLevel();
            ^///?} else {
            /^try (TestServerConnection link = server.connect()) {
                TestClientWorldContext conn = link.getClientWorld();
            ^///?}
                checkMultiplayerMachines(ctx, server, conn, problems);
                checkLargeSnapshot(ctx, server, problems);
            }
            checkMismatchedClientRefused(ctx, port, problems);
            checkClientWithoutHandshakeRefused(ctx, port, problems);
            ctx.setScreen(() -> new TitleScreen());
        }
    }

    /^*
     * A port free on 127.0.0.1 right now. The probe closes before the server binds, so another socket can take the port
     * in between; that fails the run loudly at server start (never a false pass: the joins read the server's bound port back), and the window is short.
     *^/
    private static int freeLoopbackPort() {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            return probe.getLocalPort();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("no free loopback port for the test server", e);
        }
    }

    /^* Machine state reaching the client over the wire, and a BuildCraft menu opening from a server-side tile. *^/
    private static void checkMultiplayerMachines(ClientGameTestContext ctx, TestServerContext server,
        //? if >=26.2 {
        TestServerConnection conn,
        //?} elif >=26.1 {
        /^TestClientLevelContext conn,
        ^///?} else {
        /^TestClientWorldContext conn,
        ^///?}
        List<String> problems) {
        for (String cmd : new String[] {
            "gamemode creative @p",
            "time set 6000",
            "fill -4 " + (MP_Y - 1) + " -4 8 " + (MP_Y - 1) + " 6 minecraft:smooth_stone",
            "fill -4 " + MP_Y + " -4 8 " + (MP_Y + 4) + " 6 minecraft:air",
            "setblock " + MP_ENGINE.getX() + " " + (MP_Y - 1) + " " + MP_ENGINE.getZ() + " minecraft:redstone_block",
        }) {
            server.runCommand(cmd);
        }
        pipe(server, MP_PIPE, "wood_power");
        pipe(server, MP_PIPE.east(), "stone_power");
        pipe(server, MP_PIPE.east(2), "wood_item");
        server.runCommand("setblock " + MP_ENGINE.getX() + " " + MP_Y + " " + MP_ENGINE.getZ() + " " + NS
            + ":engine_redstone");
        server.runCommand("setblock " + MP_TANK.getX() + " " + MP_Y + " " + MP_TANK.getZ() + " " + NS + ":tank");
        server.runCommand("tp @p 2.5 " + MP_Y + " 4.5 180 25");
        ctx.waitTicks(10);
        fillTank(server, MP_TANK);
        orientEngine(server, MP_ENGINE);
        conn.waitForChunksRender();
        ctx.waitTicks(20);

        // Each wants its synced state on the client within ten seconds; the reason names what the client has instead.
        expectOnClient(ctx, problems, "the tank's 12000 mB of oil", mc ->
            mc.level.getBlockEntity(MP_TANK) instanceof TileTank t ? t.tank.getAmountMb(0) + " mB" : "no tank",
            "12000 mB");
        expectOnClient(ctx, problems, "the redstone engine turned east", mc ->
            mc.level.getBlockEntity(MP_ENGINE) instanceof buildcraft.lib.engine.TileEngineBase_BC8 e
                ? String.valueOf(e.getOrientation()) : "no engine",
            "east");
        String[] defs = {"wood_power", "stone_power", "wood_item"};
        for (int i = 0; i < defs.length; i++) {
            BlockPos at = MP_PIPE.east(i);
            String def = defs[i];
            expectOnClient(ctx, problems, "a " + def + " pipe at " + at.toShortString(), mc ->
                mc.level.getBlockEntity(at) instanceof TilePipeHolder p && p.getPipe() != null
                    ? p.getPipe().getDefinition().identifier : "no pipe",
                NS + ":" + def);
        }

        setHudHidden(ctx, true);
        worldShot(ctx, conn, server, "2.5 " + MP_Y + " 4.5 180 25", "bc-mp-rig", problems);
        setHudHidden(ctx, false);
        openScreenOn(ctx, server, MP_TANK, "GuiTank", "bc-mp-tank-gui", problems);
    }

    /^* Waits up to 200 ticks for {@code read} to answer {@code want} on the client, else records what it answered. *^/
    private static void expectOnClient(ClientGameTestContext ctx, List<String> problems, String what,
        Function<Minecraft, String> readInWorld, String want) {
        // A client the server turned away has no level: report that rather than throw and lose the other problems.
        Function<Minecraft, String> read = mc -> mc.level == null ? "no world (not joined)" : readInWorld.apply(mc);
        try {
            ctx.waitFor(mc -> want.equals(read.apply(mc)), 200);
        } catch (AssertionError timedOut) {
            problems.add("multiplayer: the client never saw " + what + "; it has " + ctx.computeOnClient(read::apply));
        }
    }

    /^*
     * A blueprint too large for one packet, asked for the way a tooltip asks (the client requests the hash it holds an
     * item for; the server checks the item and replies): the reply is larger than one vanilla frame, so it arrives
     * only if it was split, and it must reassemble and decode to the same cells. The cells are noise over a 16-block
     * palette, which keeps the payload well over the frame limit; the size is re-measured below on every run.
     *^/
    private static void checkLargeSnapshot(ClientGameTestContext ctx, TestServerContext server,
        List<String> problems) {
        record Sent(Snapshot.Key key, int[] cells, int bytes) {}
        Sent sent = server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            Blueprint blueprint = noisyBlueprint(level);
            blueprint.computeKey();
            GlobalSavedDataSnapshots.get(level).addSnapshot(blueprint);
            List<ServerPlayer> players = s.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                return null;
            }
            ServerPlayer player = players.get(0);
            player.getInventory().setItem(20, BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(
                new Snapshot.Header(blueprint.key, player.getUUID(), new Date(0), "client-game-test")));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SnapshotResponsePayload.STREAM_CODEC.encode(buf, new SnapshotResponsePayload(blueprint));
                return new Sent(blueprint.key, blueprint.data, buf.readableBytes());
            } finally {
                buf.release();
            }
        });
        if (sent == null) {
            problems.add("multiplayer: nobody was on the test server to ask for the blueprint");
            return;
        }
        if (sent.bytes() <= VANILLA_FRAME_LIMIT) {
            problems.add("multiplayer: the test blueprint encodes to " + sent.bytes()
                + " bytes, small enough to cross unsplit");
            return;
        }
        try {
            ctx.waitFor(mc -> ClientSnapshots.INSTANCE.getSnapshot(sent.key()) != null, 1200);
        } catch (AssertionError timedOut) {
            problems.add("multiplayer: a " + sent.bytes() + "-byte blueprint never reached the client");
            return;
        }
        boolean same = ctx.computeOnClient(mc ->
            ClientSnapshots.INSTANCE.getSnapshot(sent.key()) instanceof Blueprint got
                && Arrays.equals(got.data, sent.cells()));
        if (!same) {
            problems.add("multiplayer: the " + sent.bytes() + "-byte blueprint arrived with different cells");
        }
        LOGGER.info("Multiplayer: a {}-byte blueprint made the round trip", sent.bytes());
    }

    private static Blueprint noisyBlueprint(ServerLevel level) {
        Block[] blocks = {Blocks.STONE, Blocks.DIRT, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.SAND, Blocks.GRAVEL,
            Blocks.GLASS, Blocks.BRICKS, Blocks.SANDSTONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
            Blocks.CLAY, Blocks.OBSIDIAN, Blocks.NETHERRACK, Blocks.END_STONE};
        Blueprint blueprint = new Blueprint();
        // About 3.5 million cells, roughly 2.7 MB compressed (2.6 million gave 1.98 MB).
        blueprint.size = new BlockPos(192, 144, 128);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        BlockPos sample = new BlockPos(0, MP_Y + 20, 0);
        for (Block block : blocks) {
            blueprint.palette.add(SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(level, sample,
                sample, block.defaultBlockState(), block)));
        }
        blueprint.data = new int[blueprint.getDataSize()];
        Random noise = new Random(98);
        for (int i = 0; i < blueprint.data.length; i++) {
            blueprint.data[i] = noise.nextInt(blocks.length);
        }
        return blueprint;
    }

    /^*
     * A client whose BuildCraft names a payload the server lacks: its handshake reply is the server's own table plus
     * {@link #FAKE_PAYLOAD}, so the refusal must name that id (the wording around it is not pinned). The real reply handler goes back afterwards.
     *^/
    private static void checkMismatchedClientRefused(ClientGameTestContext ctx, int port, List<String> problems) {
        var original = ctx.computeOnClient(mc -> ClientConfigurationNetworking.unregisterGlobalReceiver(
            FabricNetworkVersions.VersionTable.TYPE));
        try {
            ctx.runOnClient(mc -> ClientConfigurationNetworking.registerGlobalReceiver(
                FabricNetworkVersions.VersionTable.TYPE, (serverTable, c) -> {
                    Map<String, String> claimed = new TreeMap<>(serverTable.versions());
                    claimed.put(FAKE_PAYLOAD, "1");
                    c.responseSender().sendPacket(new FabricNetworkVersions.VersionTable(claimed));
                }));
            String reason = joinExpectingRefusal(ctx, port);
            if (!reason.contains(FAKE_PAYLOAD)) {
                problems.add("multiplayer: a client with a payload the server lacks was not refused naming it: "
                    + reason);
            }
        } finally {
            restoreVersionReply(ctx, original);
        }
    }

    /^* A client that cannot take part in the handshake at all (no BuildCraft, or a jar from before it). *^/
    private static void checkClientWithoutHandshakeRefused(ClientGameTestContext ctx, int port,
        List<String> problems) {
        var original = ctx.computeOnClient(mc -> ClientConfigurationNetworking.unregisterGlobalReceiver(
            FabricNetworkVersions.VersionTable.TYPE));
        try {
            String reason = joinExpectingRefusal(ctx, port);
            String want = ctx.computeOnClient(mc -> Component.translatable(NS + ".network.missing").getString());
            if (!reason.contains(want)) {
                problems.add("multiplayer: a client without the handshake was not refused as missing BuildCraft: "
                    + reason);
            }
        } finally {
            restoreVersionReply(ctx, original);
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreVersionReply(ClientGameTestContext ctx,
        ClientConfigurationNetworking.ConfigurationPayloadHandler<?> original) {
        ctx.runOnClient(mc -> {
            ClientConfigurationNetworking.unregisterGlobalReceiver(FabricNetworkVersions.VersionTable.TYPE);
            if (original == null) {
                return; // there was none to put back; at least the check's own handler is gone
            }
            ClientConfigurationNetworking.registerGlobalReceiver(FabricNetworkVersions.VersionTable.TYPE,
                (ClientConfigurationNetworking.ConfigurationPayloadHandler<FabricNetworkVersions.VersionTable>) original);
        });
    }

    /^*
     * Connects to the local dedicated server as the multiplayer screen does and waits for the refusal; answers the
     * disconnect screen's title and reason, or what happened instead.
     *^/
    private static String joinExpectingRefusal(ClientGameTestContext ctx, int port) {
        String address = "127.0.0.1:" + port; // the bind itself: "localhost" may resolve to ::1 first
        ctx.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
            new ServerData("BuildCraft client game test", address, ServerData.Type.OTHER), false, null));
        try {
            ctx.waitFor(mc -> currentScreen(mc) instanceof DisconnectedScreen || mc.level != null, 1200);
        } catch (AssertionError timedOut) {
            // Reported below with what the client shows.
        }
        String outcome = ctx.computeOnClient(mc -> {
            Screen screen = currentScreen(mc);
            if (screen instanceof DisconnectedScreen disconnected) {
                return disconnected.getNarrationMessage().getString();
            }
            return mc.level != null ? "it joined the world" : "still on " + (screen == null ? "no screen"
                : screen.getClass().getSimpleName());
        });
        ctx.setScreen(() -> new TitleScreen());
        ctx.waitTicks(5);
        return outcome;
    }
}
*///?}
