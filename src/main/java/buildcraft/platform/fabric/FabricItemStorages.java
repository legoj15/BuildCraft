/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/^*
 * The two-way adapter between {@link BCItemStorage} and Fabric's {@code Storage<ItemVariant>}. Each direction unwraps
 * the other's adapter. A BuildCraft storage goes out as a {@link SlottedStorage}, one {@link SingleSlotStorage} per
 * index; a slotted Fabric storage comes back index for index.
 *
 * <p><b>Gap.</b> A Fabric storage that is not slotted has no stable indices, so its view is one virtual slot: it shows
 * the first non-blank view's contents, and insert and extract go to the storage as a whole.
 ^/
public final class FabricItemStorages {
    private FabricItemStorages() {}

    public static Storage<ItemVariant> toFabric(BCItemStorage storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof FromSlotted view) {
            return view.storage;
        }
        if (storage instanceof FromPlain view) {
            return view.storage;
        }
        return new ToFabric(storage);
    }

    public static BCItemStorage fromFabric(Storage<ItemVariant> storage) {
        if (storage == null) {
            return null;
        }
        if (storage instanceof ToFabric adapter) {
            return adapter.storage;
        }
        if (storage instanceof SlottedStorage<ItemVariant> slotted) {
            return new FromSlotted(slotted);
        }
        return new FromPlain(storage);
    }

    static int clamp(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
    }

    static ItemStack stackOf(ItemVariant variant, long amount) {
        int count = clamp(amount);
        return variant.isBlank() || count <= 0 ? ItemStack.EMPTY : variant.toStack(count);
    }

    private static final class ToFabric implements SlottedStorage<ItemVariant> {
        final BCItemStorage storage;

        ToFabric(BCItemStorage storage) {
            this.storage = storage;
        }

        @Override
        public int getSlotCount() {
            return storage.size();
        }

        @Override
        public SingleSlotStorage<ItemVariant> getSlot(int slot) {
            return new Slot(storage, slot);
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            ItemStack stack = resource.toStack();
            int amount = clamp(maxAmount);
            BCTransactionContext tx = FabricTransfer.wrap(transaction);
            int inserted = 0;
            // Stacking order, as BuildCraft's own insertStacking: occupied indices first, then empty ones.
            for (int pass = 0; pass < 2 && inserted < amount; pass++) {
                for (int i = 0; i < storage.size() && inserted < amount; i++) {
                    if (storage.getStack(i).isEmpty() == (pass == 1)) {
                        inserted += storage.insert(i, stack, amount - inserted, tx);
                    }
                }
            }
            return inserted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            ItemStack stack = resource.toStack();
            int amount = clamp(maxAmount);
            BCTransactionContext tx = FabricTransfer.wrap(transaction);
            int extracted = 0;
            for (int i = 0; i < storage.size() && extracted < amount; i++) {
                extracted += storage.extract(i, stack, amount - extracted, tx);
            }
            return extracted;
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            List<StorageView<ItemVariant>> views = new ArrayList<>(storage.size());
            for (int i = 0; i < storage.size(); i++) {
                views.add(new Slot(storage, i));
            }
            return views.iterator();
        }
    }

    private record Slot(BCItemStorage storage, int index) implements SingleSlotStorage<ItemVariant> {
        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            return storage.insert(index, resource.toStack(), clamp(maxAmount), FabricTransfer.wrap(transaction));
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            return storage.extract(index, resource.toStack(), clamp(maxAmount), FabricTransfer.wrap(transaction));
        }

        @Override
        public boolean isResourceBlank() {
            return storage.getStack(index).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            ItemStack stack = storage.getStack(index);
            return stack.isEmpty() ? ItemVariant.blank() : ItemVariant.of(stack);
        }

        @Override
        public long getAmount() {
            return storage.getStack(index).getCount();
        }

        @Override
        public long getCapacity() {
            return storage.getCapacity(index, storage.getStack(index));
        }
    }

    private static final class FromSlotted implements BCItemStorage {
        final SlottedStorage<ItemVariant> storage;

        FromSlotted(SlottedStorage<ItemVariant> storage) {
            this.storage = storage;
        }

        @Override
        public int size() {
            return storage.getSlotCount();
        }

        @Override
        public ItemStack getStack(int index) {
            SingleSlotStorage<ItemVariant> slot = storage.getSlot(index);
            return stackOf(slot.getResource(), slot.getAmount());
        }

        @Override
        public int getCapacity(int index, ItemStack resource) {
            // Fabric reports one capacity per slot, for what it holds; an item's own stack size still bounds it.
            int capacity = clamp(storage.getSlot(index).getCapacity());
            return resource.isEmpty() ? capacity : Math.min(capacity, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemStack resource) {
            // Gap: Fabric has no per-resource validity query, so this only says the slot takes insertions at all.
            return !resource.isEmpty() && storage.getSlot(index).supportsInsertion();
        }

        @Override
        public int insert(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return clamp(storage.getSlot(index).insert(ItemVariant.of(resource), amount, FabricTransfer.unwrap(transaction)));
        }

        @Override
        public int extract(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return clamp(storage.getSlot(index).extract(ItemVariant.of(resource), amount, FabricTransfer.unwrap(transaction)));
        }
    }

    private static final class FromPlain implements BCItemStorage {
        final Storage<ItemVariant> storage;

        FromPlain(Storage<ItemVariant> storage) {
            this.storage = storage;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getStack(int index) {
            for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
                return stackOf(view.getResource(), view.getAmount());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getCapacity(int index, ItemStack resource) {
            return resource.isEmpty() ? 64 : resource.getMaxStackSize();
        }

        @Override
        public boolean isValid(int index, ItemStack resource) {
            return !resource.isEmpty() && storage.supportsInsertion();
        }

        @Override
        public int insert(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return clamp(storage.insert(ItemVariant.of(resource), amount, FabricTransfer.unwrap(transaction)));
        }

        @Override
        public int extract(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            return clamp(storage.extract(ItemVariant.of(resource), amount, FabricTransfer.unwrap(transaction)));
        }
    }
}
*///?}
