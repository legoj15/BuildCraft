/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.world.item.ItemStack;

import buildcraft.core.BCCoreItems;
import buildcraft.lib.list.ListHandler;

/** Builds BuildCraft List stacks for tests that need a real filter item (unit tests and game tests alike - this
 *  deliberately lives outside any JUnit class, so a game test can use it without loading one). */
public final class ListStacks {

    private ListStacks() {}

    /** A List whose first line holds {@code exemplars}: the stack a player drops into a template or filter slot
     *  to say "any of these", wanting {@code count} of it where the slot reads a count. */
    public static ItemStack listHolding(int count, ItemStack... exemplars) {
        ItemStack list = new ItemStack(BCCoreItems.LIST.get(), count);
        ListHandler.Line[] lines = ListHandler.getLines(list);
        for (int i = 0; i < exemplars.length; i++) {
            lines[0].stacks.set(i, exemplars[i]);
        }
        ListHandler.saveLines(list, lines);
        return list;
    }
}
