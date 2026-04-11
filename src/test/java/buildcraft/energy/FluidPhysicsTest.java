package buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;

import buildcraft.energy.BCEnergyFluids;

public class FluidPhysicsTest {

    public static void testOilBobbing(GameTestHelper helper) {
        // Create a 3-block deep pool of crude oil
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(1, y, 1), BCEnergyFluids.OIL_COOL.block().get());
        }

        // Spawn a pig at the bottom!
        BlockPos bottomPos = new BlockPos(1, 1, 1);
        Entity pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);

        // Assert that the pig successfully bobs to the surface block over time
        helper.succeedWhen(() -> {
            // Check if the pig made it strictly to the top surface
            helper.assertEntityPresent(EntityType.PIG, new BlockPos(1, 3, 1));
        });
    }
}
