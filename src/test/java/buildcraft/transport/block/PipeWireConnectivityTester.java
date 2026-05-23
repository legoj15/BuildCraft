/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.block;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.EnumWirePart;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Pins the {@link BlockPipeHolder#isWireConnected} predicate that gates the
 * {@code logic_transportation} advancement. The advancement should fire when a wire
 * placement actually completes a connection — not for an isolated wire — so the
 * predicate must distinguish four cases: isolated, in-cube neighbour matching colour,
 * in-cube neighbour wrong colour, and cross-tile neighbour matching colour.
 */
public class PipeWireConnectivityTester {

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRUCTURE.get()));
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
}
