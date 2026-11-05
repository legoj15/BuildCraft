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
/*import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import buildcraft.lib.transfer.DeferredFluidCalls;
import buildcraft.platform.transfer.BCTransaction;
*///?}

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.transfer.AdapterCache;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * The two-way adapter between {@link BCFluidStorage} and NeoForge's fluid handler type:
 * {@code ResourceHandler<FluidResource>} on 1.21.10+, the classic {@code IFluidHandler} on 1.21.1. Each direction
 * unwraps the other's adapter, so a BuildCraft storage that went out as a capability and came back through a lookup
 * is the same object again. Each direction also hands out one adapter per wrapped object ({@link AdapterCache}), so
 * a BuildCraft storage's capability is the same handler on every query, as the storage itself was before the platform
 * layer, and a steady neighbour looked up every tick is not wrapped again. Stacks cross through {@link NeoForgeFluids}.
 *
 * <p><b>1.21.1.</b> A BuildCraft storage seen as an {@code IFluidHandler} runs each call in a transaction of its own
 * ({@link LegacyTransactions#openInCurrent}), committed unless the call simulates. It answers as BuildCraft's classic
 * tanks did before the platform layer: {@code fill} and {@code drain(FluidStack)} are the storage's resource-wide
 * calls, which on 1.21.1 stop at the first index that moves anything (see {@link BCFluidStorage}), and
 * {@code drain(int)} drains the first index that gives anything (a fuelled iron engine gives its residue). A classic
 * handler seen as a {@link BCFluidStorage} can only simulate inside a transaction, so its view answers by simulating
 * and replays the real calls when the root transaction commits ({@code DeferredFluidCalls}, which also keeps a second
 * call in one transaction from promising the same fluid twice). Classic fills are not per tank, so the view takes
 * every insert through index 0 (the handler spreads it) and refuses the other indices; an extract at any index is a
 * fluid-matched drain of the whole handler, and each resource-wide call is one handler call.
 */
public final class NeoForgeFluidStorages {
    private NeoForgeFluidStorages() {}

    //? if >=1.21.10 {
    private static final AdapterCache<BCFluidStorage, ToNeo> TO_NEO = new AdapterCache<>(ToNeo::new);
    private static final AdapterCache<ResourceHandler<FluidResource>, FromNeo> FROM_NEO =
        new AdapterCache<>(FromNeo::new);

    /** The NeoForge handler for a BuildCraft storage (null stays null). */
    @Nullable
    public static ResourceHandler<FluidResource> toNeo(@Nullable BCFluidStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromNeo view) {
            return view.handler;
        }
        return TO_NEO.get(storage);
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
        return FROM_NEO.get(handler);
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
            // No precondition check: each BuildCraft storage answers an empty or negative offer itself (the tanks
            // answer 0). Before the platform layer the tanks inherited NeoForge's IllegalArgumentException; only a
            // caller breaking the ResourceHandler contract can tell the difference.
            return storage.insert(index, stackOf(resource, 1), amount, NeoForgeTransfer.wrap(transaction));
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return storage.extract(index, stackOf(resource, 1), amount, NeoForgeTransfer.wrap(transaction));
        }

        // The resource-wide calls go to the storage's own, so a storage that answers them itself keeps its answer.
        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            return storage.insert(stackOf(resource, 1), amount, NeoForgeTransfer.wrap(transaction));
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            return storage.extract(stackOf(resource, 1), amount, NeoForgeTransfer.wrap(transaction));
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
        public long getCapacityAsLong(int index, BCFluidStack resource) {
            return handler.getCapacityAsLong(index, resourceOf(resource));
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

        // The resource-wide calls are the handler's own (what BuildCraft called before the platform layer), so a
        // handler that overrides NeoForge's index-by-index default keeps its answer.
        @Override
        public int insert(BCFluidStack resource, int amount, BCTransactionContext transaction) {
            BCFluidStorage.checkNonEmptyNonNegative(resource, amount);
            return handler.insert(resourceOf(resource), amount, NeoForgeTransfer.unwrap(transaction));
        }

        @Override
        public int extract(BCFluidStack resource, int amount, BCTransactionContext transaction) {
            BCFluidStorage.checkNonEmptyNonNegative(resource, amount);
            return handler.extract(resourceOf(resource), amount, NeoForgeTransfer.unwrap(transaction));
        }

        @Override
        public String toString() {
            return "BuildCraft view of " + handler;
        }
    }
    //?} else {
    /*private static final AdapterCache<BCFluidStorage, ToClassic> TO_CLASSIC = new AdapterCache<>(ToClassic::new);
    private static final AdapterCache<IFluidHandler, FromClassic> FROM_CLASSIC = new AdapterCache<>(FromClassic::new);

    @Nullable
    public static IFluidHandler toNeo(@Nullable BCFluidStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromClassic view) {
            return view.handler;
        }
        return TO_CLASSIC.get(storage);
    }

    @Nullable
    public static BCFluidStorage fromNeo(@Nullable IFluidHandler handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToClassic adapter) {
            return adapter.storage;
        }
        return FROM_CLASSIC.get(handler);
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
            if (resource.isEmpty() || resource.getAmount() <= 0) {
                return FluidStack.EMPTY;
            }
            BCFluidStack wanted = NeoForgeFluids.fromNeo(resource);
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                int drained = storage.extract(wanted, resource.getAmount(), tx);
                if (action.execute()) {
                    tx.commit();
                }
                return drained <= 0 ? FluidStack.EMPTY : NeoForgeFluids.toNeo(wanted.copyWithAmount(drained));
            }
        }

        /^* The first index that gives anything: one that holds fluid but will not give it (an iron engine's fuel and
         *  coolant) is passed over, as the classic handlers this replaces never offered it. ^/
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            for (int i = 0; i < storage.size(); i++) {
                BCFluidStack held = storage.getStack(i);
                if (held.isEmpty()) {
                    continue;
                }
                try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                    int drained = storage.extract(i, held, maxDrain, tx);
                    if (drained > 0) {
                        if (action.execute()) {
                            tx.commit();
                        }
                        return NeoForgeFluids.toNeo(held.copyWithAmount(drained));
                    }
                }
            }
            return FluidStack.EMPTY;
        }
    }

    private static final class FromClassic implements BCFluidStorage, DeferredFluidCalls.Owner {
        final IFluidHandler handler;
        private final DeferredFluidCalls calls;

        FromClassic(IFluidHandler handler) {
            this.handler = handler;
            this.calls = new DeferredFluidCalls(handler,
                (stack, simulate) -> handler.fill(NeoForgeFluids.toNeo(stack), action(simulate)),
                (stack, simulate) -> {
                    FluidStack request = NeoForgeFluids.toNeo(stack);
                    FluidStack drained = handler.drain(request.copy(), action(simulate));
                    return FluidStack.isSameFluidSameComponents(drained, request) ? drained.getAmount() : 0;
                });
        }

        @Override
        public DeferredFluidCalls deferredCalls() {
            return calls;
        }

        private static IFluidHandler.FluidAction action(boolean simulate) {
            return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
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
            return index == 0 ? calls.fill(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
            return calls.drain(resource, amount, transaction);
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
    *///?}
}
//?}
