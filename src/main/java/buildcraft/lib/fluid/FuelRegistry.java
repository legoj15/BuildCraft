/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager;

public enum FuelRegistry implements IFuelManager {
    INSTANCE;

    private final List<IFuel> fuels = new LinkedList<>();

    @Override
    public <F extends IFuel> F addFuel(F fuel) {
        fuels.add(fuel);
        return fuel;
    }

    @Override
    public IFuel addFuel(BCFluidStack fluid, long powerPerCycle, int totalBurningTime) {
        return addFuel(new Fuel(fluid, powerPerCycle, totalBurningTime));
    }

    @Override
    public IDirtyFuel addDirtyFuel(BCFluidStack fuel, long powerPerCycle, int totalBurningTime, BCFluidStack residue) {
        return addFuel(new DirtyFuel(fuel, powerPerCycle, totalBurningTime, residue));
    }

    @Override
    public Collection<IFuel> getFuels() {
        return fuels;
    }

    @Override
    public IFuel getFuel(BCFluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return null;
        }
        for (IFuel fuel : fuels) {
            if (BCFluidStack.isSameFluid(fuel.getFluid(), fluid)) {
                return fuel;
            }
        }
        return null;
    }

    public static class Fuel implements IFuel {
        private final BCFluidStack fluid;
        private final long powerPerCycle;
        private final int totalBurningTime;
        private final long heatPerCycle;

        public Fuel(BCFluidStack fluid, long powerPerCycle, int totalBurningTime) {
            this(fluid, powerPerCycle, totalBurningTime, powerPerCycle);
        }

        public Fuel(BCFluidStack fluid, long powerPerCycle, int totalBurningTime, long heatPerCycle) {
            this.fluid = fluid;
            this.powerPerCycle = powerPerCycle;
            this.totalBurningTime = totalBurningTime;
            this.heatPerCycle = heatPerCycle;
        }

        @Override
        public BCFluidStack getFluid() {
            return fluid;
        }

        @Override
        public long getPowerPerCycle() {
            return powerPerCycle;
        }

        @Override
        public int getTotalBurningTime() {
            return totalBurningTime;
        }

        @Override
        public long getHeatPerCycle() {
            return heatPerCycle;
        }
    }

    public static class DirtyFuel extends Fuel implements IDirtyFuel {
        private final BCFluidStack residue;

        public DirtyFuel(BCFluidStack fluid, long powerPerCycle, int totalBurningTime, BCFluidStack residue) {
            this(fluid, powerPerCycle, totalBurningTime, powerPerCycle, residue);
        }

        public DirtyFuel(BCFluidStack fluid, long powerPerCycle, int totalBurningTime, long heatPerCycle,
                BCFluidStack residue) {
            super(fluid, powerPerCycle, totalBurningTime, heatPerCycle);
            this.residue = residue;
        }

        @Override
        public BCFluidStack getResidue() {
            return residue;
        }
    }
}
