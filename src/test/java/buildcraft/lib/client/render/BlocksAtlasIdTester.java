/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.client.renderer.texture.TextureAtlas;

import buildcraft.lib.test.MainSourceSet;

/**
 * Pins {@link BCLibRenderTypes#BLOCKS_ATLAS_ID}, the one shared name for the blocks-atlas texture id, which
 * replaced ~60 reads of vanilla's deprecated {@code TextureAtlas.LOCATION_BLOCKS}.
 */
public class BlocksAtlasIdTester {

    /** Files that keep the vanilla constant on purpose. SpriteHolderRegistry's atlas lookup order holds vanilla's own
     *  atlas ids (blocks next to items, which has no BuildCraft twin), so a vanilla path change reaches it directly. */
    private static final Set<String> KEEPS_VANILLA_CONSTANT = Set.of(
        "buildcraft/lib/client/sprite/SpriteHolderRegistry.java");

    private static final Pattern VANILLA_CONSTANT = Pattern.compile("\\bLOCATION_BLOCKS\\b");

    /** The literal must stay the texture id vanilla binds for the blocks atlas on THIS node — if Mojang ever moves
     *  it, every BuildCraft blocks-atlas render would silently bind the missing texture instead. */
    @Test
    @SuppressWarnings("deprecation") // comparing against the deprecated vanilla constant is the point of the test
    public void sharedIdIsTheBlocksAtlasTextureId() {
        Assertions.assertEquals(TextureAtlas.LOCATION_BLOCKS, BCLibRenderTypes.BLOCKS_ATLAS_ID);
    }

    /** New render code reaches for the shared id, not the deprecated vanilla constant (comments and literals blanked,
     *  so prose, strings and Stonecutter's commented-out branches for other lines do not count). */
    @Test
    public void onlyTheDeliberateExceptionReadsTheVanillaConstant() {
        List<MainSourceSet.SourceFile> files = MainSourceSet.javaFiles();
        Assertions.assertTrue(files.size() > 500, "expected this node's main sources, found " + files.size());
        List<String> offenders = files.stream()
            .filter(f -> !KEEPS_VANILLA_CONSTANT.contains(f.relativePath()))
            .filter(f -> VANILLA_CONSTANT.matcher(MainSourceSet.codeOnly(f.text())).find())
            .map(MainSourceSet.SourceFile::relativePath)
            .toList();
        Assertions.assertEquals(List.of(), offenders,
            "use BCLibRenderTypes.BLOCKS_ATLAS_ID (or the sprite's own SpriteHolder#getAtlasLocation()) "
                + "instead of the deprecated TextureAtlas.LOCATION_BLOCKS");
    }
}
