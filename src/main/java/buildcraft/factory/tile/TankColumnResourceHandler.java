/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.List;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import buildcraft.lib.misc.FluidUtilBC;

/**
 * A column-aware fluid handler for tanks. Delegates fill/drain across the entire
 * vertical column of connected {@link TileTank}s, mirroring the 1.12.2 behaviour
 * where {@code TileTank.fill()} and {@code drain()} iterated over all stacked tanks.
 * <p>
 * Liquids fill from bottom to top and drain from top to bottom.
 * <p>
 * On 1.21.10+ this is a NeoForge Transfer-API {@code ResourceHandler<FluidResource>};
 * on 1.21.1 the Transfer API does not exist, so it is a classic {@code IFluidHandler}.
 * The {@code >=1.21.10} branch is exactly today's code, so the released nodes are
 * behaviour-identical.
 */
//? if >=1.21.10 {
@SuppressWarnings("removal")
public class TankColumnResourceHandler implements ResourceHandler<FluidResource> {

    private final TileTank owner;

    public TankColumnResourceHandler(TileTank owner) {
        this.owner = owner;
    }

    // --- ResourceHandler implementation ---

    @Override
    public int size() {
        return 1; // Column presents as a single logical tank
    }

    @Override
    public FluidResource getResource(int index) {
        checkIndex(index);
        // Return the fluid type from the first non-empty tank in the column
        for (TileTank t : owner.getTankColumn()) {
            FluidResource fluid = t.tank.getResource(0);
            if (!fluid.isEmpty()) {
                return fluid;
            }
        }
        return FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        checkIndex(index);
        long total = 0;
        for (TileTank t : owner.getTankColumn()) {
            total += t.tank.getAmountAsLong(0);
        }
        return total;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        checkIndex(index);
        long total = 0;
        for (TileTank t : owner.getTankColumn()) {
            total += t.tank.getCapacityAsLong(0, resource);
        }
        return total;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        checkIndex(index);
        return !resource.isEmpty();
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        List<TileTank> tanks = owner.getTankColumn();

        // Check that existing fluid in the column is compatible
        for (TileTank t : tanks) {
            FluidResource current = t.tank.getResource(0);
            if (!current.isEmpty() && !current.equals(resource)) {
                return 0; // Different fluid already present
            }
        }

        boolean gaseous = FluidUtilBC.isGaseous(resource.toStack(1));

        // Fill from bottom to top (liquids) or top to bottom (gases)
        int remaining = amount;
        int totalInserted = 0;

        if (gaseous) {
            for (int i = tanks.size() - 1; i >= 0; i--) {
                TileTank t = tanks.get(i);
                if (remaining <= 0) break;
                int accepted = t.tank.insert(0, resource, remaining, transaction);
                if (accepted > 0) {
                    remaining -= accepted;
                    totalInserted += accepted;
                }
            }
        } else {
            for (TileTank t : tanks) {
                if (remaining <= 0) break;
                int accepted = t.tank.insert(0, resource, remaining, transaction);
                if (accepted > 0) {
                    remaining -= accepted;
                    totalInserted += accepted;
                }
            }
        }

        return totalInserted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        List<TileTank> tanks = owner.getTankColumn();

        boolean gaseous = FluidUtilBC.isGaseous(resource.toStack(1));

        // Drain from top to bottom (liquids) or bottom to top (gases)
        int remaining = amount;
        int totalExtracted = 0;

        if (gaseous) {
            for (TileTank t : tanks) {
                if (remaining <= 0) break;
                int extracted = t.tank.extract(0, resource, remaining, transaction);
                if (extracted > 0) {
                    remaining -= extracted;
                    totalExtracted += extracted;
                }
            }
        } else {
            for (int i = tanks.size() - 1; i >= 0; i--) {
                if (remaining <= 0) break;
                TileTank t = tanks.get(i);
                int extracted = t.tank.extract(0, resource, remaining, transaction);
                if (extracted > 0) {
                    remaining -= extracted;
                    totalExtracted += extracted;
                }
            }
        }

        return totalExtracted;
    }

    private static void checkIndex(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size 1");
        }
    }
}
//?} else {
/*public class TankColumnResourceHandler implements IFluidHandler {

    private final TileTank owner;

    public TankColumnResourceHandler(TileTank owner) {
        this.owner = owner;
    }

    @Override
    public int getTanks() {
        return 1; // Column presents as a single logical tank
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        FluidStack fluid = FluidStack.EMPTY;
        long total = 0;
        for (TileTank t : owner.getTankColumn()) {
            FluidStack held = t.tank.getFluidStack(0);
            if (!held.isEmpty()) {
                if (fluid.isEmpty()) fluid = held.copy();
                total += t.tank.getAmountMb(0);
            }
        }
        if (fluid.isEmpty()) return FluidStack.EMPTY;
        fluid.setAmount((int) Math.min(total, Integer.MAX_VALUE));
        return fluid;
    }

    @Override
    public int getTankCapacity(int tank) {
        long total = 0;
        for (TileTank t : owner.getTankColumn()) {
            total += t.tank.getCapacityMb(0);
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        List<TileTank> tanks = owner.getTankColumn();

        // Check that existing fluid in the column is compatible
        for (TileTank t : tanks) {
            FluidStack current = t.tank.getFluidStack(0);
            if (!current.isEmpty() && !FluidStack.isSameFluidSameComponents(current, resource)) {
                return 0; // Different fluid already present
            }
        }

        boolean gaseous = FluidUtilBC.isGaseous(resource);
        boolean simulate = action.simulate();
        int remaining = resource.getAmount();
        int totalFilled = 0;

        if (gaseous) {
            for (int i = tanks.size() - 1; i >= 0; i--) {
                if (remaining <= 0) break;
                FluidStack toFill = resource.copy();
                toFill.setAmount(remaining);
                int filled = tanks.get(i).tank.fill(0, toFill, simulate);
                remaining -= filled;
                totalFilled += filled;
            }
        } else {
            for (TileTank t : tanks) {
                if (remaining <= 0) break;
                FluidStack toFill = resource.copy();
                toFill.setAmount(remaining);
                int filled = t.tank.fill(0, toFill, simulate);
                remaining -= filled;
                totalFilled += filled;
            }
        }

        return totalFilled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        FluidStack avail = getFluidInTank(0);
        if (avail.isEmpty() || !FluidStack.isSameFluidSameComponents(avail, resource)) {
            return FluidStack.EMPTY;
        }
        return drainColumn(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return drainColumn(maxDrain, action);
    }

    private FluidStack drainColumn(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        List<TileTank> tanks = owner.getTankColumn();
        FluidStack representative = getFluidInTank(0);
        if (representative.isEmpty()) return FluidStack.EMPTY;

        boolean gaseous = FluidUtilBC.isGaseous(representative);
        boolean simulate = action.simulate();
        int remaining = maxDrain;
        int totalDrained = 0;

        if (gaseous) {
            for (TileTank t : tanks) {
                if (remaining <= 0) break;
                FluidStack drained = t.tank.drain(0, remaining, simulate);
                remaining -= drained.getAmount();
                totalDrained += drained.getAmount();
            }
        } else {
            for (int i = tanks.size() - 1; i >= 0; i--) {
                if (remaining <= 0) break;
                FluidStack drained = tanks.get(i).tank.drain(0, remaining, simulate);
                remaining -= drained.getAmount();
                totalDrained += drained.getAmount();
            }
        }

        if (totalDrained <= 0) return FluidStack.EMPTY;
        FluidStack result = representative.copy();
        result.setAmount(totalDrained);
        return result;
    }
}*/
//?}
