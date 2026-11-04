package buildcraft.api.items;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

import buildcraft.api.fluids.BCFluidStack;

public class FluidItemDrops {

    public static IItemFluidShard item;

    /** Adds a fragile fluid-shard drop for each non-empty fluid. Callers holding BuildCraft tanks should pass the
     * tank's contents directly, e.g. {@code addFluidDrops(toDrop, tank.getFluidStack(0))}. */
    public static void addFluidDrops(NonNullList<ItemStack> toDrop, BCFluidStack... fluids) {
        if (item != null) {
            for (BCFluidStack fluid : fluids) {
                if (fluid != null && !fluid.isEmpty()) {
                    item.addFluidDrops(toDrop, fluid);
                }
            }
        }
    }
}
