package buildcraft.lib.tile.item;

import net.minecraft.world.item.ItemStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}

import buildcraft.api.inventory.IItemHandlerFiltered;

public class DelegateItemHandler implements IBCItemHandler, IItemHandlerFiltered {
    protected final IBCItemHandler delegate;

    public DelegateItemHandler(IBCItemHandler delegate) {
        this.delegate = delegate;
    }

    //? if >=1.21.10 {
    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return delegate.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return delegate.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return delegate.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        return delegate.insert(index, resource, amount, tx);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        return delegate.extract(index, resource, amount, tx);
    }
    //?} else {
    /*@Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return delegate.isItemValid(slot, stack);
    }*/
    //?}

    @Override
    public ItemStack getFilter(int slot) {
        if (delegate instanceof IItemHandlerFiltered) {
            return ((IItemHandlerFiltered) delegate).getFilter(slot);
        }
        return IItemHandlerFiltered.super.getFilter(slot);
    }
}
