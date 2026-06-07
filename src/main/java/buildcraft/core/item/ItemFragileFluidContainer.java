package buildcraft.core.item;

import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
//? if >=1.21.10 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import java.util.function.Consumer;

import buildcraft.api.items.IItemFluidShard;
import buildcraft.core.BCCore;

@SuppressWarnings("deprecation")
public class ItemFragileFluidContainer extends Item implements IItemFluidShard {

    public static final int MAX_FLUID_HELD = 500;

    public ItemFragileFluidContainer(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        if (fluid.isEmpty()) {
            // No fluid stored — e.g. the bare item shown in the guide book or the
            // creative/JEI search. Use a generic name rather than substituting a
            // placeholder string into the "Fragile %s Shard" format.
            return Component.translatable(getDescriptionId() + ".name.empty");
        } else {
            return Component.translatable(getDescriptionId() + ".name", fluid.getHoverName().getString());
        }
    }

    @Override
    //? if >=1.21.10 {
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flagIn) {
        super.appendHoverText(stack, context, display, tooltip, flagIn);
    //?} else {
    /*// 1.21.1: appendHoverText has no TooltipDisplay and takes List<Component>; adapt to the shared
    // Consumer-based body below via tooltipList::add.
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> tooltipList, TooltipFlag flagIn) {
        Consumer<Component> tooltip = tooltipList::add;
        super.appendHoverText(stack, context, tooltipList, flagIn);*/
    //?}
        FluidStack fluid = getFluid(stack);
        if (!fluid.isEmpty() && fluid.getAmount() > 0) {
            tooltip.accept(Component.literal(fluid.getAmount() + " mB / " + MAX_FLUID_HELD + " mB"));
        }
    }

    @Override
    public void addFluidDrops(NonNullList<ItemStack> toDrop, @Nullable FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        int amount = fluid.getAmount();
        if (amount >= MAX_FLUID_HELD) {
            FluidStack fluid2 = fluid.copy();
            fluid2.setAmount(MAX_FLUID_HELD);
            while (amount >= MAX_FLUID_HELD) {
                ItemStack stack = new ItemStack(this);
                setFluid(stack, fluid2);
                amount -= MAX_FLUID_HELD;
                toDrop.add(stack);
            }
        }
        if (amount > 0) {
            ItemStack stack = new ItemStack(this);
            FluidStack fluid2 = fluid.copy();
            fluid2.setAmount(amount);
            setFluid(stack, fluid2);
            toDrop.add(stack);
        }
    }

    public static void setFluid(ItemStack container, FluidStack fluid) {
        if (fluid.isEmpty()) {
            container.remove(BCCore.FLUID_CONTENT.get());
            return;
        }
        container.set(BCCore.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(fluid));
    }

    public static FluidStack getFluid(ItemStack container) {
        if (container.isEmpty()) {
            return FluidStack.EMPTY;
        }
        SimpleFluidContent content = container.getOrDefault(BCCore.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY);
        return content.copy();
    }
}
