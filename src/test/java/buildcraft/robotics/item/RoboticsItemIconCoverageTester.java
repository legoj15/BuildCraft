/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;

/**
 * Pins the per-board item icons to {@link RoboticsItemVariants}, from both directions at once.
 *
 * <p>7.1.x gave every robot and board stack the icon its board kind asked for; the port dispatches on
 * the board id through two node-dependent mechanisms that must never disagree with each other or with
 * the shared mapping: the {@code items/*.json} condition chains (every node from 1.21.10 up) and the
 * classic model {@code overrides} fed by the {@code buildcraftunofficial:board} item property
 * (1.21.1, where item definitions do not exist). A board registered without an icon entry, a JSON file
 * that drifted from the map, a renamed skin, or an overrides list whose predicate values are out of
 * order all fail here instead of shipping a wrong or duplicate icon.
 *
 * <p>The overrides order assertion is load-bearing, not style: 1.21.1's {@code ItemOverrides} bakes the
 * JSON list REVERSED and returns the first match under threshold ({@code value &gt;= predicate})
 * semantics, so with ascending predicates the last-listed matching entry — the greatest predicate at
 * or below the stack's value — wins, which is exactly per-variant selection. Any other order silently
 * hands several variants the wrong icon.
 */
public class RoboticsItemIconCoverageTester {

    private static final String ASSETS = "assets/buildcraftunofficial/";
    private static final String MODELS = ASSETS + "models/item/";
    private static final String TEXTURES = ASSETS + "textures/item/";

    // ── The mapping vs the live registry ───────────────────────────────────

    @Test
    public void variantMapsCoverExactlyTheRegisteredBoards() {
        Set<String> live = liveBoardIds();
        Assertions.assertEquals(live, RoboticsItemVariants.robotVariants().keySet(),
                "every registered board needs a robot icon entry, and no phantom entries");
        Assertions.assertEquals(live, RoboticsItemVariants.boardTiers().keySet(),
                "every registered board needs a board tier entry, and no phantom entries");
    }

    // ── 1.21.10+ path: the items/*.json condition chains ───────────────────

    @Test
    public void robotItemDefinitionDispatchesEveryBoard() {
        Map<String, String> branches = new LinkedHashMap<>();
        String[] fallback = new String[1];
        collectDefinitionChain(readAsset(ASSETS + "items/robot.json").getAsJsonObject("model"),
                branches, fallback);

        // The empty board needs no branch: the base chassis IS the fallback, and 7.1.x drew unknown
        // boards with the same base skin.
        String base = RoboticsItemVariants.ROBOT_BASE_MODEL;
        Assertions.assertEquals("buildcraftunofficial:item/" + base, fallback[0],
                "the robot definition's fallback model is the bare chassis");
        Assertions.assertFalse(branches.containsKey(emptyBoardId()),
                "the empty board must fall through to the base chassis, not a branch");

        Map<String, String> expected = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : RoboticsItemVariants.robotVariants().entrySet()) {
            if (!e.getKey().equals(emptyBoardId())) {
                expected.put(e.getKey(), "buildcraftunofficial:item/" + base
                        + (e.getValue().isEmpty() ? "" : "_" + e.getValue()));
            }
        }
        Assertions.assertEquals(expected, branches,
                "items/robot.json must branch every non-empty board to its variant chassis model");

