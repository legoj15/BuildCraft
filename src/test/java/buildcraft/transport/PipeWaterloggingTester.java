/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import buildcraft.lib.block.BlockWaterloggingTester;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Waterlogging regression guards for pipes.
 * <p>
 * Pipes have a partial (non-full) collision shape and — because {@code pipe_holder} is
 * {@code .dynamicShape()} — report {@code blocksMotion()==false}. Before pipes implemented
 * {@link net.minecraft.world.level.block.SimpleWaterloggedBlock}, that made them <em>floodable</em>:
 * {@code FlowingFluid.spreadTo} took the destroy branch ({@code beforeDestroyingBlock} +
 * {@code setBlock(water)}) instead of the coexist branch, and because pipe drops are code-driven
 * (no loot table; the pipe item drops only on a player break) the pipe vanished with <em>no drop</em>.
 * <p>
 * Two complementary tests:
 * <ol>
 *   <li><b>{@code pipe_waterloggable}</b> — deterministic: drives {@link LiquidBlockContainer#placeLiquid}
 *       directly (the exact call {@code FlowingFluid.spreadTo} makes for a {@code LiquidBlockContainer})
 *       and asserts the pipe is waterlogged, still present, reports a water fluid state, and keeps its
 *       BlockEntity. No ticking, so it can never flake on fluid timing.</li>
 *   <li><b>{@code pipe_survives_flowing_water}</b> — end-to-end: a real water source beside the pipe
 *       (inside a walled basin, so nothing leaks into neighbouring arenas) sends flowing water at it; the
 *       pipe must NOT be deleted (the literal reported bug). It does <em>not</em> end up waterlogged —
 *       {@code SimpleWaterloggedBlock.canPlaceLiquid} only accepts {@code Fluids.WATER} (the source), so
 *       flowing water can't enter the cell at all and is held back. Waterlogging from real sources is
 *       covered by {@code BlockWaterloggingTester}.</li>
 * </ol>
 */
public class PipeWaterloggingTester {

    /** Place a wood-item pipe at the given relative position and return the tile, the same way
     *  {@code PipeDropsTester} does — TilePipeHolder.onPlacedBy attaches a real Pipe so the BE is live. */
    private static TilePipeHolder placeWoodPipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        return tile;
    }

    // ---------- Deterministic: placeLiquid waterlogs the pipe instead of destroying it ----------

    public static void testPipeWaterloggable(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeWoodPipe(helper, pipePos);

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pipePos);
        BlockState state = level.getBlockState(absPos);

        // This is exactly what FlowingFluid.spreadTo invokes when the target block is a
        // LiquidBlockContainer — the branch that must run instead of the destroy branch.
        boolean placed = ((LiquidBlockContainer) state.getBlock())
                .placeLiquid(level, absPos, state, Fluids.WATER.getSource(false));
        helper.assertTrue(placed, "placeLiquid should waterlog the pipe, not reject the water");

        BlockState after = level.getBlockState(absPos);
        helper.assertBlockPresent(BCTransportBlocks.PIPE_HOLDER.get(), pipePos);
        helper.assertTrue(after.getValue(BlockStateProperties.WATERLOGGED),
                "pipe must be waterlogged after placeLiquid");
        helper.assertTrue(level.getFluidState(absPos).is(Fluids.WATER),
                "a waterlogged pipe must report a water fluid state");
        // Same-block state change preserves the BlockEntity, so the pipe must survive untouched.
        //? if >=1.21.10 {
        TilePipeHolder afterTile = helper.getBlockEntity(pipePos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder afterTile = helper.getBlockEntity(pipePos);*/
        //?}
        helper.assertTrue(afterTile != null && afterTile.getPipe() != null,
                "the pipe BlockEntity must survive waterlogging");

        helper.succeed();
    }

    // ---------- End-to-end: real flowing water is held back instead of deleting the pipe ----------

    public static void testPipeSurvivesFlowingWater(GameTestHelper helper) {
        // Contained basin (shared with BlockWaterloggingTester) so the water can never leak into a
        // neighbouring arena: stone floor + wall ring, one source west of the pipe, dry cell east of it.
        BlockWaterloggingTester.buildBasin(helper);
        BlockPos pipePos = BlockWaterloggingTester.SUBJECT;
        placeWoodPipe(helper, pipePos);
        BlockWaterloggingTester.placeSource(helper, BlockWaterloggingTester.WEST_SOURCE, Blocks.WATER, Fluids.WATER);

        boolean[] pipeRemoved = { false };
        helper.succeedWhen(() -> {
            if (!pipeRemoved[0]) {
                // Gate on observed state: the source has run its spread tick, and spreading is synchronous, so
                // the flowing water has already tried to enter the pipe's cell. Pre-fix the pipe was replaced
                // (and deleted with no drop) right here.
                helper.assertTrue(BlockWaterloggingTester.hasSpread(helper, BlockWaterloggingTester.WEST_SOURCE, Fluids.WATER),
                    "the water source has not spread yet");
                helper.assertBlockPresent(BCTransportBlocks.PIPE_HOLDER.get(), pipePos);
                // Flowing water is not a source, so it neither waterlogs the pipe nor passes through it.
                helper.assertFalse(helper.getBlockState(pipePos).getValue(BlockStateProperties.WATERLOGGED),
                    "flowing water must not waterlog the pipe (only a source can)");
                helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(BlockWaterloggingTester.EAST_SOURCE)).isEmpty(),
                    "the pipe must hold the flowing water back, not let it through to the far side");
                // Control: take the pipe away. The same source must now flow into the cell — positive proof the
                // water really was pressing on the pipe, so the survival above is not vacuous.
                helper.setBlock(pipePos, Blocks.AIR);
                pipeRemoved[0] = true;
            }
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pipePos)).getType().isSame(Fluids.WATER),
                "with the pipe gone, the source must flow into its cell (control for the hold-back check)");
        });
    }
}
