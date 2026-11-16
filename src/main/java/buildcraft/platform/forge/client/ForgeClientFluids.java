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
 * {@link ClientFluids} on MinecraftForge (not NeoForge). The sprite is the fluid's baked world model
 * ({@link ClientFluids#model}), as on NeoForge 26.x. The tint is the fluid type's stack tint from Forge's
 * {@link IClientFluidTypeExtensions}: Forge's fluid models carry vanilla's level-only tint source (water's needs a biome),
 * where NeoForge's carry a stack-aware one, and Forge's water extension answers water's default colour as NeoForge's
 * tint source does.
 *^/
public final class ForgeClientFluids implements ClientFluids {
    @Override
    public Identifier stillTexture(BCFluidStack stack) {
        return ClientFluids.model(stack).stillMaterial().sprite().contents().name();
    }

    @Override
    public int tint(BCFluidStack stack) {
        return IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(ForgeFluidStacks.toForge(stack));
    }
}
*///?}
