package buildcraft.lib.misc;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public class InventoryUtil {
    /** Extracts all items from the handler and adds them to the given list. */
    public static void addAll(IItemHandler handler, List<ItemStack> list) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
        }
    }
}
