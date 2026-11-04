/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import javax.annotation.Nullable;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import buildcraft.api.core.BCLog;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
*///?}

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * The two-way adapter between {@link BCFluidStorage} and NeoForge's fluid handler type:
 * {@code ResourceHandler<FluidResource>} on 1.21.10+, the classic {@code IFluidHandler} on 1.21.1. Each direction
 * unwraps the other's adapter, so a BuildCraft storage that went out as a capability and came back through a lookup
 * is the same object again. Stacks cross through {@link NeoForgeFluids}.
 *
 * <p><b>1.21.1.</b> A BuildCraft storage seen as an {@code IFluidHandler} runs each call in a transaction of its own
 * ({@link LegacyTransactions#openInCurrent}), committed unless the call simulates; {@code fill} goes across the
 * indices in order and {@code drain} takes from the first index holding the fluid, as BuildCraft's classic tanks did.
 * A classic handler seen as a {@link BCFluidStorage} can only simulate inside a transaction, so its view answers by
 * simulating and replays the real calls, in order, when the root transaction commits (none if it aborts). Classic
 * fills are not per tank, so the view takes every insert through index 0 (the handler spreads it) and refuses the
 * other indices; an extract at any index is a fluid-matched drain.
 */
public final class NeoForgeFluidStorages {
    private NeoForgeFluidStorages() {}

    //? if >=1.21.10 {
    /** The NeoForge handler for a BuildCraft storage (null stays null). */
    @Nullable
    public static ResourceHandler<FluidResource> toNeo(@Nullable BCFluidStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromNeo view) {
            return view.handler;
        }
        return new ToNeo(storage);
    }

    /** The BuildCraft view of a NeoForge handler (null stays null). */
    @Nullable
    public static BCFluidStorage fromNeo(@Nullable ResourceHandler<FluidResource> handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToNeo adapter) {
            return adapter.storage;
        }
        return new FromNeo(handler);
    }

    /** The BuildCraft stack of {@code amount} of a resource (empty for an empty resource or amount). */
    public static BCFluidStack stackOf(FluidResource resource, int amount) {
        return resource.isEmpty() || amount <= 0 ? BCFluidStack.EMPTY : NeoForgeFluids.fromNeo(resource.toStack(amount));
    }

    /** The resource a BuildCraft stack names, its amount dropped. */
    public static FluidResource resourceOf(BCFluidStack stack) {
        return stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(NeoForgeFluids.toNeo(stack));
    }

    private record ToNeo(BCFluidStorage storage) implements ResourceHandler<FluidResource> {
        @Override
        public int size() {
            return storage.size();
        }

        @Override
        public FluidResource getResource(int index) {
            return resourceOf(storage.getStack(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return storage.getAmount(index);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return storage.getCapacity(index, stackOf(resource, 1));
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return storage.isValid(index, stackOf(resource, 1));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            // No precondition check: BuildCraft storages answer 0 to an empty or negative offer, as they did as handlers.
            return storage.insert(index, stackOf(resource, 1), amount, NeoForgeTransfer.wrap(transaction));
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return storage.extract(index, stackOf(resource, 1), amount, NeoForgeTransfer.wrap(transaction));
        }

        @Override
        public String toString() {
            return "NeoForge view of " + storage;
        }
    }

    private record FromNeo(ResourceHandler<FluidResource> handler) implements BCFluidStorage {
        @Override
        public int size() {
            return handler.size();
        }

        @Override
        public BCFluidStack getStack(int index) {
            return stackOf(handler.getResource(index), handler.getAmountAsInt(index));
        }

        @Override
        public int getAmount(int index) {
            return handler.getResource(index).isEmpty() ? 0 : handler.getAmountAsInt(index);
        }

        @Override
        public int getCapacity(int index, BCFluidStack resource) {
            return handler.getCapacityAsInt(index, resourceOf(resource));
        }

        @Override
        public boolean isValid(int index, BCFluidStack resource) {
            return !resource.isEmpty() && handler.isValid(index, resourceOf(resource));
        }

        @Override
        public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return handler.insert(index, resourceOf(resource), amount, NeoForgeTransfer.unwrap(transaction));
        }

        @Override
        public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return handler.extract(index, resourceOf(resource), amount, NeoForgeTransfer.unwrap(transaction));
        }

        @Override
        public String toString() {
            return "BuildCraft view of " + handler;
        }
    }
    //?} else {
    /*@Nullable
    public static IFluidHandler toNeo(@Nullable BCFluidStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromClassic view) {
            return view.handler;
        }
        return new ToClassic(storage);
    }

    @Nullable
    public static BCFluidStorage fromNeo(@Nullable IFluidHandler handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToClassic adapter) {
            return adapter.storage;
        }
        return new FromClassic(handler);
    }

    private record ToClassic(BCFluidStorage storage) implements IFluidHandler {
        @Override
        public int getTanks() {
            return storage.size();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return NeoForgeFluids.toNeo(storage.getStack(tank));
        }

        @Override
        public int getTankCapacity(int tank) {
            return storage.getCapacity(tank, BCFluidStack.EMPTY);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return storage.isValid(tank, NeoForgeFluids.fromNeo(stack));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                int filled = storage.insert(NeoForgeFluids.fromNeo(resource), resource.getAmount(), tx);
                if (action.execute()) {
                    tx.commit();
                }
                return filled;
            }
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            BCFluidStack wanted = NeoForgeFluids.fromNeo(resource);
            for (int i = 0; i < storage.size(); i++) {
                if (BCFluidStack.isSameFluidSameComponents(storage.getStack(i), wanted)) {
                    return drainIndex(i, wanted, resource.getAmount(), action);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < storage.size(); i++) {
                BCFluidStack held = storage.getStack(i);
                if (!held.isEmpty()) {
                    return drainIndex(i, held, maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }

        private FluidStack drainIndex(int index, BCFluidStack fluid, int amount, FluidAction action) {
            if (amount <= 0) {
                return FluidStack.EMPTY;
            }
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                int drained = storage.extract(index, fluid, amount, tx);
                if (action.execute()) {
                    tx.commit();
                }
                return drained <= 0 ? FluidStack.EMPTY : NeoForgeFluids.toNeo(fluid.copyWithAmount(drained));
            }
        }
    }

    private static final class FromClassic implements BCFluidStorage {
        final IFluidHandler handler;
        private final List<Runnable> pending = new ArrayList<>();
        private final BCSnapshotJournal<Integer> journal = new BCSnapshotJournal<>() {
            @Override
            protected Integer createSnapshot() {
                return pending.size();
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                pending.subList(snapshot, pending.size()).clear();
            }

            @Override
            protected void onRootCommit(Integer originalState) {
                List<Runnable> replay = new ArrayList<>(pending);
                pending.clear();
                replay.forEach(Runnable::run);
            }
        };

        FromClassic(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public BCFluidStack getStack(int index) {
            return NeoForgeFluids.fromNeo(handler.getFluidInTank(index)).copy();
        }

        @Override
        public int getCapacity(int index, BCFluidStack resource) {
            return handler.getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, BCFluidStack resource) {
            return !resource.isEmpty() && handler.isFluidValid(index, NeoForgeFluids.toNeo(resource));
        }

        @Override
        public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            if (index != 0 || resource.isEmpty() || amount <= 0) {
                return 0;
            }
            FluidStack offer = NeoForgeFluids.toNeo(resource.copyWithAmount(amount));
            int filled = handler.fill(offer.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                journal.updateSnapshots(transaction);
                FluidStack real = offer.copyWithAmount(filled);
                pending.add(() -> {
                    int done = handler.fill(real.copy(), IFluidHandler.FluidAction.EXECUTE);
                    if (done != filled) {
                        BCLog.logger.warn("[platform] {} took {} of a simulated fill of {}", handler, done, real);
                    }
                });
            }
            return filled;
        }

        @Override
        public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            FluidStack request = NeoForgeFluids.toNeo(resource.copyWithAmount(amount));
            FluidStack simulated = handler.drain(request.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty() || !FluidStack.isSameFluidSameComponents(simulated, request)) {
                return 0;
            }
            int drained = simulated.getAmount();
            journal.updateSnapshots(transaction);
            FluidStack real = request.copyWithAmount(drained);
            pending.add(() -> {
                FluidStack done = handler.drain(real.copy(), IFluidHandler.FluidAction.EXECUTE);
                if (done.getAmount() != drained) {
                    BCLog.logger.warn("[platform] {} gave {} of a simulated drain of {}", handler, done.getAmount(), real);
                }
            });
            return drained;
        }
    }
    *///?}
}
//?}
