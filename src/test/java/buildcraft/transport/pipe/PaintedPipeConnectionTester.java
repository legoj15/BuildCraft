/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.blocks.CustomPaintHelper;
import buildcraft.api.transport.pipe.IPipe;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Pins the colour-aware connection rules from {@link Pipe#canColoursConnect(DyeColor, DyeColor)}
 * across the full paint pipeline — direct {@link Pipe#setColour}, the real paintbrush event
 * ({@link CustomPaintHelper#attemptPaintBlock}), and the post-paint NBT round-trip that the
 * client performs after receiving the {@code sendBlockUpdated} packet.
 *
 * <p>Pinned invariants:
 * <ul>
 *   <li>Unpainted + unpainted → connects (sanity).</li>
 *   <li>Painted (any colour) + unpainted → connects ({@code two == null} branch).</li>
 *   <li>Painted PINK + painted PINK → connects ({@code one == two} branch).</li>
 *   <li>Painted pipes that hit any of the above stay connected through a tick AND through an
 *       NBT round-trip (so paint colour and the "con" bitfield both survive client sync).</li>
 * </ul>
 *
 * <p>Two same-coloured pipes connect; two differently-coloured pipes do not — that's the
 * BC paint-colour invariant. These tests guard the "painted-talks-to-unpainted" half of it
 * specifically, which is the easy one to break by tightening the conditional.
 */
public class PaintedPipeConnectionTester {

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos, Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    /** Sanity: two adjacent unpainted fluid pipes connect on the touching faces. */
    public static void testUnpaintedVoidFluidPipeConnects(GameTestHelper helper) {
        BlockPos voidPos = new BlockPos(1, 2, 1);
        BlockPos stonePos = new BlockPos(2, 2, 1);

        TilePipeHolder voidTile = placePipe(helper, voidPos, BCTransportItems.PIPE_VOID_FLUID.get());
        TilePipeHolder stoneTile = placePipe(helper, stonePos, BCTransportItems.PIPE_STONE_FLUID.get());

        IPipe voidPipe = voidTile.getPipe();
        IPipe stonePipe = stoneTile.getPipe();

        // Force the per-pipe updateConnections() (server tick path)
        voidPipe.markForUpdate();
        stonePipe.markForUpdate();
        ((Pipe) voidPipe).onTick();
        ((Pipe) stonePipe).onTick();

        helper.assertTrue(voidPipe.isConnected(Direction.EAST),
                "Unpainted void fluid pipe should connect EAST to adjacent stone fluid pipe");
        helper.assertTrue(stonePipe.isConnected(Direction.WEST),
                "Unpainted stone fluid pipe should connect WEST to adjacent void fluid pipe");
        helper.succeed();
    }

    /** Painting only the void pipe pink must not break its connection to an unpainted
     *  neighbour ({@code canColoursConnect(PINK, null)} → true via the {@code two == null}
     *  branch). Exercises the direct {@link Pipe#setColour} path. */
    public static void testPinkPaintedVoidFluidPipeStillConnectsToUnpaintedNeighbour(GameTestHelper helper) {
        BlockPos voidPos = new BlockPos(1, 2, 1);
        BlockPos stonePos = new BlockPos(2, 2, 1);

        TilePipeHolder voidTile = placePipe(helper, voidPos, BCTransportItems.PIPE_VOID_FLUID.get());
        TilePipeHolder stoneTile = placePipe(helper, stonePos, BCTransportItems.PIPE_STONE_FLUID.get());

        IPipe voidPipe = voidTile.getPipe();
        IPipe stonePipe = stoneTile.getPipe();

        // Initial connection check (matches the unpainted sanity test)
        voidPipe.markForUpdate();
        stonePipe.markForUpdate();
        ((Pipe) voidPipe).onTick();
        ((Pipe) stonePipe).onTick();

        helper.assertTrue(voidPipe.isConnected(Direction.EAST),
                "Pre-paint: void pipe should connect EAST to stone pipe");

        // Paint the void pipe pink
        voidPipe.setColour(DyeColor.PINK);

        // setColour() calls markForUpdate(); run a tick to flush updateConnections()
        ((Pipe) voidPipe).onTick();
        ((Pipe) stonePipe).onTick();

        helper.assertTrue(voidPipe.getColour() == DyeColor.PINK,
                "setColour(PINK) should have stuck — definition.canBeColoured must be true for void_fluid");

        helper.assertTrue(voidPipe.isConnected(Direction.EAST),
                "Post-paint: pink-painted void fluid pipe must still connect EAST to unpainted stone fluid pipe — canColoursConnect(PINK, null) is true");
        helper.assertTrue(stonePipe.isConnected(Direction.WEST),
                "Post-paint: unpainted stone fluid pipe must still connect WEST to pink-painted void fluid pipe — canColoursConnect(null, PINK) is true");
        helper.succeed();
    }

    /** Same as testPink…, but goes through the REAL paint pipeline used by the paintbrush
     *  ({@link CustomPaintHelper#attemptPaintBlock}) and ticks the holder so the full server-side
     *  update path runs (sendBlockUpdated, getUpdateTag, etc.). */
    public static void testPaintbrushPaintedVoidFluidPipeStillConnects(GameTestHelper helper) {
        BlockPos voidPos = new BlockPos(1, 2, 1);
        BlockPos stonePos = new BlockPos(2, 2, 1);

        TilePipeHolder voidTile = placePipe(helper, voidPos, BCTransportItems.PIPE_VOID_FLUID.get());
        TilePipeHolder stoneTile = placePipe(helper, stonePos, BCTransportItems.PIPE_STONE_FLUID.get());

        IPipe voidPipe = voidTile.getPipe();
        IPipe stonePipe = stoneTile.getPipe();

        // Initial connection — run the full tick to flush all updates.
        voidTile.tick();
        stoneTile.tick();
        helper.assertTrue(voidPipe.isConnected(Direction.EAST),
                "Pre-paint: void pipe must connect EAST to stone pipe");

        // Drive the real paint event the paintbrush uses.
        BlockPos absVoid = helper.absolutePos(voidPos);
        InteractionResult result = CustomPaintHelper.INSTANCE.attemptPaintBlock(
                helper.getLevel(), absVoid, helper.getLevel().getBlockState(absVoid),
                Vec3.atCenterOf(absVoid), Direction.UP, DyeColor.PINK);

        helper.assertTrue(result == InteractionResult.SUCCESS,
                "Paintbrush should accept the colour change on void_fluid (canBeColoured must be true). Got: " + result);
        helper.assertTrue(voidPipe.getColour() == DyeColor.PINK,
                "After paintbrush event, void pipe colour must be PINK");

        // Flush all post-paint updates via the holder tick (runs updateConnections + sendBlockUpdated)
        voidTile.tick();
        stoneTile.tick();

        helper.assertTrue(voidPipe.isConnected(Direction.EAST),
                "Post-paintbrush: pink void fluid pipe must still connect EAST to unpainted stone pipe");
        helper.assertTrue(stonePipe.isConnected(Direction.WEST),
                "Post-paintbrush: unpainted stone pipe must still connect WEST to pink void pipe");
        helper.succeed();
    }

    /** Pin the NBT round-trip — {@link Pipe#writeToNbt} → {@link Pipe#readFromNbt} — that the
     *  client uses after receiving the post-paint {@code sendBlockUpdated} packet. If colour or
     *  the connection bitfield ("con" int) corrupts in flight, the client renders a stub. */
    public static void testNbtRoundTripPreservesColourAndConnections(GameTestHelper helper) {
        BlockPos voidPos = new BlockPos(1, 2, 1);
        BlockPos stonePos = new BlockPos(2, 2, 1);

        TilePipeHolder voidTile = placePipe(helper, voidPos, BCTransportItems.PIPE_VOID_FLUID.get());
        placePipe(helper, stonePos, BCTransportItems.PIPE_STONE_FLUID.get());

        Pipe voidPipe = (Pipe) voidTile.getPipe();

        voidPipe.markForUpdate();
        voidPipe.onTick();
        voidPipe.setColour(DyeColor.PINK);
        voidPipe.onTick();

        helper.assertTrue(voidPipe.isConnected(Direction.EAST),
                "Pre-roundtrip: void pipe must be connected EAST");
        helper.assertTrue(voidPipe.getColour() == DyeColor.PINK,
                "Pre-roundtrip: void pipe must be PINK");

        // Roundtrip through NBT, as the server→client sync does.
        var nbt = voidPipe.writeToNbt();
        voidPipe.readFromNbt(nbt);

        helper.assertTrue(voidPipe.getColour() == DyeColor.PINK,
                "Post-NBT-roundtrip: colour must still be PINK — got " + voidPipe.getColour());
        helper.assertTrue(voidPipe.isConnected(Direction.EAST),
                "Post-NBT-roundtrip: EAST connection must survive the 'con' bitfield encode/decode");
        helper.succeed();
    }

    /** Control: painting BOTH pipes the same color should keep them connected
     *  ({@code canColoursConnect(PINK, PINK)} → true via {@code one == two}). */
    public static void testTwoPinkFluidPipesConnect(GameTestHelper helper) {
        BlockPos voidPos = new BlockPos(1, 2, 1);
        BlockPos stonePos = new BlockPos(2, 2, 1);

        TilePipeHolder voidTile = placePipe(helper, voidPos, BCTransportItems.PIPE_VOID_FLUID.get());
        TilePipeHolder stoneTile = placePipe(helper, stonePos, BCTransportItems.PIPE_STONE_FLUID.get());

        IPipe voidPipe = voidTile.getPipe();
        IPipe stonePipe = stoneTile.getPipe();

        voidPipe.setColour(DyeColor.PINK);
        stonePipe.setColour(DyeColor.PINK);

        ((Pipe) voidPipe).onTick();
        ((Pipe) stonePipe).onTick();

        helper.assertTrue(voidPipe.isConnected(Direction.EAST),
                "Both pipes painted pink should connect");
        helper.succeed();
    }

    /** Control: pipes painted DIFFERENT colours must NOT connect — this is the working-
     *  as-designed behaviour that misled the user into reporting a bug when they had a
     *  pink void pipe surrounded by pipes painted other colours. */
    public static void testDifferentColouredFluidPipesDoNotConnect(GameTestHelper helper) {
        BlockPos voidPos = new BlockPos(1, 2, 1);
        BlockPos stonePos = new BlockPos(2, 2, 1);

        TilePipeHolder voidTile = placePipe(helper, voidPos, BCTransportItems.PIPE_VOID_FLUID.get());
        TilePipeHolder stoneTile = placePipe(helper, stonePos, BCTransportItems.PIPE_STONE_FLUID.get());

        IPipe voidPipe = voidTile.getPipe();
        IPipe stonePipe = stoneTile.getPipe();

        voidPipe.setColour(DyeColor.PINK);
        stonePipe.setColour(DyeColor.LIME);

        ((Pipe) voidPipe).onTick();
        ((Pipe) stonePipe).onTick();

        helper.assertFalse(voidPipe.isConnected(Direction.EAST),
                "Pink + Lime fluid pipes must NOT connect — canColoursConnect(PINK, LIME) is false");
        helper.assertFalse(stonePipe.isConnected(Direction.WEST),
                "Lime + Pink fluid pipes must NOT connect (symmetric check)");
        helper.succeed();
    }
}
