/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.block;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.lib.test.EntityArenaUtil;

public class BlockFrameTester {

    /**
     * A quarry frame must not be washed away by flowing fluid. The frame is a non-full-cube block;
     * without {@code forceSolidOn()} its {@code blocksMotion()} is false, which makes
     * {@code FlowingFluid.canHoldFluid()} true — so flowing fluid (oil/water reaching the quarry area)
     * destroys the frame and drops the frame item, an otherwise-unobtainable block. That's an
     * infinite-frame exploit. With the frame forced solid, fluid flows AROUND it and it survives.
     *
     * <p>Places a frame, drops a water source directly above it, and after the fluid has had time to
     * spread asserts the frame block is still there.
     */
    public static void testFrameNotWashedAwayByFluid(GameTestHelper helper) {
        BlockPos frame = new BlockPos(2, 2, 2);
        // A scheduled fluid tick runs only in a ticking chunk, and the framework forces only the structure's own: the
        // water goes in once the cell ticks, and the 40 ticks it is given are the fluid's own. A clock started at
        // tick 1 could end before the water had flowed at all, and the frame would "survive" without being tested.
        EntityArenaUtil.forceLoadCell(helper);
        EntityArenaUtil.tickUntilThen(helper, EntityArenaUtil.CELL_READY_TICKS + READY_WORK_TICKS + FLOW_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, READY_WORK_TICKS, () -> true),
            () -> {
                helper.setBlock(frame, BCBuildersBlocks.FRAME.get());
                // Water source one block above — it tries to flow down into the frame's position.
                helper.setBlock(frame.above(), Blocks.WATER);
            }, FLOW_TICKS, () -> {
                // The fluid has had plenty of ticks to attempt the (now-blocked) spread: check the frame.
                BlockState s = helper.getBlockState(frame);
                if (!s.is(BCBuildersBlocks.FRAME.get())) {
                    helper.fail("Frame was washed away by fluid (now " + s.getBlock()
                        + ") — infinite-frame exploit; the frame must block motion so fluid flows around it.");
                }
                // The frame must have survived a flow, not a standstill: the water flowed sideways over the solid frame.
                if (!helper.getBlockState(frame.above().north()).is(Blocks.WATER)) {
                    helper.fail("Test precondition failed: the water never spread off its source in " + FLOW_TICKS
                        + " ticks, so nothing tried to wash the frame away.");
                }
                helper.succeed();
            }, "The arena cell never started ticking");
    }

    /** The wait, once the cell ticks, before the water goes in (immediate: the gate's condition is already true). */
    private static final int READY_WORK_TICKS = 10;
    /** How long the fluid runs before the frame is checked. Each poll's own budget adds
     *  {@code EntityArenaUtil.CELL_READY_TICKS}, read as such by {@code GameTestPollBudgetTester}. */
    private static final int FLOW_TICKS = 40;
}
