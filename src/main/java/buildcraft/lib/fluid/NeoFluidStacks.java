/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.fluids.BCFluidStack;

/**
 * Interim {@link BCFluidStack} / NeoForge {@link FluidStack} conversion for common code that is still bound to a
 * NeoForge fluid API (fluid handlers and {@code ResourceHandler}s, {@code FluidType}, {@code FluidUtil},
 * {@code SimpleFluidContent}, recipe-viewer fluid types). It is the same conversion as
 * {@code platform.neoforge.NeoForgeFluids}, which common code may not name; every caller is transfer-seam territory
 * (epic #98, S14) and moves into {@code platform/neoforge} with it, after which this class goes.
 */
public final class NeoFluidStacks {
    private NeoFluidStacks() {}

    /** The NeoForge twin of a BuildCraft stack ({@link FluidStack#EMPTY} for an empty one). */
    public static FluidStack toNeo(BCFluidStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(stack.getFluidHolder(), stack.getAmount(), stack.getComponentsPatch());
    }

    /** The BuildCraft twin of a NeoForge stack ({@link BCFluidStack#EMPTY} for an empty one). */
    public static BCFluidStack fromNeo(FluidStack stack) {
        if (stack.isEmpty()) {
            return BCFluidStack.EMPTY;
        }
        return new BCFluidStack(stack.getFluid(), stack.getAmount(), stack.getComponentsPatch());
    }
}
