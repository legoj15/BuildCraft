/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import buildcraft.BuildCraftGameTests;
import buildcraft.lib.test.TestOnlyBlocks;
import buildcraft.lib.test.UnshearableBlock;

/**
 * The NeoForge hook of {@link BuildCraftGameTests}: the test source set is part of BuildCraft's mod here, so this
 * subscriber runs on BuildCraft's mod bus. {@code FMLConstructModEvent} follows the entrypoint's constructor, so the
 * registrars subscribe to the bus {@code NeoForgeModBus} already holds, before any register event. The Fabric twin is
 * {@code platform.fabric.BCFabricGameTestEntrypoint}, and this file is not in the Fabric node's game-test set.
 *
 * <p>Bare {@code @EventBusSubscriber} on every node: FML picks each method's bus from the event type (all mod-bus
 * events here). The 1.21.1 loader still has the legacy {@code bus()} element but is pinned past the build that
 * ignores it at runtime, so it is omitted; 1.21.1 game-test count parity is verified per run.
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public final class NeoForgeGameTestHooks {

    private NeoForgeGameTestHooks() {}

    /** True in the {@code gameTestServer} run (bc.neoforge), which is where the test-only blocks are wanted. */
    private static boolean gameTestRun() {
        return Boolean.getBoolean("neoforge.enableGameTest");
    }

    //? if >=1.21.10 {
    @SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        BuildCraftGameTests.register(gameTestRun());
        if (gameTestRun()) {
            // NeoForge's IShearable: Fabric has no shearable-block extension (FabricShearServices).
            TestOnlyBlocks.registerUnshearable(UnshearableBlock::new);
        }
    }
    //?} else {
    /*@SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        if (gameTestRun()) {
            TestOnlyBlocks.register();
            TestOnlyBlocks.registerUnshearable(UnshearableBlock::new);
        }
    }

    // 1.21.1 has no dynamic TEST_FUNCTION registry and no test_instance manifests, so it reflects TestFunctions
    // straight into GameTestRegistry against the buildcraftunofficial:empty arena
    // (data/buildcraftunofficial/structure/empty.nbt — a 16x16x16 air arena standing in for the 1.21.5+
    // minecraft:empty structure, with the 1.21.1 DataVersion; the size gives encaseStructure room for the oil-gen
    // surface scan), from the same BuildCraftGameTests.registerAll.
    @SubscribeEvent
    public static void onRegisterGameTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        String filter = System.getProperty("buildcraft.gameTestFilter");
        if (filter == null || filter.isBlank()) {
            BuildCraftGameTests.registerAll((id, sup) -> addReflectively(id, sup));
            return;
        }
        // `-PbcTests=<glob>` (build.gradle.kts sets the property on this node only). 1.21.1's server Main has
        // no `--tests` option and its runner takes every TestFunction already in GameTestRegistry, so the
        // filter has to act here, before the runner reads the list. Same matching as the modern selector
        // (ResourceSelectorArgument): a pattern without a colon gets "minecraft:", then a case-sensitive
        // commons-io wildcard match (`*`/`?`) against the full test id.
        String pattern = filter.strip();
        if (!pattern.contains(":")) pattern = "minecraft:" + pattern;
        final String glob = pattern;
        java.util.concurrent.atomic.AtomicInteger selected = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();
        BuildCraftGameTests.registerAll((id, sup) -> {
            total.incrementAndGet();
            if (org.apache.commons.io.FilenameUtils.wildcardMatch(id, glob)) {
                selected.incrementAndGet();
                addReflectively(id, sup);
            }
        });
        // WARN so it clears the quiet game-test console mirror (gameTestServerLog4j2-quiet.xml).
        org.slf4j.Logger log = com.mojang.logging.LogUtils.getLogger();
        log.warn("BuildCraft game-test filter '{}' selected {} of {} tests", glob, selected.get(), total.get());
        if (selected.get() == 0) {
            log.error("-PbcTests='{}' matched none of the {} BuildCraft game tests; expected a namespace:glob such as "
                + "buildcraftunofficial:painted_pipe_*", filter, total.get());
            // Throwing from this listener only reaches server Main, which logs "Failed to start the minecraft
            // server" and returns normally, so Gradle would report a green run of zero tests for a mistyped
            // glob. Exit non-zero so the task fails like a failing game test does.
            System.exit(1);
        }
    }

    // 1.21.1 has no dynamic TEST_FUNCTION registry; GameTestRegistry.TEST_FUNCTIONS is a private
    // mutable list the runner reads via getAllTestFunctions(). We build a TestFunction from the same
    // (id, supplier) the modern path registers and add it directly (dev/CI-only path).
    private static void addReflectively(String id, java.util.function.Supplier<java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper>> sup) {
        try {
            java.lang.reflect.Field f = net.minecraft.gametest.framework.GameTestRegistry.class.getDeclaredField("TEST_FUNCTIONS");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Collection<net.minecraft.gametest.framework.TestFunction> coll =
                (java.util.Collection<net.minecraft.gametest.framework.TestFunction>) f.get(null);
            // skyAccess=true (11th arg) so StructureUtils.encaseStructure omits the BARRIER CEILING
            // (it always builds walls, but adds a ceiling only when !skyAccess). Without it the oil-gen
            // surface-scan (findSolidSurfaceTop, top-down) hits the ceiling instead of the test floor,
            // and spring tests can't place fluid above. Full ctor: batch, name, structure, rotation,
            // maxTicks, setupTicks, required, manualOnly, maxAttempts, requiredSuccesses, skyAccess, fn.
            coll.add(new net.minecraft.gametest.framework.TestFunction(
                readBatch(id), id, "buildcraftunofficial:empty",
                net.minecraft.world.level.block.Rotation.NONE, readMaxTicks(id, 100), 0L, true, false, 1, 1, true, sup.get()));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register 1.21.1 game test " + id, e);
        }
    }

    // 1.21.1 has no test_environment either, but its batch NAME plays the same role (tests of one batch run
    // concurrently, batches run one after another), so a manifest's "environment" becomes the batch name.
    // Without this every test, the marker tests included, would share "defaultBatch" and run concurrently
    // in neighbouring arenas, which is exactly the crosstalk the per-test marker environments exist to prevent
    // (see MarkerTester). minecraft:default / no environment stay in the shared default batch.
    private static String readBatch(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        try (java.io.InputStream in = NeoForgeGameTestHooks.class.getResourceAsStream(
                "/data/buildcraftunofficial/test_instance/" + path + ".json")) {
            if (in == null) return buildcraft.GameTestBatchNames.DEFAULT;
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return buildcraft.GameTestBatchNames.forEnvironment(o.has("environment") ? o.get("environment").getAsString() : null);
        } catch (Exception e) {
            return buildcraft.GameTestBatchNames.DEFAULT;
        }
    }

    // 1.21.1 has no test_instance manifests, so read each test's declared max_ticks straight from the
    // same JSON resource the modern nodes consume, keeping the legacy ctor's watchdog in lock-step with
    // the per-test timeouts (the old hard-coded 100 guillotined longer tests like the overheat hold).
    private static int readMaxTicks(String id, int fallback) {
        String path = id.substring(id.indexOf(':') + 1);
        try (java.io.InputStream in = NeoForgeGameTestHooks.class.getResourceAsStream(
                "/data/buildcraftunofficial/test_instance/" + path + ".json")) {
            if (in == null) return fallback;
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("max_ticks") ? o.get("max_ticks").getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }*/
    //?}
}
