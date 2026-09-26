/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.fluid;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/**
 * Pins how tanks, gauges, pipes and shards find a fluid's texture: they ask the fluid ({@link FluidSprites}), never
 * build a sprite name from its registry id. The old guess ({@code <namespace>:block/<path>_still}) drew every modded
 * fluid whose texture lives elsewhere (Mekanism's {@code mekanism:liquid/liquid}, for one) as the missing texture.
 *
 * <p>This is a source guard, not a render test: unit and game tests both boot as {@code DEDICATED_SERVER}, so no
 * client fluid extension or fluid model exists to query. The render result itself is verified in-client.
 */
public class FluidSpritesGuardTester {

    private static final String LOOKUP = "buildcraft/lib/client/render/fluid/FluidSprites.java";

    /** Declares BuildCraft's own fluids' textures; naming them is its job. */
    private static final Set<String> DECLARES_FLUID_TEXTURES = Set.of(
        "buildcraft/energy/client/BCEnergyFluidsClient.java");

    /** A string literal naming a still-fluid sprite, e.g. {@code "block/water_still"} or {@code + "_still"}. */
    private static final Pattern STILL_SPRITE_LITERAL = Pattern.compile("\"[^\"\\n]*_still\"");

    private static MainSourceSet.SourceFile file(String relativePath) {
        return MainSourceSet.javaFiles().stream()
            .filter(f -> f.relativePath().equals(relativePath))
            .findFirst()
            .orElseThrow(() -> new AssertionError(relativePath + " is missing from this node's main sources"));
    }

    /** The lookup reads the fluid's own client data, stack-aware, through the API this node has. */
    @Test
    public void lookupAsksTheFluidForItsStackAwareTextureAndTint() {
        String code = MainSourceSet.codeOnly(file(LOOKUP).text());
        boolean clientExtensions = code.contains("getStillTexture(stack") && code.contains("getTintColor(stack");
        boolean fluidModels = code.contains("getFluidStateModelSet(") && code.contains("stillMaterial()")
            && code.contains("colorAsStack(stack");
        Assertions.assertTrue(clientExtensions ^ fluidModels,
            "FluidSprites must resolve through IClientFluidTypeExtensions (below 26.1) or the FluidModel set (26.1+), "
                + "passing the stack; found neither or both live on this node");
    }

    /** The lookup names no texture at all — not even a fallback — so no fluid can be special-cased back into a guess.
     *  Its fallback asks water through the same API. */
    @Test
    public void lookupNamesNoTextures() {
        String code = MainSourceSet.codeOnly(file(LOOKUP).text());
        Assertions.assertFalse(code.contains("\"\""), "FluidSprites must not contain string literals");
        Assertions.assertFalse(code.contains("FLUID_TYPES"), "FluidSprites must not derive sprites from registry ids");
    }

    /** Nothing else hand-builds a still-fluid sprite name either (comments excepted). */
    @Test
    public void noOtherCodeBuildsStillSpriteNames() {
        List<MainSourceSet.SourceFile> files = MainSourceSet.javaFiles();
        Assertions.assertTrue(files.size() > 500, "expected this node's main sources, found " + files.size());
        List<String> offenders = files.stream()
            .filter(f -> !DECLARES_FLUID_TEXTURES.contains(f.relativePath()))
            .filter(f -> f.text().lines()
                .map(String::strip)
                .filter(l -> !l.startsWith("//") && !l.startsWith("*") && !l.startsWith("/*"))
                .anyMatch(l -> STILL_SPRITE_LITERAL.matcher(l).find()))
            .map(MainSourceSet.SourceFile::relativePath)
            .toList();
        Assertions.assertEquals(List.of(), offenders, "ask FluidSprites for a fluid's texture instead of naming one");
    }

    /** FluidUtilBC is common code; the client-only sprite and tint lookups live in FluidSprites. */
    @Test
    public void commonFluidUtilHasNoClientLookups() {
        String code = MainSourceSet.codeOnly(file("buildcraft/lib/misc/FluidUtilBC.java").text());
        for (String clientOnly : List.of("getFluidTexture", "getFluidColor", "Minecraft", "IClientFluidTypeExtensions")) {
            Assertions.assertFalse(code.contains(clientOnly), "FluidUtilBC must not reference " + clientOnly);
        }
    }
}
