/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import io.netty.buffer.Unpooled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.ClientSnapshots;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.SnapshotResponsePayload;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache;
import buildcraft.transport.recipe.DyedPipeRecipe;
import buildcraft.transport.tile.TilePipeHolder;

/^*
 * The Fabric node's in-world visual check ({@code :26.3-fabric:runClientGameTest}, Fabric API's client game-test
 * runner under {@code -Dfabric.client.gametest}): a flat singleplayer world, a rig of BuildCraft content built by
 * commands (a line of pipes of every kind, a powered redstone engine, a tank of oil, a distiller, a heat exchanger,
 * Stirling and combustion engines, a quarry beside a volume marker and frames), the oil bucket and fragile fluid shards
 * in the hotbar, the tank and Stirling engine GUIs and the guide book, each screenshotted into the run's
 * {@code screenshots/} folder for a person to look at.
 * <p>
 * It also runs the game-test bodies that load client classes, which the dedicated game-test server cannot
 * ({@link #runClientClassGameTests}).
 * <p>
 * Then a real multiplayer join ({@link #runDedicatedServerChecks}): Fabric API starts a dedicated server in this JVM
 * and the client connects to it over localhost TCP, so every packet also passes vanilla's frame codec and its 2 MiB
 * frame limit, which a singleplayer join's in-memory connection never applies. It checks machine state reaching the client, a menu opening, a
 * blueprint larger than a packet making the round trip, and the version handshake turning away a client whose
 * payload table differs or that has none.
 * <p>
 * What it asserts on its own: each GUI opens as its BuildCraft screen; no world or held-item shot is dominated by the
 * missing-texture magenta; and the run's log carries no BuildCraft ERROR line and no BuildCraft missing-model,
 * missing-texture or load-failure line. It needs a GL context, so it is not part of {@code nodeVerification}.
 *^/
public final class BCFabricClientGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft client game test");
    /^* bc.fabric's runClientGameTest requires this line in the log: the runner exits 0 on some aborts too. *^/
    public static final String PASS_MARKER = "BuildCraft client game test: all checks passed";

    private static final String NS = "buildcraftunofficial";
    /^* The flat world's surface is grass at y -61. *^/
    private static final int Y = -60;
    private static final BlockPos ENGINE = new BlockPos(-8, Y, 0);
    private static final BlockPos TANK = new BlockPos(4, Y, 0);
    private static final BlockPos STIRLING = new BlockPos(10, Y, 0);

    /^* A log line that names BuildCraft and says a model, texture or load went wrong. *^/
    private static final Pattern BC_PROBLEM = Pattern.compile(
        "(?i)(missing|unable to|failed|couldn't|could not|exception)");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        List<String> problems = new ArrayList<>();
        // 1600x900 rather than the default 854x480, so a pipe's texture is more than a few pixels.
        ctx.getInput().resizeWindow(1600, 900);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            buildRig(server);
            fillTank(server, TANK);
            ctx.waitTicks(10);
            orientEngine(server, ENGINE);
            ctx.waitTicks(40);
            sp.getConnection().waitForChunksRender();
            ctx.waitTicks(20);
            // The client must draw the engine turned too (its renderer reads the synced tile orientation).
            String clientEngine = ctx.computeOnClient(mc ->
                mc.level.getBlockEntity(ENGINE) instanceof buildcraft.lib.engine.TileEngineBase_BC8 e
                    ? String.valueOf(e.getOrientation()) : "no engine");
            if (!"east".equals(clientEngine)) {
                problems.add("the client's redstone engine faces " + clientEngine + ", not east");
            }
            checkRecipeSync(ctx, problems);

            // The world shots, HUD hidden (F1): the whole rig, then each part close up.
            setHudHidden(ctx, true);
            TestServerConnection spConn = sp.getConnection();
            worldShot(ctx, spConn, server, "5.5 " + (Y + 4) + " 15.5 180 25", "bc-rig-overview", problems);
            worldShot(ctx, spConn, server, "-5.5 " + Y + " 3.5 180 30", "bc-kinesis-engine", problems);
            worldShot(ctx, spConn, server, "-1.5 " + Y + " 3.5 180 30", "bc-item-pipes", problems);
            worldShot(ctx, spConn, server, "-0.5 " + Y + " -1.5 180 25", "bc-back-pipes", problems);
            worldShot(ctx, spConn, server, "4.5 " + Y + " 2.5 180 30", "bc-tank-oil", problems);
            worldShot(ctx, spConn, server, "8.5 " + Y + " 3.5 180 30", "bc-machines", problems);
            worldShot(ctx, spConn, server, "18.5 " + Y + " 3.5 180 30", "bc-builders", problems);
            setHudHidden(ctx, false);

            // The held items, first person and hotbar, looking at the floor so the item stands out.
            server.runCommand("tp @p 5.5 " + Y + " 9.5 180 45");
            selectSlot(ctx, 0);
            ctx.waitTicks(10);
            clearOverlays(ctx);
            checkShot(ctx.takeScreenshot("bc-hand-oil-bucket"), problems);
            selectSlot(ctx, 1);
            ctx.waitTicks(10);
            clearOverlays(ctx);
            checkShot(ctx.takeScreenshot("bc-hand-fluid-shard"), problems);

            openScreenOn(ctx, server, TANK, "GuiTank", "bc-tank-gui", problems);
            openScreenOn(ctx, server, STIRLING, "ScreenEngineStone", "bc-engine-stone-gui", problems);

            // The guide book, used in the air: its cover, then (a click on the cover) its contents.
            server.runCommand("tp @p 5.5 " + Y + " 9.5 180 -30");
            selectSlot(ctx, 2);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(o -> o.keyUse);
            expectScreen(ctx, "GuiGuide", problems);
            ctx.waitTicks(20);
            clearOverlays(ctx);
            ctx.takeScreenshot("bc-guide-cover");
            // A click on the cover opens the book (an animation of a few ticks). 26.3's SDL buttons count from 1:
            // left is InputConstants.MOUSE_BUTTON_LEFT, not GLFW's 0.
            ctx.getInput().setCursorPos(800, 450);
            ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(40);
            clearOverlays(ctx);
            ctx.takeScreenshot("bc-guide-contents");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);

            runClientClassGameTests(server, problems);
        }

        runDedicatedServerChecks(ctx, problems);

        problems.addAll(logProblems());
        if (!problems.isEmpty()) {
            throw new AssertionError("BuildCraft client game test found " + problems.size() + " problem(s):\n  "
                + String.join("\n  ", problems));
        }
        LOGGER.info(PASS_MARKER);
    }

    /^*
     * The guide's recipe panels read the recipes the server sends (vanilla stopped sending them in 1.21.2; #141). A
     * singleplayer guide falls back to the integrated server's own recipes, which hides a missing sync, so this reads
     * the received store alone: on a dedicated server that store is all the guide has. It wants a recipe of each kind
     * a panel shows: vanilla shaped and smelting, and one of BuildCraft's own crafting serializers.
     *^/
    private static void checkRecipeSync(ClientGameTestContext ctx, List<String> problems) {
        List<String> missing = ctx.computeOnClient(mc -> {
            Set<RecipeSerializer<?>> received = new HashSet<>();
            for (RecipeHolder<?> holder : ClientGuideRecipeCache.syncedRecipeHolders()) {
                received.add(holder.value().getSerializer());
            }
            List<String> absent = new ArrayList<>();
            if (!received.contains(ShapedRecipe.SERIALIZER)) {
                absent.add("shaped");
            }
            if (!received.contains(SmeltingRecipe.SERIALIZER)) {
                absent.add("smelting");
            }
            if (!received.contains(DyedPipeRecipe.SERIALIZER)) {
                absent.add("BuildCraft's dyed pipe");
            }
            return absent;
        });
        if (!missing.isEmpty()) {
            problems.add("the server sent the client no recipes of these kinds: " + missing);
        }
    }

    /^*
     * The game-test bodies that load client classes (the guide's recipe lookups, the Zone Planner overlay geometry),
     * which Fabric's dedicated game-test server refuses: they run here instead, on the singleplayer server's thread,
     * each through a {@link DetachedGameTestHelper} (on NeoForge they are ordinary game tests).
     *^/
    private static void runClientClassGameTests(TestServerContext server, List<String> problems) {
        List<String> found = new ArrayList<>();
        server.runOnServer(s -> {
            for (ClientClassGameTest test : CLIENT_CLASS_GAME_TESTS) {
                DetachedGameTestHelper helper = new DetachedGameTestHelper(s.overworld(), CLIENT_CLASS_TEST_ORIGIN);
                try {
                    test.body().accept(helper);
                    if (!helper.succeeded()) {
                        found.add(test.name() + ": never succeeded");
                    }
                } catch (Throwable failure) {
                    found.add(test.name() + ": " + failure);
                }
            }
        });
        for (String failure : found) {
            problems.add("game test " + failure);
        }
        // Worded so the log check (BC_PROBLEM) does not read the count itself as a problem.
        LOGGER.info("Ran {} client-class game tests, {} passed", CLIENT_CLASS_GAME_TESTS.size(),
            CLIENT_CLASS_GAME_TESTS.size() - found.size());
    }

    /^* Where the client-class bodies' arena would be: well away from the rig, above the flat world's ground. *^/
    private static final BlockPos CLIENT_CLASS_TEST_ORIGIN = new BlockPos(0, 100, 400);

    private record ClientClassGameTest(String name, java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> body) {}

    /^* Registered on NeoForge as the game tests of the same names (BuildCraftGameTests, "Not on Fabric" there). *^/
    private static final List<ClientClassGameTest> CLIENT_CLASS_GAME_TESTS = List.of(
        new ClientClassGameTest("guide_recipe_lookup_synced_cache",
            buildcraft.lib.test.guide.GuideRecipeLookupTester::testGuideRecipeLookupFromSyncedCache),
        new ClientClassGameTest("guide_modifier_recipe_renders_correct_input_variant",
            buildcraft.silicon.GateRecipeVariantTester::testGuideBookModifierRecipeRendersCorrectInputVariant),
        new ClientClassGameTest("zone_planner_overlay_in_view",
            buildcraft.robotics.ZonePlannerOverlayTester::overlayDrawsOnlyPaintedMappedColumnsInView),
        new ClientClassGameTest("zone_planner_overlay_survey_zoom",
            buildcraft.robotics.ZonePlannerOverlayTester::overlayAtSurveyZoomDrawsOneQuadPerTerrainSample),
        new ClientClassGameTest("zone_planner_cull_above_camera",
            buildcraft.robotics.ZonePlannerOverlayTester::terrainAndOverlayLeaveOutColumnsReachingTheCamera),
        new ClientClassGameTest("zone_planner_survey_edge_low_terrain",
            buildcraft.robotics.ZonePlannerOverlayTester::lowTerrainAtTheEdgeOfSurveyZoomIsDrawn),
        new ClientClassGameTest("zone_planner_survey_outline_on_cells",
            buildcraft.robotics.ZonePlannerOverlayTester::surveyOutlineIsDrawnOnTerrainCells));

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
    private static void runDedicatedServerChecks(ClientGameTestContext ctx, List<String> problems) {
        // Bound to 127.0.0.1, so nothing off this machine can join; the test server runs offline-mode (no account
        // check), which makes that bind the only thing keeping other machines out. A free port rather than vanilla's
        // 25565, which a dev server of the user's may already hold.
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("server-port", String.valueOf(freeLoopbackPort()));
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(properties)) {
            int port = server.computeOnServer(MinecraftServer::getPort);
            try (TestDedicatedServerConnection conn = server.connect()) {
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
        TestServerConnection conn, List<String> problems) {
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
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
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
            ctx.waitFor(mc -> mc.gui.screen() instanceof DisconnectedScreen || mc.level != null, 1200);
        } catch (AssertionError timedOut) {
            // Reported below with what the client shows.
        }
        String outcome = ctx.computeOnClient(mc -> {
            Screen screen = mc.gui.screen();
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

    private static void buildRig(TestServerContext server) {
        for (String cmd : new String[] {
            "gamemode creative @p",
            "time set 6000",
            "fill -12 " + Y + " -8 24 " + (Y + 6) + " 18 minecraft:air",
            "fill -12 " + (Y - 1) + " -8 24 " + (Y - 1) + " 18 minecraft:smooth_stone",
            // Kinesis: a redstone engine on a redstone block (placed below, after the pipes).
            "setblock -8 " + (Y - 1) + " 0 minecraft:redstone_block",
        }) {
            server.runCommand(cmd);
        }
        String[] frontPipes = {"wood_power", "stone_power", "gold_power", "wood_item", "cobblestone_item", "stone_item",
            "gold_item", "iron_item", "diamond_item"};
        for (int i = 0; i < frontPipes.length; i++) {
            pipe(server, -7 + i, 0, frontPipes[i]);
        }
        String[] backPipes = {"wood_fluid", "cobblestone_fluid", "stone_fluid", "clay_item", "sandstone_item",
            "quartz_item", "obsidian_item", "lapis_item", "daizuli_item", "emzuli_item", "void_item", "stripes_item",
            "diamond_wood_item", "structure"};
        for (int i = 0; i < backPipes.length; i++) {
            pipe(server, -7 + i, -6, backPipes[i]);
        }
        for (String cmd : new String[] {
            // After the pipes: an engine turns to a receiver only on its first tick (the tile's orientation, which
            // its renderer draws, starts UP whatever the placed blockstate says).
            "setblock -8 " + Y + " 0 " + NS + ":engine_redstone",
            "setblock " + TANK.getX() + " " + Y + " 0 " + NS + ":tank",
            "setblock 6 " + Y + " 0 " + NS + ":distiller",
            "setblock 8 " + Y + " 0 " + NS + ":heat_exchange",
            "setblock " + STIRLING.getX() + " " + Y + " 0 " + NS + ":engine_stone",
            "setblock 12 " + Y + " 0 " + NS + ":engine_iron",
            // Builders: a volume marker, a few frames, a quarry.
            "setblock 15 " + Y + " 0 " + NS + ":marker_volume",
            "fill 16 " + Y + " 0 19 " + Y + " 0 " + NS + ":frame",
            "fill 19 " + (Y + 1) + " 0 19 " + (Y + 2) + " 0 " + NS + ":frame",
            "setblock 21 " + Y + " 0 " + NS + ":quarry",
            // The hotbar: oil bucket, fragile fluid shard of oil, the guide, then a few block items.
            "item replace entity @p hotbar.0 with " + NS + ":oil_bucket",
            "item replace entity @p hotbar.1 with " + NS + ":fragile_fluid_container[" + NS
                + ":fluid_content={id:\"" + NS + ":oil\",amount:1000}]",
            "item replace entity @p hotbar.2 with " + NS + ":guide",
            "item replace entity @p hotbar.3 with " + NS + ":fragile_fluid_container[" + NS
                + ":fluid_content={id:\"minecraft:lava\",amount:1000}]",
            "item replace entity @p hotbar.4 with " + NS + ":fuel_light_bucket",
            "item replace entity @p hotbar.5 with " + NS + ":pipe_wood_item",
            "item replace entity @p hotbar.6 with " + NS + ":engine_redstone",
            "item replace entity @p hotbar.7 with " + NS + ":tank",
            "item replace entity @p hotbar.8 with minecraft:air",
            "tp @p 2.5 " + Y + " 10.5 180 20",
        }) {
            server.runCommand(cmd);
        }
    }

    private static void pipe(TestServerContext server, int x, int z, String def) {
        pipe(server, new BlockPos(x, Y, z), def);
    }

    private static void pipe(TestServerContext server, BlockPos at, String def) {
        String xyz = at.getX() + " " + at.getY() + " " + at.getZ();
        server.runCommand("setblock " + xyz + " " + NS + ":pipe_holder");
        server.runCommand("data merge block " + xyz + " {pipe:{def:\"" + NS + ":" + def + "\"}}");
    }

    private static void fillTank(TestServerContext server, BlockPos at) {
        server.runOnServer(s -> {
            Fluid oil = BuiltInRegistries.FLUID.getValue(Identifier.fromNamespaceAndPath(NS, "oil"));
            if (!(s.overworld().getBlockEntity(at) instanceof TileTank tank)) {
                throw new AssertionError("No tank at " + at);
            }
            tank.tank.setFluidStack(0, new BCFluidStack(oil, 12_000));
            tank.setChanged();
        });
    }

    /^*
     * Turns the redstone engine to its pipe, as a wrench would. A setblock leaves the tile facing UP whatever the
     * placed state says, and an engine only turns on its own to a receiver that already connects to it, which a
     * kinesis pipe does only to an engine facing it.
     *^/
    private static void orientEngine(TestServerContext server, BlockPos at) {
        server.runOnServer(s -> {
            var level = s.overworld();
            if (!(level.getBlockEntity(at) instanceof buildcraft.lib.engine.TileEngineBase_BC8 engine)) {
                throw new AssertionError("No engine at " + at);
            }
            engine.setOrientation(Direction.EAST);
            level.setBlock(at, level.getBlockState(at).setValue(
                buildcraft.api.properties.BuildCraftProperties.BLOCK_FACING_6, Direction.EAST), 3);
            if (level.getBlockEntity(at.east()) instanceof TilePipeHolder pipe
                && pipe.getPipe() != null) {
                pipe.getPipe().markForUpdate();
            }
        });
    }

    /^* Teleports the player ({@code "x y z yaw pitch"}), waits for the view to settle and screenshots it. *^/
    private static void worldShot(ClientGameTestContext ctx, TestServerConnection conn, TestServerContext server,
        String where, String shot, List<String> problems) {
        server.runCommand("tp @p " + where);
        ctx.waitTicks(10);
        conn.waitForChunksRender();
        ctx.waitTicks(10);
        clearOverlays(ctx);
        checkShot(ctx.takeScreenshot(shot), problems);
    }

    private static void setHudHidden(ClientGameTestContext ctx, boolean hidden) {
        ctx.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() != hidden) {
                mc.gui.hud.toggle();
            }
        });
    }

    /^* The command-feedback chat lines and the advancement/recipe toasts would cover the rig in every shot. *^/
    private static void clearOverlays(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.toastManager().clear();
        });
        ctx.waitTick();
    }

    private static void selectSlot(ClientGameTestContext ctx, int slot) {
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[slot]);
    }

    /^* Stands in front of {@code pos}, uses it with an empty hand and screenshots the screen that opens. *^/
    private static void openScreenOn(ClientGameTestContext ctx, TestServerContext server, BlockPos pos,
        String screenName, String shot, List<String> problems) {
        server.runCommand("tp @p " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() + 2.5) + " 180 30");
        selectSlot(ctx, 8);
        ctx.waitTicks(5);
        ctx.getInput().lookAt(pos);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyUse);
        expectScreen(ctx, screenName, problems);
        ctx.waitTicks(10);
        clearOverlays(ctx);
        ctx.takeScreenshot(shot);
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    private static void expectScreen(ClientGameTestContext ctx, String screenName, List<String> problems) {
        Function<Minecraft, String> current = mc -> {
            Screen screen = mc.gui.screen();
            return screen == null ? "none" : screen.getClass().getSimpleName();
        };
        try {
            ctx.waitFor(mc -> !"none".equals(current.apply(mc)), 100);
        } catch (AssertionError timedOut) {
            // Reported below with the screen it found.
        }
        String found = ctx.computeOnClient(current::apply);
        if (!screenName.equals(found)) {
            problems.add("expected the " + screenName + " screen, found " + found);
        }
    }

    /^*
     * Flags a world or held-item shot in which the missing texture (#F800F8 magenta) covers a visible patch. BC's rig
     * has no magenta of its own, so even a pipe-sized patch at this distance is a missing sprite.
     *^/
    private static void checkShot(Path file, List<String> problems) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            int magenta = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (r > 200 && b > 200 && g < 40) {
                        magenta++;
                    }
                }
            }
            if (magenta > 150) {
                problems.add(file.getFileName() + ": " + magenta + " missing-texture magenta pixels");
            }
        } catch (IOException e) {
            problems.add(file.getFileName() + ": unreadable (" + e + ")");
        }
    }

    /^* BuildCraft ERROR lines, and BuildCraft lines about a missing or failed model, texture or load. *^/
    private static List<String> logProblems() {
        Path log = FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
        List<String> found = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        } catch (IOException e) {
            found.add("cannot read " + log + ": " + e);
            return found;
        }
        for (String line : lines) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("buildcraft")) {
                continue;
            }
            if (line.contains("/ERROR]") || BC_PROBLEM.matcher(line).find()) {
                found.add("log: " + line);
            }
        }
        return found;
    }
}
*///?}
