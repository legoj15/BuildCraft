/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.crops.CropManager;
import buildcraft.api.crops.ICropHandler;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.ai.AIRobotBreak;
import buildcraft.robotics.ai.AIRobotHarvest;
import buildcraft.robotics.boards.FarmPlantRefusalTester.Fixture;

/**
 * The break boards remember a break that simply failed, the way the farmer remembers a till that did. A block the
 * robot's tool cannot break (here: bedrock, whose hardness is negative - the stand-in for a modded block that
 * refuses) was never remembered by anybody: the search settled on the nearest such block, the break failed, the block
 * was released, and the next search settled on the same block again, forever. The harvester had the same hole (its
 * crop handler can decline a crop that reads as ripe).
 *
 * <p>Driven like {@link FarmPlantRefusalTester}: the boards are cycled by hand on an unadded, zone-pinned robot parked
 * on a station of its own, and each test counts the attempts its board made (the observable the loop shows up in).
 */
public class BreakFailureMemoryTester {

    /** A break board that hunts bedrock and stone with whatever is in its hand, recording the block of every break
     *  attempt that ended (the attempt count is the loop's observable). Bedrock is expected by the search but can never
     *  be broken - exactly a block that "refuses" - while stone breaks normally under a pickaxe. */
    private static final class StoneAndBedrockBreaker extends BoardRobotGenericBreakBlock {
        final List<BlockPos> attempts = new ArrayList<>();

        StoneAndBedrockBreaker(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public boolean isExpectedTool(ItemStack stack) {
            return true;
        }

        @Override
        public boolean isExpectedBlock(BlockState state) {
            return state.is(Blocks.BEDROCK) || state.is(Blocks.STONE);
        }

        @Override
        public RedstoneBoardRobotNBT getNBTHandler() {
            return BoardRobotLumberjackNBT.INSTANCE;
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            if (ai instanceof AIRobotBreak) {
                attempts.add(blockFound());
            }
            super.delegateAIEnded(ai);
        }

        int attemptsAt(BlockPos pos) {
            return (int) attempts.stream().filter(pos::equals).count();
        }
    }

    /** The harvester, recording the block of every harvest attempt that ended. */
    private static final class CountingHarvester extends BoardRobotHarvester {
        final List<BlockPos> attempts = new ArrayList<>();

        CountingHarvester(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            if (ai instanceof AIRobotHarvest) {
                attempts.add(blockFound());
            }
            super.delegateAIEnded(ai);
        }

        int attemptsAt(BlockPos pos) {
            return (int) attempts.stream().filter(pos::equals).count();
        }
    }

    /** A crop handler for hay bales at the positions it is told about: all read as ripe, the {@code declined} ones
     *  refuse to be harvested (a handler that returns false: a modded crop with a condition the robot cannot meet), the
     *  others are cleared and counted as harvested. Handlers cannot be unregistered through the API, so this one is
     *  taken out of the manager's list again when the test is done, and inert in the meantime for any other position. */
    private static final class PinnedCropHandler implements ICropHandler, AutoCloseable {
        private final Set<BlockPos> ripe = new HashSet<>();
        private final Set<BlockPos> declined = new HashSet<>();

        PinnedCropHandler(Set<BlockPos> ripe, Set<BlockPos> declined) {
            this.ripe.addAll(ripe);
            this.declined.addAll(declined);
            CropManager.registerHandler(this);
        }

        @Override
        public boolean isSeed(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canSustainPlant(Level world, ItemStack seed, BlockPos pos) {
            return false;
        }

        @Override
        public boolean plantCrop(Level world, Player player, ItemStack seed, BlockPos pos) {
            return false;
        }

        @Override
        public boolean isMature(BlockGetter blockAccess, BlockState state, BlockPos pos) {
            return ripe.contains(pos) && state.is(Blocks.HAY_BLOCK);
        }

        @Override
        public boolean harvestCrop(Level world, BlockPos pos, NonNullList<ItemStack> drops) {
            if (declined.contains(pos)) {
                return false;
            }
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            return true;
        }

        @Override
        public void close() {
            ripe.clear();
            declined.clear();
            try {
                Field handlers = CropManager.class.getDeclaredField("handlers");
                handlers.setAccessible(true);
                ((List<?>) handlers.get(null)).remove(this);
            } catch (ReflectiveOperationException e) {
                // Left registered but inert: with no ripe position it matches nothing.
            }
        }
    }

    // ---------- helpers ----------

    private static void placeRow(GameTestHelper helper, net.minecraft.world.level.block.Block block, int... xs) {
        for (int x : xs) {
            helper.setBlock(new BlockPos(x, 1, 3), block);
        }
    }

    private static void clearRow(GameTestHelper helper, int... xs) {
        for (int x : xs) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.AIR);
        }
    }