        assertModelsExist(branches.values());
    }

    @Test
    public void boardItemDefinitionDispatchesEveryBoard() {
        Map<String, String> branches = new LinkedHashMap<>();
        String[] fallback = new String[1];
        collectDefinitionChain(readAsset(ASSETS + "items/redstone_board.json").getAsJsonObject("model"),
                branches, fallback);

        // No data / unknown id must land on the EMPTY board's clean chip: 1.7.10 resolved every
        // board icon through the registry, whose fallback IS the empty board, so a bare or corrupt
        // stack never had a look of its own.
        Assertions.assertEquals("buildcraftunofficial:item/board_clean", fallback[0],
                "the board definition's fallback is the empty board's clean chip");

        Map<String, String> expected = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : RoboticsItemVariants.boardTiers().entrySet()) {
            expected.put(e.getKey(), "buildcraftunofficial:item/board_" + e.getValue());
        }
        Assertions.assertEquals(expected, branches,
                "items/redstone_board.json must branch every board to its tier colour");

        assertModelsExist(branches.values());
    }

    // ── 1.21.1 path: classic model overrides ───────────────────────────────

    @Test
    public void robotModelOverridesMatchTheVariantMap() {
        Map<Float, String> overrides = collectOverrides(readAsset(MODELS + "robot.json"),
                "the chassis model must carry the 1.21.1 overrides");

        Map<Float, String> expected = new TreeMap<>();
        for (Map.Entry<String, String> e : RoboticsItemVariants.robotVariants().entrySet()) {
            if (!e.getKey().equals(emptyBoardId())) {
                String suffix = e.getValue();
                expected.put(RoboticsItemVariants.robotProperty(e.getKey()),
                        "buildcraftunofficial:item/" + RoboticsItemVariants.ROBOT_BASE_MODEL
                                + (suffix.isEmpty() ? "" : "_" + suffix));
            }
        }
        Assertions.assertEquals(expected, overrides,
                "models/item/robot.json overrides must map robotProperty(board) to each variant chassis");

        assertModelsExist(overrides.values());
    }

    @Test
    public void boardModelOverridesMatchTheTierMap() {
        Map<Float, String> overrides = collectOverrides(readAsset(MODELS + "redstone_board.json"),
                "the board model must carry the 1.21.1 overrides");

        Map<Float, String> expected = new TreeMap<>();
        for (Map.Entry<String, String> e : RoboticsItemVariants.boardTiers().entrySet()) {
            expected.put(RoboticsItemVariants.boardProperty(e.getKey()),
                    "buildcraftunofficial:item/board_" + e.getValue());
        }
        Assertions.assertEquals(expected, overrides,
                "models/item/redstone_board.json overrides must map boardProperty(board) to each tier");

        assertModelsExist(overrides.values());
    }

    // ── The assets behind the mappings ─────────────────────────────────────

    @Test
    public void robotVariantTexturesAndModelsExist() {
        assertAssetExists(MODELS + RoboticsItemVariants.ROBOT_BASE_MODEL + ".json");
        assertAssetExists(TEXTURES + RoboticsItemVariants.ROBOT_BASE_MODEL + ".png");
        for (String suffix : RoboticsItemVariants.robotVariants().values()) {
            if (suffix.isEmpty()) {
                continue;
            }
            assertAssetExists(MODELS + "robot_" + suffix + ".json");
            assertAssetExists(TEXTURES + "robot_" + suffix + ".png");
        }
    }

    @Test
    public void variantModelsBindTheirOwnTextures() {
        // A per-kind model that shipped with another kind's texture ref would render the wrong skin
        // while every existence check stayed green — pin the wiring, not just the files.
        for (String suffix : RoboticsItemVariants.robotVariants().values()) {
            if (suffix.isEmpty()) {
                continue;
            }
            JsonObject textures = readAsset(MODELS + "robot_" + suffix + ".json").getAsJsonObject("textures");
            String expected = "buildcraftunofficial:item/robot_" + suffix;
            Assertions.assertEquals(expected, textures.get("all").getAsString(),
                    "robot_" + suffix + " must bind its own skin as #all");
            Assertions.assertEquals(expected, textures.get("particle").getAsString(),
                    "robot_" + suffix + " must bind its own skin as #particle");
        }
        for (String tier : RoboticsItemVariants.BOARD_TIER_ORDER) {
            JsonObject textures = readAsset(MODELS + "board_" + tier + ".json").getAsJsonObject("textures");
            Assertions.assertEquals("buildcraftunofficial:item/board_" + tier, textures.get("layer0").getAsString(),
                    "board_" + tier + " must bind its own texture as layer0");
        }
    }

    @Test
    public void boardTierTexturesAndModelsExist() {
        for (String tier : RoboticsItemVariants.BOARD_TIER_ORDER) {
            assertAssetExists(MODELS + "board_" + tier + ".json");
            assertAssetExists(TEXTURES + "board_" + tier + ".png");
        }
        assertAssetExists(TEXTURES + RoboticsItemVariants.BOARD_FALLBACK_MODEL + ".png");
    }

    @Test
    public void propertyValuesAreDistinctWithBaseFallbacks() {
        Set<Float> robotValues = new TreeSet<>();
        for (String id : RoboticsItemVariants.robotVariants().keySet()) {
            if (!id.equals(emptyBoardId())) {
                Assertions.assertTrue(robotValues.add(RoboticsItemVariants.robotProperty(id)),
                        "robot property values must be distinct, collision at " + id);
            }
        }
        Assertions.assertEquals(0.0F, RoboticsItemVariants.robotProperty(emptyBoardId()),
                "the empty board reads as the base chassis");
        Assertions.assertEquals(0.0F, RoboticsItemVariants.robotProperty("buildcraftunofficial:not_a_board"),
                "unknown ids read as the base chassis, never a variant");

        // Board values are per-TIER and deliberately shared — 7.1.x kinds sharing a tier shared an
        // icon — so the assertion is that the tiers collapse to exactly the four tier values, not
        // that the twelve boards differ.
        Set<Float> boardValues = new TreeSet<>();
        for (String id : RoboticsItemVariants.boardTiers().keySet()) {
            boardValues.add(RoboticsItemVariants.boardProperty(id));
        }
        Assertions.assertEquals(Set.of(1.0F, 2.0F, 3.0F, 4.0F), boardValues,
                "the board tiers sit at the values 1..4");
        Assertions.assertEquals(1.0F, RoboticsItemVariants.boardProperty("buildcraftunofficial:not_a_board"),
                "unknown and data-less stacks read as the empty board's clean chip, as 7.1.x's "
                        + "registry fallback resolved every unknown to the empty board");
        Assertions.assertEquals(1.0F,
                RoboticsItemVariants.boardProperty(emptyBoardId()),
                "the empty board reads as the first tier (clean)");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String emptyBoardId() {
        for (Map.Entry<String, String> e : RoboticsItemVariants.robotVariants().entrySet()) {
            if (e.getValue().isEmpty()) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("no empty-board entry in the variant map");
    }

    private static Set<String> liveBoardIds() {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        Assertions.assertNotNull(registry, "precondition: the live board registry is booted by the mod load");
        Set<String> ids = new TreeSet<>();
        for (RedstoneBoardNBT<?> nbt : registry.getAllBoardNBTs()) {
            ids.add(nbt.getID());
        }
        Assertions.assertFalse(ids.isEmpty(), "precondition: boards are registered");
        return ids;
    }

    /** Flattens a nested {@code minecraft:condition} chain into id &rarr; {@code on_true} model, with the
     *  terminal {@code minecraft:model} in {@code fallback[0]}. Every condition node is asserted to key on
     *  the stack's CUSTOM_DATA board id — a branch silently retargeted to another component would other-
     *  wise select on the wrong data while this test stayed green. */
    private static void collectDefinitionChain(JsonObject node, Map<String, String> branches, String[] fallback) {
        String type = node.get("type").getAsString();
        if ("minecraft:condition".equals(type)) {
            Assertions.assertEquals("minecraft:component", node.get("property").getAsString(),
                    "conditions must dispatch on the component property");
            Assertions.assertEquals("minecraft:custom_data", node.get("predicate").getAsString(),
                    "conditions must match the custom_data component");
            Assertions.assertTrue(node.getAsJsonObject("value").entrySet().size() == 1
                            && node.getAsJsonObject("value").has("board"),
                    "the matched component shape must be exactly {board:{...}}");
            String id = node.getAsJsonObject("value").getAsJsonObject("board").get("id").getAsString();
            Assertions.assertFalse(branches.containsKey(id), "duplicate condition branch for " + id);
            branches.put(id, node.getAsJsonObject("on_true").get("model").getAsString());
            collectDefinitionChain(node.getAsJsonObject("on_false"), branches, fallback);
        } else if ("minecraft:model".equals(type)) {
            fallback[0] = node.get("model").getAsString();
        } else {
            Assertions.fail("unexpected item-definition node type: " + type);
        }
    }

    /** Reads a classic model's {@code overrides} as value &rarr; model, asserting the single predicate
     *  key and — load-bearing for 1.21.1's first-match-threshold lookup — strictly ascending values. */
    private static Map<Float, String> collectOverrides(JsonObject model, String why) {
        Assertions.assertTrue(model.has("overrides"), why);
        Map<Float, String> out = new LinkedHashMap<>();
        float previous = Float.NEGATIVE_INFINITY;
        for (JsonElement element : model.getAsJsonArray("overrides")) {
            JsonObject override = element.getAsJsonObject();
            JsonObject predicate = override.getAsJsonObject("predicate");
            Assertions.assertEquals(1, predicate.size(), "one predicate per override entry");
            Map.Entry<String, JsonElement> entry = predicate.entrySet().iterator().next();
            Assertions.assertEquals(RoboticsItemVariants.BOARD_PROPERTY, entry.getKey(),
                    "overrides must match the registered item property");
            float value = entry.getValue().getAsFloat();
            Assertions.assertTrue(value > previous,
                    "override predicate values must ascend (" + value + " after " + previous
                            + ") — 1.21.1 tests the reversed list first-match");
            previous = value;
            out.put(value, override.get("model").getAsString());
        }
        Assertions.assertFalse(out.isEmpty(), why);
        return out;
    }

    /** Every referenced model id resolves to a real model JSON. */
    private static void assertModelsExist(Iterable<String> modelIds) {
        for (String modelId : modelIds) {
            int colon = modelId.indexOf(':');
            Assertions.assertTrue(colon > 0, "model refs must be namespaced: " + modelId);
            assertAssetExists(ASSETS + "models/" + modelId.substring(colon + 1) + ".json");
        }
    }

    private static void assertAssetExists(String relativePath) {
        Assertions.assertTrue(locate(relativePath) != null,
                "missing asset " + relativePath + " (referenced by the robotics icon dispatch)");
    }

    /** Opens an asset: classpath first (the mod's main resources are on the test classpath), then the
     *  filesystem walking up to the repo root — tests run from a Stonecutter node directory
     *  ({@code versions/<id>}). Null when found nowhere. */
    private static InputStream open(String relativePath) {
        InputStream in = RoboticsItemIconCoverageTester.class.getClassLoader().getResourceAsStream(relativePath);
        if (in != null) {
            return in;
        }
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources").resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.newInputStream(candidate);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            dir = dir.getParent();
        }
        return null;
    }

    /** Classpath or repo-relative path of an asset, for existence checks only. */
    private static Path locate(String relativePath) {
        if (RoboticsItemIconCoverageTester.class.getClassLoader().getResource(relativePath) != null) {
            return Paths.get(relativePath);
        }
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources").resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static JsonObject readAsset(String relativePath) {
        try (InputStream in = open(relativePath)) {
            if (in == null) {
                throw new AssertionError(
                        "cannot locate " + relativePath + " on the classpath or under src/main/resources");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
