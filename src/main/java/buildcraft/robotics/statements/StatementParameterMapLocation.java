package buildcraft.robotics.statements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.StatementMouseClick;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.item.ItemMapLocation;

/** A statement parameter holding a map location item (spot, area or zone marker). */
public class StatementParameterMapLocation extends StatementParameterItemStack {

    public StatementParameterMapLocation() {
        super();
    }

    public StatementParameterMapLocation(ItemStack stack) {
        super(stack);
    }

    public StatementParameterMapLocation(CompoundTag nbt) {
        super(nbt);
    }

    public static StatementParameterMapLocation readFromNbt(CompoundTag nbt) {
        return new StatementParameterMapLocation(nbt);
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:maplocation";
    }

    @Override
    public StatementParameterMapLocation onClick(
            IStatementContainer source, IStatement stmt, ItemStack clickedStack, StatementMouseClick mouseClick) {
        if (clickedStack.getItem() instanceof ItemMapLocation) {
            // The base's count-1 copy inlined (the base ignores the clicked stack and returns a bare
            // StatementParameterItemStack — the widget must handle the clicked stack itself).
            ItemStack copy = clickedStack.copy();
            copy.setCount(1);
            return new StatementParameterMapLocation(copy);
        }
        // Any other click (including empty) clears the parameter.
        return new StatementParameterMapLocation();
    }
}
