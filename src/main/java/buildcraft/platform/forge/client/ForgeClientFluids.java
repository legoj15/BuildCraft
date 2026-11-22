/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import net.minecraft.resources.Identifier;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.client.ClientFluids;
import buildcraft.platform.forge.ForgeFluidStacks;

/^*
 * {@link ClientFluids} on MinecraftForge (not NeoForge). From 26.1 the sprite is the fluid's baked world model
 * ({@link ClientFluids#model}), as on NeoForge 26.x; before 26.1 (1.21.11, and 1.21.1 on Forge 52: no baked fluid
 * models) it is the still texture the fluid type's {@link IClientFluidTypeExtensions} declares, as on NeoForge before
 * 26.1. The tint is the fluid type's stack tint from those extensions on every line: Forge's 26.x fluid models carry
 * vanilla's level-only tint source (water's needs a biome), where NeoForge's carry a stack-aware one, and Forge's water
 * extension answers water's default colour as NeoForge's tint source does. Before 26.1 the light level is the fluid
 * type's own ({@link #luminance}, the legacy fluid container model's glowing layer).
 *^/
public final class ForgeClientFluids implements ClientFluids {
    @Override
    public Identifier stillTexture(BCFluidStack stack) {
        //? if >=26.1 {
        return ClientFluids.model(stack).stillMaterial().sprite().contents().name();
        //?} else {
        /^// Before 26.1 there is no baked fluid model: the type's own still texture (null for none), which Forge's
        // fluid renderer reads too.
        return IClientFluidTypeExtensions.of(stack.getFluid()).getStillTexture(ForgeFluidStacks.toForge(stack));
        ^///?}
    }

    @Override
    public int tint(BCFluidStack stack) {
        return IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(ForgeFluidStacks.toForge(stack));
    }

    //? if >=1.21.10 && <26.1 {
    /^@Override
    public int luminance(BCFluidStack stack) {
        return stack.getFluid().getFluidType().getLightLevel();
    }
    ^///?}
}
*///?}
