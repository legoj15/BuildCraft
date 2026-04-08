package buildcraft.api.items;

import javax.annotation.Nonnull;

import net.minecraft.world.item.ItemStack;

/** Interface for items that can have a custom name stored in their data.
 * Note: In 1.21+, Item.getName(ItemStack) returns Component, so this interface
 * uses 'getLocationName' to avoid the conflict. */
public interface INamedItem {
    String getLocationName(@Nonnull ItemStack stack);

    boolean setLocationName(@Nonnull ItemStack stack, String name);
}
