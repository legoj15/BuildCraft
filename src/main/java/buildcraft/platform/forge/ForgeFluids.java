/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.FluidProperties;
import buildcraft.platform.FluidServices;
import buildcraft.platform.hook.FluidBlockExtras;
import buildcraft.platform.registry.BCFluidRegistrar;
import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;

/^*
 * {@link FluidServices} on MinecraftForge (not NeoForge). Forge keeps NeoForge's {@code FluidType} (the same class
 * name, package {@code net.minecraftforge.fluids}), so the reads of a fluid ({@link #viscosity},
 * {@link #isLighterThanAir}, {@link #description}, {@link #isSameKind}, {@link #declared}) are the NeoForge twin's
 * lines. What differs is the stack: Forge's {@code FluidStack} is the old NBT one, see {@link ForgeFluidStacks} for
 * exactly what the conversion drops, and there is no fluid data component, so the fluid-content item component is
 * BuildCraft's own ({@link ForgeFluidContent}), as on Fabric.
 *
 * <p><b>Construction.</b> The first call to {@code Platform.fluids()} makes this (BCLib's init does it during mod
 * construction). The constructor installs the item-stack capability listener ({@link ForgeFluidContainers}): Forge
 * fires its attach event for an item stack only if the event bus has a listener when the stack is constructed, so it
 * has to exist before any stack the game will ask for a fluid handler is made.
 ^/
public final class ForgeFluids implements FluidServices {
    public ForgeFluids() {
        ForgeFluidContainers.installItemCapabilities();
    }

    @Override
    public Component hoverName(Holder<Fluid> fluid, int amount, DataComponentPatch components) {
        // The component patch has no Forge twin (ForgeFluidStacks): a patch never changes a Forge fluid's name.
        return new FluidStack(fluid.value(), amount).getDisplayName();
    }

    @Override
    public ItemStack filledBucket(BCFluidStack stack) {
        // FluidType.getBucket is the fluid's own bucket item; an empty stack or a fluid without a bucket gives air,
        // which is the empty stack.
        return stack.getFluid().getFluidType().getBucket(ForgeFluidStacks.toForge(stack));
    }

    @Override
    public BCFluidRegistrar createRegistrar(String namespace) {
        return new ForgeFlowingFluids(namespace);
    }

    @Override
    public LiquidBlock liquidBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties,
            FluidBlockExtras extras) {
        // The supplier constructors: Forge deprecates the ones that take the fluid, and keeps the fluid in the supplier
        // until the block or bucket first needs it.
        return new ForgeFluidBlock(fluid, properties, extras);
    }

    @Override
    public BucketItem bucketItem(Supplier<? extends Fluid> fluid, Item.Properties properties) {
        return new BucketItem(fluid, properties);
    }

    @Override
    public int viscosity(Fluid fluid) {
        return fluid.getFluidType().getViscosity();
    }

    @Override
    @Nullable
    public FluidProperties declared(Fluid fluid) {
        return fluid.getFluidType() instanceof ForgeFlowingFluids.Type type ? type.properties() : null;
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
    public BCHolder<DataComponentType<?>, DataComponentType<ForgeFluidContent>> registerFluidContent(
            BCRegistrar.DataComponents components, String name) {
        return components.registerComponentType(name, builder -> builder.persistent(ForgeFluidContent.CODEC)
                .networkSynchronized(ForgeFluidContent.STREAM_CODEC));
    }

    @Override
    public BCFluidStack getFluidContent(ItemStack stack, DataComponentType<?> type) {
        return ForgeFluidContent.read(stack, type);
    }

    @Override
    public void setFluidContent(ItemStack stack, DataComponentType<?> type, BCFluidStack fluid) {
        ForgeFluidContent.write(stack, type, fluid);
    }
}
*///?}
