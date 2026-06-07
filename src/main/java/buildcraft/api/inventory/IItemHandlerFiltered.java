package buildcraft.api.inventory;

import net.minecraft.world.item.ItemStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
//?}

/** A type of item-handler capability that has a single valid stack per slot, as specified by {@link #getFilter(int)}.
 * Note that any handler can implement this (even if the filter behaviour is more complex, or there isn't actually a
 * filter at all). Extends {@code ResourceHandler<ItemResource>} (the NeoForge Transfer API) on 1.21.10+ or a classic
 * {@code IItemHandler} on 1.21.1. */
//? if >=1.21.10 {
public interface IItemHandlerFiltered extends ResourceHandler<ItemResource> {

    /** @param slot the slot to test
     * @return The filter in that slot. Will be {@link ItemStack#EMPTY} if this is not filtered to a single item (for
     *         example if this will match against a few stacks, or nothing is allowed, or a wide range of stacks are
     *         allowed). Will be equal to the current stack if the slot contains an item. */
    default ItemStack getFilter(int slot) {
        return getResource(slot).toStack(getAmountAsInt(slot));
    }
}
//?} else {
/*public interface IItemHandlerFiltered extends net.neoforged.neoforge.items.IItemHandler {

    default ItemStack getFilter(int slot) {
        return getStackInSlot(slot);
    }
}*/
//?}

