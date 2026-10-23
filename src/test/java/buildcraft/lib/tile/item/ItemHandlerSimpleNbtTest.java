/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.item;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;

/**
 * Counts across a save. {@code ItemStack.CODEC} bounds a count to 1..99, and the default inserter fills a slot up to
 * the item's own stack limit, which a mod can raise past that ({@code IItemExtension#getMaxStackSize}). A slot holding
 * more than 99 used to fail to encode and save empty.
 */
public class ItemHandlerSimpleNbtTest extends VanillaSetupBaseTester {

    private static ItemHandlerSimple roundTrip(ItemStack... stacks) {
        ItemHandlerSimple saved = new ItemHandlerSimple(stacks.length);
        for (int i = 0; i < stacks.length; i++) {
            saved.setStackInSlot(i, stacks[i].copy());
        }
        ItemHandlerSimple loaded = new ItemHandlerSimple(stacks.length);
        loaded.deserializeNBT(saved.serializeNBT());
        return loaded;
    }

    @Test
    public void aCountAboveWhatTheItemCodecCarriesSurvives() {
        ItemStack big = new ItemStack(Items.ENDER_PEARL, 128);
        big.set(DataComponents.CUSTOM_NAME, Component.literal("Hoard"));
        ItemStack small = new ItemStack(Items.APPLE, 7);

        ItemHandlerSimple loaded = roundTrip(big, small);

        ItemStack got = loaded.getStackInSlot(0);
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(big, got), "item and components: " + got);
        Assertions.assertEquals(128, got.getCount(), "count above 99");
        Assertions.assertTrue(ItemStack.matches(small, loaded.getStackInSlot(1)), "a count the codec carries");
    }
}
