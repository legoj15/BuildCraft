/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.transfer;

import javax.annotation.Nullable;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * The two-way adapter between the robot API's simulate-flag {@link IFluidHandlerAdv} and BuildCraft's transactional
 * {@link BCFluidStorage}. Each direction unwraps the other's adapter, so a storage that went out through the API
 * (a robot's tank, a station's input or output) comes back as itself.
 *
 * <p>A storage seen as a handler runs each call in a transaction of its own ({@link BCTransaction#openInCurrent}:
 * nested in the one open on this thread, else a root), committed unless the call simulates. A handler that is not
 * one of these adapters, seen as a storage, can only simulate inside a transaction: its view answers each insert
 * and extract by simulating and replays the real calls, in order, when the root transaction commits (none if it
 * aborts), as the platform's classic-handler views do ({@link DeferredFluidCalls}).
 */
public final class FluidHandlerAdvs {
    private FluidHandlerAdvs() {}

    /** The API handler for a storage (null stays null). */
    @Nullable
    public static IFluidHandlerAdv of(@Nullable BCFluidStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof StorageView view) {
            return view.handler;
        }
        return new HandlerView(storage);
    }

    /** The storage view of an API handler (null stays null). */
    @Nullable
    public static BCFluidStorage storage(@Nullable IFluidHandlerAdv handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof HandlerView view) {
            return view.storage;
        }
        return new StorageView(handler);
    }

    /** What {@code storage} would give (or gives, unless simulating) of the first fluid {@code filter} accepts. */
    public static BCFluidStack drain(BCFluidStorage storage, IFluidFilter filter, int maxDrain, boolean simulate) {
        if (maxDrain <= 0) {
            return BCFluidStack.EMPTY;
        }
        for (int i = 0; i < storage.size(); i++) {
            BCFluidStack inTank = storage.getStack(i);
            if (!inTank.isEmpty() && filter.matches(inTank)) {
                return drainResource(storage, inTank, maxDrain, simulate);
            }
        }
        return BCFluidStack.EMPTY;
    }

    private static BCFluidStack drainResource(BCFluidStorage storage, BCFluidStack resource, int amount,
            boolean simulate) {
        if (resource.isEmpty() || amount <= 0) {
            return BCFluidStack.EMPTY;
        }
        try (BCTransaction tx = BCTransaction.openInCurrent()) {
            int drained = storage.extract(resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return drained <= 0 ? BCFluidStack.EMPTY : resource.copyWithAmount(drained);
        }
    }

    private record HandlerView(BCFluidStorage storage) implements IFluidHandlerAdv {
        @Override
        public int getTanks() {
            return storage.size();
        }

        @Override
        public BCFluidStack getFluidInTank(int tank) {
            return storage.getStack(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return storage.getCapacity(tank, BCFluidStack.EMPTY);
        }

        @Override
        public int fill(BCFluidStack resource, boolean simulate) {
            if (resource.isEmpty() || resource.getAmount() <= 0) {
                return 0;
            }
            try (BCTransaction tx = BCTransaction.openInCurrent()) {
                int filled = storage.insert(resource, resource.getAmount(), tx);
                if (!simulate) {
                    tx.commit();
                }
                return filled;
            }
        }

        @Override
        public BCFluidStack drain(BCFluidStack resource, boolean simulate) {
            return drainResource(storage, resource, resource.getAmount(), simulate);
        }

        @Override
        public BCFluidStack drain(int maxDrain, boolean simulate) {
            for (int i = 0; i < storage.size(); i++) {
                BCFluidStack inTank = storage.getStack(i);
                if (!inTank.isEmpty()) {
                    return drainResource(storage, inTank, maxDrain, simulate);
                }
            }
            return BCFluidStack.EMPTY;
        }

        @Override
        public BCFluidStack drain(IFluidFilter filter, int maxDrain, boolean simulate) {
            return FluidHandlerAdvs.drain(storage, filter, maxDrain, simulate);
        }
    }

    private static final class StorageView implements BCFluidStorage {
        final IFluidHandlerAdv handler;
        private final DeferredFluidCalls calls;

        StorageView(IFluidHandlerAdv handler) {
            this.handler = handler;
            this.calls = new DeferredFluidCalls(handler,
                (stack, simulate) -> handler.fill(stack, simulate),
                (stack, simulate) -> {
                    BCFluidStack drained = handler.drain(stack, simulate);
                    return BCFluidStack.isSameFluidSameComponents(drained, stack) ? drained.getAmount() : 0;
                });
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public BCFluidStack getStack(int index) {
            return handler.getFluidInTank(index).copy();
        }

        @Override
        public int getCapacity(int index, BCFluidStack resource) {
            return handler.getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, BCFluidStack resource) {
            BCFluidStack inTank = handler.getFluidInTank(index);
            return !resource.isEmpty() && (inTank.isEmpty() || BCFluidStack.isSameFluidSameComponents(inTank, resource));
        }

        // The API handler has no per-tank fill: an insert or extract on index 0 stands for the whole handler, and
        // the other indices refuse (BuildCraft's callers go through the resource-wide calls, which come here).
        @Override
        public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            return index == 0 ? calls.fill(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            return index == 0 ? calls.drain(resource, amount, transaction) : 0;
        }

        @Override
        public int insert(BCFluidStack resource, int amount, BCTransactionContext transaction) {
            BCFluidStorage.checkNonEmptyNonNegative(resource, amount);
            return calls.fill(resource, amount, transaction);
        }

        @Override
        public int extract(BCFluidStack resource, int amount, BCTransactionContext transaction) {
            BCFluidStorage.checkNonEmptyNonNegative(resource, amount);
            return calls.drain(resource, amount, transaction);
        }
    }
}
