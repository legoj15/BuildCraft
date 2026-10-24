/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.list.ListHandler;
import buildcraft.transport.pipe.flow.TravellingItem;

/**
 * Where {@link StackNbt} is wired in: a stack above what {@code ItemStack.CODEC} carries (an item whose stack limit a
 * mod raised) must come out of a save as it went in at each site that saved through the plain codec before. The
 * helper's own round trip is {@link StackNbtTest}; these catch a site going back to the plain codec, which loses the
 * whole stack. The block-entity and robot-order sites are pinned beside their own tests.
 */
public class StackCountSavesTest extends VanillaSetupBaseTester {

    private static ItemStack hoard() {
        ItemStack stack = new ItemStack(Items.ENDER_PEARL, 128);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Hoard"));
        return stack;
    }

    private static void assertHoard(ItemStack got, String what) {
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(hoard(), got),
                what + ": item and components must survive a save, got " + got + " " + got.getComponentsPatch());
        Assertions.assertEquals(128, got.getCount(), what + ": count above 99");
    }

    @Test
    public void pipeCargoKeepsItsCount() {
        CompoundTag saved = new TravellingItem(hoard()).writeToNbt(0L);

        assertHoard(new TravellingItem(saved, 0L).getStack(), "an item in transit");
    }

    @Test
    public void aListExemplarKeepsItsCount() {
        ListHandler.Line line = new ListHandler.Line();
        line.stacks.set(0, hoard());

        assertHoard(ListHandler.Line.fromTag(line.toTag()).stacks.get(0), "a list line's exemplar");
    }
}
