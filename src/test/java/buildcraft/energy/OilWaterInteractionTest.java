package buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import buildcraft.energy.BCEnergyFluids;

public class OilWaterInteractionTest {

    public static void testOilOverWater(GameTestHelper helper) {
        // Place water in a hole
        BlockPos waterPos = new BlockPos(1, 1, 1);
        helper.setBlock(waterPos, Blocks.WATER.defaultBlockState());
        
        // Let it stabilize for a tick, then place oil above
        BlockPos oilPos = new BlockPos(1, 2, 1);
        helper.setBlock(oilPos, BCEnergyFluids.OIL_COOL.block().get().defaultBlockState());
        
        helper.succeedWhen(() -> {
            // Check that water is still intact at the bottom
            helper.assertBlockPresent(Blocks.WATER, waterPos);
            // Check that oil spread horizontally instead
            BlockPos sidePos = new BlockPos(2, 2, 1);
            helper.assertBlockPresent(BCEnergyFluids.OIL_COOL.block().get(), sidePos);
        });
    }
}
