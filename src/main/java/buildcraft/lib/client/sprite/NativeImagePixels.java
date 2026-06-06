/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.sprite;

import com.mojang.blaze3d.platform.NativeImage;

/** Version-neutral ARGB pixel access for {@link NativeImage}.
 *
 * <p>On 1.21.10+ {@code NativeImage.getPixel(x,y)} / {@code setPixel(x,y,argb)} already work in <b>ARGB</b>
 * (they swizzle to/from the native ABGR storage internally). On 1.21.1 the only accessors are
 * {@code getPixelRGBA}/{@code setPixelRGBA}, which return/accept the <b>raw ABGR</b> int — so reusing the
 * 1.21.10+ ARGB-assuming callers verbatim would silently swap the red and blue channels. These helpers
 * keep every caller in ARGB on both nodes: identity on modern, an explicit ABGR&lt;-&gt;ARGB swizzle on 1.21.1. */
public final class NativeImagePixels {
    private NativeImagePixels() {}

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
