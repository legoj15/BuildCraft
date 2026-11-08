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
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if >=1.21.10 {
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
//?} else {
/^import buildcraft.platform.fabric.gametest.ClientGameTestShim.ClientGameTestContext;
import buildcraft.platform.fabric.gametest.ClientGameTestShim.FabricClientGameTest;
import buildcraft.platform.fabric.gametest.ClientGameTestShim.TestServerContext;
import buildcraft.platform.fabric.gametest.ClientGameTestShim.TestSingleplayerContext;
^///?}
//? if >=26.2 {
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
//?} elif >=26.1 {
/^import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientLevelContext;
^///?} elif >=1.21.10 {
/^import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext;
^///?} else {
/^import buildcraft.platform.fabric.gametest.ClientGameTestShim.TestClientWorldContext;
^///?}
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.client.guide.parts.recipe.ClientGuideRecipeCache;
import buildcraft.lib.misc.RegistryUtilBC;
import buildcraft.platform.fabric.gametest.mixin.ParticleEngineAccessor;
import buildcraft.transport.recipe.DyedPipeRecipe;
import buildcraft.transport.tile.TilePipeHolder;

/^*
 * The Fabric nodes' in-world visual check ({@code :26.3-fabric:runClientGameTest}, Fabric API's client game-test
 * runner under {@code -Dfabric.client.gametest}): a flat singleplayer world, a rig of BuildCraft content built by
 * commands (a line of pipes of every kind, a powered redstone engine, a tank of oil, a distiller, a heat exchanger,
 * Stirling and combustion engines, a quarry beside a volume marker and frames), the oil bucket and fragile fluid shards
 * in the hotbar, the tank and Stirling engine GUIs and the guide book, then ({@code BCFabricVisualScenes}) every other
 * BuildCraft screen, a guide entry page, an inventory of BuildCraft items with tooltips, fluid fog, particles, the HUD,
 * third person in goggles, a pipe close-up, markers, a volume box and a laser, each screenshotted into the run's
 * {@code screenshots/} folder for a person to look at. On 1.21.1, whose Fabric API has no client game-test module,
 * {@code gametest.ClientGameTestShim} stands in for the part of it used here.
 * <p>
 * It also runs the game-test bodies that load client classes, which the dedicated game-test server cannot
 * ({@link #runClientClassGameTests}).
 * <p>
 * Then, from 1.21.10, a real multiplayer join ({@code BCFabricMultiplayerChecks}): Fabric API starts a dedicated server
 * in this JVM and the client connects to it over localhost TCP, so every packet also passes vanilla's frame codec and
 * its 2 MiB frame limit, which a singleplayer join's in-memory connection never applies. It checks machine state
 * reaching the client, a menu opening, a blueprint larger than a packet making the round trip, and the version
 * handshake turning away a client whose payload table differs or that has none.
 * <p>
 * What it asserts on its own: each GUI opens as its BuildCraft screen; no world or held-item shot is dominated by the
 * missing-texture magenta; and the run's log carries no BuildCraft ERROR line and no BuildCraft missing-model,
 * missing-texture or load-failure line. It needs a GL context, so it is not part of {@code nodeVerification}.
 *^/
public final class BCFabricClientGameTest implements FabricClientGameTest {

    static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft client game test");
    /^* bc.fabric's runClientGameTest requires this line in the log: the runner exits 0 on some aborts too. *^/
    public static final String PASS_MARKER = "BuildCraft client game test: all checks passed";

    static final String NS = "buildcraftunofficial";
    /^* The flat world's surface is grass at y -61. *^/
    static final int Y = -60;
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
            //? if >=26.2 {
            sp.getConnection().waitForChunksRender();
            //?} elif >=26.1 {
            /^sp.getClientLevel().waitForChunksRender();
            ^///?} else {
            /^sp.getClientWorld().waitForChunksRender();
            ^///?}
            ctx.waitTicks(20);
            // The client must draw the engine turned too (its renderer reads the synced tile orientation).
            String clientEngine = ctx.computeOnClient(mc ->
                mc.level.getBlockEntity(ENGINE) instanceof buildcraft.lib.engine.TileEngineBase_BC8 e
                    ? String.valueOf(e.getOrientation()) : "no engine");
            if (!"east".equals(clientEngine)) {
                problems.add("the client's redstone engine faces " + clientEngine + ", not east");
            }
            checkRecipeSync(ctx, problems);
            checkDynamicItemModels(ctx, problems);
            checkPipeColours(ctx, server, problems);
            checkBlockParticles(ctx, server, problems);

            // The world shots, HUD hidden (F1): the whole rig, then each part close up.
            setHudHidden(ctx, true);
            //? if >=26.2 {
            TestServerConnection spConn = sp.getConnection();
            //?} elif >=26.1 {
            /^TestClientLevelContext spConn = sp.getClientLevel();
            ^///?} else {
            /^TestClientWorldContext spConn = sp.getClientWorld();
            ^///?}
            worldShot(ctx, spConn, server, "5.5 " + (Y + 4) + " 15.5 180 25", "bc-rig-overview", problems);
            worldShot(ctx, spConn, server, "-5.5 " + Y + " 3.5 180 30", "bc-kinesis-engine", problems);
            worldShot(ctx, spConn, server, "-1.5 " + Y + " 3.5 180 30", "bc-item-pipes", problems);
            worldShot(ctx, spConn, server, "-0.5 " + Y + " -1.5 180 25", "bc-back-pipes", problems);
            worldShot(ctx, spConn, server, "4.5 " + Y + " 2.5 180 30", "bc-tank-oil", problems);
            worldShot(ctx, spConn, server, "8.5 " + Y + " 3.5 180 30", "bc-machines", problems);
            worldShot(ctx, spConn, server, "18.5 " + Y + " 3.5 180 30", "bc-builders", problems);
            worldShot(ctx, spConn, server, "-6 " + Y + " -1 180 30", "bc-lens-and-paint", problems);
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

            // Every other BuildCraft screen and the remaining render paths, for comparing across MC lines.
            BCFabricVisualScenes.run(ctx, server, spConn::waitForChunksRender, problems);

            runClientClassGameTests(server, problems);
        }

        // Scanned before and after the multiplayer phase, which boots a second server in this JVM: the first scan
        // covers everything up to it, so a log the phase rolls or re-opens cannot hide those lines; the second covers
        // what was written after (the test server logs into the same file).
        LogMark scanned = scanLog(LogMark.START, problems);
        Throwable phaseError = null;
        try {
            //? if >=1.21.10 {
            BCFabricMultiplayerChecks.run(ctx, problems);
            //?} else {
            /^// Fabric API has no dedicated server for a client game test before 1.21.4 (ClientGameTestShim builds
            // singleplayer worlds only), so the multiplayer phase and its two shots (bc-mp-rig, bc-mp-tank-gui) do not
            // run on 1.21.1.
            LOGGER.info("Multiplayer phase not run on 1.21.1: its Fabric API cannot host a dedicated server in this JVM");
            ^///?}
        } catch (Throwable t) {
            phaseError = t;
            problems.add("multiplayer: the phase threw " + t + " (attached as the cause)");
        }
        scanLog(scanned, problems);
        if (!problems.isEmpty()) {
            throw new AssertionError("BuildCraft client game test found " + problems.size() + " problem(s):\n  "
                + String.join("\n  ", problems), phaseError);
        }
        LOGGER.info(PASS_MARKER);
    }

    /^* A rig pipe, and a free spot (inside the cleared area, out of every world shot's view) for a Mining Well tube. *^/
    private static final BlockPos PARTICLE_PIPE = new BlockPos(-7, Y, 0);
    private static final BlockPos PARTICLE_TUBE = new BlockPos(-11, Y, 16);

    /^*
     * A block's own hit and break particles (#153): Fabric reaches them through {@code ClientLevelMixin}. A pipe spawns
     * its own break and hit particles instead of vanilla's {@link TerrainParticle}s, and the Mining Well tube, whose
     * override handles its break by spawning nothing, spawns none. Without the hook both show vanilla's terrain ones.
     *^/
    private static void checkBlockParticles(ClientGameTestContext ctx, TestServerContext server,
            List<String> problems) {
        server.runCommand("setblock " + PARTICLE_TUBE.getX() + " " + Y + " " + PARTICLE_TUBE.getZ() + " " + NS + ":tube");
        ctx.waitTicks(10);
        List<String> found = ctx.computeOnClient(mc -> {
            List<String> out = new ArrayList<>();
            ClientLevel level = mc.level;
            Queue<Particle> queued = ((ParticleEngineAccessor) mc.particleEngine).buildcraft$particlesToAdd();
            for (BlockPos at : new BlockPos[] {PARTICLE_PIPE, PARTICLE_TUBE}) {
                BlockState state = level.getBlockState(at);
                boolean pipe = at.equals(PARTICLE_PIPE);
                if (!pipe && !BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(NS + ":tube")) {
                    out.add("particles: the client sees " + state + " at the tube spot, not the tube");
                    continue;
                }
                clearParticles(mc, queued);
                level.addDestroyBlockEffect(at, state);
                out.addAll(particleProblems("break", state, pipe, queued));
                if (pipe) {
                    // Only a pipe: the tube renders INVISIBLE, which vanilla's hit effects skip anyway.
                    //? if >=26.3 {
                    clearParticles(mc, queued);
                    level.addBreakingBlockEffects(at, Direction.UP, false);
                    out.addAll(particleProblems("hit", state, true, queued));
                    //?} elif >=1.21.10 {
                    /^clearParticles(mc, queued);
                    level.addBreakingBlockEffect(at, Direction.UP);
                    out.addAll(particleProblems("hit", state, true, queued));
                    ^///?} else {
                    /^// 1.21.1 has no level hit-effect method: MinecraftMixin wraps the crack call inside
                    // Minecraft.continueAttack, which takes a real attack on the block, so only the break is checked.
                    LOGGER.info("Particles: the hit check is not run on 1.21.1 (its hook sits in Minecraft.continueAttack)");
                    ^///?}
                }
            }
            clearParticles(mc, queued);
            return out;
        });
        problems.addAll(found);
        server.runCommand("setblock " + PARTICLE_TUBE.getX() + " " + Y + " " + PARTICLE_TUBE.getZ() + " minecraft:air");
    }

    /^* Empties the particle engine; on 1.21.1, whose clearParticles is private, the queue the checks read. *^/
    private static void clearParticles(Minecraft mc, Queue<Particle> queued) {
        //? if >=1.21.10 {
        mc.particleEngine.clearParticles();
        //?} else {
        /^queued.clear();
        ^///?}
    }

    private static List<String> particleProblems(String effect, BlockState state, boolean pipe,
            Queue<Particle> queued) {
        String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        long terrain = queued.stream().filter(p -> p instanceof TerrainParticle).count();
        if (pipe && (queued.isEmpty() || terrain > 0)) {
            return List.of("particles: a " + block + " " + effect + " spawned " + queued.size() + " particle(s), "
                + terrain + " of them vanilla terrain ones; want only the pipe's own");
        }
        if (!pipe && !queued.isEmpty()) {
            return List.of("particles: a " + block + " " + effect + " spawned " + queued.size()
                + " particle(s); its override spawns none");
        }
        return List.of();
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
            //? if >=26.1 {
            RecipeSerializer<?> shaped = ShapedRecipe.SERIALIZER;
            RecipeSerializer<?> smelting = SmeltingRecipe.SERIALIZER;
            //?} else {
            /^RecipeSerializer<?> shaped = RecipeSerializer.SHAPED_RECIPE;
            RecipeSerializer<?> smelting = RecipeSerializer.SMELTING_RECIPE;
            ^///?}
            if (!received.contains(shaped)) {
                absent.add("shaped");
            }
            if (!received.contains(smelting)) {
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
        placeColourPipes(server);
    }

    /^* The colour checks' pipes, between the rig's two rows: a red lens on top of the first, the second painted red. *^/
    private static final BlockPos LENS_PIPE = new BlockPos(-7, Y, -3);
    private static final BlockPos PAINTED_PIPE = new BlockPos(-5, Y, -3);

    private static void placeColourPipes(TestServerContext server) {
        pipe(server, LENS_PIPE, "stone_item");
        pipe(server, PAINTED_PIPE, "stone_item");
        server.runOnServer(s -> {
            var level = s.overworld();
            if (level.getBlockEntity(LENS_PIPE) instanceof TilePipeHolder holder) {
                holder.replacePluggable(Direction.UP, new buildcraft.silicon.plug.PluggableLens(
                    buildcraft.silicon.BCSiliconPlugs.lens, holder, Direction.UP, DyeColor.RED, false));
            }
            if (level.getBlockEntity(PAINTED_PIPE) instanceof TilePipeHolder holder && holder.getPipe() != null) {
                holder.getPipe().setColour(DyeColor.RED);
            }
        });
    }

    /^*
     * The item models BuildCraft swaps in at bake time (#152): the facade, gate, lens and painted-pipe stacks must
     * resolve to its dynamic models, not the JSON fallback (the brick facade, the gate placeholder, an invisible lens,
     * an unpainted pipe). Then the red lens item's glass must reach the item draw red (#154): on Fabric its colour
     * rides only on the vertex colours, which the layer's mesh carries.
     *^/
    private static void checkDynamicItemModels(ClientGameTestContext ctx, List<String> problems) {
        List<String> found = ctx.computeOnClient(mc -> {
            List<String> bad = new ArrayList<>();
            ItemStack facade = new ItemStack(buildcraft.silicon.BCSiliconItems.PLUG_FACADE.get());
            ItemStack gate = new ItemStack(buildcraft.silicon.BCSiliconItems.PLUG_GATE.get());
            ItemStack redLens = buildcraft.silicon.BCSiliconItems.PLUG_LENS.get().getStack(DyeColor.RED, false);
            ItemStack paintedPipe = new ItemStack(RegistryUtilBC.getValue(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath(NS, "pipe_wood_item")));
            paintedPipe.set(buildcraft.transport.BCTransportItems.PIPE_COLOUR.get(), DyeColor.RED);
            expectItemModel(mc, facade, buildcraft.silicon.client.model.FacadeItemModel.class, bad);
            expectItemModel(mc, gate, buildcraft.silicon.client.model.GateItemModel.class, bad);
            expectItemModel(mc, redLens, buildcraft.silicon.client.model.LensItemModel.class, bad);
            expectItemModel(mc, paintedPipe, buildcraft.transport.client.model.PipeItemModel.class, bad);

            int red = DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF;
            List<int[]> colours = itemMeshColours(mc, redLens, bad);
            if (colours.stream().noneMatch(c -> allRgb(c, red))) {
                bad.add("the red lens item draws no red glass: its layers' mesh quads carry colours "
                    + describe(colours));
            }
            //? if <1.21.10 {
            /^// 1.21.1 tints a painted pipe item's glaze through a classic ItemColor: without one the glaze draws white.
            if (net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.get(paintedPipe.getItem())
                    == null) {
                bad.add("the painted pipe item has no ItemColor for its glaze (tint 0)");
            }
            ^///?}
            return bad;
        });
        problems.addAll(found);
    }

    private static void expectItemModel(Minecraft mc, ItemStack stack, Class<?> want, List<String> bad) {
        //? if >=1.21.10 {
        Identifier modelId = stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
        net.minecraft.client.renderer.item.ItemModel model =
            modelId == null ? null : mc.getModelManager().getItemModel(modelId);
        //?} else {
        /^// 1.21.1 has no item models: BuildCraft puts its own BakedModel in the item's model slot at bake time.
        net.minecraft.client.resources.model.BakedModel model =
            mc.getItemRenderer().getItemModelShaper().getItemModel(stack);
        ^///?}
        if (!want.isInstance(model)) {
            bad.add(stack.getItem() + " resolves to " + (model == null ? "no item model" : model.getClass().getName())
                + ", not BuildCraft's " + want.getSimpleName());
        }
    }

    /^*
     * The per-vertex colours of every quad in the Fabric meshes of {@code stack}'s item layers (GUI context): where
     * Fabric draws an item quad with its own colour.
     *^/
    private static List<int[]> itemMeshColours(Minecraft mc, ItemStack stack, List<String> bad) {
        List<int[]> colours = new ArrayList<>();
        for (Object layer : itemLayers(mc, stack, bad)) {
            layerColours(layer, colours);
        }
        return colours;
    }

    /^*
     * The layers {@code stack} draws in the GUI: vanilla's item render state layers (private fields, read
     * reflectively), or on 1.21.1 the one model its classic item model answers for the GUI.
     *^/
    private static List<Object> itemLayers(Minecraft mc, ItemStack stack, List<String> bad) {
        List<Object> layers = new ArrayList<>();
        //? if >=1.21.10 {
        net.minecraft.client.renderer.item.ItemStackRenderState state =
            new net.minecraft.client.renderer.item.ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, net.minecraft.world.item.ItemDisplayContext.GUI,
            mc.level, null, 0);
        try {
            var layersField = net.minecraft.client.renderer.item.ItemStackRenderState.class.getDeclaredField("layers");
            var countField = net.minecraft.client.renderer.item.ItemStackRenderState.class
                .getDeclaredField("activeLayerCount");
            layersField.setAccessible(true);
            countField.setAccessible(true);
            Object[] all = (Object[]) layersField.get(state);
            int count = countField.getInt(state);
            for (int i = 0; i < count; i++) {
                layers.add(all[i]);
            }
        } catch (ReflectiveOperationException e) {
            bad.add("cannot read the item render state's layers: " + e);
        }
        //?} else {
        /^net.minecraft.client.resources.model.BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
        if (model instanceof buildcraft.platform.client.ClassicItemModel classic) {
            model = classic.applyTransform(net.minecraft.world.item.ItemDisplayContext.GUI,
                new com.mojang.blaze3d.vertex.PoseStack(), false);
        }
        layers.add(model);
        ^///?}
        return layers;
    }

    /^* Adds the four ARGB vertex colours of each quad that {@code layer}, one of {@link #itemLayers}, draws. *^/
    private static void layerColours(Object layer, List<int[]> colours) {
        //? if >=26.1 {
        var mesh = ((net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension) layer).fabric_getMutableMesh();
        if (mesh != null) {
            mesh.forEach(q -> colours.add(new int[] {q.color(0), q.color(1), q.color(2), q.color(3)}));
        }
        //?} elif >=1.21.11 {
        /^var mesh = ((net.fabricmc.fabric.impl.client.indigo.renderer.accessor.AccessLayerRenderState) layer)
            .fabric_getMutableMesh();
        if (mesh != null) {
            mesh.forEach(q -> colours.add(new int[] {q.color(0), q.color(1), q.color(2), q.color(3)}));
        }
        ^///?} elif >=1.21.10 {
        /^// 1.21.10 draws an item quad with the colours in its own vertex data, so BuildCraft hands Fabric no mesh
        // there: the layer's quads are what carry them.
        var renderLayer = (net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState) layer;
        for (net.minecraft.client.renderer.block.model.BakedQuad quad : renderLayer.prepareQuadList()) {
            colours.add(vertexColours(quad.vertices()));
        }
        ^///?} else {
        /^// 1.21.1 likewise: the quads of the model the classic item model answers carry them in their vertex data.
        var model = (net.minecraft.client.resources.model.BakedModel) layer;
        var random = net.minecraft.util.RandomSource.create(0);
        List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
        faces.add(null);
        for (Direction face : faces) {
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getQuads(null, face, random)) {
                colours.add(vertexColours(quad.getVertices()));
            }
        }
        ^///?}
    }

    //? if <1.21.11 {
    /^// The ARGB colours of a vanilla quad's four vertices, which it stores ABGR as int 3 of each vertex's eight.
    private static int[] vertexColours(int[] vertexData) {
        int[] argb = new int[4];
        for (int v = 0; v < 4; v++) {
            int abgr = vertexData[v * 8 + 3];
            argb[v] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
        }
        return argb;
    }
    ^///?}

    /^* One quad as a pipe's block model emits it into the chunk mesh: whether its layer is the translucent one, and
     * its four ARGB colours. *^/
    private record EmittedQuad(boolean translucent, int[] colours) {}

    /^*
     * The vertex colours a pipe's block model emits into the chunk mesh (#154): BuildCraft bakes its directional
     * shading, a painted pipe's glaze alpha and the lens glass colour into them, and on Fabric they reach the mesh only
     * if the model puts them on the emitter. Reads the quads the live block model emits for the colour pipes.
     *^/
    private static void checkPipeColours(ClientGameTestContext ctx, TestServerContext server, List<String> problems) {
        try {
            ctx.waitFor(mc -> mc.level != null
                && mc.level.getBlockEntity(LENS_PIPE) instanceof TilePipeHolder lens
                && lens.getPluggable(Direction.UP) instanceof buildcraft.silicon.plug.PluggableLens
                && mc.level.getBlockEntity(PAINTED_PIPE) instanceof TilePipeHolder painted
                && painted.getPipe() != null && painted.getPipe().getColour() == DyeColor.RED, 200);
        } catch (AssertionError timedOut) {
            problems.add("the client never saw the red lens and the red-painted pipe");
            return;
        }
        List<String> found = ctx.computeOnClient(mc -> {
            List<String> bad = new ArrayList<>();
            List<EmittedQuad> lens = emittedQuads(mc, LENS_PIPE);
            int red = DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF;
            if (lens.stream().noneMatch(q -> q.translucent() && allRgb(q.colours(), red))) {
                bad.add("the red lens's glass is not emitted red: translucent quad colours "
                    + describe(lens.stream().filter(EmittedQuad::translucent).map(EmittedQuad::colours).toList()));
            }
            if (lens.stream().noneMatch(q -> !q.translucent() && !allRgb(q.colours(), 0xFFFFFF))) {
                bad.add("the lens pipe's opaque quads are all white: no directional shading reached the mesh ("
                    + lens.size() + " quads)");
            }
            List<EmittedQuad> painted = emittedQuads(mc, PAINTED_PIPE);
            if (painted.stream().noneMatch(q -> q.translucent() && (q.colours()[0] >>> 24) == 76)) {
                bad.add("the red-painted pipe's glaze is not emitted at alpha 76: translucent quad colours "
                    + describe(painted.stream().filter(EmittedQuad::translucent).map(EmittedQuad::colours)
                        .toList()));
            }
            return bad;
        });
        problems.addAll(found);
    }

    private static List<EmittedQuad> emittedQuads(Minecraft mc, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
        List<EmittedQuad> out = new ArrayList<>();
        //? if >=26.1 {
        var model = mc.getModelManager().getBlockStateModelSet().get(state);
        var translucent = net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT;
        var capture = net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().quadEmitter(q -> out.add(
            new EmittedQuad(q.chunkLayer() == translucent, new int[] {q.color(0), q.color(1), q.color(2), q.color(3)})));
        model.emitQuads(capture, mc.level, pos, state, net.minecraft.util.RandomSource.create(0), face -> false);
        //?} elif >=1.21.10 {
        /^// Before 26.1 the renderer has no emitter that hands each quad on: the quads collect in a mesh, read after.
        var model = mc.getModelManager().getBlockModelShaper().getBlockModel(state);
        var mesh = net.fabricmc.fabric.api.renderer.v1.Renderer.get().mutableMesh();
        model.emitQuads(mesh.emitter(), mc.level, pos, state, net.minecraft.util.RandomSource.create(0), face -> false);
        var translucent = net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT;
        mesh.forEach(q -> out.add(new EmittedQuad(q.renderLayer() == translucent,
            new int[] {q.color(0), q.color(1), q.color(2), q.color(3)})));
        ^///?} else {
        /^// 1.21.1's renderer API (Fabric API 0.116) emits a block model's quads only into a RenderContext, an interface
        // whose abstract members include one deprecated for removal; a proxy implements it without naming that member,
        // its emitter the one of a mesh read after. The pipe model emits on its own materials and pushes no transform.
        var model = mc.getModelManager().getBlockModelShaper().getBlockModel(state);
        var builder = net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE.getRenderer().meshBuilder();
        var emitter = builder.getEmitter();
        var context = (net.fabricmc.fabric.api.renderer.v1.render.RenderContext) java.lang.reflect.Proxy.newProxyInstance(
            BCFabricClientGameTest.class.getClassLoader(),
            new Class<?>[] {net.fabricmc.fabric.api.renderer.v1.render.RenderContext.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getEmitter" -> emitter;
                case "hasTransform", "isFaceCulled" -> false;
                case "pushTransform", "popTransform" -> null;
                case "toString" -> "client game test render context";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args)
                    : unsupported(method);
            });
        model.emitBlockQuads(mc.level, state, pos, () -> net.minecraft.util.RandomSource.create(0), context);
        var translucent = net.fabricmc.fabric.api.renderer.v1.material.BlendMode.TRANSLUCENT;
        builder.build().forEach(q -> out.add(new EmittedQuad(q.material().blendMode() == translucent,
            new int[] {q.color(0), q.color(1), q.color(2), q.color(3)})));
        ^///?}
        return out;
    }

    //? if <1.21.10 {
    /^private static Object unsupported(java.lang.reflect.Method method) {
        throw new UnsupportedOperationException("the client game test's render context has no " + method.getName());
    }
    ^///?}

    private static boolean allRgb(int[] argb, int rgb) {
        for (int c : argb) {
            if ((c & 0xFFFFFF) != rgb) {
                return false;
            }
        }
        return true;
    }

    /^* The distinct colours, as hex, for a failure message. *^/
    private static String describe(List<int[]> colours) {
        Set<String> distinct = new java.util.TreeSet<>();
        for (int[] quad : colours) {
            for (int c : quad) {
                distinct.add(Integer.toHexString(c));
            }
        }
        return colours.isEmpty() ? "(no quads)" : distinct.toString();
    }

    static void pipe(TestServerContext server, int x, int z, String def) {
        pipe(server, new BlockPos(x, Y, z), def);
    }

    static void pipe(TestServerContext server, BlockPos at, String def) {
        String xyz = at.getX() + " " + at.getY() + " " + at.getZ();
        server.runCommand("setblock " + xyz + " " + NS + ":pipe_holder");
        server.runCommand("data merge block " + xyz + " {pipe:{def:\"" + NS + ":" + def + "\"}}");
    }

    static void fillTank(TestServerContext server, BlockPos at) {
        server.runOnServer(s -> {
            Fluid oil = RegistryUtilBC.getValue(BuiltInRegistries.FLUID, Identifier.fromNamespaceAndPath(NS, "oil"));
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
    static void orientEngine(TestServerContext server, BlockPos at) {
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
    static void worldShot(ClientGameTestContext ctx,
        //? if >=26.2 {
        TestServerConnection conn,
        //?} elif >=26.1 {
        /^TestClientLevelContext conn,
        ^///?} else {
        /^TestClientWorldContext conn,
        ^///?}
        TestServerContext server, String where, String shot, List<String> problems) {
        server.runCommand("tp @p " + where);
        ctx.waitTicks(10);
        conn.waitForChunksRender();
        ctx.waitTicks(10);
        clearOverlays(ctx);
        checkShot(ctx.takeScreenshot(shot), problems);
    }

    // The open screen: the GUI holds it from 26.2, the Minecraft instance before.
    static Screen currentScreen(net.minecraft.client.Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /^return mc.screen;
        ^///?}
    }

    static void setHudHidden(ClientGameTestContext ctx, boolean hidden) {
        ctx.runOnClient(mc -> {
            //? if >=26.2 {
            if (mc.gui.hud.isHidden() != hidden) {
                mc.gui.hud.toggle();
            }
            //?} else {
            /^mc.options.hideGui = hidden;
            ^///?}
        });
    }

    /^* The command-feedback chat lines and the advancement/recipe toasts would cover the rig in every shot. *^/
    static void clearOverlays(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            //? if >=26.2 {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.toastManager().clear();
            //?} elif >=1.21.10 {
            /^mc.gui.getChat().clearMessages(false);
            mc.getToastManager().clear();
            ^///?} else {
            /^mc.gui.getChat().clearMessages(false);
            mc.getToasts().clear();
            ^///?}
        });
        ctx.waitTick();
    }

    static void selectSlot(ClientGameTestContext ctx, int slot) {
        ctx.getInput().pressKey(o -> o.keyHotbarSlots[slot]);
    }

    /^* The Stirling screen's remembered power-ledger state (GuiConfigManager, saved to gui_state.json). *^/
    static void setEngineLedgerOpen(ClientGameTestContext ctx, boolean open) {
        ctx.runOnClient(mc -> buildcraft.lib.gui.config.GuiConfigManager.getOrAddBoolean(
            buildcraft.energy.client.gui.ScreenEngineStone.class.getName(), "LedgerEngine.is_open", false).set(open));
    }

    /^* Stands in front of {@code pos}, uses it with an empty hand and screenshots the screen that opens. *^/
    static void openScreenOn(ClientGameTestContext ctx, TestServerContext server, BlockPos pos,
        String screenName, String shot, List<String> problems) {
        openScreen(ctx, server, pos, screenName, problems);
        ctx.waitTicks(10);
        clearOverlays(ctx);
        ctx.takeScreenshot(shot);
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    /^* Stands in front of {@code pos} and uses it with an empty hand; the screen stays open. *^/
    static void openScreen(ClientGameTestContext ctx, TestServerContext server, BlockPos pos,
        String screenName, List<String> problems) {
        server.runCommand("tp @p " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() + 2.5) + " 180 30");
        selectSlot(ctx, 8);
        ctx.waitTicks(5);
        //? if >=26.1 {
        ctx.getInput().lookAt(pos);
        //?} else {
        /^// Fabric API's test input has no lookAt before 26.1: the player turns to the block's centre itself.
        ctx.runOnClient(mc -> mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
            net.minecraft.world.phys.Vec3.atCenterOf(pos)));
        ^///?}
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyUse);
        expectScreen(ctx, screenName, problems);
    }

    static void expectScreen(ClientGameTestContext ctx, String screenName, List<String> problems) {
        Function<Minecraft, String> current = mc -> {
            Screen screen = currentScreen(mc);
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
    static void checkShot(Path file, List<String> problems) {
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

    /^* How far a scan read: the line count, and the file's first line, which tells a rolled or re-opened log apart. *^/
    private record LogMark(int lines, String first) {
        static final LogMark START = new LogMark(0, null);
    }

    /^*
     * Adds BuildCraft ERROR lines, and BuildCraft lines about a missing or failed model, texture or load, written after
     * {@code mark}; returns how far it read. A log that no longer starts with the marked first line, or is shorter than
     * the mark, was rolled or re-opened in between: that is reported, and the whole current file is scanned.
     *^/
    private static LogMark scanLog(LogMark mark, List<String> problems) {
        Path log = FabricLoader.getInstance().getGameDir().resolve("logs/latest.log");
        List<String> lines;
        int complete;
        try {
            String text = Files.readString(log, StandardCharsets.UTF_8);
            lines = text.lines().toList();
            // A last line still being written is scanned now and again next time, so its tail is not skipped.
            complete = text.isEmpty() || text.endsWith("\n") ? lines.size() : lines.size() - 1;
        } catch (IOException e) {
            problems.add("cannot read " + log + ": " + e);
            return mark;
        }
        int from = mark.lines();
        if (from > 0 && (lines.size() < from || !lines.get(0).equals(mark.first()))) {
            problems.add("log: latest.log was rolled or re-opened during the multiplayer phase (" + from
                + " lines before, " + lines.size() + " now); lines written in between may be unscanned");
            from = 0;
        }
        for (String line : lines.subList(from, lines.size())) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("buildcraft") || vanillaNoise(line)) {
                continue;
            }
            if (line.contains("/ERROR]") || BC_PROBLEM.matcher(line).find()) {
                problems.add("log: " + line);
            }
        }
        return new LogMark(complete, lines.isEmpty() ? null : lines.get(0));
    }

    /^* A vanilla log line that names a BuildCraft id but says nothing about BuildCraft. *^/
    private static boolean vanillaNoise(String line) {
        //? if >=1.21.10 {
        return false;
        //?} else {
        /^// 1.21.1's EntityType.Builder.build looks each entity id up in vanilla's data fixer, which has none for a mod's,
        // and logs that at ERROR (NeoForge turns the line down to DEBUG; the later lines do not log it).
        return line.contains("(Minecraft) No data fixer registered for " + NS + ":");
        ^///?}
    }
}
*///?}
