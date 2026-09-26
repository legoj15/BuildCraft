/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.client;

import com.mojang.serialization.MapCodec;

//? if >=1.21.10 {
import net.minecraft.client.color.item.ItemTintSource;
//?}
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import org.jspecify.annotations.Nullable;

import buildcraft.core.BCCore;
import buildcraft.lib.client.render.fluid.FluidSprites;

/**
 * ItemTintSource for fragile fluid shards. Averages the fluid's still sprite and
 * multiplies in the fluid's tint (both from {@link FluidSprites}) to produce a
 * representative tint color for the grayscale shard overlay texture (layer1).
 */
//? if >=1.21.10 {
public final class FluidShardTintSource implements ItemTintSource {
//?} else {
/*public final class FluidShardTintSource implements net.minecraft.client.color.item.ItemColor {*/
//?}
    public static final FluidShardTintSource INSTANCE = new FluidShardTintSource();
    //? if >=1.21.10 {
    public static final MapCodec<FluidShardTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);
    //?}

    private FluidShardTintSource() {}

    /** Shared colour computation: samples the fluid's representative tint. Both the modern
     *  {@code calculate} and the 1.21.1 {@code getColor} delegate here for the FLUID layer.
     *  The (level, entity) args never affect the result; on 1.21.1 {@code getColor} additionally
     *  guards on tintIndex so only the fluid layer — not the frame/cover — is tinted. */
    private int computeColor(ItemStack stack) {
        SimpleFluidContent content = stack.getOrDefault(BCCore.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY);
        FluidStack fluid = content.copy();
        if (fluid.isEmpty()) {
            return 0xFFFFFFFF;
        }

        // The shard shows what a tank would: the fluid's own sprite times its tint. Many fluids
        // (water, Mekanism's) ship a grayscale sprite and get all their colour from the tint.
        return multiply(averageSpriteColor(FluidSprites.stillSprite(fluid)), FluidSprites.tint(fluid));
    }

    /** Channel-wise product of two RGB colours, fully opaque (a tint's alpha is ignored — some fluids report 0). */
    private static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    //? if >=1.21.10 {
    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        return computeColor(stack);
    }
    //?} else {
    /*@Override
    public int getColor(ItemStack stack, int tintIndex) {
        // DynamicFluidContainerModel emits tintIndex 0 = base (frame), 1 = fluid, 2 = cover.
        // Tint ONLY the fluid layer; leave the frame and cover neutral (white), matching
        // NeoForge's own DynamicFluidContainerModel.Colors and the 26.1 item-model format,
        // which scopes the tint source to the fluid layer alone. Without this guard the shard
        // FRAME takes the fluid colour. 1.21.1-only (modern calculate() is scoped by the model).
        if (tintIndex != 1) {
            return 0xFFFFFFFF;
        }
        return computeColor(stack);
    }*/
    //?}

    /** Computes the average RGB color of the first frame of a sprite, with full alpha. */
    private static int averageSpriteColor(TextureAtlasSprite sprite) {
        long totalR = 0, totalG = 0, totalB = 0;
        int count = 0;

        int w = sprite.contents().width();
        int h = sprite.contents().height();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // getPixelRGBA returns ABGR-ordered int (NativeImage pixel format)
                int pixel = sprite.getPixelRGBA(0, x, y);
                int a = (pixel >> 24) & 0xFF;
                if (a < 128) continue; // skip mostly-transparent pixels
                // NativeImage ABGR: bits 0-7=R, 8-15=G, 16-23=B, 24-31=A
                int r = pixel & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = (pixel >> 16) & 0xFF;
                totalR += r;
                totalG += g;
                totalB += b;
                count++;
            }
        }

        if (count == 0) {
            return 0xFFFFFFFF;
        }

        int avgR = (int) (totalR / count);
        int avgG = (int) (totalG / count);
        int avgB = (int) (totalB / count);
        return 0xFF000000 | (avgR << 16) | (avgG << 8) | avgB;
    }

    //? if >=1.21.10 {
    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
    //?}
}
