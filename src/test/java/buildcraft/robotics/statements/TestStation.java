/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;*/
//?}

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.statements.StatementSlot;

/** A {@link DockingStation} whose {@link #getActiveActions()} returns a fixed slot list — the
 *  gate-free fixture the statement predicate tests drive against. The item/fluid/request inputs
 *  default to non-null so the provider tests see a fully equipped station. */
public class TestStation extends DockingStation {

    private final List<StatementSlot> slots;

    public TestStation(List<StatementSlot> slots) {
        super(BlockPos.ZERO, Direction.UP);
        this.slots = slots;
    }

    @Override
    public Iterable<StatementSlot> getActiveActions() {
        return slots;
    }

    @Override
    public SimpleContainer getItemInput() {
        return new SimpleContainer(27);
    }

    //? if >=1.21.10 {
    @Override
    public ResourceHandler<FluidResource> getFluidInput() {
        // An empty handler: the provider tests only need the seam non-null.
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return 0;
            }

            @Override
            public FluidResource getResource(int index) {
                return FluidResource.EMPTY;
            }

            @Override
            public long getAmountAsLong(int index) {
                return 0;
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return 0;
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return false;
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }
    //?} else {
    /*@Override
    public IFluidHandler getFluidInput() {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 0;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return 0;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return 0;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
    }*/
    //?}

    @Override
    public IRequestProvider getRequestProvider() {
        return new IRequestProvider() {
            @Override
            public int getRequestsCount() {
                return 0;
            }

            @Override
            public ItemStack getRequest(int slot) {
                return null;
            }

            @Override
            public ItemStack offerItem(int slot, ItemStack stack) {
                return stack;
            }
        };
    }
}
