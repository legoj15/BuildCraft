/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import buildcraft.api.core.BCLog;
import buildcraft.platform.transfer.BCModifiableItemStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
*///?}

import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * The two-way adapter between {@link BCItemStorage} and NeoForge's item handler type: {@code ResourceHandler<ItemResource>}
 * on 1.21.10+, the classic {@code IItemHandler} on 1.21.1. Each direction unwraps the other's adapter, so a BuildCraft
 * storage that went out as a capability and came back through a lookup is the same object again.
 *
 * <p><b>1.21.1.</b> A BuildCraft storage seen as an {@code IItemHandler} runs each call in a transaction of its own
 * ({@link LegacyTransactions#openInCurrent}), committed unless the call simulates. A classic handler seen as a
 * {@link BCItemStorage} can only simulate inside a transaction, so its view answers every insert and extract by
 * simulating and replays the real calls, in order, when the root transaction commits (none if it aborts). Two changes
 * to the same slot in one transaction are each simulated against the slot as it was; BuildCraft's callers touch a
 * slot once per transaction. A {@link BCModifiableItemStorage} (the plain BuildCraft inventories, and the empty storage
 * the Quarry and Mining Well expose in place of {@code EmptyItemHandler}) goes out as an {@code IItemHandlerModifiable},
 * as it always did: NeoForge's own {@code SlotItemHandler} casts to it. Its {@code getStackInSlot} is the stack the
 * storage holds, not a copy, as BuildCraft's classic handlers returned it.
 */
public final class NeoForgeItemStorages {
    private NeoForgeItemStorages() {}

    //? if >=1.21.10 {
    /** The NeoForge handler for a BuildCraft storage (null stays null). */
    @Nullable
    public static ResourceHandler<ItemResource> toNeo(@Nullable BCItemStorage storage) {
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
    public static BCItemStorage fromNeo(@Nullable ResourceHandler<ItemResource> handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToNeo adapter) {
            return adapter.storage;
        }
        return new FromNeo(handler);
    }

    static ItemStack stackOf(ItemResource resource) {
        return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack();
    }

    static ItemResource resourceOf(ItemStack stack) {
        return stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack);
    }

    private record ToNeo(BCItemStorage storage) implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return storage.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return resourceOf(storage.getStack(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return storage.getStack(index).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return storage.getCapacity(index, stackOf(resource));
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return storage.isValid(index, stackOf(resource));
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            // No precondition check: BuildCraft storages answer 0 to an empty or negative offer, as they did as handlers.
            return storage.insert(index, stackOf(resource), amount, NeoForgeTransfer.wrap(transaction));
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return storage.extract(index, stackOf(resource), amount, NeoForgeTransfer.wrap(transaction));
        }
    }

    private record FromNeo(ResourceHandler<ItemResource> handler) implements BCItemStorage {
        @Override
        public int size() {
            return handler.size();
        }

        @Override
        public ItemStack getStack(int index) {
            ItemResource resource = handler.getResource(index);
            int amount = handler.getAmountAsInt(index);
            return resource.isEmpty() || amount <= 0 ? ItemStack.EMPTY : resource.toStack(amount);
        }

        @Override
        public int getCapacity(int index, ItemStack resource) {
            return handler.getCapacityAsInt(index, resourceOf(resource));
        }

        @Override
        public boolean isValid(int index, ItemStack resource) {
            return !resource.isEmpty() && handler.isValid(index, ItemResource.of(resource));
        }

        @Override
        public int insert(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return handler.insert(index, ItemResource.of(resource), amount, NeoForgeTransfer.unwrap(transaction));
        }

        @Override
        public int extract(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return handler.extract(index, ItemResource.of(resource), amount, NeoForgeTransfer.unwrap(transaction));
        }
    }
    //?} else {
    /*@Nullable
    public static IItemHandler toNeo(@Nullable BCItemStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromClassic view) {
            return view.handler;
        }
        if (storage instanceof BCModifiableItemStorage modifiable) {
            return new ToClassicModifiable(modifiable);
        }
        return new ToClassic(storage);
    }

    @Nullable
    public static BCItemStorage fromNeo(@Nullable IItemHandler handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToClassic adapter) {
            return adapter.storage;
        }
        return new FromClassic(handler);
    }

    private static class ToClassic implements IItemHandler {
        final BCItemStorage storage;

        ToClassic(BCItemStorage storage) {
            this.storage = storage;
        }

        @Override
        public int getSlots() {
            return storage.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return storage.getStack(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int inserted;
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                inserted = storage.insert(slot, stack, stack.getCount(), tx);
                if (!simulate) {
                    tx.commit();
                }
            }
            return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack current = storage.getStack(slot);
            if (current.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int extracted;
            try (BCTransaction tx = LegacyTransactions.openInCurrent()) {
                extracted = storage.extract(slot, current, amount, tx);
                if (!simulate) {
                    tx.commit();
                }
            }
            return extracted <= 0 ? ItemStack.EMPTY : current.copyWithCount(extracted);
        }

        @Override
        public int getSlotLimit(int slot) {
            return storage.getCapacity(slot, ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return storage.isValid(slot, stack);
        }

        // Value equality over the storage, as the record this class replaced had.
        @Override
        public boolean equals(Object other) {
            return other != null && other.getClass() == getClass() && ((ToClassic) other).storage.equals(storage);
        }

        @Override
        public int hashCode() {
            return storage.hashCode();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + storage + "]";
        }
    }

    private static final class ToClassicModifiable extends ToClassic implements IItemHandlerModifiable {
        private final BCModifiableItemStorage modifiable;

        ToClassicModifiable(BCModifiableItemStorage storage) {
            super(storage);
            this.modifiable = storage;
        }

        // The live stack, as 1.21.1's BuildCraft handlers returned it (IItemHandler asks callers not to modify it).
        @Override
        public ItemStack getStackInSlot(int slot) {
            return modifiable.getStackInSlot(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            modifiable.setStackInSlot(slot, stack);
        }
    }

    private static final class FromClassic implements BCItemStorage {
        final IItemHandler handler;
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

        FromClassic(IItemHandler handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemStack getStack(int index) {
            return handler.getStackInSlot(index).copy();
        }

        @Override
        public int getCapacity(int index, ItemStack resource) {
            int limit = handler.getSlotLimit(index);
            return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemStack resource) {
            return !resource.isEmpty() && handler.isItemValid(index, resource);
        }

        @Override
        public int insert(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            ItemStack left = handler.insertItem(index, resource.copyWithCount(amount), true);
            int inserted = amount - left.getCount();
            if (inserted > 0) {
                journal.updateSnapshots(transaction);
                ItemStack real = resource.copyWithCount(inserted);
                pending.add(() -> {
                    ItemStack rest = handler.insertItem(index, real, false);
                    if (!rest.isEmpty()) {
                        BCLog.logger.warn("[platform] {} refused {} of a simulated insert into slot {}: {} lost",
                            handler, rest.getCount(), index, rest);
                    }
                });
            }
            return inserted;
        }

        @Override
        public int extract(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            ItemStack current = handler.getStackInSlot(index);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, resource)) {
                return 0;
            }
            ItemStack simulated = handler.extractItem(index, amount, true);
            if (simulated.isEmpty() || !ItemStack.isSameItemSameComponents(simulated, resource)) {
                return 0;
            }
            int extracted = simulated.getCount();
            journal.updateSnapshots(transaction);
            pending.add(() -> {
                ItemStack real = handler.extractItem(index, extracted, false);
                if (real.getCount() != extracted) {
                    BCLog.logger.warn("[platform] {} gave {} of a simulated extract of {} from slot {}",
                        handler, real.getCount(), extracted, index);
                }
            });
            return extracted;
        }
    }
    *///?}
}
//?}
