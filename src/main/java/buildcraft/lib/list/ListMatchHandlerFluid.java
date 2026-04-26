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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.api.lists.ListMatchHandler;

/** Matches fluid-bearing items. TYPE accepts any item that exposes the fluid capability
 * (covers empty buckets too, so a player can build a "fluid container" filter regardless
 * of contents). MATERIAL compares the contained {@link FluidResource}. */
public class ListMatchHandlerFluid extends ListMatchHandler {

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

    @Override
    public boolean isValidSource(Type type, @Nonnull ItemStack stack) {
        switch (type) {
            case TYPE:
                return handlerOf(stack) != null;
            case MATERIAL: {
                FluidResource r = firstResource(stack);
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
                FluidResource r = firstResource(stack);
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
                FluidResource a = firstResource(source);
                FluidResource b = firstResource(target);
                if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
                return a.equals(b);
            }
            default:
                return false;
        }
    }
}
