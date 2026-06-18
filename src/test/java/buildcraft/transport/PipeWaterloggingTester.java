/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
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
 *   <li><b>{@code pipe_survives_flowing_water}</b> — end-to-end: a real water source above the pipe
 *       sends flowing water down onto it; the pipe must NOT be deleted (the literal reported bug).
 *       Note it does <em>not</em> end up waterlogged — vanilla {@code SimpleWaterloggedBlock.placeLiquid}
 *       only accepts a <em>source</em> ({@code fluidState.is(Fluids.WATER)} is false for falling/flowing
 *       water), so flowing water simply can't enter the cell. Waterlogging-from-source is covered by the
 *       deterministic test above; this one guards specifically against the destroy-on-flow regression.</li>
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

    // ---------- End-to-end: real flowing water floods the pipe rather than deleting it ----------

    public static void testPipeSurvivesFlowingWater(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos pipePos = new BlockPos(1, 2, 1);
        BlockPos waterPos = new BlockPos(1, 3, 1);

        helper.setBlock(floor, Blocks.STONE);
        placeWoodPipe(helper, pipePos);
        // A water source directly above sends flowing water down onto the pipe's cell every tick.
        helper.setBlock(waterPos, Blocks.WATER);
        // Kick the source so it schedules its spread deterministically (independent of setBlock flags).
        helper.getLevel().scheduleTick(helper.absolutePos(waterPos), Fluids.WATER, 1);

        // Check AFTER a delay (not succeedWhen, which would pass on tick 1 before any water flows):
        // by tick 40 the flowing water has been attacking the pipe cell for many ticks. Pre-fix, the
        // pipe would have been replaced by water and deleted with no drop — exactly the reported bug.
        // Post-fix it is a LiquidBlockContainer, so spreadTo no-ops (flowing water isn't a source, so
        // placeLiquid declines) and the pipe survives. The source above must still be present, proving
        // the water actually reached the pipe (otherwise the guard would be vacuous).
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(BCTransportBlocks.PIPE_HOLDER.get(), pipePos);
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(waterPos)).is(Fluids.WATER),
                    "the water source above the pipe should still be feeding flowing water onto it");
            helper.succeed();
        });
    }
}
