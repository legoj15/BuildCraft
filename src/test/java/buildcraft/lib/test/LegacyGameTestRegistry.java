/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

//? if <1.21.10 {
/*import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

import buildcraft.BuildCraftGameTests;
import buildcraft.GameTestBatchNames;

/^*
 * Every BuildCraft game test, into 1.21.1's {@code GameTestRegistry}: that line has no dynamic {@code TEST_FUNCTION}
 * registry and no test_instance manifests, so each test of {@link BuildCraftGameTests#registerAll} becomes a
 * {@code TestFunction} against the {@code buildcraftunofficial:empty} arena
 * (data/buildcraftunofficial/structure/empty.nbt: a 16x16x16 air arena standing in for the 1.21.5+
 * {@code minecraft:empty} structure, with the 1.21.1 DataVersion; the size gives encaseStructure room for the oil-gen
 * surface scan). Both loaders' 1.21.1 hooks call {@link #registerAll}: NeoForge's from {@code RegisterGameTestsEvent},
 * Fabric's from the game-test mod's initializer (Fabric API's runner reads the registry once the server starts).
 *^/
public final class LegacyGameTestRegistry {

    private LegacyGameTestRegistry() {}

    /^*
     * Registers every test, or only those matching {@code -Dbuildcraft.gameTestFilter} ({@code -PbcTests=<glob>}).
     * 1.21.1's server has no {@code --tests} option and its runner takes every function already in the registry, so
     * the filter acts here, with the modern selector's matching (ResourceSelectorArgument): a pattern without a colon
     * gets "minecraft:", then a case-sensitive commons-io wildcard match ({@code *}/{@code ?}) against the full id.
     *^/
    public static void registerAll() {
        String filter = System.getProperty("buildcraft.gameTestFilter");
        if (filter == null || filter.isBlank()) {
            BuildCraftGameTests.registerAll(LegacyGameTestRegistry::add);
            return;
        }
        String pattern = filter.strip();
        if (!pattern.contains(":")) pattern = "minecraft:" + pattern;
        final String glob = pattern;
        AtomicInteger selected = new AtomicInteger();
        AtomicInteger total = new AtomicInteger();
        BuildCraftGameTests.registerAll((id, sup) -> {
            total.incrementAndGet();
            if (org.apache.commons.io.FilenameUtils.wildcardMatch(id, glob)) {
                selected.incrementAndGet();
                add(id, sup);
            }
        });
        // WARN so it clears the quiet game-test console mirror.
        org.slf4j.Logger log = com.mojang.logging.LogUtils.getLogger();
        log.warn("BuildCraft game-test filter '{}' selected {} of {} tests", glob, selected.get(), total.get());
        if (selected.get() == 0) {
            log.error("-PbcTests='{}' matched none of the {} BuildCraft game tests; expected a namespace:glob such as "
                + "buildcraftunofficial:painted_pipe_*", filter, total.get());
            // A throw here reaches only the loader's startup, which would report a green run of zero tests for a
            // mistyped glob. Exit non-zero so the task fails like a failing game test does.
            System.exit(1);
        }
    }

    // GameTestRegistry.TEST_FUNCTIONS is a private mutable collection the runner reads through getAllTestFunctions();
    // the function is built from the same (id, supplier) the modern path registers and added directly (dev/CI only).
    private static void add(String id, Supplier<Consumer<GameTestHelper>> sup) {
        try {
            Field f = GameTestRegistry.class.getDeclaredField("TEST_FUNCTIONS");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Collection<TestFunction> functions = (Collection<TestFunction>) f.get(null);
            // skyAccess=true (11th arg) so StructureUtils.encaseStructure omits the BARRIER CEILING (it always builds
            // walls, but adds a ceiling only when !skyAccess). Without it the oil-gen surface scan
            // (findSolidSurfaceTop, top-down) hits the ceiling instead of the test floor, and spring tests can't place
            // fluid above. Full ctor: batch, name, structure, rotation, maxTicks, setupTicks, required, manualOnly,
            // maxAttempts, requiredSuccesses, skyAccess, fn.
            functions.add(new TestFunction(readBatch(id), id, "buildcraftunofficial:empty", Rotation.NONE,
                readMaxTicks(id, 100), 0L, true, false, 1, 1, true, sup.get()));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register 1.21.1 game test " + id, e);
        }
    }

    // 1.21.1 has no test_environment either, but its batch NAME plays the same role (tests of one batch run
    // concurrently, batches run one after another), so a manifest's "environment" becomes the batch name. Without
    // this every test, the marker tests included, would share "defaultBatch" and run concurrently in neighbouring
    // arenas, which is exactly the crosstalk the per-test marker environments exist to prevent (see MarkerTester).
    // minecraft:default / no environment stay in the shared default batch.
    private static String readBatch(String id) {
        JsonObject manifest = manifest(id);
        return GameTestBatchNames.forEnvironment(manifest != null && manifest.has("environment")
            ? manifest.get("environment").getAsString() : null);
    }

    // Each test's declared max_ticks, read from the same manifest the modern nodes consume, so the legacy ctor's
    // watchdog keeps in step with the per-test timeouts (a hard-coded 100 guillotined longer tests like the overheat
    // hold).
    private static int readMaxTicks(String id, int fallback) {
        JsonObject manifest = manifest(id);
        return manifest != null && manifest.has("max_ticks") ? manifest.get("max_ticks").getAsInt() : fallback;
    }

    private static JsonObject manifest(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        try (InputStream in = LegacyGameTestRegistry.class.getResourceAsStream(
                "/data/buildcraftunofficial/test_instance/" + path + ".json")) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
*///?}
