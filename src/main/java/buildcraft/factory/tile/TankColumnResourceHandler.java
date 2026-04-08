/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.List;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.lib.misc.FluidUtilBC;

/**
 * A column-aware {@link ResourceHandler} for tanks. Delegates insert/extract
 * across the entire vertical column of connected {@link TileTank}s, mirroring
 * the 1.12.2 behaviour where {@code TileTank.fill()} and {@code drain()}
 * iterated over all stacked tanks.
 * <p>
 * Liquids fill from bottom to top and drain from top to bottom.
 */
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
