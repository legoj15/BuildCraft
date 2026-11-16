/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.fluids.BCFluidStack;

/^*
 * The fluid-content item component's value on Forge, which has no {@code SimpleFluidContent} and no fluid data
 * component at all (its item fluid storage is a capability, and item capabilities are not saved with the stack): one
 * fluid stack, held as a private copy, with value equality (the component map compares values, and
 * {@link BCFluidStack}'s equality is identity). The codecs are the stack's optional ones, so the saved shape is
 * NeoForge's ({@code id}, {@code amount}, {@code components}); the content is a BuildCraft-registered data component
 * ({@link ForgeFluids#registerFluidContent}), which is why a BuildCraft stack's component patch survives here even
 * though Forge's own {@code FluidStack} cannot carry one ({@link ForgeFluidStacks}). The Fabric twin is
 * {@code FabricFluidContent}.
 *^/
public final class ForgeFluidContent {
    public static final ForgeFluidContent EMPTY = new ForgeFluidContent(BCFluidStack.EMPTY);
    public static final Codec<ForgeFluidContent> CODEC =
            BCFluidStack.OPTIONAL_CODEC.xmap(ForgeFluidContent::of, content -> content.stack);
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeFluidContent> STREAM_CODEC =
            BCFluidStack.OPTIONAL_STREAM_CODEC.map(ForgeFluidContent::of, content -> content.stack);

    private final BCFluidStack stack;

    private ForgeFluidContent(BCFluidStack stack) {
        this.stack = stack;
    }

    /^* The content holding a copy of {@code stack}. ^/
    public static ForgeFluidContent of(BCFluidStack stack) {
        return stack.isEmpty() ? EMPTY : new ForgeFluidContent(stack.copy());
    }

    /^* A copy of the stored stack. ^/
    public BCFluidStack copy() {
        return stack.copy();
    }

    /^*
     * The fluid stored in {@code stack}'s content component {@code type}, empty if none. {@code type} is the one
     * {@link ForgeFluids#registerFluidContent} made, which is a {@code ForgeFluidContent} one.
     ^/
    @SuppressWarnings("unchecked") // callers pass the type registerFluidContent made
    static BCFluidStack read(ItemStack stack, DataComponentType<?> type) {
        return stack.getOrDefault((DataComponentType<ForgeFluidContent>) type, EMPTY).copy();
    }

    /^* Stores a copy of {@code fluid} in {@code stack}'s content component {@code type}; empty removes it. ^/
    @SuppressWarnings("unchecked") // callers pass the type registerFluidContent made
    static void write(ItemStack stack, DataComponentType<?> type, BCFluidStack fluid) {
        DataComponentType<ForgeFluidContent> typed = (DataComponentType<ForgeFluidContent>) type;
        if (fluid.isEmpty()) {
            stack.remove(typed);
        } else {
            stack.set(typed, of(fluid));
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ForgeFluidContent other && stack.getAmount() == other.stack.getAmount()
                && BCFluidStack.isSameFluidSameComponents(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return 31 * BCFluidStack.hashFluidAndComponents(stack) + stack.getAmount();
    }
}
*///?}
