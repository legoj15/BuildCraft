/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.block;

import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;

import buildcraft.api.transport.EnumWirePart;

import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;
import buildcraft.transport.wire.EnumWireBetween;

/**
 * Pins the {@link BlockPipeHolder#isWireConnected} predicate that gates the
 * {@code logic_transportation} advancement. The advancement should fire when a wire
 * placement actually completes a connection — not for an isolated wire — so the
 * predicate must distinguish four cases: isolated, in-cube neighbour matching colour,
 * in-cube neighbour wrong colour, and cross-tile neighbour matching colour.
 */
public class PipeWireConnectivityTester {

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos) {
        return placePipe(helper, relPos, new ItemStack(BCTransportItems.PIPE_STRUCTURE.get()));
    }

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos, ItemStack pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, pipeItem);
        return tile;
    }

    /** An isolated wire on an otherwise-empty pipe is not "connected". */
    public static void testIsolatedWireNotConnected(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placePipe(helper, relPos);
        BlockPos worldPos = helper.absolutePos(relPos);

        tile.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED);

        helper.assertFalse(
            BlockPipeHolder.isWireConnected(
                helper.getLevel(), worldPos, tile, EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED),
            "Isolated wire must not register as connected");
        helper.succeed();
    }

    /** Two same-colour wires at adjacent corners of the same cube are connected. */
    public static void testInCubeSameColourConnected(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placePipe(helper, relPos);
        BlockPos worldPos = helper.absolutePos(relPos);

        // WEST_DOWN_NORTH (0,0,0) and EAST_DOWN_NORTH (1,0,0) are adjacent in X within the same cube.
        tile.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED);
        tile.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.RED);

        helper.assertTrue(
            BlockPipeHolder.isWireConnected(
                helper.getLevel(), worldPos, tile, EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED),
            "Two same-colour wires at adjacent corners must register as connected");
        helper.succeed();
    }

    /** Adjacent wires of different colours are not connected. */
    public static void testInCubeDifferentColourNotConnected(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placePipe(helper, relPos);
        BlockPos worldPos = helper.absolutePos(relPos);

        tile.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED);
        tile.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.BLUE);

        helper.assertFalse(
            BlockPipeHolder.isWireConnected(
                helper.getLevel(), worldPos, tile, EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED),
            "Adjacent wires of different colours must not register as connected");
        helper.succeed();
    }

    /** A same-colour wire on an adjacent pipe (reached via WireNode.offset across the boundary)
     *  counts as connected. EAST_DOWN_NORTH on the west tile offsets to WEST_DOWN_NORTH on the
     *  east tile when stepping +X, so a wire at each of those positions forms a cross-tile pair. */
    public static void testCrossTileSameColourConnected(GameTestHelper helper) {
        BlockPos westRel = new BlockPos(1, 2, 1);
        BlockPos eastRel = new BlockPos(2, 2, 1);
        TilePipeHolder west = placePipe(helper, westRel);
        TilePipeHolder east = placePipe(helper, eastRel);

        west.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        east.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);

        helper.assertTrue(
            BlockPipeHolder.isWireConnected(
                helper.getLevel(), helper.absolutePos(westRel), west,
                EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW),
            "Same-colour wires on adjacent tiles at the touching corners must register as connected");
        helper.succeed();
    }

    /** Poll budget, counted from the cell ticking, for the link to form and then to drop (#243). */
    private static final int LINK_WORK_TICKS = 60;

    /** The server drops a wire link into a neighbouring pipe once that pipe is removed, and the collision shape (which
     *  merges the link's box) drops it too. Before #243 the server recomputed the links only on its own wire edits,
     *  so the survivor kept a solid sliver reaching into the empty block. A structure pipe beside a wood item pipe
     *  links wires without the pipes connecting, so the removal changes no connection and only the removed pipe's
     *  own neighbour refresh can drop the link. */
    public static void testCrossTileLinkDropsWhenNeighbourRemoved(GameTestHelper helper) {
        BlockPos westRel = new BlockPos(2, 2, 2);
        BlockPos eastRel = new BlockPos(3, 2, 2);
        TilePipeHolder west = placePipe(helper, westRel);
        TilePipeHolder east = placePipe(helper, eastRel, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        west.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        east.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);
        BlockPos westAbs = helper.absolutePos(westRel);
        // Inside EAST_DOWN_NORTH's link box (x 13..16, y 3..4, z 3..4) and outside the pipe's own arm.
        double px = 14.5 / 16, py = 3.5 / 16, pz = 3.5 / 16;
        BooleanSupplier linkInShape = () -> helper.getLevel().getBlockState(westAbs)
            .getCollisionShape(helper.getLevel(), westAbs, CollisionContext.empty()).toAabbs().stream()
            .anyMatch(box -> box.contains(px, py, pz));

        EntityArenaUtil.forceLoadCell(helper);
        boolean[] removed = { false };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + LINK_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, LINK_WORK_TICKS, () -> {
                boolean linked = west.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH);
                if (!removed[0]) {
                    // Phase 1: the link (and its box) exists while the neighbour is there.
                    if (linked && linkInShape.getAsBoolean()) {
                        helper.setBlock(eastRel, Blocks.AIR);
                        removed[0] = true;
                    }
                    return false;
                }
                // Phase 2: both go once the neighbour is gone.
                return !linked && !linkInShape.getAsBoolean();
            }, () -> {
                boolean linked = west.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH);
                boolean inShape = linkInShape.getAsBoolean();
                String state = "(betweens " + (linked ? "has" : "lacks") + " the link, collision shape "
                    + (inShape ? "has" : "lacks") + " its box)";
                return (removed[0] ? "the link into the removed pipe is still on the server " : "the link never formed ")
                    + state;
            }),
            helper::succeed, "wire link never dropped", () -> {
                helper.setBlock(westRel, Blocks.AIR);
                helper.setBlock(eastRel, Blocks.AIR);
            });
    }
}
