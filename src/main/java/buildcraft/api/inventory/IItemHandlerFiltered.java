package buildcraft.api.inventory;

import net.minecraft.world.item.ItemStack;

/** A type of item handler that has a single valid stack per slot, as specified by {@link #getFilter(int)}.
 * Note that any handler can implement this (even if the filter behaviour is more complex, or there isn't actually a
 * filter at all). Loader-neutral: it names no handler type, so the handler that implements it supplies the slots. */
public interface IItemHandlerFiltered {

    /** @param slot the slot to test
     * @return The filter in that slot. Will be {@link ItemStack#EMPTY} if this is not filtered to a single item (for
     *         example if this will match against a few stacks, or nothing is allowed, or a wide range of stacks are
     *         allowed). Will be equal to the current stack if the slot contains an item. */
    ItemStack getFilter(int slot);
}
