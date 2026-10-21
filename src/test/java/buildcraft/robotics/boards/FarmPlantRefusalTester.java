/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.WorkClaimMod;
import buildcraft.robotics.RobotRegistryLiveTester.LiveStation;
import buildcraft.robotics.ZonePinnedRobot;
import buildcraft.robotics.ai.AIRobotPlant;
import buildcraft.robotics.ai.AIRobotSleep;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The farmer and the planter do not go back to a block a protection mod refused them. A claim mod that cancels the
 * right-click a hoe's till starts with refuses the till, and one that cancels a seed's placement event makes the plant
 * fail; both used to leave the robot's board with nothing to remember, so the next search picked the same nearest
 * block again, every cycle, forever (and a failed planting dropped the seed on the ground besides).
 *
 * <p>The boards are cycled by hand on unadded robots, like {@code RandomSearchBudgetTester}: pinned to a work zone
 * inside the test's own arena cell and parked on a station of their own, so they never touch a sibling's blocks. An
 * unadded robot gets no entity tick, so the test applies the velocity its flight AIs write after every cycle. The claim mod counts the events
 * its refusals produced, which is the observable the loop shows up in: a board that gave up asked once per block, a
 * board that did not asked again and again.
 */
public class FarmPlantRefusalTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;
    /** Far past what a few attempts (search, flight, the 40-tick use, the trip home) take. */
    private static final int CYCLE_BOUND = 4000;

    // ---------- fixture ----------

    /** A parked, charged, zone-pinned robot with its board; closing it takes everything back out of the registry.
     *  Shared with {@link BreakFailureMemoryTester}, which drives the break boards the same way. */
    static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final LiveStation home;
        final EntityRobot robot;
        final IRobotRegistry registry;
        RedstoneBoardRobot board;

        Fixture(GameTestHelper helper, BlockPos robotRel) {
            this.helper = helper;
            this.level = helper.getLevel();
            home = new LiveStation(helper.absolutePos(new BlockPos(0, 1, 3)), Direction.UP);
            home.world = level;
            // Tight around the row the tests build (and the station), so the planter's RANDOM search has so few
            // cells to draw from that a candidate is found long before its round budget runs out.
            IZone zone = new Box(helper.absolutePos(new BlockPos(0, 1, 2)), helper.absolutePos(new BlockPos(5, 3, 4)));
            // Docked at and linked to its own station, working a zone inside the arena cell.
            robot = new ZonePinnedRobot(level, null, zone).parkedAt(home);
            Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(robotRel));
            robot.setPos(spawn.x, spawn.y, spawn.z);
            robot.getBattery().addPower(SEEDED_CHARGE, false);
            registry = robot.getRegistry();
            robot.setUniqueRobotId(registry.getNextRobotId());
            registry.registerRobot(robot);
            registry.registerStation(home);
        }

        /** Cycles the board until {@code stop} holds or the bound runs out; returns whether it held. The robot flies:
         *  the velocity its flight AIs set is applied after every cycle, as {@code SearchAndGotoBlockTester} does. */
        boolean cycleUntil(BooleanSupplier stop) {
            for (int i = 0; i < CYCLE_BOUND && !stop.getAsBoolean(); i++) {
                board.cycle();
                // The entity tick an unadded robot never gets: apply the velocity the flight AIs wrote.
                robot.move(MoverType.SELF, robot.getDeltaMovement());
            }
            return stop.getAsBoolean();
        }

        boolean asleep() {
            return board.getActiveAI() instanceof AIRobotSleep;
        }

        @Override
        public void close() {
            if (board != null) {
                board.abortDelegateAI();
            }
            registry.removeStation(home);
            registry.killRobot(robot);
        }
    }

    /** Dirt at {@code (x, 1, 3)} for each x. */
    private static Set<BlockPos> dirtRow(GameTestHelper helper, int... xs) {
        Set<BlockPos> dirt = new HashSet<>();
        for (int x : xs) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.DIRT);
            dirt.add(helper.absolutePos(new BlockPos(x, 1, 3)));
        }
        return dirt;
    }

    /** Farmland at {@code (x, 1, 3)} with a lamp two above it, so the wheat that would stand at {@code (x, 2, 3)} has
     *  light to survive; returns the CROP cells, which is where the placement event is posted. */
    private static Set<BlockPos> farmlandRow(GameTestHelper helper, int... xs) {
        Set<BlockPos> crops = new HashSet<>();
        for (int x : xs) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.FARMLAND);
            helper.setBlock(new BlockPos(x, 3, 3), Blocks.GLOWSTONE);
            crops.add(helper.absolutePos(new BlockPos(x, 2, 3)));
        }
        return crops;
    }

    private static void clearRow(GameTestHelper helper, int... xs) {
        for (int x : xs) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.AIR);
            helper.setBlock(new BlockPos(x, 2, 3), Blocks.AIR);
            helper.setBlock(new BlockPos(x, 3, 3), Blocks.AIR);
        }
    }

    private static void requireProtectionChecksOn(GameTestHelper helper) {
        helper.assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
    }

    /** The loose items at the robot's feet, where a planting drops what is left of the seeds. A tight box around the
     *  robot, because neighbouring arena cells are shared. */
    private static List<ItemEntity> itemsAtTheRobot(Fixture f) {
        return f.level.getEntitiesOfClass(ItemEntity.class, f.robot.getBoundingBox().inflate(1.0));
    }

    private static void discardDrops(Fixture f) {
        for (ItemEntity drop : itemsAtTheRobot(f)) {
            drop.discard();
        }
    }

    // ---------- the farmer ----------

    /**
     * The only dirt in the zone refuses the hoe. The farmer tries it ONCE, remembers, finds nothing else to till and
     * goes to sleep - it does not fly back and ask again.
     */
    public static void farmerDoesNotRetryARefusedTill(GameTestHelper helper) {
        int[] xs = { 2 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            Set<BlockPos> dirt = dirtRow(helper, xs);
            BlockPos only = dirt.iterator().next();
            f.robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));
            f.board = new BoardRobotFarmer(f.robot);
            try (WorkClaimMod claim = new WorkClaimMod(dirt::contains, dirt::contains)) {
                boolean slept = f.cycleUntil(() -> f.asleep() || claim.toolAsks(only) > 1);

                helper.assertTrue(claim.toolAsks(only) == 1,
                        "the refused block is asked about once, not on every cycle - got " + claim.toolAsks(only));
                helper.assertTrue(slept, "with nothing left to till the farmer sleeps");
                helper.assertTrue(f.level.getBlockState(only).is(Blocks.DIRT), "a refused till leaves the dirt alone");
                helper.assertTrue(f.robot.getHeldItem().is(Items.DIAMOND_HOE),
                        "the hoe that failed is kept, got " + f.robot.getHeldItem());
            } finally {
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * One dirt block refuses the hoe, the next does not. The farmer moves on to the next block - it neither loops on
     * the refused one (it is the nearest, so it would win every search) nor stops working.
     */
    public static void farmerMovesOnFromARefusedTill(GameTestHelper helper) {
        int[] xs = { 2, 4 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            Set<BlockPos> dirt = dirtRow(helper, xs);
            BlockPos refusing = helper.absolutePos(new BlockPos(2, 1, 3));
            BlockPos allowed = helper.absolutePos(new BlockPos(4, 1, 3));
            f.robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));
            f.board = new BoardRobotFarmer(f.robot);
            try (WorkClaimMod claim = new WorkClaimMod(dirt::contains, refusing::equals)) {
                boolean tilled = f.cycleUntil(() -> f.level.getBlockState(allowed).is(Blocks.FARMLAND)
                        || claim.toolAsks(refusing) > 1);

                helper.assertTrue(claim.toolAsks(refusing) == 1,
                        "the refused block is asked about once - got " + claim.toolAsks(refusing));
                helper.assertTrue(tilled, "the farmer goes on to till the block that allows it");
                helper.assertTrue(f.level.getBlockState(refusing).is(Blocks.DIRT), "the refused dirt is untouched");
            } finally {
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * A wall of refusals: the farmer does not try every block in a claim. After {@code GIVE_UP_AFTER} failed attempts
     * in a row it sleeps, each refused block having been asked about once.
     */
    public static void farmerSleepsAfterRepeatedRefusals(GameTestHelper helper) {
        int[] xs = { 1, 2, 3, 4, 5 };
        try (Fixture f = new Fixture(helper, new BlockPos(1, 2, 3))) {
            Set<BlockPos> dirt = dirtRow(helper, xs);
            f.robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));
            f.board = new BoardRobotFarmer(f.robot);
            try (WorkClaimMod claim = new WorkClaimMod(dirt::contains, dirt::contains)) {
                boolean slept = f.cycleUntil(() -> f.asleep() || claim.totalToolAsks() > xs.length);

                helper.assertTrue(claim.totalToolAsks() == WorkRefusals.GIVE_UP_AFTER,
                        "the farmer gives up after " + WorkRefusals.GIVE_UP_AFTER + " refusals in a row, not after "
                                + "trying every block in the claim - got " + claim.totalToolAsks());
                for (BlockPos pos : dirt) {
                    helper.assertTrue(claim.toolAsks(pos) <= 1, "no block is asked about twice, " + pos + " was asked "
                            + claim.toolAsks(pos) + " times");
                }
                helper.assertTrue(slept, "...and then it sleeps");
            } finally {
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * The search asks protection mods about the block it settles on, as the generic search board does for its breaks:
     * dirt a claim will not let the robot break is skipped without the hoe ever being sent to it, and the next dirt is
     * tilled.
     */
    public static void farmerSkipsDirtItMayNotBreak(GameTestHelper helper) {
        requireProtectionChecksOn(helper);
        int[] xs = { 2, 4 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            Set<BlockPos> dirt = dirtRow(helper, xs);
            BlockPos claimed = helper.absolutePos(new BlockPos(2, 1, 3));
            BlockPos open = helper.absolutePos(new BlockPos(4, 1, 3));
            f.robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));
            f.board = new BoardRobotFarmer(f.robot);
            try (ClaimProtectionMod breakClaim = new ClaimProtectionMod(claimed, true);
                    WorkClaimMod toolClaim = new WorkClaimMod(dirt::contains, pos -> false)) {
                boolean tilled = f.cycleUntil(() -> f.level.getBlockState(open).is(Blocks.FARMLAND));

                helper.assertTrue(breakClaim.asks() >= 1, "the search asks protection mods about the claimed dirt");
                helper.assertTrue(tilled, "the farmer tills the dirt it is allowed to");
                helper.assertTrue(f.level.getBlockState(claimed).is(Blocks.DIRT),
                        "dirt the robot may not break is not tilled");
                helper.assertTrue(toolClaim.toolAsks(claimed) == 0,
                        "the hoe is never sent to dirt the search was told to leave, got " + toolClaim.toolAsks(claimed));
            } finally {
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    // ---------- the planter ----------

    /**
     * A planting a claim mod cancels fails, and the seed it did not plant stays in the robot's hand. Before, the AI
     * dropped the whole stack at the robot's feet and emptied the hand whatever happened, so the planter fetched a
     * seed again just to be refused again.
     */
    public static void refusedPlantingKeepsTheSeed(GameTestHelper helper) {
        int[] xs = { 2 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            Set<BlockPos> crops = farmlandRow(helper, xs);
            BlockPos crop = crops.iterator().next();
            BlockPos soil = crop.below();
            f.robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS, 5));
            try (WorkClaimMod claim = new WorkClaimMod(crops::contains, crops::contains)) {
                AIRobotPlant planter = new AIRobotPlant(f.robot, soil);
                for (int cycles = 0; cycles < 80 && planter.success(); cycles++) {
                    planter.cycle();
                }

                helper.assertTrue(claim.placeAsks(crop) == 1, "the planting was attempted and refused once, got "
                        + claim.placeAsks(crop));
                helper.assertTrue(!planter.success(), "a refused planting reports failure");
                helper.assertTrue(f.level.getBlockState(crop).isAir(), "nothing was planted");
                helper.assertTrue(f.robot.getHeldItem().is(Items.WHEAT_SEEDS) && f.robot.getHeldItem().getCount() == 5,
                        "the seeds the planting did not use stay in the hand, got " + f.robot.getHeldItem());
                List<ItemEntity> drops = itemsAtTheRobot(f);
                helper.assertTrue(drops.isEmpty(), "and none of them is dropped on the ground, got " + drops);
            } finally {
                discardDrops(f);
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * A planting asks the right-click-block event first, as a player's seed does: a claim mod that guards only
     * right-clicks (and never cancels the placement) stops the robot, and one that allows it sees exactly one ask
     * before the seed goes in. Before, the planting went straight to the item's use and only the placement event
     * (a different claim hook) could refuse it.
     */
    public static void plantingAsksTheRightClickEventFirst(GameTestHelper helper) {
        int[] xs = { 2 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            Set<BlockPos> crops = farmlandRow(helper, xs);
            BlockPos crop = crops.iterator().next();
            BlockPos soil = crop.below();
            f.robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS, 5));
            try {
                // Refuses the right-click on the soil; the crop cell's placement is watched but never cancelled.
                try (WorkClaimMod claim = new WorkClaimMod(pos -> pos.equals(soil) || pos.equals(crop), soil::equals)) {
                    AIRobotPlant planter = new AIRobotPlant(f.robot, soil);
                    for (int cycles = 0; cycles < 80 && planter.success(); cycles++) {
                        planter.cycle();
                    }

                    helper.assertTrue(claim.toolAsks(soil) == 1,
                            "the planting asks the right-click event once for the soil, got " + claim.toolAsks(soil));
                    helper.assertTrue(claim.placeAsks(crop) == 0,
                            "a refused right-click never reaches the placement, got " + claim.placeAsks(crop));
                    helper.assertTrue(!planter.success(), "a planting the right-click refused reports failure");
                    helper.assertTrue(f.level.getBlockState(crop).isAir(), "nothing was planted");
                    helper.assertTrue(
                            f.robot.getHeldItem().is(Items.WHEAT_SEEDS) && f.robot.getHeldItem().getCount() == 5,
                            "the seeds stay in the hand, got " + f.robot.getHeldItem());
                }
                try (WorkClaimMod claim = new WorkClaimMod(pos -> pos.equals(soil) || pos.equals(crop), pos -> false)) {
                    AIRobotPlant planter = new AIRobotPlant(f.robot, soil);
                    for (int cycles = 0; cycles < 80 && !f.level.getBlockState(crop).is(Blocks.WHEAT); cycles++) {
                        planter.cycle();
                    }

                    helper.assertTrue(f.level.getBlockState(crop).is(Blocks.WHEAT),
                            "with nothing refusing, the seed is planted");
                    helper.assertTrue(claim.toolAsks(soil) == 1,
                            "...after one right-click ask for the soil, got " + claim.toolAsks(soil));
                    helper.assertTrue(claim.placeAsks(crop) == 1,
                            "...and one placement ask for the crop cell, got " + claim.placeAsks(crop));
                }
            } finally {
                discardDrops(f);
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * The only farmland in the zone refuses the seed. The planter tries it ONCE, remembers, finds nowhere else to plant
     * and goes to sleep with its seeds still in hand - it does not fly back and ask again.
     */
    public static void planterDoesNotRetryARefusedPlanting(GameTestHelper helper) {
        int[] xs = { 2 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            Set<BlockPos> crops = farmlandRow(helper, xs);
            BlockPos crop = crops.iterator().next();
            f.robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS, 5));
            f.board = new BoardRobotPlanter(f.robot);
            try (WorkClaimMod claim = new WorkClaimMod(crops::contains, crops::contains)) {
                boolean slept = f.cycleUntil(() -> f.asleep() || claim.placeAsks(crop) > 1);

                helper.assertTrue(claim.placeAsks(crop) == 1,
                        "the refused spot is asked about once, not on every cycle - got " + claim.placeAsks(crop));
                helper.assertTrue(slept, "with nowhere left to plant the planter sleeps");
                helper.assertTrue(f.robot.getHeldItem().is(Items.WHEAT_SEEDS) && f.robot.getHeldItem().getCount() == 5,
                        "...still holding its seeds, got " + f.robot.getHeldItem());
            } finally {
                discardDrops(f);
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * One farmland cell refuses the seed, the next does not. The planter moves on and plants the other one.
     */
    public static void planterMovesOnFromARefusedPlanting(GameTestHelper helper) {
        int[] xs = { 2, 4 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            Set<BlockPos> crops = farmlandRow(helper, xs);
            BlockPos refusing = helper.absolutePos(new BlockPos(2, 2, 3));
            BlockPos allowed = helper.absolutePos(new BlockPos(4, 2, 3));
            f.robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS, 5));
            f.board = new BoardRobotPlanter(f.robot);
            try (WorkClaimMod claim = new WorkClaimMod(crops::contains, refusing::equals)) {
                boolean planted = f.cycleUntil(() -> f.level.getBlockState(allowed).is(Blocks.WHEAT)
                        || claim.placeAsks(refusing) > 1);

                helper.assertTrue(claim.placeAsks(refusing) <= 1,
                        "the refused spot is asked about once - got " + claim.placeAsks(refusing));
                helper.assertTrue(planted, "the planter goes on to plant the spot that allows it");
                helper.assertTrue(f.level.getBlockState(refusing).isAir(), "nothing grows where the claim said no");
            } finally {
                discardDrops(f);
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * A wall of refusals: the planter does not try every cell in a claim. After {@code GIVE_UP_AFTER} failed
     * plantings in a row it sleeps, each refused cell having been asked about once, its seeds still in hand.
     */
    public static void planterSleepsAfterRepeatedRefusals(GameTestHelper helper) {
        int[] xs = { 1, 2, 3, 4, 5 };
        try (Fixture f = new Fixture(helper, new BlockPos(1, 2, 3))) {
            Set<BlockPos> crops = farmlandRow(helper, xs);
            f.robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS, 5));
            f.board = new BoardRobotPlanter(f.robot);
            try (WorkClaimMod claim = new WorkClaimMod(crops::contains, crops::contains)) {
                boolean slept = f.cycleUntil(() -> f.asleep() || claim.totalPlaceAsks() > xs.length);

                helper.assertTrue(claim.totalPlaceAsks() == WorkRefusals.GIVE_UP_AFTER,
                        "the planter gives up after " + WorkRefusals.GIVE_UP_AFTER + " refusals in a row, not after "
                                + "trying every cell in the claim - got " + claim.totalPlaceAsks());
                for (BlockPos pos : crops) {
                    helper.assertTrue(claim.placeAsks(pos) <= 1, "no cell is asked about twice, " + pos + " was asked "
                            + claim.placeAsks(pos) + " times");
                }
                helper.assertTrue(slept, "...and then it sleeps");
                helper.assertTrue(f.robot.getHeldItem().is(Items.WHEAT_SEEDS) && f.robot.getHeldItem().getCount() == 5,
                        "...with its seeds still in hand, got " + f.robot.getHeldItem());
            } finally {
                discardDrops(f);
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }
}
