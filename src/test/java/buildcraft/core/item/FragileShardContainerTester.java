/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The fragile fluid shard as the loader's item fluid container (NeoForge: {@code Capabilities.Fluid.ITEM}; Fabric:
 * {@code FluidStorage.ITEM}), seen through BuildCraft's own container helpers: it reports its fluid, can be drained in
 * part or in full, is used up once empty, and can never be filled. The unit test {@code FluidContainerHelperTest}
 * covers the same on the NeoForge nodes only; this one runs on Fabric too.
 */
public final class FragileShardContainerTester {
    private FragileShardContainerTester() {}

    private static ItemHandlerSimple slotWithShard(int waterMb) {
        ItemStack shard = new ItemStack(BCCoreItems.FRAGILE_FLUID_CONTAINER.get());
        ItemFragileFluidContainer.setFluid(shard, new BCFluidStack(Fluids.WATER, waterMb));
        ItemHandlerSimple slots = new ItemHandlerSimple(1, 1);
        slots.setStackInSlot(0, shard);
        return slots;
    }

    public static void testShardIsDrainOnlyConsumableContainer(GameTestHelper helper) {
        ItemHandlerSimple slots = slotWithShard(300);
        ItemStack shard = slots.stacks.get(0);
        helper.assertTrue(FluidUtilBC.isFluidContainer(shard), "the shard is a fluid container");
        BCFluidStack contained = FluidUtilBC.getFluidContained(shard);
        helper.assertTrue(contained.is(Fluids.WATER) && contained.getAmount() == 300,
                "the shard reports its 300 mB of water, got " + FluidUtilBC.getDebugString(contained));

        // A tank with room for only part of it: the shard keeps the rest.
        BCFluidTank small = new BCFluidTank(1, 100);
        helper.assertTrue(FluidUtilBC.drainContainerSlot(slots, 0, small), "the shard drains into a small tank");
        helper.assertTrue(small.getFluidStack(0).getAmount() == 100, "the small tank took 100 mB");
        BCFluidStack rest = FluidUtilBC.getFluidContained(slots.stacks.get(0));
        helper.assertTrue(rest.is(Fluids.WATER) && rest.getAmount() == 200,
                "the shard keeps the 200 mB left, got " + FluidUtilBC.getDebugString(rest));

        // It can never be filled, not even with the fluid it holds.
        BCFluidTank full = new BCFluidTank(1, 4000);
        full.fill(0, new BCFluidStack(Fluids.WATER, 1000), false);
        helper.assertTrue(!FluidUtilBC.fillContainerSlot(slots, 0, full), "the shard cannot be filled");
        helper.assertTrue(full.getFluidStack(0).getAmount() == 1000, "a refused fill takes nothing from the tank");

        // Drained in full, it is used up.
        BCFluidTank big = new BCFluidTank(1, 4000);
        helper.assertTrue(FluidUtilBC.drainContainerSlot(slots, 0, big), "the shard drains the rest");
        helper.assertTrue(big.getFluidStack(0).getAmount() == 200, "the big tank took the last 200 mB");
        helper.assertTrue(slots.stacks.get(0).isEmpty(), "an emptied shard is used up, got " + slots.stacks.get(0));
        helper.succeed();
    }
}
