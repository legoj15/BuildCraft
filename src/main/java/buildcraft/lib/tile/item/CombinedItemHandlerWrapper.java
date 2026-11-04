package buildcraft.lib.tile.item;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.inventory.IItemHandlerFiltered;
import buildcraft.platform.transfer.BCTransactionContext;

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
            index += itemHandler[i].size();
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

    @Override
    public int size() {
        return slotCount;
    }

    @Override
    public ItemStack getStack(int index) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).getStack(localSlot);
    }

    @Override
    public int getCapacity(int index, ItemStack resource) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).getCapacity(localSlot, resource);
    }

    @Override
    public boolean isValid(int index, ItemStack resource) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).isValid(localSlot, resource);
    }

    @Override
    public int insert(int index, ItemStack resource, int amount, BCTransactionContext tx) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).insert(localSlot, resource, amount, tx);
    }

    @Override
    public int extract(int index, ItemStack resource, int amount, BCTransactionContext tx) {
        int handlerIndex = getIndexForSlot(index);
        int localSlot = getSlotFromIndex(index, handlerIndex);
        return getHandlerFromIndex(handlerIndex).extract(localSlot, resource, amount, tx);
    }

    @Override
    public ItemStack getFilter(int slot) {
        int index = getIndexForSlot(slot);
        IBCItemHandler handler = getHandlerFromIndex(index);
        slot = getSlotFromIndex(slot, index);
        if (handler instanceof IItemHandlerFiltered) {
            return ((IItemHandlerFiltered) handler).getFilter(slot);
        }
        return handler.getStack(slot);
    }
}
