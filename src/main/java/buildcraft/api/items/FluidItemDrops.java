package buildcraft.api.items;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.IFluidTank;

public class FluidItemDrops {

    public static IItemFluidShard item;

    public static void addFluidDrops(NonNullList<ItemStack> toDrop, FluidStack... fluids) {
        if (item != null) {
            for (FluidStack fluid : fluids) {
                item.addFluidDrops(toDrop, fluid);
            }
        }
    }

    public static void addFluidDrops(NonNullList<ItemStack> toDrop, IFluidTank... tanks) {
        if (item != null) {
            for (IFluidTank tank : tanks) {
                item.addFluidDrops(toDrop, tank.getFluid());
            }
        }
    }

    @SafeVarargs
    public static void addFluidDrops(NonNullList<ItemStack> toDrop, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>... tanks) {
        if (item != null) {
            for (net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> tank : tanks) {
                if (tank != null && tank.size() > 0) {
                    net.neoforged.neoforge.transfer.fluid.FluidResource res = tank.getResource(0);
                    if (!res.isEmpty()) {
                        item.addFluidDrops(toDrop, res.toStack((int) tank.getAmountAsLong(0)));
                    }
                }
            }
        }
    }
}

