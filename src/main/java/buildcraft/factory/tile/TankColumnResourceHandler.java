/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.List;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * A column-aware fluid storage for tanks. Delegates insert/extract across the entire
 * vertical column of connected {@link TileTank}s, mirroring the 1.12.2 behaviour
 * where {@code TileTank.fill()} and {@code drain()} iterated over all stacked tanks.
 * <p>
 * Liquids fill from bottom to top and drain from top to bottom; gases the other way round.
 * The platform layer exposes it as the loader's fluid handler.
 */
public class TankColumnResourceHandler implements BCFluidStorage {

    private final TileTank owner;

    public TankColumnResourceHandler(TileTank owner) {
        this.owner = owner;
    }

    @Override
    public int size() {
        return 1; // Column presents as a single logical tank
    }

    @Override
    public BCFluidStack getStack(int index) {
        checkIndex(index);
        // The fluid of the first non-empty tank in the column, with the whole column's amount
        BCFluidStack fluid = BCFluidStack.EMPTY;
        long total = 0;
        for (TileTank t : owner.getTankColumn()) {
            BCFluidStack held = t.tank.getFluidStack(0);
            if (!held.isEmpty()) {
                if (fluid.isEmpty()) fluid = held;
                total += held.getAmount();
            }
        }
        return fluid.isEmpty() ? BCFluidStack.EMPTY : fluid.copyWithAmount((int) Math.min(total, Integer.MAX_VALUE));
    }

    @Override
    public int getCapacity(int index, BCFluidStack resource) {
        checkIndex(index);
        long total = 0;
        for (TileTank t : owner.getTankColumn()) {
            total += t.tank.getCapacity(0, resource);
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    @Override
    public boolean isValid(int index, BCFluidStack resource) {
        checkIndex(index);
        return !resource.isEmpty();
    }

    @Override
    public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
        checkIndex(index);
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }

        List<TileTank> tanks = owner.getTankColumn();

        // Check that existing fluid in the column is compatible
        for (TileTank t : tanks) {
            BCFluidStack current = t.tank.getFluidStack(0);
            if (!current.isEmpty() && !BCFluidStack.isSameFluidSameComponents(current, resource)) {
                return 0; // Different fluid already present
            }
        }

        boolean gaseous = FluidUtilBC.isGaseous(resource.getFluid());

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
    public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
        checkIndex(index);
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }

        List<TileTank> tanks = owner.getTankColumn();

        boolean gaseous = FluidUtilBC.isGaseous(resource.getFluid());

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
