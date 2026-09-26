/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.compat.jei;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.core.item.ItemFragileFluidContainer;

/** The JEI identity of the core items with a hand-written key, kept JEI-free so it is unit-testable
 *  ({@code JeiSubtypeKeysTest}). Its {@code toString()} doubles as 1.21.1's legacy bookmark text, so it is pinned. */
public final class CoreJeiSubtypes {
    private CoreJeiSubtypes() {}

    /** Fragile fluid shard: the stored fluid's registry id alone, never the amount — keying on the full FluidStack
     *  would make every fill level its own entry, and a recipe's 500 mB shard alias (see FluidContainerAliases)
     *  would only match exactly-full shards. Null (no subtype) for an empty shard. */
    @Nullable
    public static Object fragileFluidKey(ItemStack stack) {
        FluidStack fluid = ItemFragileFluidContainer.getFluid(stack);
        return fluid.isEmpty() ? null : BuiltInRegistries.FLUID.getKey(fluid.getFluid());
    }
}
