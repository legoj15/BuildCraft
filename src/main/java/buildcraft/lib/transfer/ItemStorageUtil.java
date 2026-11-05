/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.transfer;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCModifiableItemStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

/** Helpers over {@link BCItemStorage}, the loader-neutral counterparts of NeoForge's {@code ResourceHandlerUtil}. */
public final class ItemStorageUtil {
    private ItemStorageUtil() {}

    /** A storage with no slots: present (pipes connect to it) but never holds anything. NeoForge's
     *  {@code EmptyResourceHandler} / the classic {@code EmptyItemHandler}. It is modifiable with a no-op set, as
     *  {@code EmptyItemHandler} is an {@code IItemHandlerModifiable}: the Quarry and the Mining Well expose it, and on
     *  1.21.1 a foreign {@code SlotItemHandler} over them casts to the modifiable type to set a slot. */
    public static final BCModifiableItemStorage EMPTY = new BCModifiableItemStorage() {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public void setStackInSlot(int index, ItemStack stack) {}

        @Override
        public ItemStack getStack(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getCapacity(int index, ItemStack resource) {
            return 0;
        }

        @Override
        public boolean isValid(int index, ItemStack resource) {
            return false;
        }

        @Override
        public int insert(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
            return 0;
        }

        @Override
        public String toString() {
            return "ItemStorageUtil.EMPTY";
        }
    };

    /**
     * Inserts up to {@code amount} of {@code resource} across {@code storage}: indices that already hold something first,
     * then the empty ones, each in index order. With a null {@code transaction} it runs in a root transaction of its own
     * and commits, as NeoForge's {@code ResourceHandlerUtil.insertStacking(handler, resource, amount, null)} does
     * (characterised by {@code ItemStorageAdapterTester}); otherwise it nests in the given one and commits into it.
     *
     * @return how many went in; 0 for a null storage or a zero amount.
     * @throws IllegalArgumentException for an empty resource or a negative amount, as NeoForge's does.
     */
    public static int insertStacking(@Nullable BCItemStorage storage, ItemStack resource, int amount,
        @Nullable BCTransactionContext transaction) {
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Expected resource to be non-empty: " + resource);
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Expected amount to be non-negative: " + amount);
        }
        if (storage == null || amount == 0) {
            return 0;
        }
        try (BCTransaction tx = BCTransaction.open(transaction)) {
            int inserted = 0;
            int size = storage.size();
            for (int index = 0; index < size && inserted < amount; index++) {
                if (!storage.getStack(index).isEmpty()) {
                    inserted += storage.insert(index, resource, amount - inserted, tx);
                }
            }
            for (int index = 0; index < size && inserted < amount; index++) {
                if (storage.getStack(index).isEmpty()) {
                    inserted += storage.insert(index, resource, amount - inserted, tx);
                }
            }
            tx.commit();
            return inserted;
        }
    }

    /** Adds a copy of every non-empty index of {@code storage} to {@code list}. */
    public static void addAll(BCItemStorage storage, List<ItemStack> list) {
        for (int i = 0; i < storage.size(); i++) {
            ItemStack stack = storage.getStack(i);
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
    }

    /** Whether every index is empty. */
    public static boolean isEmpty(BCItemStorage storage) {
        for (int i = 0; i < storage.size(); i++) {
            if (!storage.getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
