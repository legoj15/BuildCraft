/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.platform.Platform;

/**
 * A creative player clicking a tank slot with a fluid container on the cursor (#234) follows vanilla's
 * {@code ItemUtils.createFilledResult}: the cursor item never changes, the fluid moves a container's worth, and the
 * result goes to the inventory only when the inventory holds none like it.
 */
public final class CreativeCursorTankTester {
    private CreativeCursorTankTester() {}

    public static void testCreativeCursorExchangeGivesResultOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        BCFluidTank tank = new BCFluidTank(1, 4000);
        tank.fill(0, new BCFluidStack(Fluids.WATER, 3000), false);

        // Drain with an empty bucket: the tank gives a bucket's worth and the inventory gets one water bucket.
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(Platform.transfer().exchangeWithCarriedContainer(player, tank), "the drain is handled");
        helper.assertTrue(tank.getFluidStack(0).getAmount() == 2000,
                "a bucket's worth left the tank, now " + tank.getFluidStack(0).getAmount());
        assertCarried(helper, player, Items.BUCKET);
        helper.assertTrue(player.getInventory().countItem(Items.WATER_BUCKET) == 1,
                "the inventory got one water bucket, has " + player.getInventory().countItem(Items.WATER_BUCKET));

        // Again: the tank still drains, but the inventory already holds a water bucket, so it gets no second one.
        helper.assertTrue(Platform.transfer().exchangeWithCarriedContainer(player, tank), "the second drain is handled");
        helper.assertTrue(tank.getFluidStack(0).getAmount() == 1000,
                "a second bucket's worth left the tank, now " + tank.getFluidStack(0).getAmount());
        helper.assertTrue(player.getInventory().countItem(Items.WATER_BUCKET) == 1,
                "no second water bucket, has " + player.getInventory().countItem(Items.WATER_BUCKET));

        // Pour a water bucket: the tank fills and the inventory gets the empty bucket it had none of.
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(Platform.transfer().exchangeWithCarriedContainer(player, tank), "the pour is handled");
        helper.assertTrue(tank.getFluidStack(0).getAmount() == 2000,
                "a bucket's worth entered the tank, now " + tank.getFluidStack(0).getAmount());
        assertCarried(helper, player, Items.WATER_BUCKET);
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 1,
                "the inventory got one empty bucket, has " + player.getInventory().countItem(Items.BUCKET));

        // A stack of empty buckets on the cursor still moves one bucket's worth per click, as survival does.
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 3));
        helper.assertTrue(Platform.transfer().exchangeWithCarriedContainer(player, tank), "the stacked drain is handled");
        helper.assertTrue(tank.getFluidStack(0).getAmount() == 1000,
                "a stacked cursor drains one bucket's worth, tank now " + tank.getFluidStack(0).getAmount());
        ItemStack stacked = player.containerMenu.getCarried();
        helper.assertTrue(stacked.is(Items.BUCKET) && stacked.getCount() == 3,
                "the stacked cursor is unchanged, got " + stacked);
        helper.assertTrue(player.getInventory().countItem(Items.WATER_BUCKET) == 1,
                "no second water bucket from the stacked drain, has " + player.getInventory().countItem(Items.WATER_BUCKET));
        helper.succeed();
    }

    private static void assertCarried(GameTestHelper helper, Player player, net.minecraft.world.item.Item item) {
        ItemStack carried = player.containerMenu.getCarried();
        helper.assertTrue(carried.is(item) && carried.getCount() == 1,
                "the cursor item is unchanged (" + item + "), got " + carried);
    }
}
