/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?}

import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.factory.tile.TileTank;

/**
 * Regression pins from the 2026-09 sweep of the upstream 7.1.20-7.1.27 fixes that never reached any 8.0.x
 * branch (record: docs/todo-details.md, "Upstream 7.1.x tail sweep").
 */
public class UpstreamTailRegressionTester {

    /**
     * Upstream #3307 ("Auto Workbench not reacting to item insertion correctly in all cases", 7.1.21
     * {@code 9417e8a59}): once a craft stalled for missing input, the old workbench kept a sticky "jammed"
     * flag that an automated insertion did not always clear, so it sat idle forever. The port's
     * {@code WorkbenchCrafting} re-evaluates materials on every inventory change; this pins that an
     * item arriving through the block's item capability after a stall resumes crafting.
     */
    public static void testAutoWorkbenchResumesAfterInputArrives(GameTestHelper helper) {
        BlockPos workbenchPos = new BlockPos(1, 2, 1);
        helper.setBlock(workbenchPos, BCFactoryBlocks.AUTOWORKBENCH_ITEM.get());
        //? if >=1.21.10 {
        TileAutoWorkbenchItems workbench = helper.getBlockEntity(workbenchPos, TileAutoWorkbenchItems.class);
        //?} else {
        /*TileAutoWorkbenchItems workbench = helper.getBlockEntity(workbenchPos);*/
        //?}

        workbench.invBlueprint.setStackInSlot(0, new ItemStack(Items.OAK_LOG));
        // Recompute the recipe and material filters, then stall: a full battery but no materials.
        workbench.serverTick();
        helper.assertTrue(workbench.crafting.getAssumedResult().is(Items.OAK_PLANKS),
            "Precondition: an oak-log blueprint must resolve to oak planks");
        workbench.getBattery().setStored(workbench.getBattery().getCapacity());
        workbench.serverTick();
        workbench.serverTick();
        helper.assertTrue(workbench.invResult.getStackInSlot(0).isEmpty(),
            "Precondition: nothing may be crafted while the materials are missing");

        // Deliver the missing input the way a hopper or pipe would: through the block's item capability.
        BlockPos abs = helper.absolutePos(workbenchPos);
        //? if >=1.21.10 {
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, abs, Direction.UP);
        helper.assertTrue(handler != null, "Precondition: the workbench must expose an item capability");
        int inserted;
        try (Transaction tx = Transaction.openRoot()) {
            inserted = handler.insert(ItemResource.of(new ItemStack(Items.OAK_LOG)), 1, tx);
            tx.commit();
        }
        //?} else {
        /*var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.UP);
        helper.assertTrue(handler != null, "Precondition: the workbench must expose an item capability");
        int inserted = 1 - net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(
            handler, new ItemStack(Items.OAK_LOG), false).getCount();*/
        //?}
        helper.assertTrue(inserted == 1, "The workbench must accept the blueprint's ingredient through its capability");

        workbench.getBattery().setStored(workbench.getBattery().getCapacity());
        workbench.serverTick();
        ItemStack result = workbench.invResult.getStackInSlot(0);
        helper.assertTrue(result.is(Items.OAK_PLANKS) && result.getCount() == 4,
            "A stalled workbench must craft as soon as the missing input arrives, got " + result);
        helper.succeed();
    }

    /**
     * The Tank drives a redstone comparator from its fill level (1.7.10 {@code BlockTank} and 1.12.2
     * {@code BlockTank.getComparatorInputOverride}); found missing while sweeping upstream {@code 4fb401ac7},
     * which guards the comparator override. Levels follow {@code TileTank.getComparatorLevel}: empty = 0,
     * any fluid = at least 1, full = 15.
     */
    public static void testTankDrivesComparator(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(2, 2, 2);
        helper.setBlock(tankPos, BCFactoryBlocks.TANK.get());
        //? if >=1.21.10 {
        TileTank tank = helper.getBlockEntity(tankPos, TileTank.class);
        //?} else {
        /*TileTank tank = helper.getBlockEntity(tankPos);*/
        //?}
        BlockState state = helper.getBlockState(tankPos);
        BlockPos abs = helper.absolutePos(tankPos);

        helper.assertTrue(state.hasAnalogOutputSignal(), "A tank must be readable by a comparator");
        helper.assertTrue(comparatorLevel(helper, state, abs) == 0, "An empty tank must read 0");

        tank.tank.fill(0, new FluidStack(Fluids.WATER, 8_000), false);
        int half = comparatorLevel(helper, state, abs);
        helper.assertTrue(half == 8, "A half-full tank must read 8, got " + half);

        // End to end: a real comparator settles on the half-full level, then must follow a later fill. Only
        // the tank's own tick can deliver that second change (it notices the new level and notifies its
        // neighbours), so both phases gate on the observed output, never a fixed tick. Every poll is
        // scheduled up front (see EntityArenaUtil.tickUntil for why).
        BlockPos comparatorPos = tankPos.east();
        helper.setBlock(comparatorPos.below(), Blocks.STONE);
        // A comparator reads from the block its FACING points at.
        helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState()
            .setValue(ComparatorBlock.FACING, Direction.WEST));
        int maxTicks = 80;
        int[] phase = { 0 };
        for (int t = 1; t <= maxTicks; t++) {
            final int tick = t;
            helper.runAfterDelay(tick, () -> {
                if (phase[0] == 2) {
                    return;
                }
                int output = comparatorOutput(helper, comparatorPos);
                if (phase[0] == 0 && output == 8) {
                    phase[0] = 1;
                    tank.tank.fill(0, new FluidStack(Fluids.WATER, 8_000), false);
                    int full = comparatorLevel(helper, state, abs);
                    helper.assertTrue(full == 15, "A full tank must read 15, got " + full);
                } else if (phase[0] == 1 && output == 15) {
                    phase[0] = 2;
                    helper.succeed();
                } else if (tick == maxTicks) {
                    int stuckAt = phase[0];
                    phase[0] = 2;
                    helper.fail("Comparator never followed the tank (phase " + stuckAt + ", output " + output + ")");
                }
            });
        }
    }

    private static int comparatorOutput(GameTestHelper helper, BlockPos comparatorPos) {
        //? if >=1.21.10 {
        ComparatorBlockEntity comparator = helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class);
        //?} else {
        /*ComparatorBlockEntity comparator = helper.getBlockEntity(comparatorPos);*/
        //?}
        return comparator.getOutputSignal();
    }

    private static int comparatorLevel(GameTestHelper helper, BlockState state, BlockPos abs) {
        //? if >=1.21.10 {
        return state.getAnalogOutputSignal(helper.getLevel(), abs, Direction.EAST);
        //?} else {
        /*return state.getAnalogOutputSignal(helper.getLevel(), abs);*/
        //?}
    }
}
