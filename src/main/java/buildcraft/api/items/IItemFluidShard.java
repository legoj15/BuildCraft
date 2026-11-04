package buildcraft.api.items;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

import buildcraft.api.fluids.BCFluidStack;

public interface IItemFluidShard {
    void addFluidDrops(NonNullList<ItemStack> toDrop, @Nullable BCFluidStack fluid);
}

