package buildcraft.api.items;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.IFluidTank;

import buildcraft.lib.fluid.BCFluidTank;

public class FluidItemDrops {

    public static IItemFluidShard item;

    public static void addFluidDrops(NonNullList<ItemStack> toDrop, FluidStack... fluids) {
        if (item != null) {
            for (FluidStack fluid : fluids) {
                item.addFluidDrops(toDrop, fluid);
            }
        }
    }


    public static void addFluidDrops(NonNullList<ItemStack> toDrop, BCFluidTank... tanks) {
        if (item != null) {
            for (BCFluidTank tank : tanks) {
                if (tank != null) {
                    FluidStack fs = tank.getFluidStack(0);
                    if (!fs.isEmpty()) {
                        item.addFluidDrops(toDrop, fs);
                    }
                }
            }
        }
    }
}

