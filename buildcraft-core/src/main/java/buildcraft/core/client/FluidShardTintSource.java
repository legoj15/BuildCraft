/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.client;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import org.jspecify.annotations.Nullable;

import buildcraft.core.BCCore;

/**
 * ItemTintSource for fragile fluid shards. For fluids that use a tint color
 * (like water), returns the fluid's tint directly. For fluids with pre-colored
 * textures (like lava, where tint is white/0xFFFFFF), samples the average pixel
 * color from the fluid's flowing sprite to produce a representative color.
 *
 * MC 26.1: IClientFluidTypeExtensions.getTintColor/getStillTexture/getFlowingTexture
 * have been removed. Using hardcoded texture lookups as a workaround until
 * NeoForge 26.1 fluid rendering API stabilizes.
 * TODO: Implement proper NeoForge 26.1 fluid rendering API when available.
 */
public final class FluidShardTintSource implements ItemTintSource {
    public static final FluidShardTintSource INSTANCE = new FluidShardTintSource();
    public static final MapCodec<FluidShardTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    private FluidShardTintSource() {}

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        SimpleFluidContent content = stack.getOrDefault(BCCore.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY);
        FluidStack fluid = content.copy();
        if (fluid.isEmpty()) {
            return 0xFFFFFFFF;
        }

        // MC 26.1: IClientFluidTypeExtensions removed getTintColor/getStillTexture/getFlowingTexture.
        // For water, return the biome-based tint (blue). For other fluids, sample texture color.
        if (fluid.getFluid().isSame(Fluids.WATER)) {
            // Default water blue tint
            return 0xFF3F76E4;
        }

        // For pre-colored fluids (lava, oil, etc.), sample the sprite to get color
        Identifier flowingTex = getFluidTexture(fluid);
        if (flowingTex == null) {
            return 0xFFFFFFFF;
        }

        TextureAtlas atlas = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(flowingTex);
        return averageSpriteColor(sprite);
    }

    /**
     * Gets a fluid's still texture identifier using hardcoded lookups.
     * TODO: Replace with proper NeoForge 26.1 fluid rendering API.
     */
    @Nullable
    private static Identifier getFluidTexture(FluidStack fluid) {
        if (fluid.getFluid().isSame(Fluids.WATER) || fluid.getFluid().isSame(Fluids.FLOWING_WATER)) {
            return Identifier.withDefaultNamespace("block/water_flow");
        } else if (fluid.getFluid().isSame(Fluids.LAVA) || fluid.getFluid().isSame(Fluids.FLOWING_LAVA)) {
            return Identifier.withDefaultNamespace("block/lava_flow");
        }
        // For mod fluids, try the fluid's registry name as a texture path
        Identifier fluidId = net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES
                .getKey(fluid.getFluid().getFluidType());
        if (fluidId != null) {
            return Identifier.parse(fluidId.getNamespace() + ":block/" + fluidId.getPath() + "_still");
        }
        return null;
    }

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
        // Return ARGB with full alpha (0xFF) — the previous bug omitted alpha,
        // causing items to render as completely transparent.
        return 0xFF000000 | (avgR << 16) | (avgG << 8) | avgB;
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
