package buildcraft.lib.tile.item;

import net.minecraft.world.item.ItemStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}

import buildcraft.api.inventory.IItemHandlerFiltered;

/** Combines several {@link IBCItemHandler} into one class.
 * <p>
 * Also provides {@link IItemHandlerFiltered#getFilter(int)} if the wrapped handlers support it. */
public class CombinedItemHandlerWrapper implements IBCItemHandler, IItemHandlerFiltered {
    private final IBCItemHandler[] itemHandler;
    private final int[] baseIndex;
    private final int slotCount;

    public CombinedItemHandlerWrapper(IBCItemHandler... itemHandler) {
        this.itemHandler = itemHandler;
        this.baseIndex = new int[itemHandler.length];
        int index = 0;
        for (int i = 0; i < itemHandler.length; i++) {
            //? if >=1.21.10 {
            index += itemHandler[i].size();
            //?} else {
            /*index += itemHandler[i].getSlots();*/
            //?}
            baseIndex[i] = index;
        }
        this.slotCount = index;
    }

    protected int getIndexForSlot(int slot) {
        if (slot < 0 || slot >= slotCount) {
            throw new IndexOutOfBoundsException("Slot " + slot + " not in valid range - [0," + slotCount + ")");
        }
        for (int i = 0; i < baseIndex.length; i++) {
            if (slot < baseIndex[i]) {
                return i;
            }
        }
        return -1;
    }

    protected IBCItemHandler getHandlerFromIndex(int index) {
        return itemHandler[index];
    }

    protected int getSlotFromIndex(int slot, int index) {
        if (index == -1 || index >= baseIndex.length) {
            return -1;
        }
        return index == 0 ? slot : slot - baseIndex[index - 1];
    }

    //? if >=1.21.10 {
    @Override
    public int size() {
        return slotCount;
    }

    @Override
    public ItemResource getResource(int index) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).getResource(localSlot);
    }

    @Override
    public long getAmountAsLong(int index) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).getAmountAsLong(localSlot);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).getCapacityAsLong(localSlot, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).isValid(localSlot, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).insert(localSlot, resource, amount, tx);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).extract(localSlot, resource, amount, tx);
    }
    //?} else {
    /*@Override
    public int getSlots() {
        return slotCount;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int handlerIndex = getIndexForSlot(slot);
        int localSlot = getSlotFromIndex(slot, handlerIndex);
        return getHandlerFromIndex(handlerIndex).getStackInSlot(localSlot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int handlerIndex = getIndexForSlot(slot);
        int localSlot = getSlotFromIndex(slot, handlerIndex);
        return getHandlerFromIndex(handlerIndex).insertItem(localSlot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int handlerIndex = getIndexForSlot(slot);
        int localSlot = getSlotFromIndex(slot, handlerIndex);
        return getHandlerFromIndex(handlerIndex).extractItem(localSlot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int handlerIndex = getIndexForSlot(slot);
        int localSlot = getSlotFromIndex(slot, handlerIndex);
        return getHandlerFromIndex(handlerIndex).getSlotLimit(localSlot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int handlerIndex = getIndexForSlot(slot);
        int localSlot = getSlotFromIndex(slot, handlerIndex);
        return getHandlerFromIndex(handlerIndex).isItemValid(localSlot, stack);
    }*/
    //?}

    @Override
    public ItemStack getFilter(int slot) {
        int index = getIndexForSlot(slot);
        IBCItemHandler handler = getHandlerFromIndex(index);
        slot = getSlotFromIndex(slot, index);
        if (handler instanceof IItemHandlerFiltered) {
            return ((IItemHandlerFiltered) handler).getFilter(slot);
        }
        //? if >=1.21.10 {
        return handler.getResource(slot).toStack(handler.getAmountAsInt(slot));
        //?} else {
        /*return handler.getStackInSlot(slot);*/
        //?}
    }
}
