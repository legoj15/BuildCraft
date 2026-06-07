package buildcraft.builders.block;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.builders.BCBuildersBlocks;

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
        helper.setBlock(frame, BCBuildersBlocks.FRAME.get());
        // Water source one block above — it tries to flow down into the frame's position.
        helper.setBlock(frame.above(), Blocks.WATER);

        // Give the fluid plenty of ticks to attempt the (now-blocked) spread, then check the frame.
        helper.runAfterDelay(40, () -> {
            BlockState s = helper.getBlockState(frame);
            if (!s.is(BCBuildersBlocks.FRAME.get())) {
                helper.fail("Frame was washed away by fluid (now " + s.getBlock()
                    + ") — infinite-frame exploit; the frame must block motion so fluid flows around it.");
            } else {
                helper.succeed();
            }
        });
    }
}
