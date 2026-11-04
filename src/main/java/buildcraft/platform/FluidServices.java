/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import buildcraft.api.fluids.BCFluidStack;

/**
 * What the loader knows about a fluid stack that vanilla does not. The stack is passed as its vanilla parts (fluid,
 * amount in millibuckets, component patch) because the contract may not name BuildCraft's own stack type; BuildCraft's
 * lib installs {@link #hoverName} as {@code BCFluidStack}'s display-name provider. Converting a stack to the loader's
 * own fluid type is not part of the contract: it happens inside each loader's implementation package.
 */
public interface FluidServices {
    /** The display name of a stack of the fluid, as the loader names it (NeoForge: the fluid type's description). */
    Component hoverName(Holder<Fluid> fluid, int amount, DataComponentPatch components);

    /** A bucket filled with {@code stack}'s fluid, as the loader makes one; empty if the fluid has no bucket. */
    ItemStack filledBucket(BCFluidStack stack);
}
