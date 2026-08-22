/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.lib.inventory.filter;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;
import buildcraft.lib.misc.StackUtil;

/**
 * Returns true if the stack matches any one one of the filter stacks. Takes
 * into account item lists.
 */
public class ArrayStackOrListFilter extends ArrayStackFilter {

    public ArrayStackOrListFilter(ItemStack... stacks) {
        super(stacks);
    }

    @Override
    public boolean matches(ItemStack stack) {
        if (stacks.size() == 0 || !hasFilter()) {
            return true;
        }

        for (ItemStack s : stacks) {
            if (StackUtil.isMatchingItemOrList(s, stack)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean matches(IStackFilter filter2) {
        // An empty or-list filter matches everything — the same contract as {@link #matches(ItemStack)}.
        if (stacks.size() == 0 || !hasFilter()) {
            return true;
        }
        return super.matches(filter2);
    }
}
