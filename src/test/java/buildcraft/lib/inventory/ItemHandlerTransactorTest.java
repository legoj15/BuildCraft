/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IStackFilter;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/** {@link ItemHandlerTransactor} reads and writes through the node's item-capability API (Transfer API transactions on
 *  1.21.10+, the classic handler on 1.21.1), so a simulate that commits, or a real move that does not, shows up here
 *  as robots duplicating or never receiving a station's items. */
public class ItemHandlerTransactorTest extends VanillaSetupBaseTester {

    private static final IStackFilter DIRT = stack -> stack.is(Items.DIRT);

    private static ItemHandlerSimple supply() {
        ItemHandlerSimple handler = new ItemHandlerSimple(4);
        handler.setStackInSlot(0, new ItemStack(Items.DIRT, 10));
        handler.setStackInSlot(1, new ItemStack(Items.STONE, 5));
        handler.setStackInSlot(2, new ItemStack(Items.DIRT, 7));
        return handler;
    }

    @Test
    public void simulatedExtractLeavesTheInventoryAlone() {
        ItemHandlerSimple handler = supply();
        ItemStack seen = new ItemHandlerTransactor(handler).extract(DIRT, 1, 64, true);
        Assertions.assertTrue(seen.is(Items.DIRT) && seen.getCount() == 17, "both dirt slots, got " + seen);
        Assertions.assertEquals(10, handler.getStackInSlot(0).getCount());
        Assertions.assertEquals(7, handler.getStackInSlot(2).getCount());
    }

    @Test
    public void extractTakesAcrossSlotsUpToMaxAndHonoursMin() {
        ItemHandlerSimple handler = supply();
        ItemHandlerTransactor transactor = new ItemHandlerTransactor(handler);

        ItemStack taken = transactor.extract(DIRT, 1, 12, false);
        Assertions.assertTrue(taken.is(Items.DIRT) && taken.getCount() == 12, "got " + taken);
        int dirtLeft = handler.getStackInSlot(0).getCount() + handler.getStackInSlot(2).getCount();
        Assertions.assertEquals(5, dirtLeft);
        Assertions.assertEquals(5, handler.getStackInSlot(1).getCount(), "stone fails the filter and stays");

        Assertions.assertTrue(transactor.extract(DIRT, 6, 64, false).isEmpty(), "only 5 dirt remain, min is 6");
        Assertions.assertEquals(5, handler.getStackInSlot(0).getCount() + handler.getStackInSlot(2).getCount());
    }

    @Test
    public void insertMergesThenFillsEmptySlotsAndReturnsOverflow() {
        ItemHandlerSimple handler = supply();
        ItemHandlerTransactor transactor = new ItemHandlerTransactor(handler);

        ItemStack overflow = transactor.insert(new ItemStack(Items.STONE, 100), false, false);
        // 59 tops up the stone slot, the other 41 land in the empty slot 3.
        Assertions.assertTrue(overflow.isEmpty(), "got " + overflow);
        Assertions.assertEquals(64, handler.getStackInSlot(1).getCount());
        Assertions.assertEquals(41, handler.getStackInSlot(3).getCount());

        // Only 23 more stone fit, so all-or-none inserts nothing and refuses the whole 30, not the 7 that overflowed.
        ItemStack refused = new ItemStack(Items.STONE, 30);
        ItemStack refusedOverflow = transactor.insert(refused, true, false);
        Assertions.assertTrue(refusedOverflow.is(Items.STONE) && refusedOverflow.getCount() == 30,
                "all-or-none overflow is the whole input, got " + refusedOverflow);
        Assertions.assertEquals(30, refused.getCount(), "the caller's stack is left alone");
        Assertions.assertEquals(64, handler.getStackInSlot(1).getCount());
        Assertions.assertEquals(41, handler.getStackInSlot(3).getCount(), "all-or-none must insert nothing");
    }
}
