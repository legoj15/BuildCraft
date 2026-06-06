/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.list;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.capabilities.Capabilities;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
//?} else {
/*import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;*/
//?}

import buildcraft.api.lists.ListMatchHandler;

/** Matches fluid-bearing items. TYPE accepts any item that exposes the fluid capability
 * (covers empty buckets too, so a player can build a "fluid container" filter regardless
 * of contents). MATERIAL compares the contained fluid.
 * <p>On 1.21.10+ this reads the Transfer-API {@code Capabilities.Fluid.ITEM}; on 1.21.1
 * the Transfer API does not exist, so it reads the classic {@code FluidUtil} item helpers.
 * The {@code >=1.21.10} branches are exactly today's code, so the released nodes are
 * behaviour-identical. */
public class ListMatchHandlerFluid extends ListMatchHandler {

    //? if >=1.21.10 {
    @Nullable
    private static ResourceHandler<FluidResource> handlerOf(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return null;
        return stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack));
    }

    @Nullable
    private static FluidResource firstResource(@Nonnull ItemStack stack) {
        ResourceHandler<FluidResource> h = handlerOf(stack);
        if (h == null || h.size() == 0) return null;
        return h.getResource(0);
    }
    //?} else {
    /*@Nullable
    private static IFluidHandlerItem handlerOf(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return null;
        return FluidUtil.getFluidHandler(stack).orElse(null);
    }

    private static FluidStack firstResource(@Nonnull ItemStack stack) {
        return FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
    }*/
    //?}

    @Override
    public boolean isValidSource(Type type, @Nonnull ItemStack stack) {
        switch (type) {
            case TYPE:
                return handlerOf(stack) != null;
            case MATERIAL: {
                var r = firstResource(stack);
                return r != null && !r.isEmpty();
            }
            default:
                return false;
        }
    }

    @Nonnull
    @Override
    public java.util.List<String> describeMatch(Type type, @Nonnull ItemStack stack) {
        switch (type) {
            case TYPE:
                if (handlerOf(stack) != null) {
                    return java.util.List.of("any fluid container");
                }
                return java.util.List.of();
            case MATERIAL: {
                var r = firstResource(stack);
                if (r == null || r.isEmpty()) return java.util.List.of();
                net.minecraft.resources.Identifier id =
                        net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(r.getFluid());
                return java.util.List.of("fluid: " + (id != null ? id.toString() : r.getFluid().toString()));
            }
            default:
                return java.util.List.of();
        }
    }

    @Override
    public boolean matches(Type type, @Nonnull ItemStack source, @Nonnull ItemStack target, boolean precise) {
        switch (type) {
            case TYPE:
                return handlerOf(source) != null && handlerOf(target) != null;
            case MATERIAL: {
                var a = firstResource(source);
                var b = firstResource(target);
                if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
                //? if >=1.21.10 {
                return a.equals(b);
                //?} else {
                /*return net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(a, b);*/
                //?}
            }
            default:
                return false;
        }
    }
}
