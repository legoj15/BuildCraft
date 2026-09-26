/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.list;

import javax.annotation.Nonnull;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.lists.ListMatchHandler;
import buildcraft.lib.misc.FluidUtilBC;

/** Matches fluid-bearing items. TYPE accepts any item that exposes the item fluid capability
 * (covers empty buckets too, so a player can build a "fluid container" filter regardless
 * of contents). MATERIAL compares the contained fluid (fluid and components, amount ignored).
 * <p>Reads through {@link FluidUtilBC}'s version-neutral container helpers, which use the
 * Transfer API on 1.21.10+ and the classic helpers on 1.21.1. */
public class ListMatchHandlerFluid extends ListMatchHandler {

    @Override
    public boolean isValidSource(Type type, @Nonnull ItemStack stack) {
        switch (type) {
            case TYPE:
                return FluidUtilBC.isFluidContainer(stack);
            case MATERIAL:
                return !FluidUtilBC.getFluidContained(stack).isEmpty();
            default:
                return false;
        }
    }

    @Nonnull
    @Override
    public java.util.List<String> describeMatch(Type type, @Nonnull ItemStack stack) {
        switch (type) {
            case TYPE:
                if (FluidUtilBC.isFluidContainer(stack)) {
                    return java.util.List.of("any fluid container");
                }
                return java.util.List.of();
            case MATERIAL: {
                FluidStack fluid = FluidUtilBC.getFluidContained(stack);
                if (fluid.isEmpty()) return java.util.List.of();
                net.minecraft.resources.Identifier id =
                        net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                return java.util.List.of("fluid: " + (id != null ? id.toString() : fluid.getFluid().toString()));
            }
            default:
                return java.util.List.of();
        }
    }

    @Override
    public boolean matches(Type type, @Nonnull ItemStack source, @Nonnull ItemStack target, boolean precise) {
        switch (type) {
            case TYPE:
                return FluidUtilBC.isFluidContainer(source) && FluidUtilBC.isFluidContainer(target);
            case MATERIAL: {
                FluidStack a = FluidUtilBC.getFluidContained(source);
                FluidStack b = FluidUtilBC.getFluidContained(target);
                return !a.isEmpty() && !b.isEmpty() && FluidStack.isSameFluidSameComponents(a, b);
            }
            default:
                return false;
        }
    }
}
