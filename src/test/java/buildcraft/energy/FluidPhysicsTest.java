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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.lib.test.EntityArenaUtil;

public class FluidPhysicsTest {

    public static void testOilBobbing(GameTestHelper helper) {
        // Crude oil's buoyancy is resolved the SAME way on every line now: isInWater() gates on
        // FluidType.getIsWaterLike(), NOT the minecraft:water tag. NeoForge PR #3249 re-implemented
        // vanilla's entity-fluid-interaction patches for 26.2 (26.2.0.49-beta); its 26.1.x backport
        // PR #3303 shipped in 26.1.2.95, closing the last divergence — 26.1.2 was previously tag-based,
        // a regression from the 26.1 port dropping those patches. Crude oil is built
        // isWaterLike(false)/canSwim(false) on purpose (the waterLike flag in BCEnergyFluids — thick crude
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
        helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, 1, 1, 1);
        //?} else {
        /*helper.spawn(EntityType.PIG, 1, 1, 1);
        *///?}

        // Surface region of the oil column (upper blocks, relative y 2..4). A region — not the single block
        // (1,3,1) — keeps the positive case robust: a bobbing mob often settles with its body in block
        // (1,2,1) and never reliably touches block 3, and the position is sampled at an arbitrary tick.
        AABB surface = new AABB(0.5, 2.0, 0.5, 2.5, 4.5, 2.5);

        //? if >=26.2 {
        // FluidType-gated water physics (PR #3249; 26.1.x identical via the PR #3303 backport): crude oil
        // isn't water-like, so prove the pig did NOT float — after a settle window it remains out of the
        // surface region (resting on the floor at the bottom).
        helper.runAtTickTime(100, () -> {
            helper.assertEntityNotPresent(net.minecraft.world.entity.EntityTypes.PIG, surface);
            helper.succeed();
        });
        //?} elif >=1.21.10 {
        /*// FluidType-gated water physics (26.2 since PR #3249, 26.1.x since the PR #3303 backport,
        // 26.1.2.95): crude oil isn't water-like, so prove the pig did NOT float — after a settle window
        // it remains out of the surface region (resting on the floor at the bottom).
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

    /** The push given to the pig along +x: about a block of travel in crude oil's drag, two in water's. */
    private static final double WADE_IMPULSE = 0.4;
    /** How far the impulse must carry the pig, and how far it must sink, before the test passes. */
    private static final double WADE_MIN_DX = 0.5;
    private static final double SINK_MIN_DY = 0.25;
    /** The poll's budget once the cell ticks: the impulse settles within ten ticks, and sinking a quarter block at
     *  crude oil's slowest (about 0.006 blocks a tick) takes about 45. */
    private static final int WADE_WORK_TICKS = 200;

