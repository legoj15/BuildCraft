/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;

import buildcraft.api.fluids.BCFluidStack;

//? if >=1.21.10 {
import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
//?} else {
/*import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;*/
//?}

/**
 * Version-neutral multi-slot fluid tank. On 1.21.10+ this IS a NeoForge Transfer-API
 * {@link FluidStacksResourceHandler} (so it can still be exposed via {@code Capabilities.Fluid.BLOCK} and the
 * inherited Transfer-API methods keep working unchanged); on 1.21.1 the Transfer API does not exist, so it is a
 * classic {@code IFluidHandler} backed by {@code FluidTank[]} ({@code Capabilities.FluidHandler.BLOCK}).
 *
 * <p>BuildCraft tiles use the version-neutral, {@link BCFluidStack}-based convenience API below
 * ({@link #getFluidStack}, {@link #fill}, {@link #drain}, …) and override the {@link #isFluidValid} /
 * {@link #onChanged} hooks instead of the Transfer-API {@code isValid(int, FluidResource)} /
 * {@code onContentsChanged} — so tile bodies never name {@code FluidResource} or {@code TransactionContext}.
 * The {@code >=1.21.10} method bodies are exactly what BuildCraft did before, so the released nodes are
 * behaviour-identical.
 */
//? if >=1.21.10 {
public class BCFluidTank extends FluidStacksResourceHandler {
    /**
     * The save key of the per-slot list. BuildCraft owns it: it is the value NeoForge's
     * {@code StacksResourceHandler.VALUE_IO_KEY} had when BC inherited it, pinned by the golden fixtures, and
     * {@code TileTank.removeComponentsFromTag} strips it.
     */
    public static final String STACKS_KEY = "stacks";

    /** One {@link BCFluidStack#OPTIONAL_CODEC} entry per slot, so an empty slot is {@code {}}. */
    private static final Codec<List<BCFluidStack>> STACKS_CODEC = BCFluidStack.OPTIONAL_CODEC.listOf();

    public BCFluidTank(int size, int capacity) {
        super(size, capacity);
    }

