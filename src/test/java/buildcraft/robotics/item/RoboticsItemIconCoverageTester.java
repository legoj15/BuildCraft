/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.awt.image.BufferedImage;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;

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
 *
 * <p>The icon geometry itself lives in the shared parent {@code robot_chassis_base} (the 8x8x8 chassis
 * cube plus the 7.1.x eye/port decal pass and the display section restoring 7.1.x's apparent icon
 * sizes); every chassis model parents it and only rebinds textures. The four hand contexts must stay
 * ABSENT there — inheriting {@code block/block}'s IS the 1.7.10 hand parity — so their absence is
 * pinned too. The decals are the stack's charge readout: 7.1.x's own overlay art
 * ({@code robot_overlay_side.png}, the red eyes, tintindex 0, faded by charge through the
 * {@code buildcraftunofficial:robot_charge} tint source declared on every {@code items/robot.json}
 * model leaf, and {@code robot_overlay_bottom.png}, the pale-cyan under-port, tintindex 1, always on
 * — a vanilla {@code minecraft:constant} there; 1.21.1 tints per item instead, no JSON).
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
                branches, fallback, ROBOT_CHARGE_TINT);

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
                branches, fallback, null);

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
        assertAssetExists(TEXTURES + "robot_overlay_side.png");
        assertAssetExists(TEXTURES + "robot_overlay_bottom.png");
    }

    /** The decal textures must be 7.1.x's overlay art, exact in layout and colour:
     *  {@code robot_overlay_side.png} is exactly the four 2x2 pure-red eye marks (strip rows 11-12,
     *  centred in each 8px panel — the same texels every skin paints its eyes at) and
     *  {@code robot_overlay_bottom.png} exactly the pale-cyan 2x2 under-port. A stray opaque texel
     *  would smear the charge tint onto the body; a recolour would stop being the classic decal
     *  the tint multiplies. */
    @Test
    public void robotDecalTexturesAreTheSevenOneXOverlayArt() throws IOException {
        Set<Long> eyes = new TreeSet<>();
        for (int y = 11; y <= 12; y++) {
            for (int x : new int[] {3, 4, 11, 12, 19, 20, 27, 28}) {
                eyes.add(key(x, y));
            }
        }
        assertDecalArt("robot_overlay_side.png", eyes, 0xFFFF0000,
                "the eye decal must be 7.1.x's overlay_side art: exactly the four 2x2 pure-red eye marks");

        Set<Long> port = new TreeSet<>();
        for (int y = 3; y <= 4; y++) {
            for (int x = 11; x <= 12; x++) {
                port.add(key(x, y));
            }
        }
        assertDecalArt("robot_overlay_bottom.png", port, 0xFF9DFFFF,
                "the under-port must be 7.1.x's overlay_bottom art: exactly the pale-cyan 2x2 port mark");
    }

    /** Pins one decal texture: 32x32 like the skins, opaque on exactly the expected texels, and
     *  every opaque texel exactly the expected colour — the tint multiplies, so the art's colour
     *  IS the rendered colour at full charge. */
    private static void assertDecalArt(String fileName, Set<Long> expectedOpaque, int expectedArgb,
            String message) throws IOException {
        try (InputStream in = open(TEXTURES + fileName)) {
            Assertions.assertNotNull(in, "missing " + TEXTURES + fileName);
            BufferedImage image = ImageIO.read(in);
            Assertions.assertEquals(32, image.getWidth(), fileName + " must share the skins' 32x32 net");
            Assertions.assertEquals(32, image.getHeight(), fileName + " must share the skins' 32x32 net");

            Set<Long> opaque = new TreeSet<>();
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) == 0) {
                        continue;
                    }
                    opaque.add(key(x, y));
                    Assertions.assertEquals(expectedArgb, argb,
                            fileName + " texel " + x + "," + y + " drifted from the 7.1.x art");
                }
            }
            Assertions.assertEquals(expectedOpaque, opaque, message);
        }
    }

    private static long key(int x, int y) {
        return ((long) y << 32) | x;
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

    // ── The shared chassis/display parent ─────────────────────────────────

    @Test
    public void robotChassisBaseCarriesTheSharedDisplay() {
        JsonObject model = readAsset(MODELS + "robot_chassis_base.json");
        JsonArray elements = model.getAsJsonArray("elements");
        Assertions.assertEquals(2, elements.size(),
                "robot_chassis_base must carry exactly the chassis cube and the eye/port decal overlay");

        JsonObject cube = elements.get(0).getAsJsonObject();
        assertElementVec(cube, "from", new double[] {4, 4, 4});
        assertElementVec(cube, "to", new double[] {12, 12, 12});
        Assertions.assertTrue(cube.get("shade").getAsBoolean(),
                "the chassis cube must stay shaded — only 7.1.x's decal pass drew unlit");
        Map<String, String> bodyTextures = new LinkedHashMap<>();
        for (String face : CHASSIS_FACE_UVS.keySet()) {
            bodyTextures.put(face, "#all");
        }
        assertElementFaces(cube, bodyTextures, Map.of(), "chassis cube");

        JsonObject decals = elements.get(1).getAsJsonObject();
        assertElementVec(decals, "from", new double[] {3.99, 3.99, 3.99});
        assertElementVec(decals, "to", new double[] {12.01, 12.01, 12.01});
        Assertions.assertFalse(decals.get("shade").getAsBoolean(),
                "the decal pass must be unshaded — 1.7.10 drew it with lighting disabled, and a "
                        + "charge eye that dims with the face it sits on stops reading as a charge eye");
        Map<String, String> decalTextures = new LinkedHashMap<>();
        Map<String, Integer> decalTints = new LinkedHashMap<>();
        for (String face : CHASSIS_FACE_UVS.keySet()) {
            boolean port = "down".equals(face);
            decalTextures.put(face, port ? "#port" : "#eye");
            decalTints.put(face, port ? 1 : 0);
        }
        assertElementFaces(decals, decalTextures, decalTints, "eye/port decal overlay");

        JsonObject textures = model.getAsJsonObject("textures");
        Assertions.assertEquals("buildcraftunofficial:item/robot_overlay_side", textures.get("eye").getAsString(),
                "robot_chassis_base must bind the 7.1.x eye decal as #eye (children inherit it)");
        Assertions.assertEquals("buildcraftunofficial:item/robot_overlay_bottom", textures.get("port").getAsString(),
                "robot_chassis_base must bind the 7.1.x under-port decal as #port (children inherit it)");

        JsonObject display = model.getAsJsonObject("display");
        assertTransform(display, "gui",
                new double[] {30, 225, 0}, new double[] {0, 0, 0}, new double[] {1, 1, 1});
        assertTransform(display, "ground",
                new double[] {0, 0, 0}, new double[] {0, 2.2, 0}, new double[] {0.3, 0.3, 0.3});
        assertTransform(display, "fixed",
                new double[] {0, 0, 0}, new double[] {0, 0, 0}, new double[] {1, 1, 1});

        // Deliberately absent: block/block's firstperson 0.40 / thirdperson 0.375 equal 1.7.10's
        // vanilla surround scales (0.5 * 0.40 and 0.5 * 0.375), so inheriting the hand contexts IS
        // the hand parity. Display inheritance resolves per context up the parent chain, so a hand
        // key defined here would freeze the hands to this file and lose that parity.
        for (String hand : HAND_CONTEXTS) {
            Assertions.assertFalse(display.has(hand),
                    "robot_chassis_base must not define " + hand
                            + " — inheriting block/block's IS the 1.7.10 hand parity");
        }
    }

    @Test
    public void everyRobotModelParentsTheChassisBase() {
        String expected = "buildcraftunofficial:item/robot_chassis_base";
        JsonObject base = readAsset(MODELS + RoboticsItemVariants.ROBOT_BASE_MODEL + ".json");
        Assertions.assertEquals(expected, base.get("parent").getAsString(),
                "the bare-chassis fallback model must parent robot_chassis_base");
        Assertions.assertNull(base.getAsJsonArray("elements"),
                "the bare-chassis fallback model must not shadow robot_chassis_base's elements");
        for (String suffix : RoboticsItemVariants.robotVariants().values()) {
            if (suffix.isEmpty()) {
                continue;
            }
            JsonObject model = readAsset(MODELS + "robot_" + suffix + ".json");
            Assertions.assertEquals(expected, model.get("parent").getAsString(),
                    "robot_" + suffix + " must parent robot_chassis_base or it never sees the shared display");
            Assertions.assertNull(model.getAsJsonArray("elements"),
                    "robot_" + suffix + " must not shadow robot_chassis_base's elements");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** The four hand contexts {@code robot_chassis_base} must NOT define — it inherits
     *  {@code block/block}'s, and that inheritance is the 1.7.10 hand parity. */
    private static final String[] HAND_CONTEXTS = {
        "firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"
    };

    /** The tint source id every {@code items/robot.json} model leaf must tint through. */
    private static final String ROBOT_CHARGE_TINT = "buildcraftunofficial:robot_charge";

    /** The skin-net UV table both chassis elements share, face &rarr; [u1, v1, u2, v2]. The overlay's
     *  faces must sample the same rectangles as the cube's or its LEDs land off the painted marks. */
    private static final Map<String, int[]> CHASSIS_FACE_UVS = new LinkedHashMap<>();
    static {
        CHASSIS_FACE_UVS.put("up", new int[] {8, 0, 12, 4});
        CHASSIS_FACE_UVS.put("down", new int[] {4, 0, 8, 4});
        CHASSIS_FACE_UVS.put("north", new int[] {4, 4, 8, 8});
        CHASSIS_FACE_UVS.put("south", new int[] {12, 4, 16, 8});
        CHASSIS_FACE_UVS.put("west", new int[] {8, 4, 12, 8});
        CHASSIS_FACE_UVS.put("east", new int[] {0, 4, 4, 8});
    }

    /** Pins one element's from/to against the exact triple. */
    private static void assertElementVec(JsonObject element, String key, double[] expected) {
        JsonArray values = element.getAsJsonArray(key);
        Assertions.assertEquals(3, values.size(), "element." + key + " must have 3 components");
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(expected[i], values.get(i).getAsDouble(), 1e-6,
                    "element." + key + "[" + i + "] drifted");
        }
    }

    /** Pins one chassis element's six faces against the shared UV table, with per-face texture refs
     *  and tintindexes (a missing tint entry asserts the face carries none — only the decal pass is
     *  tinted; a tinted body would recolor the whole skin per charge). */
    private static void assertElementFaces(JsonObject element, Map<String, String> textures,
            Map<String, Integer> tints, String what) {
        JsonObject faces = element.getAsJsonObject("faces");
        for (Map.Entry<String, int[]> face : CHASSIS_FACE_UVS.entrySet()) {
            String name = face.getKey();
            Assertions.assertTrue(faces.has(name), what + " must define its " + name + " face");
            JsonObject jsonFace = faces.getAsJsonObject(name);
            Assertions.assertEquals(textures.get(name), jsonFace.get("texture").getAsString(),
                    what + "'s " + name + " face must sample " + textures.get(name));
            JsonArray uv = jsonFace.getAsJsonArray("uv");
            Assertions.assertEquals(4, uv.size(), what + "'s " + name + " uv must have 4 components");
            for (int i = 0; i < 4; i++) {
                Assertions.assertEquals(face.getValue()[i], uv.get(i).getAsDouble(), 1e-6,
                        what + "'s " + name + " uv[" + i + "] drifted off the shared skin net");
            }
            Integer tint = tints.get(name);
            if (tint == null) {
                Assertions.assertFalse(jsonFace.has("tintindex"),
                        what + "'s " + name + " face must not carry a tintindex — only the decal pass is tinted");
            } else {
                Assertions.assertEquals(tint.intValue(), jsonFace.get("tintindex").getAsInt(),
                        what + "'s " + name + " face has the wrong tintindex");
            }
        }
    }

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
     *  wise select on the wrong data while this test stayed green. {@code expectedTintSourceId} pins the
     *  two-entry {@code tints} of every {@code minecraft:model} leaf (charge-faded eyes at index 0, the
     *  vanilla constant under-port at index 1); null asserts the leaves carry no tints at all. */
    private static void collectDefinitionChain(JsonObject node, Map<String, String> branches, String[] fallback,
            String expectedTintSourceId) {
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
            collectDefinitionChain(node.getAsJsonObject("on_false"), branches, fallback, expectedTintSourceId);
        } else if ("minecraft:model".equals(type)) {
            if (expectedTintSourceId == null) {
                Assertions.assertFalse(node.has("tints"),
                        "this definition's model leaves must not declare tints");
            } else {
                JsonArray tints = node.getAsJsonArray("tints");
                Assertions.assertEquals(2, tints.size(),
                        "each model leaf needs the charge tint (index 0, the eyes) and the constant "
                                + "port tint (index 1, the always-on under-port)");
                JsonObject charge = tints.get(0).getAsJsonObject();
                Assertions.assertEquals(expectedTintSourceId, charge.get("type").getAsString(),
                        "model leaves must tint through " + expectedTintSourceId
                                + " or the eye decals render untinted");
                Assertions.assertEquals(1, charge.entrySet().size(),
                        "the charge tint entry must be type-only");
                JsonObject constant = tints.get(1).getAsJsonObject();
                Assertions.assertEquals("minecraft:constant", constant.get("type").getAsString(),
                        "the under-port must tint through the vanilla constant — 7.1.x drew it at "
                                + "full alpha whenever the robot was awake");
                Assertions.assertEquals(2, constant.entrySet().size(),
                        "the constant tint entry must be type + value");
                Assertions.assertEquals(16777215, constant.get("value").getAsInt(),
                        "the port tint must be opaque white so the decal art's own pale cyan shows");
            }
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

    /** Asserts one display context matches component-for-component within 1e-6. Every context the
     *  shared parent defines must be fully specified — display inheritance replaces a context
     *  wholesale (no per-field merge), so a scale-only entry would silently reset rotation to 0. */
    private static void assertTransform(JsonObject display, String context,
            double[] rotation, double[] translation, double[] scale) {
        Assertions.assertTrue(display.has(context), "display must define the " + context + " context");
        JsonObject transform = display.getAsJsonObject(context);
        assertVec(context, transform, "rotation", rotation);
        assertVec(context, transform, "translation", translation);
        assertVec(context, transform, "scale", scale);
    }

    private static void assertVec(String context, JsonObject transform, String key, double[] expected) {
        JsonArray values = transform.getAsJsonArray(key);
        Assertions.assertEquals(expected.length, values.size(),
                context + "." + key + " must have " + expected.length + " components");
        for (int i = 0; i < expected.length; i++) {
            Assertions.assertEquals(expected[i], values.get(i).getAsDouble(), 1e-6,
                    context + "." + key + "[" + i + "] drifted");
        }
    }
}
