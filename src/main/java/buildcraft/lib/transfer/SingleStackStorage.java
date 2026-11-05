/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.transfer;

import java.util.Objects;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCModifiableItemStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * A one-slot {@link BCItemStorage} over a stack the owner keeps ({@link #getStack()}/{@link #setStack}): the loader-neutral
 * twin of NeoForge's {@code ItemStackResourceHandler}, with the same rules. It takes any item {@link #isValid(ItemStack)}
 * allows, up to the item's own stack size (never above {@link Item#ABSOLUTE_MAX_STACK_SIZE}), gives up whatever it
 * holds, and reports a committed change through {@link #onRootCommit}. It is also a {@link BCModifiableItemStorage}
 * (the 1.21.1 fuel slot of the stone engine was an {@code IItemHandlerModifiable}), whose
 * {@link #setStackInSlot} reports its change the same way.
 */
public abstract class SingleStackStorage implements BCModifiableItemStorage {
    private final BCSnapshotJournal<ItemStack> journal = new BCSnapshotJournal<>() {
        @Override
        protected ItemStack createSnapshot() {
            ItemStack original = getStack();
            setStack(original.copy());
            return original;
        }

        @Override
        protected void revertToSnapshot(ItemStack snapshot) {
            setStack(snapshot);
        }

        @Override
        protected void onRootCommit(ItemStack originalState) {
            SingleStackStorage.this.onRootCommit(originalState);
        }
    };

    /** The stack held; the storage may grow or shrink it in place inside a transaction. */
    protected abstract ItemStack getStack();

    protected abstract void setStack(ItemStack stack);

    /** Whether {@code resource} may go in at all. */
    protected boolean isValid(ItemStack resource) {
        return true;
    }

    /** The most of {@code resource} the slot holds ({@link Item#ABSOLUTE_MAX_STACK_SIZE} for an empty one). */
    protected int getCapacity(ItemStack resource) {
        return resource.isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE
            : Math.min(resource.getMaxStackSize(), Item.ABSOLUTE_MAX_STACK_SIZE);
    }

    /** After a root transaction changed the slot; {@code originalState} is the stack before it. */
    protected void onRootCommit(ItemStack originalState) {}

    @Override
    public final int size() {
        return 1;
    }

    /**
     * Whether {@code index} is the one slot. Past it, each node answers as the handler this class replaced did: NeoForge's
     * {@code ItemStackResourceHandler} on 1.21.10+ throws, and 1.21.1's classic fuel handler answered an empty slot that
     * takes nothing.
     */
    private boolean hasIndex(int index) {
        //? if >=1.21.10 {
        Objects.checkIndex(index, size());
        return true;
        //?} else {
        /*return index == 0;
        *///?}
    }

    @Override
    public ItemStack getStack(int index) {
        return hasIndex(index) ? getStack().copy() : ItemStack.EMPTY;
    }

    /** The held stack itself for slot 0, as the stone engine's classic fuel handler handed it out; any other index is
     *  empty. */
    @Override
    public ItemStack getStackInSlot(int index) {
        return index == 0 ? getStack() : ItemStack.EMPTY;
    }

    /** Overwrites the slot outside any transaction and reports the change through {@link #onRootCommit}; any index but
     *  the one slot is ignored, as the stone engine's classic fuel handler ignored it. */
    @Override
    public void setStackInSlot(int index, ItemStack stack) {
        if (index == 0) {
            ItemStack before = getStack();
            setStack(stack);
            onRootCommit(before);
        }
    }

    @Override
    public int getCapacity(int index, ItemStack resource) {
        if (!hasIndex(index)) {
            // The classic fuel handler's slot limit ignored the slot.
            return Item.ABSOLUTE_MAX_STACK_SIZE;
        }
        return resource.isEmpty() || isValid(resource) ? getCapacity(resource) : 0;
    }

    @Override
    public boolean isValid(int index, ItemStack resource) {
        return hasIndex(index) && !resource.isEmpty() && isValid(resource);
    }

    @Override
    public int insert(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
        if (!hasIndex(index) || resource.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemStack current = getStack();
        if ((current.isEmpty() || ItemStack.isSameItemSameComponents(current, resource)) && isValid(resource)) {
            int inserted = Math.min(amount, getCapacity(resource) - current.getCount());
            if (inserted > 0) {
                journal.updateSnapshots(transaction);
                current = getStack();
                if (current.isEmpty()) {
                    current = resource.copyWithCount(inserted);
                } else {
                    current.grow(inserted);
                }
                setStack(current);
                return inserted;
            }
        }
        return 0;
    }

    @Override
    public int extract(int index, ItemStack resource, int amount, BCTransactionContext transaction) {
        if (!hasIndex(index) || resource.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemStack current = getStack();
        if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, resource)) {
            int extracted = Math.min(current.getCount(), amount);
            if (extracted > 0) {
                journal.updateSnapshots(transaction);
                current = getStack();
                current.shrink(extracted);
                setStack(current);
                return extracted;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + getStack() + "]";
    }
}
