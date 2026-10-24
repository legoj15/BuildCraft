/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.AIRobotStripesHandler;
import buildcraft.robotics.ai.MockRobotAccess;

/** The Stripes robot used to loop forever on an item no stripes handler can use (7.1.x did too): fetch it, fly to
 *  a random empty cell, fail to use it, fly to the next cell, for ever, with every other item in the supply chest
 *  starved behind it. A single failed use proves nothing - a seed only takes on farmland, a bucket needs a
 *  target - so the board counts failed uses in a row, and after
 *  {@link BoardRobotStripes#GIVE_UP_AFTER_FAILED_USES} of them gives the item back (stashes it and disposes of it
 *  the way the delivery board disposes of cargo) and never fetches that item again this session. */
public class BoardRobotStripesUnusableItemTest extends VanillaSetupBaseTester {

    private static final BlockPos BLOCK = new BlockPos(1, 64, 1);

    /** A robot whose transfer slots are a plain list. {@code getInventorySize()} stays 0 like the delivery tests'
     *  cargo robot, so the disposal that follows the stash cannot reach for a level: that path is the game
     *  test's ({@code RobotStripesTester}). */
    private static final class StashRobot extends MockRobotAccess {
        final List<ItemStack> stashed = new ArrayList<>();
        int inventoryReads;

        @Override
        public IItemTransactor getTransactor() {
            return new IItemTransactor() {
                @Override
                public ItemStack insert(ItemStack stack, boolean allOrNone, boolean simulate) {
                    if (!simulate) {
                        stashed.add(stack.copy());
                    }
                    return ItemStack.EMPTY;
                }

                @Override
                public ItemStack extract(buildcraft.api.core.IStackFilter filter, int min, int max, boolean simulate) {
                    return ItemStack.EMPTY;
                }
            };
        }

        @Override
        public boolean containsItems() {
            return !stashed.isEmpty();
        }

        @Override
        public int getInventorySize() {
            inventoryReads++;
            return 0;
        }
    }

    private static AIRobotStripesHandler failedUse(IRobotAccess robot) {
        return new AIRobotStripesHandler(robot, BLOCK) {
            {
                setSuccess(false);
            }
        };
    }

    private static AIRobotStripesHandler refusedUse(IRobotAccess robot) {
        return new AIRobotStripesHandler(robot, BLOCK) {
            {
                markRefused();
            }
        };
    }

    private static AIRobotStripesHandler workedUse(IRobotAccess robot) {
        return new AIRobotStripesHandler(robot, BLOCK);
    }

    private static StashRobot robotHolding(ItemStack held) {
        StashRobot robot = new StashRobot();
        robot.setItemInUse(held);
        return robot;
    }

