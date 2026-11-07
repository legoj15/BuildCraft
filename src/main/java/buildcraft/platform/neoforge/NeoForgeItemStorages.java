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
//?} else {
/*import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import buildcraft.lib.transfer.AdapterCache;
import buildcraft.platform.transfer.BCModifiableItemStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;
*///?}

import buildcraft.platform.transfer.BCItemStorage;

/**
 * The two-way adapter between {@link BCItemStorage} and NeoForge's item handler type: {@code ResourceHandler<ItemResource>}
 * on 1.21.10+ (the item kind of {@link NeoForgeStorageAdapter}), the classic {@code IItemHandler} on 1.21.1. Each
 * direction unwraps the other's adapter, so a BuildCraft storage that went out as a capability and came back through a
 * lookup is the same object again.
 *
 * <p><b>1.21.1.</b> A BuildCraft storage seen as an {@code IItemHandler} runs each call in a transaction of its own
 * ({@link LegacyTransactions#openInCurrent}), committed unless the call simulates. A classic handler seen as a
 * {@link BCItemStorage} can only simulate inside a transaction, so its view answers every insert and extract by
 * simulating and replays the real calls, in order, when the root transaction commits (none if it aborts). The owed
 * calls are kept per slot of what the handler is a view of ({@code ClassicSlotLedger}), so a second call on a slot,
 * through this view or another lookup's fresh vanilla wrapper of the same container, is simulated together with what
 * is already owed there: the slot is never promised twice. A call on a slot that owes the other direction, or another
 * item, is refused, since the handler cannot be asked about both at once. Each direction also hands out one adapter
 * per wrapped object while it is held ({@link AdapterCache}). A {@link BCModifiableItemStorage} (the
 * plain BuildCraft inventories, and the empty storage the Quarry and Mining Well expose in place of
 * {@code EmptyItemHandler}) goes out as an {@code IItemHandlerModifiable}, as it always did: NeoForge's own
 * {@code SlotItemHandler} casts to it. Its {@code getStackInSlot} is the stack the storage holds, not a copy, as
 * BuildCraft's classic handlers returned it.
 */
public final class NeoForgeItemStorages {
    private NeoForgeItemStorages() {}

    //? if >=1.21.10 {
    private static final Kind KIND = new Kind();

    /** The NeoForge handler for a BuildCraft storage (null stays null). */
    @Nullable
    public static ResourceHandler<ItemResource> toNeo(@Nullable BCItemStorage storage) {
        return KIND.toNeoHandler(storage);
    }

    /** The BuildCraft view of a NeoForge handler (null stays null). */
    @Nullable
    public static BCItemStorage fromNeo(@Nullable ResourceHandler<ItemResource> handler) {
        return KIND.fromNeoHandler(handler);
    }

    /**
     * The item kind of {@link NeoForgeStorageAdapter}. Amounts are counts on both sides, and every query gets a fresh
     * adapter (they compare equal over what they wrap).
     */
    private static final class Kind extends NeoForgeStorageAdapter<ItemStack, ItemResource, BCItemStorage> {
        @Override
        boolean isEmpty(ItemStack resource) {
            return resource.isEmpty();
        }

        @Override
        ItemResource blank() {
            return ItemResource.EMPTY;
        }

        @Override
        ItemResource variantOf(ItemStack resource) {
            return ItemResource.of(resource);
        }

        @Override
        ItemStack stackOf(ItemResource resource, int amount) {
            return resource.isEmpty() || amount <= 0 ? ItemStack.EMPTY : resource.toStack(amount);
        }

        @Override
        long amountAt(BCItemStorage storage, int index) {
            return storage.getStack(index).getCount();
        }

        @Override
        ResourceHandler<ItemResource> outgoing(BCItemStorage storage) {
            return new ToNeo<>(this, storage);
        }

        @Override
        BCItemStorage incoming(ResourceHandler<ItemResource> handler) {
            return new FromNeo(handler);
        }
    }

    private static final class FromNeo extends NeoForgeStorageAdapter.View<ItemStack, ItemResource, BCItemStorage>
        implements BCItemStorage {
        FromNeo(ResourceHandler<ItemResource> handler) {
            super(KIND, handler);
        }
    }
    //?} else {
    /*private static final AdapterCache<BCItemStorage, ToClassic> TO_CLASSIC = new AdapterCache<>(
        storage -> storage instanceof BCModifiableItemStorage modifiable
            ? new ToClassicModifiable(modifiable) : new ToClassic(storage));
    private static final AdapterCache<IItemHandler, FromClassic> FROM_CLASSIC = new AdapterCache<>(FromClassic::new);

    @Nullable
    public static IItemHandler toNeo(@Nullable BCItemStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromClassic view) {
            return view.handler;
        }
        return TO_CLASSIC.get(storage);
    }

    @Nullable
    public static BCItemStorage fromNeo(@Nullable IItemHandler handler) {
        if (handler == null) {
            return null;
        }
        if (handler instanceof ToClassic adapter) {
            return adapter.storage;
        }
        return FROM_CLASSIC.get(handler);
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

        FromClassic(IItemHandler handler) {
            this.handler = handler;
        }

        private static int plus(int owed, int amount) {
            return (int) Math.min((long) owed + amount, Integer.MAX_VALUE);
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
            ClassicSlotLedger.Slot slot = ClassicSlotLedger.slotOf(handler, index);
            int owed = slot.owed(true, resource);
            if (owed < 0) {
                return 0;
            }
            int asked = plus(owed, amount);
            int taken = asked - handler.insertItem(index, resource.copyWithCount(asked), true).getCount();
            int inserted = Math.min(amount, Math.max(0, taken - owed));
            if (inserted > 0) {
                slot.promise(handler, index, true, resource.copyWithCount(inserted), transaction);
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
            ClassicSlotLedger.Slot slot = ClassicSlotLedger.slotOf(handler, index);
            int owed = slot.owed(false, resource);
            if (owed < 0) {
                return 0;
            }
            ItemStack simulated = handler.extractItem(index, plus(owed, amount), true);
            if (simulated.isEmpty() || !ItemStack.isSameItemSameComponents(simulated, resource)) {
                return 0;
            }
            int extracted = Math.min(amount, Math.max(0, simulated.getCount() - owed));
            if (extracted > 0) {
                slot.promise(handler, index, false, resource.copyWithCount(extracted), transaction);
            }
            return extracted;
        }
    }
    *///?}
}
//?}
