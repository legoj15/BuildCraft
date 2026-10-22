/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
//? if <1.21.10 {
/*import net.minecraft.server.packs.resources.ResourceMetadata;
*///?}

import buildcraft.lib.client.sprite.NativeImagePixels;

/** The shard tint is the average colour of the fluid's sprite. The sprite accessor hands back ARGB on 1.21.10+ and
 *  raw ABGR on 1.21.1, so a decode that ignores which node it is on swaps red and blue (lava went blue). These
 *  tests drive a real sprite built from a {@link NativeImage}, so they pin the accessor and the decode together. */
public class FluidShardTintSourceTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    /** A bare sprite over {@code image}: just enough atlas geometry to construct it, no GPU involved. */
    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(NativeImage image) {
            super(Identifier.fromNamespaceAndPath("buildcraft", "test_atlas"),
                new SpriteContents(Identifier.fromNamespaceAndPath("buildcraft", "test_sprite"),
                    new FrameSize(image.getWidth(), image.getHeight()), image
                    //? if <1.21.10 {
                    /*, ResourceMetadata.EMPTY
                    *///?}
                ),
                image.getWidth(), image.getHeight(), 0, 0
                //? if >=1.21.11 {
                , 0
                //?}
            );
        }
    }

    private static TestSprite solidSprite(int argb) {
        NativeImage image = new NativeImage(2, 2, false);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                NativeImagePixels.setArgb(image, x, y, argb);
            }
        }
        return new TestSprite(image);
    }

    /** The tint a shard would take from a sprite that is {@code argb} everywhere. */
    private static int tintOfSolidSprite(int argb) {
        TestSprite sprite = solidSprite(argb);
        try {
            return FluidShardTintSource.averageSpriteColor(sprite);
        } finally {
            sprite.contents().close(); // frees the native image; the sprite itself is not AutoCloseable on every node
        }
    }

    @Test
    public void opaqueRedSpriteTintsRedNotBlue() {
        assertEquals(RED, tintOfSolidSprite(RED));
    }

    @Test
    public void opaqueBlueSpriteTintsBlueNotRed() {
        assertEquals(BLUE, tintOfSolidSprite(BLUE));
    }

    @Test
    public void mostlyTransparentPixelsAreSkippedAndAllTransparentIsWhite() {
        // One opaque green pixel and three clear ones: the clear ones carry red that must not leak in.
        assertEquals(0xFF00FF00, FluidShardTintSource.averageArgb(2, 2,
            (x, y) -> x == 0 && y == 0 ? 0xFF00FF00 : 0x00FF0000));
        assertEquals(0xFFFFFFFF, FluidShardTintSource.averageArgb(2, 2, (x, y) -> 0x00FF0000));
    }
}
