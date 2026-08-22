/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.lib.inventory.filter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;

/**
 * Returns true if the stack matches any one one of the filter stacks.
 */
public class StatementParameterStackFilter extends ArrayStackOrListFilter {

    public StatementParameterStackFilter(IStatementParameter... parameters) {
        super(collect(parameters));
    }

    private static ItemStack[] collect(IStatementParameter... parameters) {
        List<ItemStack> tmp = new ArrayList<>();

        for (IStatementParameter s : parameters) {
            if (s instanceof StatementParameterItemStack) {
                ItemStack stack = ((StatementParameterItemStack) s).getItemStack();
                if (!stack.isEmpty()) {
                    tmp.add(stack);
                }
            }
        }

        return tmp.toArray(new ItemStack[0]);
    }
}
