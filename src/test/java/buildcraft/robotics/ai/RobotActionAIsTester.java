/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
//? if >=1.21.11 {
import net.minecraft.world.entity.monster.zombie.Zombie;
//?} else {
/*import net.minecraft.world.entity.monster.Zombie;*/
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.IEntityFilter;
import buildcraft.robotics.boards.BoardRobotKnightNBT;
import buildcraft.robotics.entity.EntityRobot;

/** Ph9's "one representative each" of the world-action AIs the gameplay audit listed as untested —
 *  {@link AIRobotBreak}'s progress formula, one each of harvest/plant/pump/use-tool, the melee damage
 *  formula and cadence, and {@link AIRobotSearchEntity} — plus the knight's end-to-end fight, which
 *  chains SearchEntity into Attack through the real tick loop.
 *
 *  <p>The first seven drive their AI SYNCHRONOUSLY on an unadded robot (the {@code SearchBlockTester}
 *  pattern: hand-cycle {@code cycle()} exactly as the entity's tick would — these AIs are pure
 *  world-interaction, no entity ticking required). Loops observe WORLD STATE, never a termination flag:
 *  {@code AIRobot.terminate()} is structural (it unwinds the delegate chain), so the honest signal that
 *  a hand-cycled AI finished is the effect it was meant to produce. The knight fight is async: it proves
 *  the board's delegate chain under the live {@code AIRobotMain} cadence, where the attack cooldown's
 *  21-tick spacing actually matters (each swing must clear the target's 10-tick invulnerability
 *  window — three 8.0 swings cannot fell a zombie any faster). */
public class RobotActionAIsTester {

    /** Above {@code SAFETY_POWER} so the {@code AIRobotMain} ladder never preempts with a recharge. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    private static EntityRobot unaddedRobot(GameTestHelper helper, BlockPos relPos) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        return robot;
    }

    // ---------- AIRobotBreak: the progress formula ----------

    /** Vanilla's destroy-progress math as the robot drives it: one {@code getDestroyProgress} per cycle
     *  (a fake player holding the robot's tool), threshold {@code > 1.0}. Stone (hardness 1.5) under a
     *  diamond pickaxe (dig speed 8, correct tool → divisor 30) advances 8/1.5/30 = 0.1778 per cycle,
     *  so the sixth cycle crosses 1.0 — the fifth must not. Bedrock (hardness −1) refuses outright. */
    public static void breakProgressFormulaMatchesToolAndHardness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stoneRel = new BlockPos(2, 1, 2);
        helper.setBlock(stoneRel, Blocks.STONE);
        BlockPos stonePos = helper.absolutePos(stoneRel);

        EntityRobot robot = unaddedRobot(helper, new BlockPos(2, 2, 2));
        robot.setItemInUse(new ItemStack(Items.DIAMOND_PICKAXE));

        AIRobotBreak breaker = new AIRobotBreak(robot, stonePos);
        int cycles = 0;
        while (!level.getBlockState(stonePos).isAir() && cycles < 20) {
            breaker.cycle();
            cycles++;
        }
        helper.assertTrue(level.getBlockState(stonePos).isAir(),
                "the stone must break well inside 20 cycles");
        helper.assertTrue(cycles == 6,
                "diamond pickaxe on stone is 8/1.5/30 = 0.1778 per cycle — exactly 6 cycles, got "
                        + cycles + " (a wrong divisor would need 19, a tool-less hand 151)");
        helper.assertTrue(breaker.success(), "a completed break reports success");

        BlockPos bedrockRel = new BlockPos(3, 1, 2);
        helper.setBlock(bedrockRel, Blocks.BEDROCK);
        AIRobotBreak refuser = new AIRobotBreak(robot, helper.absolutePos(bedrockRel));
        refuser.cycle();
        helper.assertTrue(!refuser.success(),
                "a negative-hardness block refuses on the first cycle");
        helper.assertTrue(level.getBlockState(helper.absolutePos(bedrockRel)).is(Blocks.BEDROCK),
                "bedrock is not broken by refusal");

