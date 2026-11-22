/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.fluids.BCFluidStack;

/^*
 * Where a {@link BCFluidStack} becomes a Forge {@link FluidStack} and back for the transfer adapters (the Forge twin of
 * the stack conversion in {@code NeoForgeFluids}, which the NeoForge adapters call).
 *
 * <p><b>Components are dropped.</b> NeoForge's {@code FluidStack} carries a data-component patch, so the NeoForge
 * conversion passes BuildCraft's patch through unchanged. Forge's is the old NBT type: one optional {@code CompoundTag}
 * and no components, and its equality ({@code isFluidEqual}) compares fluid and tag. There is no honest mapping from a
 * component patch to a tag (component values may need registry access to encode), so the patch is dropped on the way out
 * and the tag on the way in. Nothing in BuildCraft puts a non-empty patch on a fluid today (it only reads patches to
 * hand them to recipe-viewer ingredients), so no stack changes meaning; a tagged foreign fluid (a potion tank) crosses
 * as its plain fluid, and a handler that insists on the tag will refuse the plain one.
 *
 * <p>An empty stack on either side is the other side's empty singleton. {@code FluidStack}'s constructor rejects a fluid
 * missing from the registry, which only a never-registered fluid is.
 ^/
public final class ForgeFluidStacks {
    private ForgeFluidStacks() {}

    /^* The Forge twin of a BuildCraft stack ({@link FluidStack#EMPTY} for an empty one). ^/
    public static FluidStack toForge(BCFluidStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(stack.getFluid(), stack.getAmount());
    }

    /^* The BuildCraft twin of a Forge stack ({@link BCFluidStack#EMPTY} for an empty one). ^/
    public static BCFluidStack fromForge(FluidStack stack) {
        if (stack.isEmpty()) {
            return BCFluidStack.EMPTY;
        }
        return new BCFluidStack(stack.getFluid(), stack.getAmount());
    }
}
*///?}
