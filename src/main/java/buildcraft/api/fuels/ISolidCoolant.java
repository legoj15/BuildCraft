/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.fuels;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

public interface ISolidCoolant {
    FluidStack getFluidFromSolidCoolant(ItemStack stack);

    /** @return A canonical ItemStack identifying the solid form of this coolant, or
     *  {@link ItemStack#EMPTY} if no single representative stack applies. Used by external
     *  systems (the guide book's group population) to enumerate registered solid coolants. */
    default ItemStack getRepresentativeStack() {
        return ItemStack.EMPTY;
    }
}

