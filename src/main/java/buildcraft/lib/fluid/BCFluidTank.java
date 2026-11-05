/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

//? if >=1.21.10 {
import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;*/
//?}

/**
 * Loader-neutral multi-slot fluid tank, a {@link BCFluidStorage} on every node (the platform layer exposes it as the
 * loader's fluid handler). Its transfer semantics are NeoForge's {@code FluidStacksResourceHandler}, which it used to
 * extend on 1.21.10+: an index takes a fluid while empty or holding the same fluid and components, up to one shared
 * capacity, each index journals its own stack, and the change hook fires once per index when the root transaction
 * commits (or at once for {@link #setFluidStack}).
 *
 * <p>BuildCraft tiles use the convenience API below ({@link #getFluidStack}, {@link #fill}, {@link #drain}, ...) and
 * override the {@link #isFluidValid} / {@link #onChanged} hooks.
 */
public class BCFluidTank implements BCFluidStorage {
    /**
     * The save key of the per-slot list on 1.21.10+. BuildCraft owns it: it is the value NeoForge's
     * {@code StacksResourceHandler.VALUE_IO_KEY} had when BC inherited it, pinned by the golden fixtures, and
     * {@code TileTank.removeComponentsFromTag} strips it. 1.21.1 writes {@code fluid<i>}/{@code amount<i>} instead.
     */
    public static final String STACKS_KEY = "stacks";

    //? if >=1.21.10 {
    /** One {@link BCFluidStack#OPTIONAL_CODEC} entry per slot, so an empty slot is {@code {}}. */
    private static final Codec<List<BCFluidStack>> STACKS_CODEC = BCFluidStack.OPTIONAL_CODEC.listOf();
    //?}

    protected int capacity;
    private BCFluidStack[] stacks;
    private final List<StackJournal> journals = new ArrayList<>();

    public BCFluidTank(int size, int capacity) {
        this.capacity = capacity;
        this.stacks = new BCFluidStack[size];
        Arrays.fill(stacks, BCFluidStack.EMPTY);
        updateJournals();
    }

    private void updateJournals() {
        while (journals.size() < stacks.length) {
            journals.add(new StackJournal(journals.size()));
        }
        if (journals.size() > stacks.length) {
            journals.subList(stacks.length, journals.size()).clear();
        }
    }

    // --- BCFluidStorage ---

    @Override
    public int size() {
        return stacks.length;
    }

    @Override
    public BCFluidStack getStack(int index) {
        return stacks[Objects.checkIndex(index, stacks.length)].copy();
    }

    @Override
    public int getAmount(int index) {
        return stacks[Objects.checkIndex(index, stacks.length)].getAmount();
    }

    @Override
    public int getCapacity(int index, BCFluidStack resource) {
        Objects.checkIndex(index, stacks.length);
        return resource.isEmpty() || isValid(index, resource) ? capacity : 0;
    }

    @Override
    public boolean isValid(int index, BCFluidStack resource) {
        return isFluidValid(resource.copyWithAmount(1));
    }

    @Override
    public int insert(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
        Objects.checkIndex(index, stacks.length);
        if (movesNothing(resource, amount)) {
            return 0;
        }
        BCFluidStack current = stacks[index];
        int currentAmount = current.getAmount();
        if ((currentAmount == 0 || BCFluidStack.isSameFluidSameComponents(current, resource)) && isValid(index, resource)) {
            int inserted = Math.min(amount, capacity - currentAmount);
            if (inserted > 0) {
                journals.get(index).updateSnapshots(transaction);
                stacks[index] = resource.copyWithAmount(currentAmount + inserted);
                return inserted;
            }
        }
        return 0;
    }

    @Override
    public int extract(int index, BCFluidStack resource, int amount, BCTransactionContext transaction) {
        Objects.checkIndex(index, stacks.length);
        if (movesNothing(resource, amount)) {
            return 0;
        }
        BCFluidStack current = stacks[index];
        if (!current.isEmpty() && BCFluidStack.isSameFluidSameComponents(current, resource)) {
            int extracted = Math.min(amount, current.getAmount());
            if (extracted > 0) {
                journals.get(index).updateSnapshots(transaction);
                stacks[index] = current.copyWithAmount(current.getAmount() - extracted);
                if (stacks[index].isEmpty()) {
                    stacks[index] = BCFluidStack.EMPTY;
                }
                return extracted;
            }
        }
        return 0;
    }

    /**
     * Whether an offer to one index moves nothing: an empty resource or an amount of 0 or less. On NeoForge 1.21.10+
     * an empty resource or a negative amount throws {@code IllegalArgumentException} instead, as it did before the
     * platform layer, when this tank was NeoForge's {@code FluidStacksResourceHandler} and the Tank's column handler
     * called {@code TransferPreconditions.checkNonEmptyNonNegative} itself. NeoForge 1.21.1's classic tanks answered
     * 0, and Fabric keeps that answer.
     */
    public static boolean movesNothing(BCFluidStack resource, int amount) {
        //? if neoforge && >=1.21.10 {
        BCFluidStorage.checkNonEmptyNonNegative(resource, amount);
        return amount == 0;
        //?} else {
        /*return resource.isEmpty() || amount <= 0;*/
        //?}
    }