    /**
     * Issue #117: a mob in crude oil must move. Crude oil is neither water-like nor lava, and from 26.1.2 on NeoForge's
     * {@code travelInFluid} moves such a modded fluid only through its {@code FluidType.move}; without one the pig got
     * no movement step at all, so an impulse never moved it and gravity never pulled it down. The fluid's type (and on
     * Fabric {@code LivingEntityMixin}) now wades it through {@code ThickFluidMotion}. A pig with its goals removed
     * (so nothing but the impulse and the fluid moves it) is put in the top layer of a sealed three-deep crude-oil
     * pool and pushed along +x: it must travel at least {@link #WADE_MIN_DX} and sink at least {@link #SINK_MIN_DY}
     * (crude oil is denser than water and not swimmable, so nothing holds it up). Gated on the cell ticking and on
     * the pig's observed position, never on a fixed tick.
     */
    public static void crudeOilLetsMobsWadeAndSink(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        Mob[] pig = { null };
        double[] start = new double[2];
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + WADE_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, WADE_WORK_TICKS, () -> {
                    if (pig[0] == null) {
                        buildCrudePool(helper);
                        //? if >=26.2 {
                        pig[0] = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, 1, 3, 3);
                        //?} else {
                        /*pig[0] = helper.spawn(EntityType.PIG, 1, 3, 3);
                        *///?}
                        pig[0].removeAllGoals(goal -> true);
                        start[0] = pig[0].getX();
                        start[1] = pig[0].getY();
                        pig[0].setDeltaMovement(WADE_IMPULSE, 0.0, 0.0);
                        return false;
                    }
                    return pig[0].getX() - start[0] >= WADE_MIN_DX && start[1] - pig[0].getY() >= SINK_MIN_DY;
                }, () -> pig[0] == null ? "the pig was never spawned"
                        : "the pig moved dx=" + (pig[0].getX() - start[0]) + " and sank dy=" + (start[1] - pig[0].getY())
                        + " (needs dx>=" + WADE_MIN_DX + ", dy>=" + SINK_MIN_DY + "); still in crude oil: "
                        + pig[0].level().getFluidState(pig[0].blockPosition()).is(BCEnergyFluids.OIL_COOL.source().get())),
                helper::succeed, "a pig in crude oil never waded and sank", () -> EntityArenaUtil.discard(pig[0]));
    }

    /** A sealed pool of crude-oil sources, x 1..4, y 1..3, z 2..4, on a stone floor, walled one block higher. Every
     *  block of the cell's x 0..5, z 1..5 box up to y 4 is written, which also replaces the barriers the framework
     *  leaves in the origin corner. */
    private static void buildCrudePool(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean wall = x == 0 || x == 5 || z == 1 || z == 5;
                for (int y = 1; y <= 4; y++) {
                    if (wall) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    } else {
                        helper.setBlock(new BlockPos(x, y, z),
                                y <= 3 ? BCEnergyFluids.OIL_COOL.block().get() : Blocks.AIR);
                    }
                }
            }
        }
    }

    /**
     * Pins crude oil's deliberate non-water-like configuration (the {@code waterLike} flag in
     * {@link BCEnergyFluids}). Crude oil ("oil") is built with {@code isWaterLike(false)/canSwim(false)}
     * so thick crude doesn't behave like swimmable water, and so is residue; every other refined fluid
     * (heavy/dense/distilled oil, fuels) stays water-like. {@code isInWater()} gates on {@code FluidType.getIsWaterLike()} on
     * every line (NeoForge PR #3249; the 26.1.x backport PR #3303 shipped in 26.1.2.95), so this flag,
     * not the minecraft:water tag, decides whether mobs bob/swim. Fabric has no fluid type: its entity physics reads
     * the same flags from the registered {@code FluidProperties} (FabricFluidPhysics). Deterministic; no entity physics.
     */
    public static void crudeOilIsNotWaterLike(GameTestHelper helper) {
        Fluid crude = BCEnergyFluids.OIL_COOL.source().get();
        // 1.21.1: FluidType has no getIsWaterLike() (the isWaterLike property predates this NeoForge line), so only the
        // swim flag is asserted there (waterLike answers null).
        if (Boolean.TRUE.equals(waterLike(crude)) || canSwim(crude)) {
            helper.fail("crude oil must be non-water-like and non-swimmable: isWaterLike="
                + waterLike(crude) + " canSwim=" + canSwim(crude));
            return;
        }
        // Residue is the other thick fluid, at every heat. Its base name is "oil_residue": a compare against
        // "residue" once silently left it water-like (issue #59).
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            if (!entry.baseName().equals("oil_residue")) {
                continue;
            }
            Fluid residue = entry.source().get();
            if (Boolean.TRUE.equals(waterLike(residue)) || canSwim(residue)) {
                helper.fail(entry.name() + " must be non-water-like and non-swimmable: isWaterLike="
                    + waterLike(residue) + " canSwim=" + canSwim(residue));
                return;
            }
        }
        Fluid heavy = BCEnergyFluids.ALL.stream()
            .filter(e -> e.baseName().equals("oil_heavy")).findFirst().orElseThrow().source().get();
        if (Boolean.FALSE.equals(waterLike(heavy)) || !canSwim(heavy)) {
            helper.fail("refined oils must stay water-like/swimmable: heavy oil isWaterLike="
                + waterLike(heavy) + " canSwim=" + canSwim(heavy));
            return;
        }
        helper.succeed();
    }

    /**
     * Anchors the flowing-fluid push fix: {@code motionScale} is the per-tick scalar applied to a
     * fluid's FLOW velocity to carry entities along a current (vanilla water = 0.014), NOT friction.
     * A value near the old 0.8 launched entities downstream. Guards every BC energy fluid against
     * that regression — deterministic, so no flaky entity physics involved.
     */
    public static void energyFluidsMotionScaleIsWaterLike(GameTestHelper helper) {
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            double scale = motionScale(entry.source().get());
            if (!(scale > 0.0 && scale <= 0.1)) {
                helper.fail(entry.name() + " motionScale=" + scale
                    + " — expected a water-like flow-push in (0, 0.1] (water is 0.014); a value near"
                    + " 0.8 launches entities downstream in flowing fluid.");
                return;
            }
        }
        helper.succeed();
    }

    // What the loader's entity physics reads for a BC fluid: NeoForge's FluidType, Fabric's registered properties.

    /** The water-like flag, or null where the loader has none (NeoForge on 1.21.1). */
    private static Boolean waterLike(Fluid fluid) {
        //? if neoforge && >=1.21.10 {
        return fluid.getFluidType().getIsWaterLike();
        //?} elif neoforge {
        /*return null;
        *///?} else {
        /*return buildcraft.platform.fabric.FabricFlowingFluids.declared(fluid).waterLike();
        *///?}
    }

    private static boolean canSwim(Fluid fluid) {
        //? if neoforge {
        return fluid.getFluidType().canSwim(null);
        //?} else {
        /*return buildcraft.platform.fabric.FabricFlowingFluids.declared(fluid).canSwim();
        *///?}
    }

    private static double motionScale(Fluid fluid) {
        //? if neoforge {
        return fluid.getFluidType().motionScale(null);
        //?} else {
        /*return buildcraft.platform.fabric.FabricFlowingFluids.declared(fluid).motionScale().getAsDouble();
        *///?}
    }
}
