/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.inventory.filter;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.IFluidFilter;

/** Returns true if the stack matches any one one of the filter stacks. */
public class PassThroughFluidFilter implements IFluidFilter {

    @Override
    public boolean matches(FluidStack fluid) {
        return fluid != null;
    }

}
