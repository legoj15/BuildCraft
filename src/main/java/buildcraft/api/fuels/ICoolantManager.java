/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.fuels;

import java.util.Collection;

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.material.Fluid;

import buildcraft.api.fluids.BCFluidStack;

public interface ICoolantManager {
    ICoolant addCoolant(ICoolant coolant);

    ICoolant addCoolant(BCFluidStack fluid, float degreesCoolingPerMb);

    default ICoolant addCoolant(Fluid fluid, float degreesCoolingPerMb) {
        return addCoolant(new BCFluidStack(fluid, 1), degreesCoolingPerMb);
    }

    ISolidCoolant addSolidCoolant(ISolidCoolant solidCoolant);

    ISolidCoolant addSolidCoolant(ItemStack solid, BCFluidStack fluid, float multiplier);

    Collection<ICoolant> getCoolants();

    Collection<ISolidCoolant> getSolidCoolants();

    ICoolant getCoolant(BCFluidStack fluid);

    float getDegreesPerMb(BCFluidStack fluid, float heat);

    ISolidCoolant getSolidCoolant(ItemStack solid);
}