    // --- Convenience API ---

    public BCFluidStack getFluidStack(int slot) {
        return getStack(slot);
    }

    public int getAmountMb(int slot) {
        return getAmount(slot);
    }

    /**
     * The tank's raw capacity, NOT the validity-gated {@link #getCapacity}: guarded output tanks (e.g. the
     * Distiller/Heat Exchanger OutputTank, whose isFluidValid only passes mid-internal-insert) reject the very fluid
     * they hold, so the gated capacity of the held fluid is 0 once the fill finishes.
     */
    public int getCapacityMb(int slot) {
        return getCapacity(slot, BCFluidStack.EMPTY);
    }

    /** Replaces the contents of {@code slot} outright (no journal, no validity check) and fires the change hook. */
    public void setFluidStack(int slot, BCFluidStack stack) {
        Objects.checkIndex(slot, stacks.length);
        BCFluidStack old = stacks[slot];
        stacks[slot] = stack.isEmpty() ? BCFluidStack.EMPTY : stack.copy();
        onContentsChanged(slot, old);
    }

    public boolean isTankEmpty(int slot) {
        return stacks[Objects.checkIndex(slot, stacks.length)].isEmpty();
    }

    public int fill(int slot, BCFluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int n = insert(slot, stack, stack.getAmount(), tx);
            if (!simulate && n > 0) {
                tx.commit();
            }
            return n;
        }
    }

    public BCFluidStack drain(int slot, int maxMb, boolean simulate) {
        BCFluidStack r = getStack(slot);
        if (r.isEmpty() || maxMb <= 0) {
            return BCFluidStack.EMPTY;
        }
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int n = extract(slot, r, maxMb, tx);
            if (!simulate && n > 0) {
                tx.commit();
            }
            return n <= 0 ? BCFluidStack.EMPTY : r.copyWithAmount(n);
        }
    }

    /** Overridable validity hook: whether the tank accepts {@code stack}'s fluid at all. */
    protected boolean isFluidValid(BCFluidStack stack) {
        return true;
    }

    /** Overridable change hook (fires once per changed slot when a transaction commits). */
    protected void onChanged() {}

    /** Called with the slot's contents before the change, after the change is final. */
    protected void onContentsChanged(int slot, BCFluidStack previousContents) {
        onChanged();
    }

    // --- Save format ---

    //? if >=1.21.10 {
    /** Writes the {@code "stacks"} list of optional fluid stacks (the format NeoForge's parent wrote). */
    public void serialize(ValueOutput output) {
        List<BCFluidStack> list = new ArrayList<>(stacks.length);
        for (BCFluidStack stack : stacks) {
            list.add(stack.copy());
        }
        output.store(STACKS_KEY, STACKS_CODEC, list);
    }

    /**
     * An absent key leaves the contents alone; a present list replaces them wholesale (its length becomes the slot
     * count) without firing the change hook, as NeoForge's parent did.
     */
    public void deserialize(ValueInput input) {
        input.read(STACKS_KEY, STACKS_CODEC).ifPresent(list -> {
            BCFluidStack[] read = new BCFluidStack[list.size()];
            for (int i = 0; i < read.length; i++) {
                BCFluidStack stack = list.get(i);
                read[i] = stack.isEmpty() ? BCFluidStack.EMPTY : stack.copy();
            }
            stacks = read;
            updateJournals();
        });
    }
    //?} else {
    /*// NBT: id + amount per slot (no registry provider needed); components are not written.
    public void serialize(CompoundTag tag) {
        for (int i = 0; i < stacks.length; i++) {
            BCFluidStack fs = stacks[i];
            if (!fs.isEmpty()) {
                tag.putString("fluid" + i, BuiltInRegistries.FLUID.getKey(fs.getFluid()).toString());
                tag.putInt("amount" + i, fs.getAmount());
            }
        }
    }

    public void deserialize(CompoundTag tag) {
        for (int i = 0; i < stacks.length; i++) {
            if (tag.contains("fluid" + i)) {
                Fluid f = BuiltInRegistries.FLUID.get(Identifier.parse(tag.getString("fluid" + i)));
                BCFluidStack read = new BCFluidStack(f, tag.getInt("amount" + i));
                stacks[i] = read.isEmpty() ? BCFluidStack.EMPTY : read;
            } else {
                stacks[i] = BCFluidStack.EMPTY;
            }
        }
    }*/
    //?}

    @Override
    public String toString() {
        return "BCFluidTank" + Arrays.toString(stacks);
    }

    private final class StackJournal extends BCSnapshotJournal<BCFluidStack> {
        private final int index;

        StackJournal(int index) {
            this.index = index;
        }

        @Override
        protected BCFluidStack createSnapshot() {
            return stacks[index].copy();
        }

        @Override
        protected void revertToSnapshot(BCFluidStack snapshot) {
            stacks[index] = snapshot;
        }

        @Override
        protected void onRootCommit(BCFluidStack originalState) {
            onContentsChanged(index, originalState);
        }
    }
}
