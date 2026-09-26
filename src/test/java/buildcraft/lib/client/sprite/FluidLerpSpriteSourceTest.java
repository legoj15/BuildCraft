/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.sprite;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.energy.BCEnergyFluids;

/**
 * Pins {@link FluidLerpSpriteSource}: the per-channel intensity lerp that reproduces the historical
 * pre-baked fluid PNGs from the grayscale heat base, the per-heat animation timing that is the only
 * difference between a fluid's three heat-tier sprites, and the atlas config that feeds it.
 *
 * <p>The recolour expectations were verified bit-exact against the deleted
 * {@code <fluid>_heat_0_<frame>.png} files (0 error across all 8192 pixels of every fluid).
 */
public class FluidLerpSpriteSourceTest {

    /** First pixel of the heat base texture: grayscale 88, opaque. */
    private static final int HEAT_BASE_88 = 0xFF585858;

    private static final String BLOCKS_ATLAS = "src/main/resources/assets/minecraft/atlases/blocks.json";

    @Test
    public void testRecolourMatchesBakedFluidPixels() {
        // oil: light 0x505050, dark 0x050505 — (88,88,88) recolors to (30,30,30).
        Assertions.assertEquals(
            0xFF1E1E1E, FluidLerpSpriteSource.recolour(HEAT_BASE_88, 0x505050, 0x050505));

        // fuel_dense: light 0xFFAF3F, dark 0xE07F00 — (88,88,88) recolors to (234,143,21).
        Assertions.assertEquals(
            0xFFEA8F15, FluidLerpSpriteSource.recolour(HEAT_BASE_88, 0xFFAF3F, 0xE07F00));
    }

    @Test
    public void testRecolourEndpointsAndAlpha() {
        // A fully-dark base pixel maps exactly onto the fluid's dark endpoint.
        Assertions.assertEquals(
            0xFFE07F00, FluidLerpSpriteSource.recolour(0xFF000000, 0xFFAF3F, 0xE07F00));

        // Input alpha is discarded — output is always opaque even from a transparent base.
        Assertions.assertEquals(
            0xFF050505, FluidLerpSpriteSource.recolour(0x00000000, 0x505050, 0x050505));
    }

    /**
     * The hotter a fluid, the faster its texture animates — the only visual cue separating a fluid's
     * three heat tiers, whose pixels are identical. This is why each fluid needs three animated atlas
     * sprites rather than one: a single atlas region can only show one frame at a time. Collapsing
     * them would silently flatten every tier to one speed.
     */
    @Test
    public void testHeatTiersAnimateAtDistinctSpeeds() {
        Assertions.assertEquals(3, FluidLerpSpriteSource.HEAT_TIERS, "one sprite per 1.12.2 heat tier");
        Assertions.assertEquals(3, FluidLerpSpriteSource.frametime(0), "cool steps every 3 ticks");
        Assertions.assertEquals(2, FluidLerpSpriteSource.frametime(1), "hot steps every 2 ticks");
        Assertions.assertEquals(1, FluidLerpSpriteSource.frametime(2), "searing steps every tick");

        // Every registered fluid variant's heat must have a tier sprite.
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            Assertions.assertTrue(entry.heat() >= 0 && entry.heat() < FluidLerpSpriteSource.HEAT_TIERS,
                entry.name() + " has heat " + entry.heat() + " with no matching sprite tier");
        }
    }

    /**
     * Matches upstream 1.12.2's {@code heat_<n>_<frame>.png.mcmeta}: the two slower tiers blend
     * between frames, the searing tier does not. A one-tick frame has no in-between ticks to blend,
     * so interpolating it only allocates blend buffers (1.21.1/1.21.10) or picks the costlier
     * two-texture animation shader (1.21.11+) for an identical picture.
     */
    @Test
    public void testOnlyMultiTickFramesInterpolate() {
        Assertions.assertTrue(FluidLerpSpriteSource.interpolates(FluidLerpSpriteSource.frametime(0)));
        Assertions.assertTrue(FluidLerpSpriteSource.interpolates(FluidLerpSpriteSource.frametime(1)));
        Assertions.assertFalse(FluidLerpSpriteSource.interpolates(FluidLerpSpriteSource.frametime(2)));
    }

    /**
     * The blocks-atlas {@code fluid_lerp} entries are a hand-kept copy of
     * {@code BCEnergyFluids#FLUID_DATA}'s colours. A missing entry renders that fluid as the missing
     * texture; a mistyped colour silently recolours it. Every base fluid needs exactly one still and
     * one flow entry, decoding through the real codec, with the fluid table's light/dark endpoints.
     */
    @Test
    public void testAtlasEntriesMatchFluidTable() throws IOException {
        Map<String, FluidLerpSpriteSource> byOutputAndFrame = new HashMap<>();
        for (JsonElement el : atlasSources()) {
            JsonObject src = el.getAsJsonObject();
            if (!FluidLerpSpriteSource.ID.toString().equals(src.get("type").getAsString())) continue;
            FluidLerpSpriteSource decoded = FluidLerpSpriteSource.MAP_CODEC.codec()
                .parse(JsonOps.INSTANCE, src)
                .getOrThrow(msg -> new AssertionError("fluid_lerp entry does not decode: " + msg + " in " + src));
            String key = decoded.output().getPath() + "#" + decoded.frame();
            Assertions.assertNull(byOutputAndFrame.put(key, decoded), "duplicate fluid_lerp entry " + key);
        }

        List<String> checked = new ArrayList<>();
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            if (entry.heat() != 0) continue; // one entry pair per base fluid emits all three tiers
            for (String frame : List.of("still", "flow")) {
                String key = "block/fluids/" + entry.baseName() + "#" + frame;
                FluidLerpSpriteSource src = byOutputAndFrame.remove(key);
                Assertions.assertNotNull(src, "no fluid_lerp atlas entry for " + key);
                Assertions.assertEquals("buildcraftunofficial", src.output().getNamespace(), key);
                Assertions.assertEquals("block/fluids/heat_" + frame, src.source().getPath(), key + " source");
                Assertions.assertEquals(entry.texLight(), src.light(), key + " light");
                Assertions.assertEquals(entry.texDark(), src.dark(), key + " dark");
                checked.add(key);
            }
        }
        Assertions.assertEquals(BCEnergyFluids.BASE_NAMES.size() * 2, checked.size(), "one still + one flow per base fluid");
        Assertions.assertTrue(byOutputAndFrame.isEmpty(), "fluid_lerp entries for no registered fluid: " + byOutputAndFrame.keySet());
    }

    private static Iterable<JsonElement> atlasSources() throws IOException {
        try (Reader in = Files.newBufferedReader(repoRoot().resolve(BLOCKS_ATLAS), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(in, JsonObject.class).getAsJsonArray("sources");
        }
    }

    /** Walks up from the working directory to the repo root — tests run from a Stonecutter node
     * directory, and the classpath also carries vanilla's own {@code atlases/blocks.json}. */
    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve(BLOCKS_ATLAS))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root from " + Paths.get("").toAbsolutePath());
    }
}
