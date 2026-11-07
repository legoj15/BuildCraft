/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.lib.test.EntityArenaUtil;

public class FluidPhysicsTest {

    /** How long the pig is watched in the oil shaft before the test passes (both arms of the test read it). */
    private static final int BOB_SETTLE_TICKS = 100;

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
        Mob pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, 1, 1, 1);
        //?} else {
        /*Mob pig = helper.spawn(EntityType.PIG, 1, 1, 1);
        *///?}

        // Surface region of the oil column (upper blocks, relative y 2..4). A region — not the single block
        // (1,3,1) — keeps the positive case robust: a bobbing mob often settles with its body in block
        // (1,2,1) and never reliably touches block 3, and the position is sampled at an arbitrary tick.
        AABB surface = new AABB(0.5, 2.0, 0.5, 2.5, 4.5, 2.5);

        //? if >=1.21.10 {
        // FluidType-gated water physics (26.2 since PR #3249, 26.1.x since the PR #3303 backport,
        // 26.1.2.95): crude oil isn't water-like, so the pig must never count as in water or swimming, which
        // is what keeps FloatGoal (the bob) off. That is asserted on every tick rather than read off its
        // position: since 26.1.2 a mob can wade in crude oil (FluidType.move, #117) and, as in lava, climb out
        // at a wall it presses against, so in this 1-wide shaft it may reach the surface region by climbing
        // without ever floating.
        // Checked before the success tick only: on that tick the framework discards the test's entities.
        helper.onEachTick(() -> {
            if (helper.getTick() >= BOB_SETTLE_TICKS) {
                return;
            }
            if (!pig.isAlive()) {
                helper.fail("the pig must stay alive in the oil shaft");
            }
            if (pig.isInWater() || pig.isSwimming()) {
                helper.fail("crude oil must not count as water for the pig (isInWater=" + pig.isInWater()
                    + ", isSwimming=" + pig.isSwimming() + ")");
            }
        });
        helper.runAtTickTime(BOB_SETTLE_TICKS, helper::succeed);
        //?} else {
        /*// 1.21.1: GameTestHelper has no assertEntityNotPresent(EntityType, AABB) overload yet — use the
        // (EntityType, Vec3 min, Vec3 max) corner form against the same surface region.
        helper.runAtTickTime(BOB_SETTLE_TICKS, () -> {
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
                        buildPool(helper, BCEnergyFluids.OIL_COOL.block().get());
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

    /** A sealed pool of {@code fluid} sources, x 1..4, y 1..3, z 2..4, on a stone floor, walled one block higher.
     *  Every block of the cell's x 0..5, z 1..5 box up to y 4 is written, which also replaces the barriers the
     *  framework leaves in the origin corner. */
    private static void buildPool(GameTestHelper helper, Block fluid) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean wall = x == 0 || x == 5 || z == 1 || z == 5;
                for (int y = 1; y <= 4; y++) {
                    if (wall) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    } else {
                        helper.setBlock(new BlockPos(x, y, z), y <= 3 ? fluid : Blocks.AIR);
                    }
                }
            }
        }
    }

    /** How far the ridden horse must rise from the pool floor before the test passes. */
    private static final double RIDDEN_RISE_MIN_DY = 0.5;
    /** The poll's budget once the cell ticks: the lift nets about 0.15 blocks a tick once its speed settles. */
    private static final int RIDDEN_WORK_TICKS = 100;

    /**
     * Issue #142: a ridden horse in crude oil floats on both 26.3 loaders. Vanilla 26.3's {@code travelInFluid} ends
     * with {@code floatInLiquidWhileRidden(entity_floatable)}, a lift of 0.04 a tick for a ridden
     * {@code can_float_while_ridden} mount deeper than its jump threshold in any water-tag fluid; NeoForge runs it
     * after the fluid type's {@code move}, and Fabric's {@code LivingEntityMixin} after {@code ThickFluidMotion}. A
     * horse with its goals removed, carrying an armour stand, stands on the floor of a four-deep crude-oil pool (deep
     * enough that the rider's eyes start in the oil): it must rise at least {@link #RIDDEN_RISE_MIN_DY} and keep its
     * rider, as NeoForge throws a rider off a {@code dismounts_underwater} mount only in water itself (on Fabric
     * vanilla's eye-in-water test let crude oil throw it). Older lines keep the #117 sink-gently behaviour and make
     * no claim here.
     */
    public static void crudeOilFloatsRiddenHorse(GameTestHelper helper) {
        //? if >=26.3 {
        EntityArenaUtil.forceLoadCell(helper);
        Mob[] horse = { null };
        Entity[] rider = { null };
        double[] startY = new double[1];
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + RIDDEN_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, RIDDEN_WORK_TICKS, () -> {
                    if (horse[0] == null) {
                        Block oil = BCEnergyFluids.OIL_COOL.block().get();
                        buildPool(helper, oil);
                        // A fourth layer, held in by the pool's y=4 wall, so the rider's eyes (about y 4.2) start in oil.
                        for (int x = 1; x <= 4; x++) {
                            for (int z = 2; z <= 4; z++) {
                                helper.setBlock(new BlockPos(x, 4, z), oil);
                            }
                        }
                        horse[0] = helper.spawn(net.minecraft.world.entity.EntityTypes.HORSE, 3.0F, 1.0F, 3.5F);
                        horse[0].removeAllGoals(goal -> true);
                        rider[0] = helper.spawn(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, 3.0F, 1.0F, 3.5F);
                        if (!rider[0].startRiding(horse[0], true, false)) {
                            throw helper.assertionException(net.minecraft.network.chat.Component.literal(
                                    "the armour stand could not mount the horse"));
                        }
                        startY[0] = horse[0].getY();
                        return false;
                    }
                    if (!horse[0].isVehicle()) {
                        throw helper.assertionException(net.minecraft.network.chat.Component.literal(
                                "the rider was thrown off the horse in crude oil"));
                    }
                    return horse[0].getY() - startY[0] >= RIDDEN_RISE_MIN_DY;
                }, () -> horse[0] == null ? "the horse was never spawned"
                        : "the ridden horse rose dy=" + (horse[0].getY() - startY[0]) + " (needs >="
                        + RIDDEN_RISE_MIN_DY + "); ridden: " + horse[0].isVehicle()),
                helper::succeed, "a ridden horse in crude oil never floated",
                () -> EntityArenaUtil.discard(rider[0], horse[0]));
        //?} else {
        /*helper.succeed();
        *///?}
    }

    /** The poll's budget once the cell ticks: the two tests below answer on the first poll that finds it ticking. */
    private static final int POOL_WORK_TICKS = 20;

    /** Where the item and the boat float in the pool: the middle of its three layers. */
    private static final BlockPos POOL_MIDDLE = new BlockPos(2, 2, 3);

    /**
     * An item dropped into crude oil floats. NeoForge's {@code ItemEntity.tick} gives any fluid type that is not
     * vanilla's, deeper than a tenth of a block, the item movement of water (a small upward nudge, no gravity),
     * whether or not the fluid is water-like; vanilla's own test is {@code isInWater()}, which crude oil (not
     * water-like) fails, so on Fabric the item fell through the lake to its floor. A motionless item in the
     * middle of a sealed crude pool is ticked once: it must rise, where gravity would pull it down. The same pool
     * of water is the control, since a pool where nothing rises would pass any test of falling. Gated on the cell
     * ticking so the fluid scan sees loaded chunks; the entity is ticked by hand so the answer is the one tick's,
     * never a race with the level's own.
     */
    public static void crudeOilFloatsItems(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        ItemEntity[] item = { null };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + POOL_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, POOL_WORK_TICKS, () -> {
                    item[0] = spawnItem(helper);
                    buildPool(helper, Blocks.WATER);
                    double waterDy = tickedMotion(helper, item[0], POOL_MIDDLE).y;
                    if (waterDy <= 0.0) {
                        helper.fail("control: an item in water must float (vertical motion after a tick " + waterDy + ")");
                    }
                    buildPool(helper, BCEnergyFluids.OIL_COOL.block().get());
                    double oilDy = tickedMotion(helper, item[0], POOL_MIDDLE).y;
                    if (oilDy <= 0.0) {
                        helper.fail("an item in crude oil must float like one in water, not fall (vertical motion after"
                                + " a tick " + oilDy + ")");
                    }
                    return true;
                }), helper::succeed, "an item in crude oil was never checked", () -> EntityArenaUtil.discard(item[0]));
    }

    /** A cobblestone item added to the level; {@link #tickedMotion} puts it where the test reads it. */
    private static ItemEntity spawnItem(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        ItemEntity item = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                new ItemStack(Items.COBBLESTONE));
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    /**
     * A flowing crude-oil current pushes an item along it, by the fluid's own motion scale (NeoForge sums one
     * current per fluid type; Fabric's {@code EntityFluidInteractionMixin} sums one per BuildCraft fluid, since
     * vanilla's water current would otherwise take every fluid in the water tag). A three-block channel steps down
     * from a source through two shallower flowing blocks, walled on every side, so the middle block flows toward +x;
     * a motionless item in it is ticked once and must gain +x motion and none in z. Still crude oil is the control:
     * the same item in a pool of sources moves nowhere sideways, so the push is the current's and not the item's own.
     */
    public static void crudeOilCurrentPushesItems(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        ItemEntity[] item = { null };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + POOL_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, POOL_WORK_TICKS, () -> {
                    item[0] = spawnItem(helper);
                    buildPool(helper, BCEnergyFluids.OIL_COOL.block().get());
                    Vec3 still = tickedMotion(helper, item[0], CHANNEL_MIDDLE);
                    if (Math.abs(still.x) > 1.0E-6 || Math.abs(still.z) > 1.0E-6) {
                        helper.fail("control: an item in still crude oil must not be pushed sideways (motion " + still + ")");
                    }
                    buildChannel(helper);
                    Vec3 flowing = tickedMotion(helper, item[0], CHANNEL_MIDDLE);
                    if (flowing.x < MIN_PUSH || Math.abs(flowing.z) > 1.0E-6) {
                        helper.fail("a current flowing toward +x must push the item along it by at least " + MIN_PUSH
                                + " a tick, and not sideways (motion " + flowing + ")");
                    }
                    return true;
                }), helper::succeed, "an item in a crude-oil current was never checked",
                () -> EntityArenaUtil.discard(item[0]));
    }

    /** The middle block of the channel, where the current is read: its neighbours are a source and a shallower block. */
    private static final BlockPos CHANNEL_MIDDLE = new BlockPos(2, 1, 3);

    /** The least +x motion one tick of the current must give the item: vanilla keeps a current's push to at least
     *  0.0045 while the item is nearly at rest, and gravity and drag leave x alone. */
    private static final double MIN_PUSH = 0.001;

    /** A one-block-wide channel along z = 3, x 1..3, y = 1, in the same stone box as {@link #buildPool}: a source,
     *  then crude oil of amount 4 and of amount 2, each lower than the last, with air above, so its middle block
     *  flows toward +x. */
    private static void buildChannel(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    boolean above = z == 3 && x >= 1 && x <= 3;
                    helper.setBlock(new BlockPos(x, y, z), above ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        Block oil = BCEnergyFluids.OIL_COOL.block().get();
        helper.setBlock(new BlockPos(1, 1, 3), oil.defaultBlockState());
        helper.setBlock(new BlockPos(2, 1, 3), oil.defaultBlockState().setValue(LiquidBlock.LEVEL, 4));
        helper.setBlock(new BlockPos(3, 1, 3), oil.defaultBlockState().setValue(LiquidBlock.LEVEL, 6));
    }

    /** Puts {@code item} motionless low in {@code block}, ticks it once and returns its motion. */
    private static Vec3 tickedMotion(GameTestHelper helper, ItemEntity item, BlockPos block) {
        BlockPos at = helper.absolutePos(block);
        item.setPos(at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5);
        item.setDeltaMovement(Vec3.ZERO);
        item.tick();
        return item.getDeltaMovement();
    }

    /**
     * A boat does not float on crude oil. NeoForge asks each fluid type whether a boat can float on it
     * ({@code canBoatInFluid}: off by default, and BuildCraft's fluid types leave it off); vanilla asks the water
     * tag, which every BuildCraft fluid is in, so on Fabric a boat sailed on oil. A boat in the middle of a sealed
     * crude pool is ticked once and must not count as under water; the same pool of water is the control.
     */
    public static void crudeOilDoesNotFloatBoats(GameTestHelper helper) {
        EntityArenaUtil.forceLoadCell(helper);
        Entity[] boat = { null };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + POOL_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, POOL_WORK_TICKS, () -> {
                    buildPool(helper, Blocks.WATER);
                    //? if >=26.2 {
                    boat[0] = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, POOL_MIDDLE.getX(),
                            POOL_MIDDLE.getY(), POOL_MIDDLE.getZ());
                    //?} elif >=1.21.10 {
                    /*boat[0] = helper.spawn(EntityType.OAK_BOAT, POOL_MIDDLE.getX(), POOL_MIDDLE.getY(),
                            POOL_MIDDLE.getZ());
                    *///?} else {
                    /*boat[0] = helper.spawn(EntityType.BOAT, POOL_MIDDLE.getX(), POOL_MIDDLE.getY(),
                            POOL_MIDDLE.getZ());
                    *///?}
                    boat[0].tick();
                    if (!boatUnderWater(boat[0])) {
                        helper.fail("control: a boat in a pool of water must count as under water");
                    }
                    buildPool(helper, BCEnergyFluids.OIL_COOL.block().get());
                    boat[0].tick();
                    if (boatUnderWater(boat[0])) {
                        helper.fail("a boat in a pool of crude oil must not count as under water: oil is not a fluid"
                                + " boats float on");
                    }
                    return true;
                }), helper::succeed, "a boat in crude oil was never checked", () -> EntityArenaUtil.discard(boat[0]));
    }

    private static boolean boatUnderWater(Entity boat) {
        //? if >=1.21.11 {
        return ((net.minecraft.world.entity.vehicle.boat.AbstractBoat) boat).isUnderWater();
        //?} elif >=1.21.10 {
        /*return ((net.minecraft.world.entity.vehicle.AbstractBoat) boat).isUnderWater();
        *///?} else {
        /*return ((net.minecraft.world.entity.vehicle.Boat) boat).isUnderWater();
        *///?}
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
