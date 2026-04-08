/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Adapts multiple legacy {@link FluidTank} instances to the NeoForge 1.21.11
 * {@link ResourceHandler}{@code <FluidResource>} API so they can be exposed via
 * {@link net.neoforged.neoforge.capabilities.Capabilities.Fluid#BLOCK}.
 * <p>
 * Transaction support is provided by snapshotting all tanks' {@link FluidStack}s.
 * <p>
 * Insert tries each tank in order until one accepts the fluid.
 * Extract tries each tank in order until one yields fluid.
 */
@SuppressWarnings("removal")
public class MultiTankResourceHandler extends SnapshotJournal<FluidStack[]> implements ResourceHandler<FluidResource> {

    private final FluidTank[] tanks;

    public MultiTankResourceHandler(FluidTank... tanks) {
        this.tanks = tanks;
    }

    // --- Snapshot / rollback for transactions ---

    @Override
    protected FluidStack[] createSnapshot() {
        FluidStack[] snap = new FluidStack[tanks.length];
        for (int i = 0; i < tanks.length; i++) {
            snap[i] = tanks[i].getFluid().copy();
        }
        return snap;
    }

    @Override
    protected void revertToSnapshot(FluidStack[] snapshot) {
        for (int i = 0; i < tanks.length; i++) {
            tanks[i].setFluid(snapshot[i]);
        }
    }

    // --- ResourceHandler implementation ---

    @Override
    public int size() {
        return tanks.length;
    }

    @Override
    public FluidResource getResource(int index) {
        checkIndex(index);
        FluidStack fluid = tanks[index].getFluid();
        return fluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(fluid);
    }

    @Override
    public long getAmountAsLong(int index) {
        checkIndex(index);
        return tanks[index].getFluidAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        checkIndex(index);
        if (!resource.isEmpty() && !isValid(index, resource)) {
            return 0;
        }
        return tanks[index].getCapacity();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        checkIndex(index);
        return tanks[index].isFluidValid(resource.toStack(1));
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        FluidStack toFill = resource.toStack(amount);
        int accepted = tanks[index].fill(toFill, IFluidHandler.FluidAction.SIMULATE);
        if (accepted > 0) {
            updateSnapshots(transaction);
            tanks[index].fill(toFill.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        }
        return accepted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        checkIndex(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);

        FluidStack toDrain = resource.toStack(amount);
        FluidStack simulated = tanks[index].drain(toDrain, IFluidHandler.FluidAction.SIMULATE);
        int extracted = simulated.isEmpty() ? 0 : simulated.getAmount();
        if (extracted > 0) {
            updateSnapshots(transaction);
            tanks[index].drain(toDrain.copyWithAmount(extracted), IFluidHandler.FluidAction.EXECUTE);
        }
        return extracted;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= tanks.length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + tanks.length);
        }
    }
}
