/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Adapts a legacy {@link FluidTank} to the NeoForge 1.21.11
 * {@link ResourceHandler}{@code <FluidResource>} API so it can be exposed via
 * {@link net.neoforged.neoforge.capabilities.Capabilities.Fluid#BLOCK}.
 * <p>
 * Transaction support is provided by snapshotting the tank's {@link FluidStack}.
 */
@SuppressWarnings("removal")
public class FluidTankResourceHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {

    private final FluidTank tank;

    public FluidTankResourceHandler(FluidTank tank) {
        this.tank = tank;
    }

    // --- Snapshot / rollback for transactions ---

    @Override
    protected FluidStack createSnapshot() {
        return tank.getFluid().copy();
    }

    @Override
    protected void revertToSnapshot(FluidStack snapshot) {
        tank.setFluid(snapshot);
    }

    // --- ResourceHandler implementation ---

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        checkIndex(index);
        FluidStack fluid = tank.getFluid();
        return fluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(fluid);
    }

    @Override
    public long getAmountAsLong(int index) {
        checkIndex(index);
        return tank.getFluidAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        checkIndex(index);
        if (!resource.isEmpty() && !isValid(index, resource)) {
            return 0;
        }
        return tank.getCapacity();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        checkIndex(index);
        return tank.isFluidValid(resource.toStack(1));
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        FluidStack toFill = resource.toStack(amount);
        // Simulate first to find accepted amount
        int accepted = tank.fill(toFill,
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        if (accepted > 0) {
            updateSnapshots(transaction);
            tank.fill(toFill.copyWithAmount(accepted),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        return accepted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        FluidStack toDrain = resource.toStack(amount);
        FluidStack simulated = tank.drain(toDrain,
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        int extracted = simulated.isEmpty() ? 0 : simulated.getAmount();
        if (extracted > 0) {
            updateSnapshots(transaction);
            tank.drain(toDrain.copyWithAmount(extracted),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        return extracted;
    }

    private static void checkIndex(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size 1");
        }
    }
}
