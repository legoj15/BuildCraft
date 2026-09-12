/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import buildcraft.api.transport.pipe.IPipe.ConnectedType;

import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Pins the client-visibility contract of {@link PipeFlowItems#insertItemsForce} — the insertion entry
 * point used by robot pipe stations (unload and request-offer), the obsidian pipe and the stripes pipe.
 *
 * <p>7.1.x announced every item entering a pipe to clients (sendTravelerPacket, unconditionally) and
 * let it visibly cross from the entry face to the center. The port's force path did neither: the item
 * was created with a zero-length entry leg ({@code genTimings(now, 0)}, so {@code timeToDest == 0}) and
 * was never passed to {@code sendItemDataToClient}, so robot-delivered cargo entered pipes invisibly
 * while every other entry path ({@code injectItem} → {@code insertItemEvents}) rendered fine. The fix
 * gives the force path the same real entry leg and client notification as the normal path.
 *
 * <p>{@code timeToDest == 0} is the exact server-side fingerprint of the old bug — a real insertion
 * always has a positive travel time ({@code getPipeLength} is at least 0.25) — and only this test
 * package can read the package-private field. Delivery into the chest is asserted end-to-end to prove
 * the longer entry leg did not break (or stall) transport.
 */
public class PipeFlowForceInsertTester {

    /** Rig: a chest under pipe B, pipe B east of pipe A. The item is force-inserted into pipe A from
     *  BELOW — the robot-station parity geometry, where {@code AIRobotUnload} injects through the face
     *  opposite the station pluggable. Path: A entry leg (below → center) → A exit (center → east)
     *  → B entry (west → center) → B exit (center → down) → chest. */
    public static void forceInsertEntryLegAndDelivery(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(2, 1, 1);
        BlockPos pipeBPos = new BlockPos(2, 2, 1);
        BlockPos pipeAPos = new BlockPos(1, 2, 1);

        helper.setBlock(chestPos, Blocks.CHEST);
        TilePipeHolder tileB = placeItemPipe(helper, pipeBPos, BCTransportItems.PIPE_COBBLE_ITEM.get());
        TilePipeHolder tileA = placeItemPipe(helper, pipeAPos, BCTransportItems.PIPE_WOOD_ITEM.get());

        // Connections resolve in onTick/updateConnections, and only when the pipe is marked for an
        // update — mark + tick synchronously so the rig does not depend on arena BE-tick scheduling.
        tileB.getPipe().markForUpdate();
        tileB.getPipe().onTick();
        tileA.getPipe().markForUpdate();
        tileA.getPipe().onTick();

        helper.assertTrue(tileA.getPipe().getConnectedType(Direction.EAST) == ConnectedType.PIPE,
            "pipe A should be pipe-connected east into pipe B");
        helper.assertTrue(tileB.getPipe().getConnectedType(Direction.WEST) == ConnectedType.PIPE,
            "pipe B should be pipe-connected west into pipe A");
        helper.assertTrue(tileB.getPipe().getConnectedType(Direction.DOWN) == ConnectedType.TILE,
            "pipe B should be tile-connected down into the chest");

        PipeFlowItems flowA = (PipeFlowItems) tileA.getPipe().getFlow();
        flowA.insertItemsForce(new ItemStack(Items.EMERALD, 3), Direction.DOWN, null, 0.04);

        List<TravellingItem> inFlight = flowA.getAllItemsForRender();
        helper.assertTrue(inFlight.size() == 1, "force insert must register the item in the pipe's flow");
        helper.assertTrue(inFlight.get(0).timeToDest > 0,
            "force-inserted item must have a real entry leg (timeToDest == 0 is the invisible-instant bug)");

        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(chestPos)) instanceof ChestBlockEntity chest)) {
            helper.fail("chest block entity missing from the rig");
            return;
        }

        EntityArenaUtil.tickUntil(helper, 200, () -> !chest.isEmpty(), () -> {
            helper.assertTrue(flowA.getAllItemsForRender().isEmpty(), "item should have left pipe A");
            PipeFlowItems flowB = (PipeFlowItems) tileB.getPipe().getFlow();
            helper.assertTrue(flowB.getAllItemsForRender().isEmpty(), "item should have left pipe B");
            helper.succeed();
        }, "emerald never reached the chest after force insertion");
    }

    /** Pipe A (the rig's west pipe) must be WOOD and B anything else: {@link PipeBehaviourWood}
     *  refuses wood-to-wood connections, so two adjacent wooden pipes would never link up. */
    private static TilePipeHolder placeItemPipe(GameTestHelper helper, BlockPos relPos, Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }
}
