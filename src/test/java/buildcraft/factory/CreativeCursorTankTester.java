/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
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
 * result goes to the inventory only when the inventory holds none like it. Right-clicking a tank in the world with a
 * held stack of containers moves one container's worth too, in survival and creative.
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

    public static void testHeldStackMovesOneContainer(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);

        // Survival, one empty bucket: it becomes a water bucket in the hand.
        Player survivor = helper.makeMockPlayer(GameType.SURVIVAL);
        BCFluidTank tank = new BCFluidTank(1, 4000);
        tank.fill(0, new BCFluidStack(Fluids.WATER, 2000), false);
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        helper.assertTrue(Platform.transfer().interactWithHeldContainer(survivor, InteractionHand.MAIN_HAND, pos, tank),
                "the single drain is handled");
        assertTank(helper, tank, 1000, "a single held bucket drains one bucket's worth");
        assertHand(helper, survivor, Items.WATER_BUCKET, 1);
        helper.assertTrue(survivor.getInventory().countItem(Items.BUCKET) == 0,
                "no empty bucket is left over, has " + survivor.getInventory().countItem(Items.BUCKET));

        // Survival, a stack of three: one becomes a water bucket in the inventory, two stay in the hand.
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));
        helper.assertTrue(Platform.transfer().interactWithHeldContainer(survivor, InteractionHand.MAIN_HAND, pos, tank),
                "the stacked drain is handled");
        assertTank(helper, tank, 0, "a held stack drains one bucket's worth");
        assertHand(helper, survivor, Items.BUCKET, 2);
        helper.assertTrue(survivor.getInventory().countItem(Items.WATER_BUCKET) == 1,
                "the filled bucket went to the inventory, has " + survivor.getInventory().countItem(Items.WATER_BUCKET));
        helper.assertTrue(survivor.getInventory().countItem(Items.BUCKET) == 2,
                "only the two in the hand are empty buckets, has " + survivor.getInventory().countItem(Items.BUCKET));

        // Creative, a stack of three: the hand is unchanged and the inventory gets one water bucket.
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        tank.fill(0, new BCFluidStack(Fluids.WATER, 2000), false);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));
        helper.assertTrue(Platform.transfer().interactWithHeldContainer(creative, InteractionHand.MAIN_HAND, pos, tank),
                "the creative stacked drain is handled");
        assertTank(helper, tank, 1000, "a creative held stack drains one bucket's worth");
        assertHand(helper, creative, Items.BUCKET, 3);
        helper.assertTrue(creative.getInventory().countItem(Items.WATER_BUCKET) == 1,
                "the creative inventory got one water bucket, has " + creative.getInventory().countItem(Items.WATER_BUCKET));
        helper.assertTrue(creative.getInventory().countItem(Items.BUCKET) == 3,
                "only the three in the hand are empty buckets, has " + creative.getInventory().countItem(Items.BUCKET));

        // Creative again: the tank drains, but the inventory already holds a water bucket, so no second one.
        helper.assertTrue(Platform.transfer().interactWithHeldContainer(creative, InteractionHand.MAIN_HAND, pos, tank),
                "the second creative drain is handled");
        assertTank(helper, tank, 0, "the second creative drain takes another bucket's worth");
        helper.assertTrue(creative.getInventory().countItem(Items.WATER_BUCKET) == 1,
                "no second water bucket, has " + creative.getInventory().countItem(Items.WATER_BUCKET));

        // Survival pour: the held water bucket empties into the tank and becomes an empty bucket in the hand.
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(Platform.transfer().interactWithHeldContainer(survivor, InteractionHand.MAIN_HAND, pos, tank),
                "the pour is handled");
        assertTank(helper, tank, 1000, "a held water bucket pours one bucket's worth");
        assertHand(helper, survivor, Items.BUCKET, 1);
        helper.succeed();
    }

    private static void assertTank(GameTestHelper helper, BCFluidTank tank, int expected, String what) {
        int amount = tank.getFluidStack(0).getAmount();
        helper.assertTrue(amount == expected, what + ": expected " + expected + " mB, tank has " + amount);
    }

    private static void assertHand(GameTestHelper helper, Player player, net.minecraft.world.item.Item item, int count) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(held.is(item) && held.getCount() == count,
                "the hand should hold " + count + " x " + item + ", holds " + held);
    }

    private static void assertCarried(GameTestHelper helper, Player player, net.minecraft.world.item.Item item) {
        ItemStack carried = player.containerMenu.getCarried();
        helper.assertTrue(carried.is(item) && carried.getCount() == 1,
                "the cursor item is unchanged (" + item + "), got " + carried);
    }
}
