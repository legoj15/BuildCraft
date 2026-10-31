/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.inventory;

import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;

import buildcraft.lib.inventory.filter.StackFilter;
import buildcraft.lib.misc.StackUtil;

/** Designates an {@link IItemTransactor} that is backed by a simple, static, array based inventory. */
public abstract class AbstractInvItemTransactor implements IItemTransactor {
    /** Returns {@link ItemStack#EMPTY} if it was empty, or the input stack if it was not. */
    @Nonnull
    public static ItemStack asValid(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return StackUtil.EMPTY;
        } else {
            return stack;
        }
    }

    @Nonnull
    protected abstract ItemStack insert(int slot, @Nonnull ItemStack stack, boolean simulate);

    @Nonnull
    protected abstract ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate);

    protected abstract int size();

    protected abstract boolean isEmpty(int slot);

    /** The stack in {@code slot} as the inventory itself reports it, which is more than {@link #extract} offers when
     *  the slot limits how much may leave at once. The default is what a simulated extract offers, exact for every
     *  inventory that lets a whole slot out; one that can limit it overrides this with a plain read. */
    @Nonnull
    protected ItemStack slotContents(int slot) {
        return extract(slot, StackFilter.ALL, 1, Integer.MAX_VALUE, true);
    }

    /** Takes what each slot will let out, one slot at a time, and hands every stack to {@code sink} as soon as it is
     *  out. A stack goes to {@code sink} only as far as the slot really gave it up, judged by re-reading the slot
     *  rather than by what the extraction claims: a slot that still holds everything it held gave nothing, so
     *  sending what it returned would duplicate it. A slot that lets only part out gives that part.
     *
     *  @return true if every slot is empty afterwards, false if anything is still in the inventory. */
    public final boolean drainInto(@Nonnull Consumer<ItemStack> sink) {
        boolean emptied = true;
        for (int slot = 0; slot < size(); slot++) {
            ItemStack before = slotContents(slot).copy();
            if (before.isEmpty()) {
                continue;
            }
            ItemStack offered = extract(slot, StackFilter.ALL, 1, Integer.MAX_VALUE, true);
            if (!offered.isEmpty()) {
                ItemStack taken = extract(slot, StackFilter.ALL, offered.getCount(), offered.getCount(), false);
                ItemStack after = slotContents(slot);
                boolean sameStack = !after.isEmpty() && ItemStack.isSameItemSameComponents(after, before);
                int gone = before.getCount() - (sameStack ? after.getCount() : 0);
                int out = Math.min(taken.getCount(), gone);
                if (out > 0) {
                    sink.accept(out == taken.getCount() ? taken : taken.copyWithCount(out));
                }
            }
            if (!isEmpty(slot)) {
                emptied = false;
            }
        }
        return emptied;
    }

    @Override
    @Nonnull
    public ItemStack insert(@Nonnull ItemStack stack, boolean allAtOnce, boolean simulate) {
        if (allAtOnce) {
            return insertAllAtOnce(stack, simulate);
        } else {
            return insertAnyAmount(stack, simulate);
        }
    }

    @Nonnull
    private ItemStack insertAnyAmount(@Nonnull ItemStack stack, boolean simulate) {
        // Works on a copy for the same reason as insertAllAtOnce: a simulated slot insert may shrink what it is handed.
        stack = stack.copy();
        int slotCount = size();
        IntArrayList emptySlots = new IntArrayList(slotCount);
        for (int slot = 0; slot < size(); slot++) {
            if (isEmpty(slot)) {
                emptySlots.add(slot);
            } else {
                stack = insert(slot, stack, simulate);
                if (stack.isEmpty()) return StackUtil.EMPTY;
            }
        }
        for (int slot : emptySlots.toIntArray()) {
            stack = insert(slot, stack, simulate);
            if (stack.isEmpty()) return StackUtil.EMPTY;
        }
        return stack;
    }

    @Nonnull
    private ItemStack insertAllAtOnce(@Nonnull ItemStack stack, boolean simulate) {
        // A slot insert may split or shrink the stack it is handed even when simulating (InventoryWrapper does), so
        // each pass gets its own copy: the caller's stack stays whole for the real pass and for a refusal.
        ItemStack input = asValid(stack);
        ItemStack remaining = input.copy();
        IntArrayList insertedSlots = new IntArrayList(size());
        IntArrayList emptySlots = new IntArrayList(size());
        for (int slot = 0; slot < size(); slot++) {
            if (isEmpty(slot)) {
                emptySlots.add(slot);
            } else {
                remaining = insert(slot, remaining, true);
                insertedSlots.add(slot);
                if (remaining.isEmpty()) break;
            }
        }
        for (int slot : emptySlots.toIntArray()) {
            remaining = insert(slot, remaining, true);
            insertedSlots.add(slot);
            if (remaining.isEmpty()) break;
        }
        if (!remaining.isEmpty()) {
            // Not everything fits and none of it was inserted, so the overflow is the whole input, not the part that
            // overran the simulated slots.
            return input.copy();
        }
        if (simulate) return StackUtil.EMPTY;
        ItemStack before = input.copy();
        for (int slot : insertedSlots.toIntArray()) {
            before = insert(slot, before, false);
        }
        if (!before.isEmpty()) {
            // We have a bad implementation that doesn't respect simulation properly- we are in an invalid state at this
            // point with no chance of recovery
            throw new IllegalStateException("Somehow inserting a lot of items at once failed when we thought it shouldn't! ("
                + getClass() + ")");
        }
        return StackUtil.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack extract(IStackFilter filter, int min, int max, boolean simulate) {
        if (min < 1) min = 1;
        if (min > max) return StackUtil.EMPTY;
        if (max < 0) return StackUtil.EMPTY;

        if (filter == null) {
            filter = StackFilter.ALL;
        }

        int slots = size();
        IntArrayList valids = new IntArrayList();
        int totalSize = 0;
        ItemStack toExtract = StackUtil.EMPTY;

        for (int slot = 0; slot < slots; slot++) {
            ItemStack possible = extract(slot, filter, 1, max - totalSize, true);
            if (!possible.isEmpty()) {
                if (toExtract.isEmpty()) {
                    toExtract = possible.copy();
                }
                if (StackUtil.canMerge(toExtract, possible)) {
                    totalSize += possible.getCount();
                    valids.add(slot);
                    if (totalSize >= max) {
                        break;
                    }
                }
            }
        }

        ItemStack total = StackUtil.EMPTY;
        if (min <= totalSize) {
            for (int slot : valids.toIntArray()) {
                ItemStack extracted = extract(slot, filter, 1, max - total.getCount(), simulate);
                if (total.isEmpty()) {
                    total = extracted.copy();
                } else {
                    total.grow(extracted.getCount());
                }
            }
        }
        return total;
    }

    /** @return True if the entire stack can be inserted into this inventory without any leftover. */
    public boolean canFullyAccept(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return true;
        ItemStack leftover = insert(stack.copy(), true, true);
        return leftover.isEmpty();
    }

    @Override
    public String toString() {
        ItemStack[] stacks = new ItemStack[size()];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = extract(i, StackFilter.ALL, 1, Integer.MAX_VALUE, true);
        }
        return Arrays.toString(stacks);
    }
}