    /** The drops a break leaves at the broken block, swept from this test's own cell only. */
    private static void discardDrops(GameTestHelper helper) {
        for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                EntityArenaUtil.cellBounds(helper))) {
            drop.discard();
        }
    }

    // ---------- the break boards ----------

    /**
     * The only block in the zone cannot be broken. The board tries it ONCE, remembers, finds nothing else and goes to
     * sleep - it does not fly back and swing at it again.
     */
    public static void breakBoardDoesNotRetryABlockItCannotBreak(GameTestHelper helper) {
        int[] xs = { 2 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            placeRow(helper, Blocks.BEDROCK, xs);
            BlockPos bedrock = helper.absolutePos(new BlockPos(2, 1, 3));
            f.robot.setItemInUse(new ItemStack(Items.DIAMOND_PICKAXE));
            StoneAndBedrockBreaker board = new StoneAndBedrockBreaker(f.robot);
            f.board = board;
            try {
                boolean slept = f.cycleUntil(() -> f.asleep() || board.attemptsAt(bedrock) > 1);

                helper.assertTrue(board.attemptsAt(bedrock) == 1,
                        "the block is tried once, not on every search - got " + board.attemptsAt(bedrock));
                helper.assertTrue(slept, "with nothing left to break the board sleeps");
                helper.assertTrue(f.level.getBlockState(bedrock).is(Blocks.BEDROCK), "the block is untouched");
            } finally {
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * An unbreakable block is the nearest, a stone farther along. The board moves on and mines the stone - it neither
     * loops on the block that refuses (it is the nearest, so it would win every search) nor stops working.
     */
    public static void breakBoardMovesOnFromABlockItCannotBreak(GameTestHelper helper) {
        int[] xs = { 2, 4 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            placeRow(helper, Blocks.BEDROCK, 2);
            placeRow(helper, Blocks.STONE, 4);
            BlockPos bedrock = helper.absolutePos(new BlockPos(2, 1, 3));
            BlockPos stone = helper.absolutePos(new BlockPos(4, 1, 3));
            f.robot.setItemInUse(new ItemStack(Items.DIAMOND_PICKAXE));
            StoneAndBedrockBreaker board = new StoneAndBedrockBreaker(f.robot);
            f.board = board;
            try {
                boolean mined = f.cycleUntil(() -> f.level.getBlockState(stone).isAir()
                        || board.attemptsAt(bedrock) > 1);

                helper.assertTrue(board.attemptsAt(bedrock) == 1,
                        "the block that refuses is tried once - got " + board.attemptsAt(bedrock));
                helper.assertTrue(mined, "the board goes on to break the stone");
                helper.assertTrue(f.level.getBlockState(bedrock).is(Blocks.BEDROCK), "the bedrock is untouched");
            } finally {
                discardDrops(helper);
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * A wall of blocks that refuse: the board does not try every one. After {@code GIVE_UP_AFTER} failed breaks in a
     * row it sleeps, each block having been tried once.
     */
    public static void breakBoardSleepsAfterRepeatedFailedBreaks(GameTestHelper helper) {
        int[] xs = { 1, 2, 3, 4, 5 };
        try (Fixture f = new Fixture(helper, new BlockPos(1, 2, 3))) {
            placeRow(helper, Blocks.BEDROCK, xs);
            f.robot.setItemInUse(new ItemStack(Items.DIAMOND_PICKAXE));
            StoneAndBedrockBreaker board = new StoneAndBedrockBreaker(f.robot);
            f.board = board;
            try {
                boolean slept = f.cycleUntil(() -> f.asleep() || board.attempts.size() > xs.length);

                helper.assertTrue(board.attempts.size() == WorkRefusals.GIVE_UP_AFTER,
                        "the board gives up after " + WorkRefusals.GIVE_UP_AFTER + " failed breaks in a row, not after"
                                + " trying every block in the wall - got " + board.attempts.size());
                for (int x : xs) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, 1, 3));
                    helper.assertTrue(board.attemptsAt(pos) <= 1, "no block is tried twice, " + pos + " was tried "
                            + board.attemptsAt(pos) + " times");
                }
                helper.assertTrue(slept, "...and then it sleeps");
            } finally {
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    // ---------- the harvester ----------

    /**
     * The only ripe crop in the zone is declined by its handler. The harvester tries it ONCE, remembers, finds nothing
     * else and goes to sleep.
     */
    public static void harvesterDoesNotRetryAFailedHarvest(GameTestHelper helper) {
        int[] xs = { 2 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            placeRow(helper, Blocks.HAY_BLOCK, xs);
            BlockPos crop = helper.absolutePos(new BlockPos(2, 1, 3));
            CountingHarvester board = new CountingHarvester(f.robot);
            f.board = board;
            try (PinnedCropHandler handler = new PinnedCropHandler(Set.of(crop), Set.of(crop))) {
                boolean slept = f.cycleUntil(() -> f.asleep() || board.attemptsAt(crop) > 1);

                helper.assertTrue(board.attemptsAt(crop) == 1,
                        "the declined crop is tried once, not on every search - got " + board.attemptsAt(crop));
                helper.assertTrue(slept, "with nothing left to harvest the harvester sleeps");
                helper.assertTrue(f.level.getBlockState(crop).is(Blocks.HAY_BLOCK), "the crop is untouched");
            } finally {
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }

    /**
     * A declined crop is the nearest, a harvestable one farther along. The harvester moves on and harvests the second.
     */
    public static void harvesterMovesOnFromAFailedHarvest(GameTestHelper helper) {
        int[] xs = { 2, 4 };
        try (Fixture f = new Fixture(helper, new BlockPos(2, 2, 3))) {
            placeRow(helper, Blocks.HAY_BLOCK, xs);
            BlockPos declined = helper.absolutePos(new BlockPos(2, 1, 3));
            BlockPos good = helper.absolutePos(new BlockPos(4, 1, 3));
            CountingHarvester board = new CountingHarvester(f.robot);
            f.board = board;
            try (PinnedCropHandler handler = new PinnedCropHandler(Set.of(declined, good), Set.of(declined))) {
                boolean harvested = f.cycleUntil(() -> f.level.getBlockState(good).isAir()
                        || board.attemptsAt(declined) > 1);

                helper.assertTrue(board.attemptsAt(declined) == 1,
                        "the declined crop is tried once - got " + board.attemptsAt(declined));
                helper.assertTrue(harvested, "the harvester goes on to the crop that harvests");
                helper.assertTrue(f.level.getBlockState(declined).is(Blocks.HAY_BLOCK), "the declined crop is untouched");
            } finally {
                clearRow(helper, xs);
            }
        }
        helper.succeed();
    }
}
