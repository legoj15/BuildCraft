/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
//?} else {
/*import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;*/
//?}

/**
 * Version-neutral multi-slot fluid tank. On 1.21.10+ this IS a NeoForge Transfer-API
 * {@link FluidStacksResourceHandler} (so it can still be exposed via {@code Capabilities.Fluid.BLOCK} and the
 * inherited Transfer-API methods keep working unchanged); on 1.21.1 the Transfer API does not exist, so it is a
 * classic {@code IFluidHandler} backed by {@code FluidTank[]} ({@code Capabilities.FluidHandler.BLOCK}).
 *
 * <p>BuildCraft tiles use the version-neutral, {@link FluidStack}-based convenience API below
 * ({@link #getFluidStack}, {@link #fill}, {@link #drain}, …) and override the {@link #isFluidValid} /
 * {@link #onChanged} hooks instead of the Transfer-API {@code isValid(int, FluidResource)} /
 * {@code onContentsChanged} — so tile bodies never name {@code FluidResource} or {@code TransactionContext}.
 * The {@code >=1.21.10} method bodies are exactly what BuildCraft did before, so the released nodes are
 * behaviour-identical.
 */
//? if >=1.21.10 {
public class BCFluidTank extends FluidStacksResourceHandler {
    public BCFluidTank(int size, int capacity) {
        super(size, capacity);
    }

    // Bridge the Transfer-API hooks to the version-neutral ones tiles override.
    @Override
    public boolean isValid(int index, FluidResource resource) {
        return isFluidValid(resource.toStack(1));
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChanged();
    }

    public FluidStack getFluidStack(int slot) {
        FluidResource r = getResource(slot);
        return r.isEmpty() ? FluidStack.EMPTY : r.toStack(getAmountAsInt(slot));
    }

    public int getAmountMb(int slot) {
        return getAmountAsInt(slot);
    }

    public int getCapacityMb(int slot) {
        return getCapacityAsInt(slot, getResource(slot));
    }

    public void setFluidStack(int slot, FluidStack stack) {
        set(slot, stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack), stack.getAmount());
    }

    public boolean isTankEmpty(int slot) {
        return getResource(slot).isEmpty();
    }

    public int fill(int slot, FluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int n = insert(slot, FluidResource.of(stack), stack.getAmount(), tx);
            if (!simulate && n > 0) {
                tx.commit();
            }
            return n;
        }
    }

    public FluidStack drain(int slot, int maxMb, boolean simulate) {
        FluidResource r = getResource(slot);
        if (r.isEmpty() || maxMb <= 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int n = extract(slot, r, maxMb, tx);
            if (!simulate && n > 0) {
                tx.commit();
            }
            return n <= 0 ? FluidStack.EMPTY : r.toStack(n);
        }
    }

    /** Overridable validity hook (replaces {@code isValid(int, FluidResource)}). */
    protected boolean isFluidValid(FluidStack stack) {
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
        Predicate<FluidStack> validator = this::isFluidValid;
        for (int i = 0; i < size; i++) {
            this.tanks[i] = new FluidTank(capacity, validator);
        }
    }

    public int size() {
        return tanks.length;
    }

    public FluidStack getFluidStack(int slot) {
        return tanks[slot].getFluid();
    }

    public int getAmountMb(int slot) {
        return tanks[slot].getFluidAmount();
    }

    public int getCapacityMb(int slot) {
        return tanks[slot].getCapacity();
    }

    public void setFluidStack(int slot, FluidStack stack) {
        tanks[slot].setFluid(stack);
        onChanged();
    }

    public boolean isTankEmpty(int slot) {
        return tanks[slot].getFluid().isEmpty();
    }

    public int fill(int slot, FluidStack stack, boolean simulate) {
        int n = tanks[slot].fill(stack, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
        if (!simulate && n > 0) {
            onChanged();
        }
        return n;
    }

    public FluidStack drain(int slot, int maxMb, boolean simulate) {
        FluidStack out = tanks[slot].drain(maxMb, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
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
        return isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        for (int i = 0; i < tanks.length; i++) {
            int n = fill(i, resource, action.simulate());
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
                return drain(i, resource.getAmount(), action.simulate());
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (int i = 0; i < tanks.length; i++) {
            if (!tanks[i].getFluid().isEmpty()) {
                return drain(i, maxDrain, action.simulate());
            }
        }
        return FluidStack.EMPTY;
    }

    // --- NBT (id + amount per slot; no registry provider needed) ---

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

    protected boolean isFluidValid(FluidStack stack) {
        return true;
    }

    protected void onChanged() {}
}*/
//?}
