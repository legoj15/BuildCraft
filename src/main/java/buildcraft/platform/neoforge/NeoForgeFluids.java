/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.FluidProperties;
import buildcraft.platform.FluidServices;
import buildcraft.platform.registry.BCFluidRegistrar;
import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;

/**
 * {@link FluidServices} on NeoForge, and the one place a {@link BCFluidStack} becomes a NeoForge {@link FluidStack}
 * and back. Both directions carry the fluid holder, the amount and the component patch unchanged, so a round trip
 * preserves the save format (both stacks share the {@code id}/{@code amount}/{@code components} codec shape); empty
 * maps to empty.
 */
public final class NeoForgeFluids implements FluidServices {
    @Override
    public Component hoverName(Holder<Fluid> fluid, int amount, DataComponentPatch components) {
        return new FluidStack(fluid, amount, components).getHoverName();
    }

    @Override
    public ItemStack filledBucket(BCFluidStack stack) {
        return stack.getFluid().getFluidType().getBucket(toNeo(stack));
    }

    @Override
    public BCFluidRegistrar createRegistrar(String namespace) {
        return new NeoForgeFlowingFluids(namespace);
    }

    @Override
    public int viscosity(Fluid fluid) {
        return fluid.getFluidType().getViscosity();
    }

    @Override
    @Nullable
    public FluidProperties declared(Fluid fluid) {
        return fluid.getFluidType() instanceof NeoForgeFlowingFluids.Type type ? type.properties() : null;
    }

    @Override
    public boolean isLighterThanAir(Fluid fluid) {
        return fluid.getFluidType().isLighterThanAir();
    }

    @Override
    public boolean isSameKind(Fluid a, Fluid b) {
        return a == b || a.getFluidType() == b.getFluidType();
    }

    @Override
    public Component description(Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }

    @Override
    public BCHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> registerFluidContent(
            BCRegistrar.DataComponents components, String name) {
        return components.registerComponentType(name, builder -> builder.persistent(SimpleFluidContent.CODEC)
                .networkSynchronized(SimpleFluidContent.STREAM_CODEC));
    }

    @Override
    public BCFluidStack getFluidContent(ItemStack stack, DataComponentType<?> type) {
        return fromNeo(stack.getOrDefault(contentType(type), SimpleFluidContent.EMPTY).copy());
    }

    @Override
    public void setFluidContent(ItemStack stack, DataComponentType<?> type, BCFluidStack fluid) {
        if (fluid.isEmpty()) {
            stack.remove(contentType(type));
        } else {
            stack.set(contentType(type), SimpleFluidContent.copyOf(toNeo(fluid)));
        }
    }

    @SuppressWarnings("unchecked") // callers pass the type registerFluidContent made, which is a SimpleFluidContent one
    private static DataComponentType<SimpleFluidContent> contentType(DataComponentType<?> type) {
        return (DataComponentType<SimpleFluidContent>) type;
    }

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
//?}
