/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

import net.minecraft.resources.Identifier;

import buildcraft.api.fluids.BCFluidStack;

/** Client-only: how the loader draws a fluid stack (its still texture and its tint). */
public interface ClientFluids {
    /**
     * The name of the still sprite the loader declares for the stack's fluid. Below 26.1 null if it declares none; on
     * 26.1 and later water's still sprite then, the fallback {@code model} applies.
     */
    Identifier stillTexture(BCFluidStack stack);

    /** The stack's ARGB tint as the loader reports it (it may carry zero alpha); opaque white for no tint. */
    int tint(BCFluidStack stack);

    //? if >=26.1 {
    /**
     * The stack's fluid's baked model, the one place that decides what a fluid with no usable model looks like: the
     * model set hands back a missing-texture model (never null) for a fluid with none registered, and this falls back
     * to water's instead of drawing that. It reads vanilla's own model set, so both loaders answer from it; the 26.1+
     * still sprite ({@code FluidSprites}) and each loader's still texture and tint all go through it.
     */
    static net.minecraft.client.renderer.block.FluidModel model(BCFluidStack stack) {
        net.minecraft.client.renderer.block.FluidStateModelSet models =
            net.minecraft.client.Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        net.minecraft.client.renderer.block.FluidModel model = models.get(stack.getFluid().defaultFluidState());
        boolean missing = model.stillMaterial().sprite().contents().name()
            .equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
        return missing ? models.get(net.minecraft.world.level.material.Fluids.WATER.defaultFluidState()) : model;
    }
    //?}
}
