/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.PipeEventItem;

import buildcraft.lib.test.WorkClaimMod;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Pins the stripes pipe's item drop through its (now cached, leased) fake player: a stack of blocks reaching the
 * pipe's open end places ONE block and the remainder goes back down the pipe. The travelling stack is handed to the
 * fake player's main hand and the handler shrinks that same stack; the pipe then drains the player's inventory back
 * into the flow before the lease scrubs it — if the main hand were not part of that drain, the remainder would be
 * silently deleted when the lease closes.
 */
public class PipeBehaviourStripesDropTester {

    public static void testDropPlacesOneAndReturnsRemainder(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.above();
        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipeRel);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRIPES_ITEM.get()));

        if (!(tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes stripes)) {
            helper.fail("the stripes pipe item did not produce a stripes behaviour");
            return;
        }
        // Set synchronously and used in the same call: an unconnected stripes pipe clears its direction on tick.
        stripes.direction = Direction.UP;

        ItemStack travelling = new ItemStack(Items.COBBLESTONE, 5);
        ItemEntity carrier = new ItemEntity(helper.getLevel(), 0, 0, 0, travelling);
        PipeFlowItems flow = (PipeFlowItems) tile.getPipe().getFlow();
        PipeEventItem.Drop drop = new PipeEventItem.Drop(tile, (IFlowItems) flow, carrier);

        stripes.onDrop(drop);

        helper.assertBlockPresent(Blocks.COBBLESTONE, targetRel);
        helper.assertTrue(drop.getStack().isEmpty(), "a handled drop no longer carries a stack");

        List<TravellingItem> returned = flow.getAllItemsForRender();
        int returnedCobble = 0;
        for (TravellingItem item : returned) {
            ItemStack stack = item.getStack();
            if (stack.is(Items.COBBLESTONE)) {
                returnedCobble += stack.getCount();
            }
        }
        helper.assertTrue(returnedCobble == 4,
                "the 4 unplaced cobblestone must travel back down the pipe, found " + returnedCobble);

        // Empty the cargo so removing the arena's pipe drops nothing into neighbouring tests.
        for (TravellingItem item : returned) {
            item.getStack().setCount(0);
        }
        helper.succeed();
    }

    /**
     * A hoe reaching the open end of a stripes pipe asks the right-click-block event before it tills, as a player's
     * right-click does: a claim mod that cancels that event keeps the dirt dirt and the hoe unconsumed, and the same
     * pipe tills when nothing refuses. On 26.3 that event is the only ask a till gets (hoes became a block
     * transformer, which posts no tool-modification event), so a pipe that called {@code useOn} directly would till
     * a claimed block.
     */
    public static void testHoeAsksTheRightClickEventBeforeTilling(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos soilRel = pipeRel.above();
        BlockPos soil = helper.absolutePos(soilRel);
        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipeRel);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRIPES_ITEM.get()));

        if (!(tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes stripes)) {
            helper.fail("the stripes pipe item did not produce a stripes behaviour");
            return;
        }
        // Set synchronously and used in the same call: an unconnected stripes pipe clears its direction on tick.
        stripes.direction = Direction.UP;
        helper.setBlock(soilRel, Blocks.DIRT);
        PipeFlowItems flow = (PipeFlowItems) tile.getPipe().getFlow();

        try (WorkClaimMod claim = new WorkClaimMod(soil::equals, soil::equals)) {
            PipeEventItem.Drop refused = hoeDrop(helper, tile, flow);
            stripes.onDrop(refused);

            helper.assertTrue(claim.toolAsks(soil) == 1,
                    "the pipe asks the right-click event once for the block it would till, got "
                            + claim.toolAsks(soil));
            helper.assertTrue(helper.getBlockState(soilRel).is(Blocks.DIRT), "a refused till leaves the dirt alone");
            helper.assertTrue(refused.getStack().is(Items.DIAMOND_HOE),
                    "a refused hoe is not consumed by the pipe, got " + refused.getStack());
        }

        try (WorkClaimMod claim = new WorkClaimMod(soil::equals, pos -> false)) {
            PipeEventItem.Drop allowed = hoeDrop(helper, tile, flow);
            stripes.onDrop(allowed);

            helper.assertTrue(claim.toolAsks(soil) == 1,
                    "the pipe asks the right-click event once when nothing refuses, got " + claim.toolAsks(soil));
            helper.assertTrue(helper.getBlockState(soilRel).is(Blocks.FARMLAND), "an allowed till tills the dirt");
            helper.assertTrue(allowed.getStack().isEmpty(), "a handled drop no longer carries a stack");
        }

        // Empty the cargo (the used hoe goes back down the pipe) so removing the arena's pipe drops nothing into
        // neighbouring tests.
        for (TravellingItem item : flow.getAllItemsForRender()) {
            item.getStack().setCount(0);
        }
        helper.succeed();
    }

    private static PipeEventItem.Drop hoeDrop(GameTestHelper helper, TilePipeHolder tile, PipeFlowItems flow) {
        ItemEntity carrier = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.DIAMOND_HOE));
        return new PipeEventItem.Drop(tile, (IFlowItems) flow, carrier);
    }
}
