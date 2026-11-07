/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.sprite;

import com.mojang.blaze3d.platform.NativeImage;

//? if <1.21.10 {
/*import net.minecraft.client.renderer.texture.TextureAtlasSprite;
*///?}

/** Version-neutral ARGB pixel access for {@link NativeImage} (and, on 1.21.1, {@code TextureAtlasSprite}).
 *
 * <p>On 1.21.10+ {@code NativeImage.getPixel(x,y)} / {@code setPixel(x,y,argb)} already work in <b>ARGB</b>
 * (they swizzle to/from the native ABGR storage internally). On 1.21.1 the only accessors are
 * {@code getPixelRGBA}/{@code setPixelRGBA}, which return/accept the <b>raw ABGR</b> int — so reusing the
 * 1.21.10+ ARGB-assuming callers verbatim would silently swap the red and blue channels. These helpers
 * keep every caller in ARGB on both nodes: identity on modern, an explicit ABGR&lt;-&gt;ARGB swizzle on 1.21.1. */
public final class NativeImagePixels {
    private NativeImagePixels() {}

    //? if <1.21.10 {
    /*/^** 1.21.1 only (its one caller is the fragile shard's {@code ItemColor}; later lines tint the shard in its model).
     * @return the pixel at (x,y) of one frame of {@code sprite} as 0xAARRGGBB: the platform's sprite accessor hands
     * back the raw ABGR int there. {@code frameIndex} is the cell index into the sprite sheet (row-major,
     * {@code index % cellsPerRow}), not a position in the animation's frame order, so {@code 0} is always the
     * top-left cell. *^/
    public static int getSpriteArgb(TextureAtlasSprite sprite, int frameIndex, int x, int y) {
        return swizzle(buildcraft.platform.client.ClientPlatform.extensions().spritePixel(sprite, frameIndex, x, y));
    }*/
    //?}

    /** @return the pixel at (x,y) as 0xAARRGGBB. */
    public static int getArgb(NativeImage img, int x, int y) {
        //? if >=1.21.10 {
        return img.getPixel(x, y);
        //?} else {
        /*return swizzle(img.getPixelRGBA(x, y));*/
        //?}
    }

    /** Sets the pixel at (x,y) from a 0xAARRGGBB value. */
    public static void setArgb(NativeImage img, int x, int y, int argb) {
        //? if >=1.21.10 {
        img.setPixel(x, y, argb);
        //?} else {
        /*img.setPixelRGBA(x, y, swizzle(argb));*/
        //?}
    }

    //? if <1.21.10 {
    /*// ABGR (0xAABBGGRR) <-> ARGB (0xAARRGGBB): keep alpha + green, swap red and blue. Symmetric.
    private static int swizzle(int c) {
        return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
    }*/
    //?}
}
