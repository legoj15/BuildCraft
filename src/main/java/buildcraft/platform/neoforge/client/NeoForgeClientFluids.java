/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
import net.minecraft.resources.Identifier;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.client.ClientFluids;
import buildcraft.platform.neoforge.NeoForgeFluids;

/**
 * {@link ClientFluids} on NeoForge: on 26.1+ the fluid's baked {@code FluidModel} ({@link ClientFluids#model}, with its
 * NeoForge tint source), before that the fluid type's {@code IClientFluidTypeExtensions}.
 */
public final class NeoForgeClientFluids implements ClientFluids {
    @Override
    public Identifier stillTexture(BCFluidStack stack) {
        //? if >=26.1 {
        return ClientFluids.model(stack).stillMaterial().sprite().contents().name();
        //?} else {
        /*return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(stack.getFluid())
            .getStillTexture(NeoForgeFluids.toNeo(stack));*/
        //?}
    }

    @Override
    public int tint(BCFluidStack stack) {
        //? if >=26.1 {
        net.neoforged.neoforge.client.fluid.FluidTintSource source = ClientFluids.model(stack).fluidTintSource();
        return source != null ? source.colorAsStack(NeoForgeFluids.toNeo(stack)) : 0xFFFFFFFF;
        //?} else {
        /*return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(stack.getFluid())
            .getTintColor(NeoForgeFluids.toNeo(stack));*/
        //?}
    }
}
//?}
