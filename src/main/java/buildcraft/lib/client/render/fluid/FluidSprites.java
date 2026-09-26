/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.fluid;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client-only lookup of how a fluid stack looks outside the world: its still sprite and its tint. Every tank, gauge,
 * fluid pipe, blueprint view and fluid shard goes through here.
 *
 * <p>The answer always comes from the fluid itself — its {@code IClientFluidTypeExtensions} below 26.1, its baked
 * {@code FluidModel} from 26.1 — so BuildCraft, vanilla and modded fluids resolve the same way. Never derive a sprite
 * name from a fluid's registry id: mods are free to keep fluid textures anywhere (Mekanism uses one tinted
 * {@code mekanism:liquid/liquid} for all of its fluids), and a guessed name draws as the missing texture.
 * {@code FluidSpritesGuardTester} pins this.
 */
public final class FluidSprites {
    private FluidSprites() {}

    /** The fluid's still sprite on the blocks atlas, or {@code null} for an empty stack. A fluid that declares no
     *  texture falls back to water's. */
    @Nullable
    public static TextureAtlasSprite stillSprite(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        //? if >=26.1 {
        return model(stack).stillMaterial().sprite();
        //?} else {
        /*return blocksAtlas().getSprite(stillTexture(stack));*/
        //?}
    }

    /** The name of the fluid's still sprite, or {@code null} for an empty stack. For callers that cache the name and
     *  resolve the sprite later (it survives a resource reload; a sprite object does not). */
    @Nullable
    public static Identifier stillTexture(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        //? if >=26.1 {
        return model(stack).stillMaterial().sprite().contents().name();
        //?} else {
        /*// Same rule as the 26.1+ model(): a fluid that declares no texture, or one that never reached the atlas,
        // falls back to water's instead of drawing the missing texture.
        Identifier texture = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(stack.getFluid())
            .getStillTexture(stack);
        boolean missing = texture == null || blocksAtlas().getSprite(texture).contents().name()
            .equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
        return missing
            ? net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(Fluids.WATER).getStillTexture()
            : texture;*/
        //?}
    }

    /** The fluid's ARGB tint for this stack; opaque white when it has none. A tint reporting zero alpha (some fluids
     *  return a bare RGB) is made opaque, so no caller draws a fluid invisible. */
    public static int tint(FluidStack stack) {
        if (stack.isEmpty()) {
            return 0xFFFFFFFF;
        }
        //? if >=26.1 {
        net.neoforged.neoforge.client.fluid.FluidTintSource source = model(stack).fluidTintSource();
        int tint = source != null ? source.colorAsStack(stack) : 0xFFFFFFFF;
        //?} else {
        /*int tint = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);*/
        //?}
        return (tint >>> 24) == 0 ? tint | 0xFF000000 : tint;
    }

    //? if >=26.1 {
    /** The fluid's baked model. The set hands back a missing-texture model (never null) for a fluid with none
     *  registered; fall back to water's instead of drawing that. */
    private static net.minecraft.client.renderer.block.FluidModel model(FluidStack stack) {
        net.minecraft.client.renderer.block.FluidStateModelSet models =
            Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        net.minecraft.client.renderer.block.FluidModel model = models.get(stack.getFluid().defaultFluidState());
        boolean missing = model.stillMaterial().sprite().contents().name()
            .equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
        return missing ? models.get(Fluids.WATER.defaultFluidState()) : model;
    }
    //?} else {
    /*private static net.minecraft.client.renderer.texture.TextureAtlas blocksAtlas() {
        return (net.minecraft.client.renderer.texture.TextureAtlas) Minecraft.getInstance().getTextureManager()
            .getTexture(buildcraft.lib.client.render.BCLibRenderTypes.BLOCKS_ATLAS_ID);
    }*/
    //?}
}
