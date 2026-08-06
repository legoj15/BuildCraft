/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.boards.BoardRobotPicker;
import buildcraft.robotics.entity.EntityRobot;

/** Ph4 game tests for {@link AIRobotFetchItem}, all driven SYNCHRONOUSLY on unadded robots: the AI's scan
 *  reads the world (real dropped items, added to the arena) and the robot's transactor is a plain slot
 *  adapter, so no entity ticking is needed anywhere. Each robot is hand-cycled — {@code cycle()} is the real
 *  entry point the entity tick uses, which matters for the removed-target test: the bug there lives in the
 *  preempt-then-update ordering inside one cycle. */
public class AIRobotFetchItemTester {

    private static EntityRobot robotAt(GameTestHelper helper, BlockPos relPos) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        return robot;
    }

    private static ItemEntity dropAt(GameTestHelper helper, BlockPos relPos, ItemStack stack) {
        ServerLevel level = helper.getLevel();
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
        level.addFreshEntity(drop);
        return drop;
    }

    /** The robot's target vanishes mid-fetch (a player grabs it, a hopper eats it) and the SAME cycle's
     *  update still runs after {@code preempt} has terminated the AI — that update must not pick from the
     *  removed entity's stale stack, or the items exist twice: once with whoever took the drop, once in the
     *  robot. 7.1.x had this hole too (its update never re-checked liveness); it is latent dupe bait, so the
     *  port closes it rather than characterising it. */
    public static void fetchItemRemovedTargetIsNotPicked(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        EntityRobot robot = robotAt(helper, rel);
        // Same block as the robot, so the scan locks the target without starting a goto delegate.
        ItemEntity drop = dropAt(helper, rel, new ItemStack(Items.STONE, 10));

        AIRobotFetchItem fetch = new AIRobotFetchItem(robot, 16, null, null);
        RecordingParent parent = new RecordingParent(robot);
        parent.startDelegateAI(fetch);

        // One cycle scans and locks the target; six more bring pickTime to 5 — the NEXT update would pick.
        for (int i = 0; i < 7; i++) {
            fetch.cycle();
        }

        // The world takes the drop. The robot's target reference is now stale.
        drop.discard();

        fetch.cycle(); // preempt terminates on the removed target; the same-tick update must stay out

        helper.assertTrue(parent.ended == fetch,
                "the fetch must terminate the moment its target leaves the world");
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(robot.getInventoryStack(i).isEmpty(),
                    "the world already took this drop — picking it up from the removed entity duplicates "
                            + "the items (slot " + i + ")");
        }
        helper.succeed();
    }

    /** 7.1.x's eligibility check was {@code inject(...) > 0} — a drop the robot can only PARTIALLY absorb is
     *  still fetched, the robot takes what fits and leaves the remainder. A full-fit gate strands every
     *  overflow drop forever (a near-full robot would hover over items it could legitimately take one of and
     *  never touch them). */
    public static void fetchItemPartialFitStillTargets(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        EntityRobot robot = robotAt(helper, rel);
        // Exactly ONE stone's worth of room across the whole inventory.
        robot.setInventoryStack(0, new ItemStack(Items.STONE, 64));
        robot.setInventoryStack(1, new ItemStack(Items.STONE, 64));
        robot.setInventoryStack(2, new ItemStack(Items.STONE, 64));
        robot.setInventoryStack(3, new ItemStack(Items.STONE, 63));
        ItemEntity drop = dropAt(helper, rel, new ItemStack(Items.STONE, 10));

        AIRobotFetchItem fetch = new AIRobotFetchItem(robot, 16, null, null);
        RecordingParent parent = new RecordingParent(robot);
        parent.startDelegateAI(fetch);

        try {
            fetch.cycle(); // the scan cycle

            helper.assertTrue(parent.ended == null,
                    "a drop that only partially fits must still be fetched — 7.1.x took what fit and left "
                            + "the rest, it did not strand the drop");
            helper.assertTrue(BoardRobotPicker.targettedItems.contains(drop.getUUID()),
                    "the partial-fit drop must be locked as this robot's target");
        } finally {
            fetch.abort(); // releases the target lock via end(), whatever the assertions did
            drop.discard(); // world-added — the framework's cleanup bounds don't reach rel (2,2,2)
        }
        helper.succeed();
    }

    /** The shared {@code targettedItems} lock (D4, UUID-keyed) is what keeps two picker robots from flying at
     *  the same drop: each locks a DISTINCT drop, and ending a fetch releases its lock. A leak in either
     *  direction is fatal — without dedup two robots fight over one drop forever; without release every drop
     *  a robot ever looked at stays untouchable. Assertions key on this test's own drops only, never on the
     *  whole set: the set is static and a picker running in another arena may legitimately hold its own
     *  locks at the same moment. */
    public static void fetchItemTargetLocksDedupeAndRelease(GameTestHelper helper) {
        ItemEntity drop1 = dropAt(helper, new BlockPos(2, 2, 2), new ItemStack(Items.DIRT, 5));
        ItemEntity drop2 = dropAt(helper, new BlockPos(4, 2, 5), new ItemStack(Items.DIRT, 5));
        // B sits CLOSER to drop1 than to drop2, so only the dedup lock steers it to drop2.
        EntityRobot robotA = robotAt(helper, new BlockPos(1, 2, 1));
        EntityRobot robotB = robotAt(helper, new BlockPos(2, 2, 4));

        AIRobotFetchItem fetchA = new AIRobotFetchItem(robotA, 16, null, null);
        AIRobotFetchItem fetchB = new AIRobotFetchItem(robotB, 16, null, null);
        RecordingParent parentA = new RecordingParent(robotA);
        RecordingParent parentB = new RecordingParent(robotB);
        parentA.startDelegateAI(fetchA);
        parentB.startDelegateAI(fetchB);

        try {
            fetchA.cycle();
            helper.assertTrue(BoardRobotPicker.targettedItems.contains(drop1.getUUID())
                            && !BoardRobotPicker.targettedItems.contains(drop2.getUUID()),
                    "robot A locks the nearest drop, and only that one");

            fetchB.cycle();
            helper.assertTrue(BoardRobotPicker.targettedItems.contains(drop1.getUUID())
                            && BoardRobotPicker.targettedItems.contains(drop2.getUUID()),
                    "robot B cannot take A's drop — the lock steers it to the other one");
        } finally {
            fetchA.abort();
            fetchB.abort();
            drop1.discard(); // world-added drops outlive the test otherwise (cleanup bounds reach only
            drop2.discard(); // the arena corner) and a later batch's picker at these coords would eat them
        }

        helper.assertTrue(!BoardRobotPicker.targettedItems.contains(drop1.getUUID())
                        && !BoardRobotPicker.targettedItems.contains(drop2.getUUID()),
                "ending a fetch must release its lock — a leaked lock freezes the drop for every robot "
                        + "until the server restarts");
        helper.succeed();
    }

    /** Records the delegate that ended, so termination is observable from outside. */
    private static class RecordingParent extends AIRobot {
        AIRobot ended;

        RecordingParent(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            ended = ai;
        }
    }
}
