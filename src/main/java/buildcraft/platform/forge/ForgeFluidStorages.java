/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import javax.annotation.Nullable;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.transfer.AdapterCache;
import buildcraft.lib.transfer.DeferredFluidCalls;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

/^*
 * The two-way adapter between {@link BCFluidStorage} and Forge's {@code IFluidHandler}, the Forge twin of
 * {@code NeoForgeFluidStorages} on 1.21.1 (the handler has the same seven methods; its stack argument is Forge's own
 * NBT-based {@code FluidStack}, which crosses through {@link ForgeFluidStacks}). Each direction unwraps the other's
 * adapter, so a BuildCraft storage that went out as a capability and came back through a lookup is the same object
 * again. Each direction also hands out one adapter per wrapped object ({@link AdapterCache}), so a BuildCraft
 * storage's capability is the same handler on every query while that handler is held, and a lookup made while an
 * earlier view of the same handler is in use gives that view.
 *
 * <p>A BuildCraft storage seen as an {@code IFluidHandler} runs each call in a transaction of its own
 * ({@link LegacyTransactions#openInCurrent}), committed unless the call simulates. It answers as BuildCraft's classic
 * tanks did before the platform layer: {@code fill} and {@code drain(FluidStack)} are the storage's resource-wide
 * calls (see {@link BCFluidStorage}, whose {@code spansIndices} is left at its 26.x value here: Forge 26.x is as new a
 * port as the NeoForge 26.x nodes, with no earlier BuildCraft handler behaviour to keep), and {@code drain(int)} drains
 * the first index that gives anything (a fuelled iron engine gives its residue). A classic handler seen as a
 * {@link BCFluidStorage} can only simulate inside a transaction, so its view answers by simulating and replays the
 * real calls when the root transaction commits ({@code DeferredFluidCalls}, which also keeps a second call in one
 * transaction from promising the same fluid twice). Classic fills are not per tank, so the view takes every insert
 * through index 0 (the handler spreads it) and refuses the other indices; an extract at any index is a fluid-matched
 * drain of the whole handler, and each resource-wide call is one handler call. That queue is per view.
 *
 * <p><b>Fluid equality</b> on the Forge side is {@code FluidStack.isFluidEqual} (fluid and NBT tag), where the NeoForge
 * arm compares fluid and components. BuildCraft's stacks never carry a tag ({@link ForgeFluidStacks}), so a foreign
 * handler holding a tagged fluid matches no BuildCraft request for it and gives nothing.
 ^/
public final class ForgeFluidStorages {
    private ForgeFluidStorages() {}

    private static final AdapterCache<BCFluidStorage, ToClassic> TO_CLASSIC = new AdapterCache<>(ToClassic::new);
    private static final AdapterCache<IFluidHandler, FromClassic> FROM_CLASSIC = new AdapterCache<>(FromClassic::new);

    /^* The Forge handler for a BuildCraft storage (null stays null). ^/
    @Nullable
    public static IFluidHandler toForge(@Nullable BCFluidStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromClassic view) {
            return view.handler;
        }
        return TO_CLASSIC.get(storage);
    }

    /^* The BuildCraft view of a Forge handler (null stays null). ^/
    @Nullable
    public static BCFluidStorage fromForge(@Nullable IFluidHandler handler) {
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
            return ForgeFluidStacks.toForge(storage.getStack(tank));
        }

        @Override
        public int getTankCapacity(int tank) {
            return storage.getCapacity(tank, BCFluidStack.EMPTY);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return storage.isValid(tank, ForgeFluidStacks.fromForge(stack));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                int filled = storage.insert(ForgeFluidStacks.fromForge(resource), resource.getAmount(), tx);
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
            BCFluidStack wanted = ForgeFluidStacks.fromForge(resource);
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                int drained = storage.extract(wanted, resource.getAmount(), tx);
                if (action.execute()) {
                    tx.commit();
                }
                return drained <= 0 ? FluidStack.EMPTY : ForgeFluidStacks.toForge(wanted.copyWithAmount(drained));
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
                        return ForgeFluidStacks.toForge(held.copyWithAmount(drained));
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
            this.calls = new DeferredFluidCalls(this, handler,
                (stack, simulate) -> handler.fill(ForgeFluidStacks.toForge(stack), action(simulate)),
                (stack, simulate) -> {
                    FluidStack request = ForgeFluidStacks.toForge(stack);
                    FluidStack drained = handler.drain(request.copy(), action(simulate));
                    return drained.isFluidEqual(request) ? drained.getAmount() : 0;
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
            return ForgeFluidStacks.fromForge(handler.getFluidInTank(index)).copy();
        }

        @Override
        public int getCapacity(int index, BCFluidStack resource) {
            return handler.getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, BCFluidStack resource) {
            return !resource.isEmpty() && handler.isFluidValid(index, ForgeFluidStacks.toForge(resource));
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
}
*///?}
