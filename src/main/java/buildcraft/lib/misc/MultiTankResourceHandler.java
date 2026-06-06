/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}

/**
 * Adapts multiple classic {@link FluidTank} instances to a fluid capability handler so they can be exposed via
 * {@code Capabilities.Fluid#BLOCK} (1.21.10+) / {@code Capabilities.FluidHandler#BLOCK} (1.21.1).
 * <p>
 * On 1.21.10+ this is a {@link ResourceHandler}{@code <FluidResource>} with transaction support provided by
 * snapshotting all tanks' {@link FluidStack}s; on 1.21.1 (no Transfer API) it is a classic {@link IFluidHandler}
 * over the same {@code FluidTank[]}. Insert tries each tank in order until one accepts; extract until one yields.
 */
@SuppressWarnings("removal")
//? if >=1.21.10 {
public class MultiTankResourceHandler extends SnapshotJournal<FluidStack[]> implements ResourceHandler<FluidResource> {
//?} else {
/*public class MultiTankResourceHandler implements IFluidHandler {*/
//?}

    private final FluidTank[] tanks;

    public MultiTankResourceHandler(FluidTank... tanks) {
        this.tanks = tanks;
    }

    //? if >=1.21.10 {
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
    //?} else {
    /*// --- classic IFluidHandler implementation (1.21.1) ---

    @Override
    public int getTanks() {
        return tanks.length;
    }

    @Override
    public FluidStack getFluidInTank(int index) {
        checkIndex(index);
        return tanks[index].getFluid();
    }

    @Override
    public int getTankCapacity(int index) {
        checkIndex(index);
        return tanks[index].getCapacity();
    }

    @Override
    public boolean isFluidValid(int index, FluidStack stack) {
        checkIndex(index);
        return tanks[index].isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
        for (FluidTank tank : tanks) {
            int accepted = tank.fill(resource, action);
            if (accepted > 0) {
                return accepted;
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
        for (FluidTank tank : tanks) {
            FluidStack drained = tank.drain(resource, action);
            if (!drained.isEmpty()) {
                return drained;
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
        for (FluidTank tank : tanks) {
            FluidStack drained = tank.drain(maxDrain, action);
            if (!drained.isEmpty()) {
                return drained;
            }
        }
        return FluidStack.EMPTY;
    }*/
    //?}

    private void checkIndex(int index) {
        if (index < 0 || index >= tanks.length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + tanks.length);
        }
    }
}
