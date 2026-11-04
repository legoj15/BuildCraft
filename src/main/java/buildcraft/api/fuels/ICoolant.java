/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.fuels;

import buildcraft.api.fluids.BCFluidStack;

public interface ICoolant {
    boolean matchesFluid(BCFluidStack fluid);

    /** @param fluid
     * @param heat
     * @return 0 if the input fluid provides no cooling, or a value greater than 0 if it does. */
    float getDegreesCoolingPerMB(BCFluidStack fluid, float heat);

    /** @return A canonical BCFluidStack identifying this coolant's input fluid, or
     *  {@link BCFluidStack#EMPTY} if this coolant doesn't have a single representative fluid
     *  (e.g. if it matches a tag-style group). Used by external systems (the guide book's
     *  {@code Linked From} / {@code Linked To} group population) to enumerate registered
     *  coolants without having to query every fluid in the registry. */
    default BCFluidStack getRepresentativeFluid() {
        return BCFluidStack.EMPTY;
    }
}

