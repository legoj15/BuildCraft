/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * The Builder's snapshot slot across a save. The slot is saved through {@code ItemStack.CODEC}, which bounds a count to
 * 1..99 and never checks the item's own stack limit; a stack of more would fail to encode and the slot would save empty.
 * (The slot takes snapshot items in play; the point is that the save does not depend on that.)
 */
public class TileBuilderSnapshotNbtTest extends VanillaSetupBaseTester {

    private static TileBuilder newBuilder() {
        return new TileBuilder(BlockPos.ZERO, BCBuildersBlocks.BUILDER.get().defaultBlockState());
    }

    private static ItemStack roundTrip(ItemStack snapshot) {
        TileBuilder builder = newBuilder();
        builder.setSnapshot(snapshot);
        CompoundTag saved = ValueIoTestUtil.write(builder::writeData);

        TileBuilder reloaded = newBuilder();
        reloaded.readData(ValueIoTestUtil.inputOf(saved));
        return reloaded.getSnapshot();
    }

    @Test
    public void aCountAboveWhatTheItemCodecCarriesSurvives() {
        ItemStack big = new ItemStack(Items.ENDER_PEARL, 128);
        big.set(DataComponents.CUSTOM_NAME, Component.literal("Hoard"));

        ItemStack got = roundTrip(big);

        Assertions.assertTrue(ItemStack.isSameItemSameComponents(big, got), "item and components: " + got);
        Assertions.assertEquals(128, got.getCount(), "count above 99");
    }

    @Test
    public void anOrdinaryStackStillRoundTrips() {
        ItemStack small = new ItemStack(Items.ENDER_PEARL, 16);

        Assertions.assertTrue(ItemStack.matches(small, roundTrip(small)));
    }
}
