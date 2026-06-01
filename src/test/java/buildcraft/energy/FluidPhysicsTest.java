package buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import buildcraft.energy.BCEnergyFluids;

public class FluidPhysicsTest {

    public static void testOilBobbing(GameTestHelper helper) {
        // Create a 3-block deep pool of crude oil
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(1, y, 1), BCEnergyFluids.OIL_COOL.block().get());
        }

        // Spawn a pig at the bottom with AI intact: NeoForge 26.1.x exposes no
        // per-fluid entity-physics hook, so oil falls back to vanilla water physics
        // (oil is in FluidTags.WATER). The pig's FloatGoal — which only triggers
        // while AI is enabled — drives the bob to the surface.
        helper.spawn(EntityType.PIG, 1, 1, 1);

        // Succeed once the pig has floated UP into the surface region of the oil column (the upper
        // blocks, relative y 2..4) rather than sinking/staying at the spawn block (1,1,1). A
        // surface-region AABB — not the single block (1,3,1) — keeps this robust: a mob bobbing on
        // fluid often settles with its body in block (1,2,1) and never reliably touches block 3, and
        // succeedWhen samples the position at an arbitrary tick. We only need to prove buoyancy: the
        // pig left the bottom and rose toward the surface (water-like physics), not its exact block.
        helper.succeedWhen(() -> {
            helper.assertEntityPresent(EntityType.PIG, new AABB(0.5, 2.0, 0.5, 2.5, 4.5, 2.5));
        });
    }

    public static void testDenseOilSinking(GameTestHelper helper) {
        // Create a pool of water with Air above it
        BlockPos waterPos = new BlockPos(1, 1, 1);
        BlockPos abovePos = new BlockPos(1, 2, 1);
        helper.setBlock(waterPos, Blocks.WATER);
        
        // Place Dense Oil
        helper.setBlock(abovePos, BCEnergyFluids.ALL.get(3).block().get());
        
        helper.succeedWhen(() -> {
            // Assert that the Dense Oil annihilated the water and fell down!
            helper.assertBlockPresent(BCEnergyFluids.ALL.get(3).block().get(), waterPos);
            helper.assertBlockProperty(abovePos, net.minecraft.world.level.block.LiquidBlock.LEVEL, 0); // Still standard source
        });
    }

    public static void testLightFuelSpreading(GameTestHelper helper) {
        // Place water across the floor
        for(int x = 0; x <= 2; x++) {
            for(int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
            }
        }
        
        // Place Light Fuel (which floats) over the center water block
        BlockPos centerPos = new BlockPos(1, 2, 1);
        BlockPos sidePos = new BlockPos(2, 2, 1);
        helper.setBlock(centerPos, BCEnergyFluids.ALL.get(7).block().get());
        
        helper.succeedWhen(() -> {
            // Assert that the Purgatory Escape Hatch successfully cascaded the oil onto the side water blocks!
            helper.assertBlockPresent(BCEnergyFluids.ALL.get(7).block().get(), sidePos);
        });
    }

    public static void testCrudeOilSelfCollision(GameTestHelper helper) {
        // Simulate the Layer Cake bug: Crude Oil sits on top of Crude Oil
        BlockPos basePos = new BlockPos(1, 1, 1);
        BlockPos topPos = new BlockPos(1, 2, 1);
        BlockPos sidePos = new BlockPos(2, 2, 1);
        
        helper.setBlock(basePos, BCEnergyFluids.OIL_COOL.block().get());
        helper.setBlock(topPos, BCEnergyFluids.OIL_COOL.block().get());
        
        // It should NOT spread to sidePos (Layer Cake bug) because Native isWaterHole gracefully merges them
        // So we wait 20 ticks to ensure it does not spread sideways
        helper.runAfterDelay(20, () -> {
            helper.assertBlockNotPresent(BCEnergyFluids.OIL_COOL.block().get(), sidePos);
            helper.succeed();
        });
    }
}
