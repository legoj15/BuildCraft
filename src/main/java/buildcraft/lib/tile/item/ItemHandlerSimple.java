package buildcraft.lib.tile.item;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
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
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.item.StackInsertionFunction.InsertionResult;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
//?}

@SuppressWarnings("this-escape")
public class ItemHandlerSimple extends AbstractInvItemTransactor
    implements IItemHandlerAdv, INBTSerializable<CompoundTag> {

    //? if >=1.21.10 {
    private final SnapshotJournal<ItemStack[]> journal = new SnapshotJournal<>() {
        @Override
        protected ItemStack[] createSnapshot() {
            ItemStack[] snap = new ItemStack[stacks.size()];
            for (int i = 0; i < snap.length; i++) {
                snap[i] = stacks.get(i).copy();
            }
            return snap;
        }

        @Override
        protected void revertToSnapshot(ItemStack[] snapshot) {
            for (int i = 0; i < snapshot.length; i++) {
                stacks.set(i, snapshot[i] != null ? snapshot[i] : StackUtil.EMPTY);
            }
        }
    };
    //?}


    private StackInsertionChecker checker;
    private StackInsertionFunction inserter;

    @Nullable
    private StackChangeCallback callback;

    public final NonNullList<ItemStack> stacks;

    private int firstUsed = Integer.MAX_VALUE;
    /** Per-slot capacity reported by {@link #getCapacityAsLong} and consulted by
     * {@link buildcraft.lib.gui.slot.SlotBase#getMaxStackSize} when vanilla decides
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

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            CompoundTag itemNbt = new CompoundTag();
            if (!stack.isEmpty()) {
                // Use ItemStack.CODEC so DataComponentPatch (enchantments, custom name,
                // damage, dyed colors, etc.) round-trips on save/load.
                ItemStack.CODEC.encodeStart(buildcraft.lib.misc.NBTUtilBC.registryAwareOps(), stack)
                        .resultOrPartial()
                        .ifPresent(payload -> itemNbt.put("stack", payload));
            }
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
            Tag stackPayload = itemNbt.get("stack");
            if (stackPayload != null) {
                stack = ItemStack.CODEC.parse(buildcraft.lib.misc.NBTUtilBC.registryAwareOps(), stackPayload)
                        .resultOrPartial()
                        .orElse(ItemStack.EMPTY);
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
        return 64; // Default legacy behavior
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

    // Transfer-API (ResourceHandler<ItemResource>) adapter surface — 1.21.10+ only. On 1.21.1 the classic
    // IItemHandler methods (insertItem/extractItem/getStackInSlot/getSlots/getSlotLimit/...) satisfy the cap.
    //? if >=1.21.10 {
    @Override
    public ItemResource getResource(int index) {
        return badSlotIndex(index) ? ItemResource.EMPTY : ItemResource.of(stacks.get(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return badSlotIndex(index) ? 0 : stacks.get(index).getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return slotCapacity;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return canSet(index, resource.toStack());
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        if (badSlotIndex(index) || amount <= 0 || resource.isEmpty()) return 0;
        ItemStack stack = resource.toStack(amount);
        
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
            if (tx != null) {
                journal.updateSnapshots(tx);
            }
            setStackInternal(index, result.toSet);
            if (callback != null) {
                callback.onStackChange(this, index, current, result.toSet);
            }
        }
        return inserted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        if (badSlotIndex(index) || amount <= 0 || resource.isEmpty()) return 0;
        ItemStack current = stacks.get(index);
        if (current.isEmpty() || !ItemResource.of(current).equals(resource)) return 0;

        int toExtract = Math.min(amount, current.getCount());
        if (toExtract > 0) {
            ItemStack before = current.copy();
            ItemStack after = current.copy();
            after.shrink(toExtract);
            if (after.getCount() <= 0) after = StackUtil.EMPTY;

            if (tx != null) {
                journal.updateSnapshots(tx);
            }
            setStackInternal(index, after);
            if (callback != null) {
                callback.onStackChange(this, index, before, after);
            }
        }
        return toExtract;
    }
    //?}

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
        if (stack.isEmpty() && firstUsed == slot) {
            for (int s = firstUsed; s < size(); s++) {
                if (!stacks.get(s).isEmpty()) {
                    firstUsed = s;
                    break;
                }
            }
            if (firstUsed == slot) {
                firstUsed = Integer.MAX_VALUE;
            }
        } else if (!stack.isEmpty() && firstUsed > slot) {
            firstUsed = slot;
        }
    }

    //? if >=1.21.10 {
    @Override
    protected ItemStack insert(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = insert(slot, ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) tx.commit();
            return stack.copyWithCount(stack.getCount() - inserted);
        }
    }

    @Override
    protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
        if (badSlotIndex(slot) || max < min) return StackUtil.EMPTY;
        ItemStack current = stacks.get(slot);
        if (current.isEmpty() || current.getCount() < min || !filter.matches(asValid(current))) return StackUtil.EMPTY;
        try (Transaction tx = Transaction.openRoot()) {
            int toExtract = Math.min(max, current.getCount());
            int ex = extract(slot, ItemResource.of(current), toExtract, tx);
            if (!simulate) tx.commit();
            return current.copyWithCount(ex);
        }
    }
    //?} else {
    /*@Override
    protected ItemStack insert(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return StackUtil.EMPTY;
        if (badSlotIndex(slot)) return stack;
        ItemStack current = stacks.get(slot);
        if (!canSet(slot, stack) || !canSet(slot, current)) return stack;
        InsertionResult result = inserter.modifyForInsertion(slot, asValid(current.copy()), asValid(stack.copy()));
        if (!canSet(slot, result.toSet)) {
            throw new RuntimeException("Conflicting Insertion in ItemHandlerSimple! existing=" + current
                + " inserting=" + stack + " toSet=" + result.toSet);
        }
        int inserted = stack.getCount() - result.toReturn.getCount();
        if (inserted > 0 && !simulate) {
            setStackInternal(slot, result.toSet);
            if (callback != null) {
                callback.onStackChange(this, slot, current, result.toSet);
            }
        }
        return result.toReturn;
    }

    @Override
    protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
        if (badSlotIndex(slot) || max < min) return StackUtil.EMPTY;
        ItemStack current = stacks.get(slot);
        if (current.isEmpty() || current.getCount() < min || !filter.matches(asValid(current))) return StackUtil.EMPTY;
        int toExtract = Math.min(max, current.getCount());
        if (toExtract <= 0) return StackUtil.EMPTY;
        ItemStack before = current.copy();
        ItemStack after = current.copy();
        after.shrink(toExtract);
        if (after.getCount() <= 0) after = StackUtil.EMPTY;
        if (!simulate) {
            setStackInternal(slot, after);
            if (callback != null) {
                callback.onStackChange(this, slot, before, after);
            }
        }
        return before.copyWithCount(toExtract);
    }*/
    //?}

    @Override
    public String toString() {
        return "ItemHandlerSimple " + stacks;
    }
}