    @Test
    public void anItemNoHandlerCanUseIsGivenBackAfterTenFailedUsesInARow() {
        StashRobot robot = robotHolding(new ItemStack(Items.STICK, 3));
        BoardRobotStripes board = new BoardRobotStripes(robot);

        for (int use = 1; use < BoardRobotStripes.GIVE_UP_AFTER_FAILED_USES; use++) {
            board.delegateAIEnded(failedUse(robot));
            Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.STICK, 3), robot.getHeldItem()),
                    "failed use " + use + " proves nothing yet - the item is still in hand");
        }
        Assertions.assertTrue(robot.stashed.isEmpty(), "nothing is given back before the limit");

        board.delegateAIEnded(failedUse(robot));

        Assertions.assertTrue(robot.getHeldItem().isEmpty(), "the item has left the robot's hand");
        Assertions.assertEquals(1, robot.stashed.size(), "...into its cargo, whole");
        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.STICK, 3), robot.stashed.get(0)),
                "the cargo is the very stack that failed, all three sticks");
        Assertions.assertTrue(robot.inventoryReads > 0,
                "and the cargo disposal ran (unload at a station, else drop it), as the delivery board's does");
    }

    @Test
    public void aUseThatWorksStartsTheCountAgain() {
        StashRobot robot = robotHolding(new ItemStack(Items.STICK, 3));
        BoardRobotStripes board = new BoardRobotStripes(robot);

        for (int use = 1; use < BoardRobotStripes.GIVE_UP_AFTER_FAILED_USES; use++) {
            board.delegateAIEnded(failedUse(robot));
        }
        board.delegateAIEnded(workedUse(robot));
        for (int use = 1; use < BoardRobotStripes.GIVE_UP_AFTER_FAILED_USES; use++) {
            board.delegateAIEnded(failedUse(robot));
        }

        Assertions.assertFalse(robot.getHeldItem().isEmpty(),
                "failed uses are counted in a row: nine, a success, nine more never reaches the limit");
        Assertions.assertTrue(robot.stashed.isEmpty());
    }

    @Test
    public void aProtectionRefusalIsNotAnUnusableItem() {
        StashRobot robot = robotHolding(new ItemStack(Items.STICK, 3));
        BoardRobotStripes board = new BoardRobotStripes(robot);

        for (int use = 1; use < BoardRobotStripes.GIVE_UP_AFTER_FAILED_USES; use++) {
            board.delegateAIEnded(failedUse(robot));
        }
        for (int use = 1; use <= 2 * BoardRobotStripes.GIVE_UP_AFTER_FAILED_USES; use++) {
            board.delegateAIEnded(refusedUse(robot));
        }

        Assertions.assertTrue(ItemStack.matches(new ItemStack(Items.STICK, 3), robot.getHeldItem()),
                "a claim refusing the cell says nothing about the item: refused uses never give it up");
        Assertions.assertTrue(robot.stashed.isEmpty());

        board.delegateAIEnded(failedUse(robot));
        Assertions.assertTrue(robot.getHeldItem().isEmpty(),
                "nor do they break the run of failed uses: the tenth unusable use still gives the item up");
    }

    @Test
    public void aGivenUpItemIsNotFetchedAgainButOthersAre() {
        StashRobot robot = robotHolding(new ItemStack(Items.STICK, 3));
        BoardRobotStripes board = new BoardRobotStripes(robot);
        Assertions.assertTrue(board.worthEquipping(new ItemStack(Items.STICK, 1)),
                "precondition: before it fails, a stick is as good as anything");

        for (int use = 1; use <= BoardRobotStripes.GIVE_UP_AFTER_FAILED_USES; use++) {
            board.delegateAIEnded(failedUse(robot));
        }

        Assertions.assertFalse(board.worthEquipping(new ItemStack(Items.STICK, 1)),
                "disposed back to a supply chest, the stick would be fetched straight away again; any stack of it "
                        + "is refused, whatever its count");
        Assertions.assertFalse(board.worthEquipping(new ItemStack(Items.STICK, 64)));
        Assertions.assertTrue(board.worthEquipping(new ItemStack(Items.COBBLESTONE, 1)),
                "other items are unaffected");
        Assertions.assertFalse(board.worthEquipping(ItemStack.EMPTY), "an empty stack was never worth equipping");
    }

    @Test
    public void aFailedUseStillGivesBackItsCell() {
        StashRobot robot = robotHolding(new ItemStack(Items.STICK, 3));
        BoardRobotStripes board = new BoardRobotStripes(robot);
        AIRobotSearchAndGotoBlock flight = new AIRobotSearchAndGotoBlock(robot);
        CompoundTag found = new CompoundTag();
        found.putIntArray("blockFound", new int[] { BLOCK.getX(), BLOCK.getY(), BLOCK.getZ() });
        flight.loadSelfFromNBT(found);
        board.delegateAIEnded(flight);
        Assertions.assertEquals(BLOCK, board.blockFound(), "precondition: the board is using the cell");

        board.delegateAIEnded(failedUse(robot));

        Assertions.assertNull(board.blockFound(), "success or not, the cell goes back to the registry");
    }

    @Test
    public void theGiveUpStateIsNotSaved() {
        StashRobot robot = robotHolding(new ItemStack(Items.STICK, 3));
        BoardRobotStripes board = new BoardRobotStripes(robot);
        for (int use = 1; use <= BoardRobotStripes.GIVE_UP_AFTER_FAILED_USES; use++) {
            board.delegateAIEnded(failedUse(robot));
        }

        CompoundTag saved = new CompoundTag();
        board.writeSelfToNBT(saved);

        Assertions.assertTrue(saved.isEmpty(),
                "the count and the refused items are a session's worth of memory, not part of the save: " + saved);
    }
}
