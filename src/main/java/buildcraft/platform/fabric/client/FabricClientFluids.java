/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.client.ClientFluids;
import buildcraft.platform.fabric.FabricFluids;

/^*
 * {@link ClientFluids} on Fabric: the fluid's baked model ({@link ClientFluids#model}) for the sprite, Fabric's variant
 * rendering for the tint. Only the pre-26.1 {@code FluidSprites} reads {@link #stillTexture} (26.1 and later read the
 * model itself, with its water fallback), so nothing on this node calls it.
 ^/
public final class FabricClientFluids implements ClientFluids {
    /^* The model's still sprite: water's where the fluid has none, as NeoForge's lookup answers on 26.1 and later. ^/
    @Override
    public Identifier stillTexture(BCFluidStack stack) {
        // The same missing-texture-to-water rule as the sprite FluidSprites draws and as NeoForge's lookup.
        return ClientFluids.model(stack).stillMaterial().sprite().contents().name();
    }

    @Override
    public int tint(BCFluidStack stack) {
        return FluidVariantRendering.getColor(FabricFluids.toVariant(stack));
    }
}
*///?}
