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
        // Crude oil's buoyancy is resolved DIFFERENTLY across the two MC lines, so this test asserts the
        // correct (and genuinely different) outcome on each:
        //   * 26.1.x  — isInWater() is purely tag-based. Crude oil joins the minecraft:water fluid tag, so
        //               the FloatGoal sees water and bobs the pig UP to the surface.
        //   * 1.21.11 — NeoForge 21.11 gates isInWater() on FluidType.getIsWaterLike(), NOT the tag. Crude
        //               oil is built isWaterLike(false)/canSwim(false) on purpose (turnOffSplashes in
        //               BCEnergyFluids — thick crude shouldn't be swimmable), so the pig never counts as in
        //               water, FloatGoal never engages, and it does NOT bob. That non-float is the correct
        //               behaviour here, and is what we assert.
        // Crude oil (not a water-like sibling) is used deliberately: its canSwim(false) keeps the pig out of
        // swim navigation, so the 26.1.x bob is reliable rather than flaky. The deterministic companion
        // crudeOilIsNotWaterLike pins the underlying FluidType flags.

        // Stone floor so the non-floating (1.21.11) case rests cleanly at the bottom instead of falling into
        // the void arena, keeping the negative assertion stable.
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);

        // 3-block deep pool of crude oil
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(1, y, 1), BCEnergyFluids.OIL_COOL.block().get());
        }

        // Spawn a pig at the bottom with AI intact (FloatGoal only drives the bob while AI is enabled).
        helper.spawn(EntityType.PIG, 1, 1, 1);

        // Surface region of the oil column (upper blocks, relative y 2..4). A region — not the single block
        // (1,3,1) — keeps the positive case robust: a bobbing mob often settles with its body in block
        // (1,2,1) and never reliably touches block 3, and the position is sampled at an arbitrary tick.
        AABB surface = new AABB(0.5, 2.0, 0.5, 2.5, 4.5, 2.5);

        //? if >=26.1 {
        // Tag-based water physics: prove buoyancy — the pig left the bottom and rose into the surface region.
        helper.succeedWhen(() -> helper.assertEntityPresent(EntityType.PIG, surface));
        //?} elif >=1.21.10 {
        /*// FluidType-gated water physics: crude oil isn't water-like, so prove the pig did NOT float — after
        // a settle window it remains out of the surface region (resting on the floor at the bottom).
        helper.runAtTickTime(100, () -> {
            helper.assertEntityNotPresent(EntityType.PIG, surface);
            helper.succeed();
        });
        *///?} else {
        /*// 1.21.1: GameTestHelper has no assertEntityNotPresent(EntityType, AABB) overload yet — use the
        // (EntityType, Vec3 min, Vec3 max) corner form against the same surface region.
        helper.runAtTickTime(100, () -> {
            helper.assertEntityNotPresent(EntityType.PIG, surface.getMinPosition(), surface.getMaxPosition());
            helper.succeed();
        });
        *///?}
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

    /**
     * Pins crude oil's deliberate non-water-like configuration (the {@code turnOffSplashes} branch in
     * {@link BCEnergyFluids}). Crude oil ("oil") is built with {@code isWaterLike(false)/canSwim(false)}
     * so thick crude doesn't behave like swimmable water, while every refined fluid (heavy/dense/distilled
     * oil, fuels) stays water-like. This matters on the 1.21.11 line, where NeoForge 21.11 resolves
     * {@code isInWater()} from {@code FluidType.getIsWaterLike()} rather than the minecraft:water tag — so
     * this flag, not tag membership, decides whether mobs bob/swim. Deterministic; no entity physics.
     */
    public static void crudeOilIsNotWaterLike(GameTestHelper helper) {
        net.neoforged.neoforge.fluids.FluidType crude = BCEnergyFluids.OIL_COOL.fluidType().get();
        //? if >=1.21.10 {
        if (crude.getIsWaterLike() || crude.canSwim(null)) {
            helper.fail("crude oil must be non-water-like and non-swimmable (turnOffSplashes): isWaterLike="
                + crude.getIsWaterLike() + " canSwim=" + crude.canSwim(null));
            return;
        }
        //?} else {
        /*// 1.21.1: FluidType has no getIsWaterLike() (the isWaterLike property predates this NeoForge line);
        // assert only the swim flag that exists here. Crude must be non-swimmable (turnOffSplashes).
        if (crude.canSwim(null)) {
            helper.fail("crude oil must be non-swimmable (turnOffSplashes): canSwim=" + crude.canSwim(null));
            return;
        }*/
        //?}
        net.neoforged.neoforge.fluids.FluidType heavy = BCEnergyFluids.ALL.stream()
            .filter(e -> e.baseName().equals("oil_heavy")).findFirst().orElseThrow().fluidType().get();
        //? if >=1.21.10 {
        if (!heavy.getIsWaterLike() || !heavy.canSwim(null)) {
            helper.fail("refined oils must stay water-like/swimmable: heavy oil isWaterLike="
                + heavy.getIsWaterLike() + " canSwim=" + heavy.canSwim(null));
            return;
        }
        //?} else {
        /*// 1.21.1: no getIsWaterLike(); assert only the swim flag. Refined oils stay swimmable.
        if (!heavy.canSwim(null)) {
            helper.fail("refined oils must stay swimmable: heavy oil canSwim=" + heavy.canSwim(null));
            return;
        }*/
        //?}
        helper.succeed();
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

    /**
     * Anchors the flowing-fluid push fix: {@code motionScale} is the per-tick scalar applied to a
     * fluid's FLOW velocity to carry entities along a current (vanilla water = 0.014), NOT friction.
     * A value near the old 0.8 launched entities downstream. Guards every BC energy fluid against
     * that regression — deterministic, so no flaky entity physics involved.
     */
    public static void energyFluidsMotionScaleIsWaterLike(GameTestHelper helper) {
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            net.neoforged.neoforge.fluids.FluidType type = entry.fluidType().get();
            double scale = type.motionScale(null);
            if (!(scale > 0.0 && scale <= 0.1)) {
                helper.fail(entry.name() + " motionScale=" + scale
                    + " — expected a water-like flow-push in (0, 0.1] (water is 0.014); a value near"
                    + " 0.8 launches entities downstream in flowing fluid.");
                return;
            }
        }
        helper.succeed();
    }
}
