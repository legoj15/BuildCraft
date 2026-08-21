/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

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
        // Crude oil's buoyancy is resolved the SAME way on every line now: isInWater() gates on
        // FluidType.getIsWaterLike(), NOT the minecraft:water tag. NeoForge PR #3249 re-implemented
        // vanilla's entity-fluid-interaction patches for 26.2 (26.2.0.49-beta); its 26.1.x backport
        // PR #3303 shipped in 26.1.2.95, closing the last divergence — 26.1.2 was previously tag-based,
        // a regression from the 26.1 port dropping those patches. Crude oil is built
        // isWaterLike(false)/canSwim(false) on purpose (turnOffSplashes in BCEnergyFluids — thick crude
        // shouldn't be swimmable), so the pig never counts as in water, FloatGoal never engages, and it
        // does NOT bob — the correct behaviour everywhere, and what we assert. The minecraft:water tag
        // still lists every BC oil: it stays load-bearing for BCEnergyFluids' water-merge spread logic
        // (FluidTags.WATER checks), only its isInWater() role died.
        // Crude oil (not a water-like sibling) is used deliberately: its canSwim(false) keeps the pig out of
        // swim navigation, so the non-float is reliable rather than flaky. The deterministic companion
        // crudeOilIsNotWaterLike pins the underlying FluidType flags.

        // The pig must actually TICK for any of this to be observable: an un-ticked entity never runs its
        // fluid scan, so isInWater() stays false and FloatGoal never engages. The game-test framework
        // force-loads the chunk(s) of the (minecraft:empty -> 1x1x1) test STRUCTURE, but the pig spawns at
        // relative (1,1,1), which — depending on where the framework drops this test's origin — can land in a
        // NEIGHBOURING chunk that isn't force-loaded. That entity then never ticks, and the test flaked (~1/10
        // runs the pig sat frozen on the floor with the oil present but undetected). Force-load the pig's own
        // chunk (3x3 to cover any origin alignment) so it is reliably entity-ticking; the framework unforces
        // every force-loaded chunk at batch end (getForceLoadedChunks), so this self-cleans.
        BlockPos pigPos = new BlockPos(1, 1, 1);
        int pigChunkX = helper.absolutePos(pigPos).getX() >> 4;
        int pigChunkZ = helper.absolutePos(pigPos).getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.getLevel().setChunkForced(pigChunkX + dx, pigChunkZ + dz, true);
            }
        }

        // Sealed stone shaft around a 1x1 crude-oil well: once the pig ticks, this makes the bob instant and
        // deterministic. Crude oil ("oil") behaves as a normal SPREADING fluid here (it isn't dense, and its
        // water-merge spread branch needs water below — here it's the stone floor), so an OPEN 1-wide column
        // over stone would bleed sideways into flowing oil that (canPushEntity=true) shoves the pig out of the
        // column. Walls hold the oil as three stable full sources and trap the pig in the shaft, so buoyancy
        // lifts it into the surface region on its first ticked frame. They also keep the non-floating
        // (other-line) pig resting cleanly at the bottom, stabilising that branch too.
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        for (int y = 1; y <= 3; y++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                helper.setBlock(new BlockPos(1, y, 1).relative(d), Blocks.STONE);
            }
        }
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(1, y, 1), BCEnergyFluids.OIL_COOL.block().get());
        }

        // Spawn a pig at the bottom with AI intact (FloatGoal only drives the bob while AI is enabled).
        //? if >=26.2 {
        /*helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, 1, 1, 1);*/
        //?} else {
        helper.spawn(EntityType.PIG, 1, 1, 1);
        //?}

        // Surface region of the oil column (upper blocks, relative y 2..4). A region — not the single block
        // (1,3,1) — keeps the positive case robust: a bobbing mob often settles with its body in block
        // (1,2,1) and never reliably touches block 3, and the position is sampled at an arbitrary tick.
        AABB surface = new AABB(0.5, 2.0, 0.5, 2.5, 4.5, 2.5);

        //? if >=26.2 {
        /*// FluidType-gated water physics (PR #3249; 26.1.x identical via the PR #3303 backport): crude oil
        // isn't water-like, so prove the pig did NOT float — after a settle window it remains out of the
        // surface region (resting on the floor at the bottom).
        helper.runAtTickTime(100, () -> {
            helper.assertEntityNotPresent(net.minecraft.world.entity.EntityTypes.PIG, surface);
            helper.succeed();
        });
        *///?} elif >=1.21.10 {
        // FluidType-gated water physics (26.2 since PR #3249, 26.1.x since the PR #3303 backport,
        // 26.1.2.95): crude oil isn't water-like, so prove the pig did NOT float — after a settle window
        // it remains out of the surface region (resting on the floor at the bottom).
        helper.runAtTickTime(100, () -> {
            helper.assertEntityNotPresent(EntityType.PIG, surface);
            helper.succeed();
        });
        //?} else {
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
     * oil, fuels) stays water-like. {@code isInWater()} gates on {@code FluidType.getIsWaterLike()} on
     * every line (NeoForge PR #3249; the 26.1.x backport PR #3303 shipped in 26.1.2.95), so this flag,
     * not the minecraft:water tag, decides whether mobs bob/swim. Deterministic; no entity physics.
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
