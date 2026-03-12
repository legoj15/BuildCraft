/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.List;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A column-aware {@link ResourceHandler} for tanks. Delegates insert/extract
 * across the entire vertical column of connected {@link TileTank}s, mirroring
 * the 1.12.2 behaviour where {@code TileTank.fill()} and {@code drain()}
 * iterated over all stacked tanks.
 * <p>
 * Liquids fill from bottom to top and drain from top to bottom.
 */
@SuppressWarnings("removal")
public class TankColumnResourceHandler extends SnapshotJournal<List<FluidStack>> implements ResourceHandler<FluidResource> {

    private final TileTank owner;

    public TankColumnResourceHandler(TileTank owner) {
        this.owner = owner;
    }

    // --- Snapshot / rollback for transactions ---

    @Override
    protected List<FluidStack> createSnapshot() {
        List<TileTank> tanks = owner.getTankColumn();
        return tanks.stream().map(t -> t.tank.getFluid().copy()).toList();
    }

    @Override
    protected void revertToSnapshot(List<FluidStack> snapshot) {
        List<TileTank> tanks = owner.getTankColumn();
        for (int i = 0; i < Math.min(snapshot.size(), tanks.size()); i++) {
            tanks.get(i).tank.setFluid(snapshot.get(i));
        }
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
            FluidStack fluid = t.tank.getFluid();
            if (!fluid.isEmpty()) {
                return FluidResource.of(fluid);
            }
        }
        return FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        checkIndex(index);
        long total = 0;
        for (TileTank t : owner.getTankColumn()) {
            total += t.tank.getFluidAmount();
        }
        return total;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        checkIndex(index);
        long total = 0;
        for (TileTank t : owner.getTankColumn()) {
            total += t.tank.getCapacity();
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
            FluidStack current = t.tank.getFluid();
            if (!current.isEmpty() && !FluidResource.of(current).equals(resource)) {
                return 0; // Different fluid already present
            }
        }

        // Fill from bottom to top (liquids)
        int remaining = amount;
        int totalInserted = 0;
        boolean snapshotted = false;

        for (TileTank t : tanks) {
            if (remaining <= 0) break;
            FluidStack toFill = resource.toStack(remaining);
            int accepted = t.tank.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
            if (accepted > 0) {
                if (!snapshotted) {
                    updateSnapshots(transaction);
                    snapshotted = true;
                }
                t.tank.fill(toFill.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
                remaining -= accepted;
                totalInserted += accepted;
            }
        }

        return totalInserted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        List<TileTank> tanks = owner.getTankColumn();

        // Drain from top to bottom (liquids drain from the top first)
        int remaining = amount;
        int totalExtracted = 0;
        boolean snapshotted = false;

        for (int i = tanks.size() - 1; i >= 0; i--) {
            if (remaining <= 0) break;
            TileTank t = tanks.get(i);
            FluidStack toDrain = resource.toStack(remaining);
            FluidStack simulated = t.tank.drain(toDrain, IFluidHandler.FluidAction.SIMULATE);
            int extracted = simulated.isEmpty() ? 0 : simulated.getAmount();
            if (extracted > 0) {
                if (!snapshotted) {
                    updateSnapshots(transaction);
                    snapshotted = true;
                }
                t.tank.drain(toDrain.copyWithAmount(extracted), IFluidHandler.FluidAction.EXECUTE);
                remaining -= extracted;
                totalExtracted += extracted;
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
