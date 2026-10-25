/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.fuels;

import net.neoforged.neoforge.fluids.FluidStack;

public interface IFuel {
    /** @return The input fluid. The {@link FluidStack#amount} is ignored. */
    FluidStack getFluid();

    /** @return The number of ticks that a single bucket (1000mb) of this fuel will burn for. */
    int getTotalBurningTime();

    /** @return The amount (in micro mj) of power that this fuel will give off in 1 tick. */
    long getPowerPerCycle();

    /** @return The engine heat this fuel adds in 1 tick, given as the power (in micro mj) whose heat it matches.
     *         Defaults to {@link #getPowerPerCycle()}: heat in proportion to power. A fuel that runs hotter than
     *         its output (BuildCraft's searing fuels) returns more. */
    default long getHeatPerCycle() {
        return getPowerPerCycle();
    }
}

