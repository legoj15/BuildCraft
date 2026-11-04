/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.FluidServices;

/^*
 * {@link FluidServices} on Fabric, and the one place a {@link BCFluidStack} becomes a Fabric {@link FluidVariant} plus
 * a droplet amount and back.
 *
 * <p><b>Units.</b> BuildCraft counts whole millibuckets; Fabric counts droplets, 81 to the millibucket
 * ({@link FluidConstants#BUCKET} is 81000). Millibuckets go out exactly ({@code mB * 81}); droplets come back floored
 * to whole millibuckets, so an offer or a stored remainder below 81 droplets is invisible to BuildCraft. That is the
 * accepted Fabric-only granularity gap (docs/platform-layer.md, "Fluids (value type)", open question 4).
 ^/
public final class FabricFluids implements FluidServices {
    /^* Fabric droplets per BuildCraft millibucket. ^/
    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    @Override
    public Component hoverName(Holder<Fluid> fluid, int amount, DataComponentPatch components) {
        return FluidVariantAttributes.getName(FluidVariant.of(fluid.value(), components));
    }

    @Override
    public ItemStack filledBucket(BCFluidStack stack) {
        // Fabric has no fluid-type bucket hook: the fluid's own bucket item, without the stack's components.
        Item bucket = stack.isEmpty() ? Items.AIR : stack.getFluid().getBucket();
        return bucket == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucket);
    }

    /^* The variant of a BuildCraft stack: its fluid and component patch ({@link FluidVariant#blank()} if empty). ^/
    public static FluidVariant toVariant(BCFluidStack stack) {
        if (stack.isEmpty()) {
            return FluidVariant.blank();
        }
        return FluidVariant.of(stack.getFluid(), stack.getComponentsPatch());
    }

    /^* Millibuckets to droplets, exactly. ^/
    public static long toDroplets(int millibuckets) {
        return millibuckets * DROPLETS_PER_MB;
    }

    /^* Droplets to whole millibuckets, floored (and clamped to {@code int}). ^/
    public static int toMillibuckets(long droplets) {
        long mb = Math.floorDiv(droplets, DROPLETS_PER_MB);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, mb));
    }

    /^* The BuildCraft stack for a variant and a droplet amount, floored to whole millibuckets (empty below 81). ^/
    public static BCFluidStack fromVariant(FluidVariant variant, long droplets) {
        int mb = toMillibuckets(droplets);
        if (variant.isBlank() || mb <= 0) {
            return BCFluidStack.EMPTY;
        }
        return new BCFluidStack(variant.getFluid(), mb, variant.getComponentsPatch());
    }
}
*///?}
