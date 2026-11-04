/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import buildcraft.api.fluids.BCFluidStack;

/^*
 * The fluid-content item component's value on Fabric, which has no {@code SimpleFluidContent}: one fluid stack, held
 * as a private copy, with value equality (the component map compares values, and {@link BCFluidStack}'s equality is
 * identity). The codecs are the stack's optional ones, so the saved shape is NeoForge's ({@code id}, {@code amount},
 * {@code components}).
 *^/
public final class FabricFluidContent {
    public static final FabricFluidContent EMPTY = new FabricFluidContent(BCFluidStack.EMPTY);
    public static final Codec<FabricFluidContent> CODEC =
            BCFluidStack.OPTIONAL_CODEC.xmap(FabricFluidContent::of, content -> content.stack);
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricFluidContent> STREAM_CODEC =
            BCFluidStack.OPTIONAL_STREAM_CODEC.map(FabricFluidContent::of, content -> content.stack);

    private final BCFluidStack stack;

    private FabricFluidContent(BCFluidStack stack) {
        this.stack = stack;
    }

    /^* The content holding a copy of {@code stack}. ^/
    public static FabricFluidContent of(BCFluidStack stack) {
        return stack.isEmpty() ? EMPTY : new FabricFluidContent(stack.copy());
    }

    /^* A copy of the stored stack. ^/
    public BCFluidStack copy() {
        return stack.copy();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FabricFluidContent other && stack.getAmount() == other.stack.getAmount()
                && BCFluidStack.isSameFluidSameComponents(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return 31 * BCFluidStack.hashFluidAndComponents(stack) + stack.getAmount();
    }
}
*///?}
