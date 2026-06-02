/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

//? if >=26.1 {
import net.minecraft.util.LightCoordsUtil;
//?} else {
/*import net.minecraft.client.renderer.LightTexture;*/
//?}

/**
 * Isolates the packed-light API cliff so render code stays version-agnostic.
 *
 * <p>26.1 introduced {@code net.minecraft.util.LightCoordsUtil} for light packing and renamed
 * {@code LevelRenderer.getLightColor} → {@code getLightCoords}; pre-CalVer (1.21.x) uses the
 * classic {@code LightTexture} + {@code LevelRenderer.getLightColor}. The packing convention
 * ({@code block << 4 | sky << 20}) and the {@code FULL_BRIGHT} value (15728880) are identical
 * across both lines — only the class/method names differ — so callers route through this util and
 * the {@code //? if} directives live here alone (durable for future 1.21.1 / 26.2 nodes).
 */
public final class LightUtil {
    private LightUtil() {}

    //? if >=26.1 {
    public static final int FULL_BRIGHT = LightCoordsUtil.FULL_BRIGHT;
    //?} else {
    /*public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;*/
    //?}

    /** Pack block + sky light levels into a single coords int ({@code block << 4 | sky << 20}). */
    public static int pack(int blockLight, int skyLight) {
        //? if >=26.1 {
        return LightCoordsUtil.pack(blockLight, skyLight);
        //?} else {
        /*return LightTexture.pack(blockLight, skyLight);*/
        //?}
    }

    /** Packed light coords for a world position (a {@link Level} satisfies both lines' getter type). */
    public static int getLightCoords(Level level, BlockPos pos) {
        //? if >=26.1 {
        return LevelRenderer.getLightCoords(level, pos);
        //?} else {
        /*return LevelRenderer.getLightColor(level, pos);*/
        //?}
    }
}
