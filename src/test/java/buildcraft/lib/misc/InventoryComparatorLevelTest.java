/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * {@link InventoryUtil#getComparatorLevel}: the 7.1.x {@code BlockBuildCraft.getComparatorInputOverride} formula
 * (the vanilla container formula restricted to the slots that "count") that drives the Chute, Filtered Buffer and
 * Requester comparator output. Includes the no-counted-slot case, where 7.1.x divided by zero.
 */
public class InventoryComparatorLevelTest extends VanillaSetupBaseTester {

    @Test
    void noCountedSlotReadsZeroEvenWhenItemsArePresent() {
        ItemHandlerSimple inv = new ItemHandlerSimple(4);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        Assertions.assertEquals(0, InventoryUtil.getComparatorLevel(inv, slot -> false));
    }

    @Test
    void emptyCountedSlotsReadZero() {
        Assertions.assertEquals(0, InventoryUtil.getComparatorLevel(new ItemHandlerSimple(4), slot -> true));
    }

    @Test
    void anyItemReadsAtLeastOne() {
        ItemHandlerSimple inv = new ItemHandlerSimple(4);
        inv.setStackInSlot(3, new ItemStack(Items.STONE, 1));
        Assertions.assertEquals(1, InventoryUtil.getComparatorLevel(inv, slot -> true));
    }

    @Test
    void fullCountedSlotsReadFifteen() {
        ItemHandlerSimple inv = new ItemHandlerSimple(2);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        inv.setStackInSlot(1, new ItemStack(Items.ENDER_PEARL, 16));
        Assertions.assertEquals(15, InventoryUtil.getComparatorLevel(inv, slot -> true));
    }

    @Test
    void onlyCountedSlotsWeigh() {
        ItemHandlerSimple inv = new ItemHandlerSimple(4);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 32));
        inv.setStackInSlot(2, new ItemStack(Items.STONE, 64)); // not counted: must not raise the level
        // Slots 0 and 1 count: (32/64) / 2 = 0.25 -> floor(0.25 * 14) + 1 = 4.
        Assertions.assertEquals(4, InventoryUtil.getComparatorLevel(inv, slot -> slot < 2));
    }

    @Test
    void slotCapacityBelowStackSizeIsTheLimit() {
        ItemHandlerSimple inv = new ItemHandlerSimple(1, 16);
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 16));
        Assertions.assertEquals(15, InventoryUtil.getComparatorLevel(inv, slot -> true));
    }
}
