/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import buildcraft.api.core.IStackFilter;
import buildcraft.lib.inventory.AbstractInvItemTransactor;
import buildcraft.lib.misc.INBTSerializable;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackNbt;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.item.StackInsertionFunction.InsertionResult;

import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

@SuppressWarnings("this-escape")
public class ItemHandlerSimple extends AbstractInvItemTransactor
    implements IItemHandlerAdv, INBTSerializable<CompoundTag> {

    /**
     * One journal per slot, created lazily (a slot only needs one once a transaction touches it). The change callback
     * fires from {@link SlotJournal#onRootCommit}, i.e. once per slot per committed root transaction and only if the
     * slot really differs from its pre-transaction contents — never mid-transaction, where a later rollback would
     * leave the tile told about a change that never happened (and dirtied for nothing). Mirrors NeoForge's own
     * {@code StacksResourceHandler}; on 1.21.1 BuildCraft's own transaction manager runs the same journal.
     */
    @Nullable
    private SlotJournal[] journals;

    private final class SlotJournal extends BCSnapshotJournal<ItemStack> {
        private final int index;

        SlotJournal(int index) {
            this.index = index;
        }

        @Override
        protected ItemStack createSnapshot() {
            return stacks.get(index).copy();
        }

        @Override
        protected void revertToSnapshot(ItemStack snapshot) {
            stacks.set(index, asValid(snapshot));
        }

        @Override
        protected void onRootCommit(ItemStack originalState) {
            ItemStack now = stacks.get(index);
            if (callback != null && !ItemStack.matches(originalState, now)) {
                callback.onStackChange(ItemHandlerSimple.this, index, asValid(originalState), now);
            }
        }
    }

    /** Snapshot {@code index} into {@code tx} before changing it; without a transaction, report the change now. */
    private void changeSlot(int index, ItemStack before, ItemStack after, @Nullable BCTransactionContext tx) {
        if (tx == null) {
            setStackInternal(index, after);
            if (callback != null) {
                callback.onStackChange(this, index, before, asValid(after));
            }
            return;
        }
        if (journals == null) {
            journals = new SlotJournal[stacks.size()];
        }
        SlotJournal journal = journals[index];
        if (journal == null) {
            journal = journals[index] = new SlotJournal(index);
        }
        journal.updateSnapshots(tx);
        setStackInternal(index, after);
    }


    private StackInsertionChecker checker;
    private StackInsertionFunction inserter;

    @Nullable
    private StackChangeCallback callback;

    public final NonNullList<ItemStack> stacks;

    /** Per-slot capacity reported by {@link #getCapacityAsLong} and {@link #getSlotLimit},
     * consulted by {@link buildcraft.lib.gui.slot.SlotBase#getMaxStackSize} when vanilla decides
     * how many items a click or shift-click may deposit. Defaults to 64; the
     * {@code (size, maxStackSize)} constructor lowers it for tiles like the heat
     * exchanger that want true single-bucket slots. Without this the slot widget
     * would let players drop a 64-stack into a 1-bucket slot — and the auto-fill
     * loop would then replace the whole stack with a single filled bucket. */
    private int slotCapacity = 64;

    public ItemHandlerSimple(int size) {
        this(size, (slot, stack) -> true, StackInsertionFunction.getDefaultInserter(), null);
    }

    public ItemHandlerSimple(int size, int maxStackSize) {
        this(size);
        setLimitedInsertor(maxStackSize);
        this.slotCapacity = maxStackSize;
    }

    public ItemHandlerSimple(int size, @Nullable StackChangeCallback callback) {
        this(size, (slot, stack) -> true, StackInsertionFunction.getDefaultInserter(), callback);
    }

    public ItemHandlerSimple(int size, StackInsertionChecker checker, StackInsertionFunction insertionFunction,
        @Nullable StackChangeCallback callback) {
        stacks = NonNullList.withSize(size, StackUtil.EMPTY);
        this.checker = checker;
        this.inserter = insertionFunction;
        this.callback = callback;
    }

    public void setChecker(StackInsertionChecker checker) {
        this.checker = checker;
    }

    public void setInsertor(StackInsertionFunction insertor) {
        this.inserter = insertor;
    }

    public void setLimitedInsertor(int maxStackSize) {
        setInsertor(StackInsertionFunction.getInsertionFunction(maxStackSize));
        this.slotCapacity = maxStackSize;
    }

    public void setCallback(StackChangeCallback callback) {
        this.callback = callback;
    }

    public boolean hasCallback() {
        return callback != null;
    }

    /** A slot's real count, saved beside its {@code stack} only when the codec cannot carry it (the default inserter
     *  fills a slot up to the item's stack limit, which a mod can raise past the codec's bound; see
     *  {@link StackNbt}). Also the count key of the legacy {@code id} + {@code count} form, which has no
     *  {@code stack}. */
    private static final String COUNT_KEY = "count";

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            CompoundTag itemNbt = new CompoundTag();
            // Through the codec so DataComponentPatch (enchantments, custom name, damage, dyed colors, etc.)
            // round-trips on save/load; StackNbt keeps a count past the codec's bound beside the stack.
            StackNbt.write(itemNbt, "stack", COUNT_KEY, stack);
            list.add(itemNbt);
        }
        nbt.put("items", list);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ListTag list = NBTUtilBC.getList(nbt, "items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < size(); i++) {
            CompoundTag itemNbt = NBTUtilBC.getCompound(list, i);
            ItemStack stack = ItemStack.EMPTY;
            if (itemNbt.contains("stack")) {
                stack = StackNbt.read(itemNbt, "stack", COUNT_KEY);
            } else if (itemNbt.contains("id")) {
                // Legacy format (id + count, no components) -- read existing world saves.
                String idStr = NBTUtilBC.getString(itemNbt, "id", "");
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(idStr);
                if (id != null) {
                    net.minecraft.world.item.Item item = buildcraft.lib.misc.RegistryUtilBC.getValue(net.minecraft.core.registries.BuiltInRegistries.ITEM, id);
                    int count = NBTUtilBC.getInt(itemNbt, "count", 1);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        stack = new ItemStack(item, count);
                    }
                }
            }
            setStackInternal(i, stack);
        }
        for (int i = list.size(); i < size(); i++) {
            setStackInternal(i, StackUtil.EMPTY);
        }
    }

    @Override
    public int size() {
        return stacks.size();
    }

    public int getSlots() {
        return size();
    }

    public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
        if (badSlotIndex(slot)) return net.minecraft.world.item.ItemStack.EMPTY;
        return stacks.get(slot);
    }

    public net.minecraft.world.item.ItemStack insertItem(int slot, @Nonnull net.minecraft.world.item.ItemStack stack, boolean simulate) {
        return insert(slot, stack, simulate);
    }

    public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
        return extract(slot, s -> true, 1, amount, simulate);
    }

    public int getSlotLimit(int slot) {
        // Per-slot capacity, not a hardcoded 64 — 1.21.1's SlotBase.getMaxStackSize reads this,
        // so a flat 64 would let clicks drop full stacks into 1-bucket slots (heat exchanger).
        return slotCapacity;
    }

    // Classic IItemHandler contract method (required on 1.21.1; harmless extra public method on modern nodes).
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return canSet(slot, stack);
    }

    private boolean badSlotIndex(int slot) {
        return slot < 0 || slot >= stacks.size();
    }

    @Override
    protected boolean isEmpty(int slot) {
        if (badSlotIndex(slot)) return true;
        return stacks.get(slot).isEmpty();
    }

    // BCItemStorage: the surface the platform layer adapts to each loader's item handler (NeoForge's
    // ResourceHandler<ItemResource> on 1.21.10+, the classic IItemHandler on 1.21.1).
    @Override
    public ItemStack getStack(int index) {
        return badSlotIndex(index) ? ItemStack.EMPTY : stacks.get(index).copy();
    }

    @Override
    public int getCapacity(int index, ItemStack resource) {
        return slotCapacity;
    }

    @Override
    public boolean isValid(int index, ItemStack resource) {
        return canSet(index, resource.isEmpty() ? ItemStack.EMPTY : resource.copyWithCount(1));
    }

    @Override
    public int insert(int index, ItemStack resource, int amount, BCTransactionContext tx) {
        if (badSlotIndex(index) || amount <= 0 || resource.isEmpty()) return 0;
        ItemStack stack = resource.copyWithCount(amount);

        ItemStack current = stacks.get(index);
        if (!canSet(index, stack) || !canSet(index, current)) return 0;

        InsertionResult result = inserter.modifyForInsertion(index, asValid(current.copy()), asValid(stack.copy()));
        if (!canSet(index, result.toSet)) {
            CrashReport report = new CrashReport("Inserting an item (buildcraft:ItemHandlerSimple)",
                new IllegalStateException("Conflicting Insertion!"));
            CrashReportCategory cat = report.addCategory("Inventory details");
            cat.setDetail("Existing Item", current.toString());
            cat.setDetail("Inserting Item", stack.toString());
            cat.setDetail("To Set", result.toSet.toString());
            cat.setDetail("To Return", result.toReturn.toString());
            cat.setDetail("Slot", String.valueOf(index));
            throw new RuntimeException("Conflicting Insertion! See log for details.");
        }
        
        int inserted = amount - result.toReturn.getCount();
        if (inserted > 0) {
            changeSlot(index, current, result.toSet, tx);
        }
        return inserted;
    }

    @Override
    public int extract(int index, ItemStack resource, int amount, BCTransactionContext tx) {
        if (badSlotIndex(index) || amount <= 0 || resource.isEmpty()) return 0;
        ItemStack current = stacks.get(index);
        if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, resource)) return 0;

        int toExtract = Math.min(amount, current.getCount());
        if (toExtract > 0) {
            ItemStack after = current.copy();
            after.shrink(toExtract);
            if (after.getCount() <= 0) after = StackUtil.EMPTY;
            changeSlot(index, current, after, tx);
        }
        return toExtract;
    }

    /** Empties every non-empty slot through {@link #setStackInSlot}, so the change callback fires once per slot
     *  that held something. */
    public void clearStacks() {
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!stacks.get(slot).isEmpty()) {
                setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (badSlotIndex(slot)) {
            throw new IndexOutOfBoundsException("Slot index out of range: " + slot);
        }
        ItemStack before = stacks.get(slot);
        setStackInternal(slot, stack);
        if (callback != null) {
            callback.onStackChange(this, slot, before, asValid(stack));
        }
    }

    @Override
    public final boolean canSet(int slot, @Nonnull ItemStack stack) {
        ItemStack copied = asValid(stack);
        if (copied.isEmpty()) return true;
        return checker.canSet(slot, copied);
    }

    private void setStackInternal(int slot, @Nonnull ItemStack stack) {
        stacks.set(slot, asValid(stack));
    }

    @Override
    protected ItemStack insert(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int inserted = insert(slot, stack, stack.getCount(), tx);
            if (!simulate) tx.commit();
            return stack.copyWithCount(stack.getCount() - inserted);
        }
    }

    @Override
    protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
        if (badSlotIndex(slot) || max < min) return StackUtil.EMPTY;
        ItemStack current = stacks.get(slot);
        if (current.isEmpty() || current.getCount() < min || !filter.matches(asValid(current))) return StackUtil.EMPTY;
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int toExtract = Math.min(max, current.getCount());
            int ex = extract(slot, current, toExtract, tx);
            if (!simulate) tx.commit();
            return current.copyWithCount(ex);
        }
    }

    @Override
    public String toString() {
        return "ItemHandlerSimple " + stacks;
    }
}
