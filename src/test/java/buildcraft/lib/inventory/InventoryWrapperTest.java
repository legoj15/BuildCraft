/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;

/** {@link InventoryWrapper#insert} splits the stack it is handed even when simulating, so the all-or-none path in
 *  {@link AbstractInvItemTransactor} must work on its own copy: handing the caller's stack to the simulate pass
 *  shrank it before the real pass, which then inserted less than it reported (or nothing, returning EMPTY). */
public class InventoryWrapperTest extends VanillaSetupBaseTester {

    @Test
    public void allOrNoneInsertCommitsTheWholeStackAndLeavesTheCallersStackAlone() {
        SimpleContainer container = new SimpleContainer(2);
        InventoryWrapper wrapper = new InventoryWrapper(container);

        ItemStack input = new ItemStack(Items.STONE, 100);
        Assertions.assertTrue(wrapper.insert(input, true, true).isEmpty(), "100 stone fit in two empty slots");
        Assertions.assertEquals(100, input.getCount(), "a simulation leaves the caller's stack alone");
        Assertions.assertTrue(container.getItem(0).isEmpty() && container.getItem(1).isEmpty());

        Assertions.assertTrue(wrapper.insert(input, true, false).isEmpty());
        Assertions.assertEquals(64, container.getItem(0).getCount());
        Assertions.assertEquals(36, container.getItem(1).getCount());
    }

    @Test
    public void allOrNoneRefusalReturnsTheWholeStackAndInsertsNothing() {
        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, new ItemStack(Items.STONE, 64));
        container.setItem(1, new ItemStack(Items.STONE, 36));
        InventoryWrapper wrapper = new InventoryWrapper(container);

        // Only 28 more fit, so the whole 100 is refused, simulated or not.
        for (boolean simulate : new boolean[] {true, false}) {
            ItemStack input = new ItemStack(Items.STONE, 100);
            ItemStack overflow = wrapper.insert(input, true, simulate);
            Assertions.assertTrue(overflow.is(Items.STONE) && overflow.getCount() == 100,
                    "simulate=" + simulate + ", got " + overflow);
            Assertions.assertEquals(100, input.getCount(), "simulate=" + simulate + ", caller's stack left alone");
            Assertions.assertEquals(64, container.getItem(0).getCount());
            Assertions.assertEquals(36, container.getItem(1).getCount());
        }
    }
}