        // The break's dropped stone must not leak into sibling arenas.
        for (Entity e : level.getEntities((Entity) null, new AABB(stonePos).inflate(2),
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity)) {
            e.discard();
        }
        helper.succeed();
    }

    // ---------- one representative each: harvest / plant / pump / use-tool ----------

    /** Mature wheat is reaped: crop gone, wheat dropped at the robot's feet (7.1.x's anchor), young
     *  wheat left alone. Soil and light go down first — the arena is dark bedrock, and a crop whose
     *  {@code canSurvive} fails is air before the property read (the Ph5 world-property lesson). */
    public static void harvestReapsMatureWheat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos matureRel = new BlockPos(2, 2, 2);
        BlockPos youngRel = new BlockPos(4, 2, 2);
        BlockPos robotRel = new BlockPos(3, 2, 2);
        helper.setBlock(matureRel.below(), Blocks.FARMLAND);
        helper.setBlock(youngRel.below(), Blocks.FARMLAND);
        helper.setBlock(matureRel.above(), Blocks.GLOWSTONE);
        helper.setBlock(matureRel,
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        helper.setBlock(youngRel,
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));

        EntityRobot robot = unaddedRobot(helper, robotRel);
        AIRobotHarvest harvester = new AIRobotHarvest(robot, helper.absolutePos(matureRel));
        int cycles = 0;
        while (!level.getBlockState(helper.absolutePos(matureRel)).isAir() && cycles < 60) {
            harvester.cycle();
            cycles++;
        }
        helper.assertTrue(level.getBlockState(helper.absolutePos(matureRel)).isAir(),
                "the mature wheat is harvested (after its 20-cycle delay, well inside 60)");
        helper.assertTrue(harvester.success(), "the reaped harvest reports success");
        helper.assertTrue(!level.getBlockState(helper.absolutePos(youngRel)).isAir(),
                "the young wheat is left to grow");

        boolean droppedWheat = false;
        for (var item : EntityArenaUtil.droppedItems(helper, robotRel, 3)) {
            droppedWheat |= item.getItem().is(Items.WHEAT);
            item.discard();
        }
        helper.assertTrue(droppedWheat,
                "the harvest drops its loot at the robot (wheat among it)");
        helper.succeed();
    }

    /** A robot holding exactly one wheat seed plants it through the fake-player crop handler: farmland
     *  gains an age-0 wheat, the held seed is consumed, and nothing spills (the B4 pair — the fetch
     *  takes one seed, the plant keeps it). */
    public static void plantSowsSeedOnFarmland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos soilRel = new BlockPos(2, 2, 2);
        BlockPos robotRel = new BlockPos(3, 2, 2);
        helper.setBlock(soilRel.below(), Blocks.FARMLAND);
        helper.setBlock(soilRel.above(), Blocks.GLOWSTONE);

        EntityRobot robot = unaddedRobot(helper, robotRel);
        robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS));

        // The AI's target is the SOIL block — CropHandlerPlantable plants into pos.above().
        AIRobotPlant planter = new AIRobotPlant(robot, helper.absolutePos(soilRel.below()));
        int cycles = 0;
        while (!level.getBlockState(helper.absolutePos(soilRel)).is(Blocks.WHEAT) && cycles < 80) {
            planter.cycle();
            cycles++;
        }
        helper.assertTrue(level.getBlockState(helper.absolutePos(soilRel)).is(Blocks.WHEAT),
                "the farmland now carries a wheat crop (after its 40-cycle delay)");
        helper.assertTrue(planter.success(), "the planting reports success");
        helper.assertTrue(
                level.getBlockState(helper.absolutePos(soilRel)).getValue(CropBlock.AGE) == 0,
                "the planted crop starts at age 0");
        boolean spilledSeed = false;
        for (var item : EntityArenaUtil.droppedItems(helper, robotRel, 3)) {
            spilledSeed |= item.getItem().is(Items.WHEAT_SEEDS);
            item.discard();
        }
        helper.assertTrue(!spilledSeed,
                "the single seed is planted, not spilled — the B4 fetch/plant pair");
        helper.succeed();
    }

    /** A water source is drained into the robot's 4000 mB tank: exactly one bucket moves, the source is
     *  gone. The source sits in a stone cup so no neighbour can re-feed the cell. */
    public static void pumpDrainsSourceIntoTank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wellRel = new BlockPos(2, 1, 2);
        helper.setBlock(wellRel, Blocks.STONE);
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST}) {
            helper.setBlock(wellRel.relative(dir), Blocks.STONE);
        }
        helper.setBlock(wellRel, Blocks.WATER);

        EntityRobot robot = unaddedRobot(helper, wellRel.above());
        AIRobotPumpBlock pumper = new AIRobotPumpBlock(robot, helper.absolutePos(wellRel));
        int cycles = 0;
        while (EntityArenaUtil.tankAmount(robot.getFluidHandler()) < 1000 && cycles < 120) {
            pumper.cycle();
            cycles++;
        }
        helper.assertTrue(pumper.pumped == 1000,
                "exactly one bucket is pumped, got " + pumper.pumped);
        helper.assertTrue(EntityArenaUtil.tankAmount(robot.getFluidHandler()) == 1000,
                "the robot's tank holds exactly the drained bucket");
        helper.assertTrue(level.getFluidState(helper.absolutePos(wellRel)).isEmpty(),
                "the source is drained from the world");
        helper.succeed();
    }

    /** The use-tool action layer: a hoe through the fake-player {@code useOn} path tills dirt into
     *  farmland (and wears the hoe by one, exactly as a player's would). */
    public static void useToolHoesDirtIntoFarmland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos dirtRel = new BlockPos(2, 1, 2);
        helper.setBlock(dirtRel, Blocks.DIRT);

        EntityRobot robot = unaddedRobot(helper, dirtRel.above());
        robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));

        AIRobotUseToolOnBlock user = new AIRobotUseToolOnBlock(robot, helper.absolutePos(dirtRel));
        int cycles = 0;
        while (!level.getBlockState(helper.absolutePos(dirtRel)).is(Blocks.FARMLAND)
                && cycles < 80) {
            user.cycle();
            cycles++;
        }
        helper.assertTrue(level.getBlockState(helper.absolutePos(dirtRel)).is(Blocks.FARMLAND),
                "the dirt is tilled into farmland (after its 40-cycle delay)");
        helper.assertTrue(user.success(), "the tilling reports success");
        helper.assertTrue(robot.getHeldItem().getDamageValue() == 1,
                "the hoe wears exactly one durability, as a player's would");
        helper.succeed();
    }

    // ---------- attack: damage formula + cadence ----------

    /** The melee formula ({@code EntityRobotBase.attackTargetEntityWithCurrentItem}): base 1.0 unarmed,
     *  plus the held item's ATTACK_DAMAGE modifier — a diamond sword's +7 lands 8.0. Two fresh zombies
     *  avoid invulnerability-frame coupling; a hand-cycled {@link AIRobotAttack} then pins the cadence
     *  (three 8.0 swings at the 21-cycle cooldown fell a 20-HP zombie, each swing past the target's
     *  10-tick invulnerability window). */
    public static void attackFormulaAndCadenceFellTheTarget(GameTestHelper helper) {
        // The FORMULA targets are cows — armour-less (a zombie's 2 armour points shave the landed
        // damage below the formula's raw value); the CADENCE target stays a zombie.
        //? if >=26.2 {
        /*Mob unarmedTarget = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, 1, 2, 2);
        Mob swordTarget = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, 2, 2, 4);
        Zombie cadenceTarget = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 4, 2, 2);*/
        //?} else {
        Mob unarmedTarget = helper.spawn(net.minecraft.world.entity.EntityType.COW, 1, 2, 2);
        Mob swordTarget = helper.spawn(net.minecraft.world.entity.EntityType.COW, 2, 2, 4);
        Zombie cadenceTarget = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 4, 2, 2);
        //?}
        Mob[] targets = { unarmedTarget, swordTarget, cadenceTarget };
        for (Mob mob : targets) {
            mob.setNoAi(true);
            mob.setNoGravity(true);
        }

        try {
            EntityRobot robot = unaddedRobot(helper, new BlockPos(3, 2, 2));
            robot.attackTargetEntityWithCurrentItem(unarmedTarget);
            helper.assertTrue(unarmedTarget.getHealth() == 9.0F,
                    "an unarmed robot lands exactly the 1.0 base hit on an armour-less cow (10 HP), got "
                            + unarmedTarget.getHealth());

            robot.setItemInUse(new ItemStack(Items.DIAMOND_SWORD));
            // Let the first target's invulnerability window pass so the formula reads cleanly.
            for (int i = 0; i < 11; i++) {
                swordTarget.tick();
            }
            robot.attackTargetEntityWithCurrentItem(swordTarget);
            helper.assertTrue(swordTarget.getHealth() == 3.0F,
                    "a diamond sword lands 1.0 + its +6.0 modifier = 7.0 (vanilla's stated total) on the "
                            + "10-HP cow, got " + swordTarget.getHealth());

            // Cadence, hand-cycled exactly as AIRobotMain would (the target ticks between swings, so
            // the 10-tick invulnerability window is real).
            AIRobotAttack attack = new AIRobotAttack(robot, cadenceTarget);
            for (int i = 0; i < 90 && !cadenceTarget.isDeadOrDying(); i++) {
                attack.cycle();
                cadenceTarget.tick();
            }
            helper.assertTrue(cadenceTarget.isDeadOrDying(),
                    "three 8.0 swings at the 21-cycle cadence fell a 20-HP zombie");
        } finally {
            for (Mob mob : targets) {
                mob.discard();
            }
        }
        helper.succeed();
    }

    // ---------- search entity ----------

    /** {@link AIRobotSearchEntity} picks the NEAREST matching entity inside its box and reports nothing
     *  when no candidate sits inside the range. Real entities, inert (a wandering mob could cross arena
     *  borders). The scan runs in {@code start()} — the board starts the AI and reads the answer, which
     *  is exactly what this pins. */
    public static void searchEntityFindsNearestAndHonoursRange(GameTestHelper helper) {
        //? if >=26.2 {
        /*Mob near = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 2, 2, 2);
        Mob far = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 4, 2, 5);
        Mob cow = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, 5, 2, 5);*/
        //?} else {
        Mob near = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 2, 2, 2);
        Mob far = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 4, 2, 5);
        Mob cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, 5, 2, 5);
        //?}
        Mob[] spawned = { near, far, cow };
        for (Mob mob : spawned) {
            mob.setNoAi(true);
            mob.setNoGravity(true);
        }

        try {
            EntityRobot robot = unaddedRobot(helper, new BlockPos(1, 2, 2));
            IEntityFilter hostiles = entity -> entity instanceof Enemy;
            AIRobotSearchEntity search = new AIRobotSearchEntity(robot, hostiles, 8F, null);
            search.start();
            helper.assertTrue(search.success(), "the search finds a hostile in range");
            helper.assertTrue(search.target == near,
                    "the nearest hostile wins, got " + search.target);

            near.discard();
            far.discard();
            AIRobotSearchEntity empty = new AIRobotSearchEntity(robot, hostiles, 8F, null);
            empty.start();
            helper.assertTrue(!empty.success(),
                    "no hostiles left in range — the search reports nothing (the cow never matched)");
        } finally {
            for (Mob mob : spawned) {
                mob.discard();
            }
        }
        helper.succeed();
    }

    // ---------- knight E2E: SearchEntity → Attack through the live tick loop ----------

    /** A knight seeded with a sword fights a zombie to the death through the real {@code AIRobotMain}
     *  cadence — the delegate chain (board → SearchEntity → Attack) under live entity ticking, the
     *  attack cooldown clearing the target's invulnerability window every swing. */
    public static void knightFightsZombieToTheDeath(GameTestHelper helper) {
        BlockPos robotRel = new BlockPos(3, 4, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        //? if >=26.2 {
        /*Zombie zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 3, 4, 3);*/
        //?} else {
        Zombie zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 3, 4, 3);
        //?}
        zombie.setNoAi(true);
        zombie.setNoGravity(true);

        EntityRobot robot = new EntityRobot(helper.getLevel(), BoardRobotKnightNBT.INSTANCE);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.setItemInUse(new ItemStack(Items.DIAMOND_SWORD));
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        helper.getLevel().addFreshEntity(robot);

        EntityArenaUtil.tickUntil(helper, 200, zombie::isDeadOrDying, () -> {
                },
                "the knight must fell the zombie (three 8.0 swings at the 21-tick cadence)");

        zombie.discard();
        robot.discard();
        helper.succeed();
    }
}
