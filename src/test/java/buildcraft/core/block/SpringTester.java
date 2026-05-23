package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.enums.EnumSpring;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.ITileOilSpring;

public class SpringTester {

    public static void testWaterSpring(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCCoreBlocks.SPRING_WATER.get());

        // For water, tickrate is 5 and chance is 100%. 
        // We'll just invoke randomTick a few times directly instead of waiting.
        BlockState state = helper.getBlockState(pos);
        ServerLevel level = helper.getLevel();
        RandomSource rand = level.getRandom();
        
        if (state.getBlock() instanceof BlockSpring) {
            ((BlockSpring) state.getBlock()).randomTick(state, level, helper.absolutePos(pos), rand);
        }

        helper.succeedWhenBlockPresent(Blocks.WATER, new BlockPos(1, 2, 1));
    }

    public static void testOilSpring(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCCoreBlocks.SPRING_OIL.get());

        // Oil spring has a tickrate of 6000 and a 1 in 8 chance.
        // We bypass the timing and probability delay by forcing it to tick continuously until it generates.
        BlockState state = helper.getBlockState(pos);
        ServerLevel level = helper.getLevel();
        RandomSource rand = level.getRandom();
        BlockPos absoluteUpPos = helper.absolutePos(new BlockPos(1, 2, 1));

        if (state.getBlock() instanceof BlockSpring) {
            BlockSpring spring = (BlockSpring) state.getBlock();
            EnumSpring type = spring.getSpringType();
            
            // Safety check: Ensure the liquid block is correctly initialized
            if (type.liquidBlock == null || type.liquidBlock.isAir()) {
                throw new IllegalStateException("Oil spring liquid block is not initialized!");
            }

            // Force generation by spamming ticks until the block above is no longer air
            int maxAttempts = 10000;
            for (int i = 0; i < maxAttempts; i++) {
                spring.randomTick(state, level, helper.absolutePos(pos), rand);
                if (!level.isEmptyBlock(absoluteUpPos)) {
                    break;
                }
            }
        }

        // We succeed if the block above is now the oil liquid block
        helper.succeedIf(() -> {
            BlockState actual = helper.getBlockState(new BlockPos(1, 2, 1));
            BlockState expected = BCCoreBlocks.SPRING_OIL.get().getSpringType().liquidBlock;
            if (actual.isAir()) {
                throw new IllegalStateException("Oil did not generate above spring!");
            }
            if (actual.getBlock() != expected.getBlock()) {
                throw new IllegalStateException("Generated fluid does not match expected oil fluid!");
            }
        });
    }

    /**
     * Verifies that placing an oil spring block also attaches a TileSpringOil
     * (via {@code ITileOilSpring}). Regression test for the bug where
     * {@link BlockSpring} didn't implement {@code EntityBlock}, so the BE was
     * never created and worldgen logged
     * "[energy.gen.oil] Setting the blockstate didn't also set the tile".
     */
    public static void testOilSpringAttachesTile(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCCoreBlocks.SPRING_OIL.get());

        BlockEntity tile = helper.getBlockEntity(pos, BlockEntity.class);
        if (tile == null) {
            throw new IllegalStateException(
                "Oil spring did not attach a BlockEntity (BlockSpring is missing EntityBlock support).");
        }
        if (!(tile instanceof ITileOilSpring)) {
            throw new IllegalStateException(
                "Oil spring attached the wrong BlockEntity type: " + tile.getClass().getName());
        }
        helper.succeed();
    }

    /**
     * An oil spring must replace water that occupies the block above it — otherwise
     * an ocean-borne spring goes permanently sterile once water seeps in. Mirrors
     * {@link #testOilSpring} but pre-fills the block above with a water source.
     */
    public static void testOilSpringRegeneratesThroughWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos up = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCCoreBlocks.SPRING_OIL.get());
        helper.setBlock(up, Blocks.WATER);

        BlockState state = helper.getBlockState(pos);
        ServerLevel level = helper.getLevel();
        RandomSource rand = level.getRandom();
        BlockPos absoluteUpPos = helper.absolutePos(up);
        BlockState oil = BCCoreBlocks.SPRING_OIL.get().getSpringType().liquidBlock;

        if (state.getBlock() instanceof BlockSpring spring) {
            for (int i = 0; i < 10000; i++) {
                spring.randomTick(state, level, helper.absolutePos(pos), rand);
                if (level.getBlockState(absoluteUpPos).getBlock() == oil.getBlock()) {
                    break;
                }
            }
        }

        helper.succeedIf(() -> {
            BlockState actual = helper.getBlockState(up);
            if (actual.getBlock() != oil.getBlock()) {
                throw new IllegalStateException(
                    "Oil spring failed to regenerate through water — block above is " + actual.getBlock());
            }
        });
    }

    /**
     * The spring must not overwrite a solid block above it — only air and fluids
     * are valid placement targets.
     */
    public static void testOilSpringKeepsSolidBlockAbove(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos up = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCCoreBlocks.SPRING_OIL.get());
        helper.setBlock(up, Blocks.STONE);

        BlockState state = helper.getBlockState(pos);
        ServerLevel level = helper.getLevel();
        RandomSource rand = level.getRandom();

        if (state.getBlock() instanceof BlockSpring spring) {
            for (int i = 0; i < 2000; i++) {
                spring.randomTick(state, level, helper.absolutePos(pos), rand);
            }
        }

        helper.succeedIf(() -> {
            BlockState actual = helper.getBlockState(up);
            if (!actual.is(Blocks.STONE)) {
                throw new IllegalStateException(
                    "Oil spring overwrote a solid block above it — block above is now " + actual.getBlock());
            }
        });
    }
}
