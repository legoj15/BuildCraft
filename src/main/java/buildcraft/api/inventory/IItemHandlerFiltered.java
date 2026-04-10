package buildcraft.api.inventory;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** A type of {@link ResourceHandler} that has a single valid stack per slot, as specified by {@link #getFilter(int)}. Note
 * that any {@link ResourceHandler} can implement this (even if the filter behaviour is more complex, or their isn't
 * actually a filter at all) */
public interface IItemHandlerFiltered extends ResourceHandler<ItemResource> {

    /** @param slot the slot to test
     * @return The filter in that slot. Will be {@link ItemStack#EMPTY} if this is not filtered to a single item (for
     *         example if this will match against a few stacks, or nothing is allowed, or a wide range of stacks are
     *         allowed). Will be equal to the current stack if the slot contains an item. */
    default ItemStack getFilter(int slot) {
        return getResource(slot).toStack(getAmountAsInt(slot));
    }
}