    /**
     * Writes exactly what the inherited {@code StacksResourceHandler.serialize} wrote (the {@code "stacks"} list of
     * optional fluid stacks), but through BuildCraft's own codec, so the format no longer rides on the parent.
     */
    @Override
    public void serialize(ValueOutput output) {
        List<BCFluidStack> list = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            list.add(getFluidStack(i));
        }
        output.store(STACKS_KEY, STACKS_CODEC, list);
    }

    /**
     * Mirrors the inherited {@code deserialize}: an absent key leaves the contents alone, a present list replaces
     * them wholesale (its length becomes the slot count) without firing the change hook.
     */
    @Override
    public void deserialize(ValueInput input) {
        input.read(STACKS_KEY, STACKS_CODEC).ifPresent(list -> {
            NonNullList<FluidStack> neo =
                    NonNullList.withSize(list.size(), FluidStack.EMPTY);
            for (int i = 0; i < list.size(); i++) {
                neo.set(i, NeoFluidStacks.toNeo(list.get(i)));
            }
            //? if >=1.21.11 {
            setStacks(neo);
            //?} else {
            /*stacks = neo; // 21.10's parent has no setStacks and assigns the field itself, journals untouched
            *///?}
        });
    }

    // Bridge the Transfer-API hooks to the version-neutral ones tiles override.
    @Override
    public boolean isValid(int index, FluidResource resource) {
        return isFluidValid(NeoFluidStacks.fromNeo(resource.toStack(1)));
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChanged();
    }

    public BCFluidStack getFluidStack(int slot) {
        FluidResource r = getResource(slot);
        return r.isEmpty() ? BCFluidStack.EMPTY : NeoFluidStacks.fromNeo(r.toStack(getAmountAsInt(slot)));
    }

    public int getAmountMb(int slot) {
        return getAmountAsInt(slot);
    }

    public int getCapacityMb(int slot) {
        // Report the tank's raw capacity, NOT the validity-gated one. getCapacityAsLong returns 0 for a
        // non-empty resource that fails isValid(), and guarded output tanks (e.g. the Distiller/Heat
        // Exchanger OutputTank, whose isFluidValid only passes mid-internal-insert) reject the very fluid
        // they hold — so passing the held resource here reports capacity 0 once the fill finishes. Pass
        // EMPTY to take getCapacityAsLong's empty/estimate branch, which returns the true constant capacity.
        return getCapacityAsInt(slot, FluidResource.EMPTY);
    }

    public void setFluidStack(int slot, BCFluidStack stack) {
        set(slot, stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(NeoFluidStacks.toNeo(stack)), stack.getAmount());
    }

    public boolean isTankEmpty(int slot) {
        return getResource(slot).isEmpty();
    }

    public int fill(int slot, BCFluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int n = insert(slot, FluidResource.of(NeoFluidStacks.toNeo(stack)), stack.getAmount(), tx);
            if (!simulate && n > 0) {
                tx.commit();
            }
            return n;
        }
    }

    public BCFluidStack drain(int slot, int maxMb, boolean simulate) {
        FluidResource r = getResource(slot);
        if (r.isEmpty() || maxMb <= 0) {
            return BCFluidStack.EMPTY;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int n = extract(slot, r, maxMb, tx);
            if (!simulate && n > 0) {
                tx.commit();
            }
            return n <= 0 ? BCFluidStack.EMPTY : NeoFluidStacks.fromNeo(r.toStack(n));
        }
    }

    /** Overridable validity hook (replaces {@code isValid(int, FluidResource)}). */
    protected boolean isFluidValid(BCFluidStack stack) {
        return true;
    }

    /** Overridable change hook (replaces overriding insert/extract just to mark dirty). */
    protected void onChanged() {}
}
//?} else {
/*public class BCFluidTank implements IFluidHandler {
    private final FluidTank[] tanks;

    public BCFluidTank(int size, int capacity) {
        this.tanks = new FluidTank[size];
        Predicate<FluidStack> validator = stack -> isFluidValid(NeoFluidStacks.fromNeo(stack));
        for (int i = 0; i < size; i++) {
            this.tanks[i] = new FluidTank(capacity, validator);
        }
    }

    public int size() {
        return tanks.length;
    }

    public BCFluidStack getFluidStack(int slot) {
        return NeoFluidStacks.fromNeo(tanks[slot].getFluid());
    }

    public int getAmountMb(int slot) {
        return tanks[slot].getFluidAmount();
    }

    public int getCapacityMb(int slot) {
        return tanks[slot].getCapacity();
    }

    public void setFluidStack(int slot, BCFluidStack stack) {
        tanks[slot].setFluid(NeoFluidStacks.toNeo(stack));
        onChanged();
    }

    public boolean isTankEmpty(int slot) {
        return tanks[slot].getFluid().isEmpty();
    }

    public int fill(int slot, BCFluidStack stack, boolean simulate) {
        int n = tanks[slot].fill(NeoFluidStacks.toNeo(stack), simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
        if (!simulate && n > 0) {
            onChanged();
        }
        return n;
    }

    public BCFluidStack drain(int slot, int maxMb, boolean simulate) {
        BCFluidStack out = NeoFluidStacks.fromNeo(tanks[slot].drain(maxMb, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE));
        if (!simulate && !out.isEmpty()) {
            onChanged();
        }
        return out;
    }

    // --- classic IFluidHandler (capability exposure) ---

    @Override
    public int getTanks() {
        return tanks.length;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tanks[tank].getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return tanks[tank].getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return isFluidValid(NeoFluidStacks.fromNeo(stack));
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        for (int i = 0; i < tanks.length; i++) {
            int n = fill(i, NeoFluidStacks.fromNeo(resource), action.simulate());
            if (n > 0) {
                return n;
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        for (int i = 0; i < tanks.length; i++) {
            FluidStack inTank = tanks[i].getFluid();
            if (!inTank.isEmpty() && FluidStack.isSameFluidSameComponents(inTank, resource)) {
                return NeoFluidStacks.toNeo(drain(i, resource.getAmount(), action.simulate()));
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (int i = 0; i < tanks.length; i++) {
            if (!tanks[i].getFluid().isEmpty()) {
                return NeoFluidStacks.toNeo(drain(i, maxDrain, action.simulate()));
            }
        }
        return FluidStack.EMPTY;
    }

    // --- NBT (id + amount per slot; no registry provider needed) ---

    public static final String STACKS_KEY = "stacks"; // not written here: 1.21.1 keeps fluid<i>/amount<i>

    public void serialize(CompoundTag tag) {
        for (int i = 0; i < tanks.length; i++) {
            FluidStack fs = tanks[i].getFluid();
            if (!fs.isEmpty()) {
                tag.putString("fluid" + i, BuiltInRegistries.FLUID.getKey(fs.getFluid()).toString());
                tag.putInt("amount" + i, fs.getAmount());
            }
        }
    }

    public void deserialize(CompoundTag tag) {
        for (int i = 0; i < tanks.length; i++) {
            if (tag.contains("fluid" + i)) {
                Fluid f = BuiltInRegistries.FLUID.get(Identifier.parse(tag.getString("fluid" + i)));
                tanks[i].setFluid(new FluidStack(f, tag.getInt("amount" + i)));
            } else {
                tanks[i].setFluid(FluidStack.EMPTY);
            }
        }
    }

    protected boolean isFluidValid(BCFluidStack stack) {
        return true;
    }

    protected void onChanged() {}
}*/
//?}
