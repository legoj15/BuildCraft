package buildcraft.api.items;

import net.minecraft.world.item.ItemStack;




public interface IItemCustomPipeRender {
    float getPipeRenderScale(ItemStack stack);

    /** @return False to use the default renderer, true otherwise. */
    
    boolean renderItemInPipe(ItemStack stack, double x, double y, double z);
}

