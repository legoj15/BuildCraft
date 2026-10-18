/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.tile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.test.ValueIoTestUtil;
import buildcraft.robotics.BCRoboticsBlocks;

/**
 * The Requester across a save. The tile registers two item handlers (the request templates and the delivered goods)
 * but used to persist neither, so every chunk unload or server restart wiped the templates a player had set and
 * deleted whatever robots had already delivered.
 */
public class TileRequesterNbtTest extends VanillaSetupBaseTester {

    private static TileRequester newRequester() {
        return new TileRequester(BlockPos.ZERO, BCRoboticsBlocks.REQUESTER.get().defaultBlockState());
    }

    private static TileRequester reload(TileRequester requester) {
        CompoundTag saved = ValueIoTestUtil.write(requester::writeData);
        TileRequester reloaded = newRequester();
        reloaded.readData(ValueIoTestUtil.inputOf(saved));
        return reloaded;
    }

    private static void assertSameStack(ItemStack want, ItemStack got, String what) {
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(want, got),
                what + ": item and components must survive a save — wanted " + want + " but got " + got);
        Assertions.assertEquals(want.getCount(), got.getCount(), what + ": count");
    }

    @Test
    public void templatesAndDeliveriesSurviveAReload() {
        TileRequester requester = newRequester();
        requester.invRequests.setStackInSlot(2, new ItemStack(Items.DIAMOND, 5));
        requester.invRequests.setStackInSlot(19, new ItemStack(Items.IRON_INGOT, 64));
        requester.invDeliveries.setStackInSlot(2, new ItemStack(Items.DIAMOND, 3));

        TileRequester reloaded = reload(requester);

        assertSameStack(new ItemStack(Items.DIAMOND, 5), reloaded.invRequests.getStackInSlot(2), "template in slot 2");
        assertSameStack(new ItemStack(Items.IRON_INGOT, 64), reloaded.invRequests.getStackInSlot(19),
                "template in the last slot");
        assertSameStack(new ItemStack(Items.DIAMOND, 3), reloaded.invDeliveries.getStackInSlot(2),
                "delivery in slot 2");
        Assertions.assertTrue(reloaded.invRequests.getStackInSlot(0).isEmpty(), "an unset template stays unset");
        Assertions.assertTrue(reloaded.invDeliveries.getStackInSlot(19).isEmpty(),
                "a slot nothing was delivered to stays empty");
    }

    @Test
    public void theRequestStillOwedIsTheSameAfterAReload() {
        TileRequester requester = newRequester();
        requester.invRequests.setStackInSlot(7, new ItemStack(Items.DIAMOND, 5));
        requester.invDeliveries.setStackInSlot(7, new ItemStack(Items.DIAMOND, 2));

        TileRequester reloaded = reload(requester);

        assertSameStack(new ItemStack(Items.DIAMOND, 3), reloaded.getRequest(7), "the shortfall robots must deliver");
        Assertions.assertEquals(requester.getComparatorLevel(), reloaded.getComparatorLevel(),
                "the comparator output follows the restored contents");
    }

    @Test
    public void templateAndDeliveryKeepTheirDataComponents() {
        ItemStack named = new ItemStack(Items.DIAMOND, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Shiny"));
        TileRequester requester = newRequester();
        requester.invRequests.setStackInSlot(4, named.copy());
        requester.invDeliveries.setStackInSlot(4, named.copy());

        TileRequester reloaded = reload(requester);

        assertSameStack(named, reloaded.invRequests.getStackInSlot(4), "named template");
        assertSameStack(named, reloaded.invDeliveries.getStackInSlot(4), "named delivery");
    }
}
