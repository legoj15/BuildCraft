/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.boards.BoardRobotPicker;
import buildcraft.robotics.entity.EntityRobot;

/** Ph4 game tests for {@link AIRobotFetchItem}, all driven SYNCHRONOUSLY on unadded robots: the AI's scan
 *  reads the world (real dropped items, added to the arena) and the robot's transactor is a plain slot
 *  adapter, so no entity ticking is needed anywhere. Each robot is hand-cycled — {@code cycle()} is the real
 *  entry point the entity tick uses, which matters for the removed-target test: the bug there lives in the
 *  preempt-then-update ordering inside one cycle.
 *
 *  <p><b>Hermeticity (the 2026-09 flake of {@code robot_fetch_item_partial_fit_targets}).</b> Two things made
 *  these tests depend on where the framework happened to drop the arena:
 *  <ul>
 *  <li><b>Scan visibility.</b> {@code Level.getEntitiesOfClass} only walks entity sections whose chunk is
 *      accessible ({@code EntitySectionStorage.forEachAccessibleNonEmptySection}). The framework force-loads
 *      only the 1x1x1 structure's chunk; a drop a few blocks away lands in a NEIGHBOURING chunk whenever the
 *      random origin sits near a chunk border, and until that chunk is promoted the drop is in the world but
 *      invisible to the scan — the fetch finds nothing and terminates. So every test force-loads the arena and
 *      runs its synchronous body only once {@link #whenScannable} has SEEN every drop through the same query
 *      the AI uses.</li>
 *  <li><b>Neighbouring arenas.</b> The 16-block scan reaches ~2 arenas in every direction. Every fetch here is
 *      given the test's own arena cell as its work zone ({@link #arenaZone}), so a sibling test's drop can
 *      never be picked instead of this test's own.</li>
 *  </ul>
 *  Drops are spawned motionless and weightless so they stay in the cell the assertions name while the
 *  visibility gate waits. */
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
        // Pinned in place: once its chunk ticks, the drop would otherwise fall out of the cell under test.
        drop.setNoGravity(true);
        drop.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(drop);
        return drop;
    }

    /** This test's own 6x7 arena cell — the work zone every fetch here gets, so the scan can never wander
     *  into a neighbouring arena's drops. */
    private static IZone arenaZone(GameTestHelper helper) {
        return new Box(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(5, 5, 6)));
    }

    private static AIRobotFetchItem fetch(GameTestHelper helper, EntityRobot robot) {
        return new AIRobotFetchItem(robot, 16, null, arenaZone(helper));
    }

    /** Force-loads the arena, then runs {@code body} on the first tick every drop is visible to the exact query
     *  {@link AIRobotFetchItem}'s scan makes. A drop in a not-yet-accessible entity section is in the world but
     *  invisible to {@code getEntitiesOfClass}; scanning before then is what made the partial-fit test flake. */
    private static void whenScannable(GameTestHelper helper, List<ItemEntity> drops, Runnable body) {
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(2, 2, 3));
        EntityArenaUtil.tickUntil(helper, 60, () -> drops.stream().allMatch(drop -> helper.getLevel()
                        .getEntitiesOfClass(ItemEntity.class, AABB.ofSize(drop.position(), 1, 1, 1))
                        .contains(drop)),
                body, "the test's drops never became visible to entity scans");
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

        whenScannable(helper, List.of(drop), () -> {
            AIRobotFetchItem fetch = fetch(helper, robot);
            RecordingParent parent = new RecordingParent(robot);
            parent.startDelegateAI(fetch);

            try {
                // One cycle scans and locks the target; six more bring pickTime to 5 — the NEXT update picks.
                fetch.cycle();
                helper.assertTrue(parent.ended == null && BoardRobotPicker.isTargetted(drop.getUUID()),
                        "precondition: the fetch must have locked the drop, or the guard below proves nothing");
                for (int i = 0; i < 6; i++) {
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
            } finally {
                fetch.abort();
                drop.discard();
            }
            helper.succeed();
        });
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

        whenScannable(helper, List.of(drop), () -> {
            AIRobotFetchItem fetch = fetch(helper, robot);
            RecordingParent parent = new RecordingParent(robot);
            parent.startDelegateAI(fetch);

            try {
                fetch.cycle(); // the scan cycle

                helper.assertTrue(parent.ended == null,
                        "a drop that only partially fits must still be fetched — 7.1.x took what fit and left "
                                + "the rest, it did not strand the drop");
                helper.assertTrue(BoardRobotPicker.isTargetted(drop.getUUID()),
                        "the partial-fit drop must be locked as this robot's target");
            } finally {
                fetch.abort(); // releases the target lock via end(), whatever the assertions did
                drop.discard(); // world-added — the framework's cleanup bounds don't reach rel (2,2,2)
            }
            helper.succeed();
        });
    }

    /** The shared target lock (D4, UUID-keyed) is what keeps two picker robots from flying at the same drop:
     *  each locks a DISTINCT drop, and ending a fetch releases its lock. A leak in either direction is fatal —
     *  without dedup two robots fight over one drop forever; without release every drop a robot ever looked
     *  at stays untouchable. Assertions key on this test's own drops only: the lock table is global and a
     *  picker running in another arena may legitimately hold its own locks at the same moment. */
    public static void fetchItemTargetLocksDedupeAndRelease(GameTestHelper helper) {
        ItemEntity drop1 = dropAt(helper, new BlockPos(2, 2, 2), new ItemStack(Items.DIRT, 5));
        ItemEntity drop2 = dropAt(helper, new BlockPos(4, 2, 5), new ItemStack(Items.DIRT, 5));
        // B sits CLOSER to drop1 than to drop2, so only the dedup lock steers it to drop2.
        EntityRobot robotA = robotAt(helper, new BlockPos(1, 2, 1));
        EntityRobot robotB = robotAt(helper, new BlockPos(2, 2, 4));

        whenScannable(helper, List.of(drop1, drop2), () -> {
            AIRobotFetchItem fetchA = fetch(helper, robotA);
            AIRobotFetchItem fetchB = fetch(helper, robotB);
            RecordingParent parentA = new RecordingParent(robotA);
            RecordingParent parentB = new RecordingParent(robotB);
            parentA.startDelegateAI(fetchA);
            parentB.startDelegateAI(fetchB);

            try {
                fetchA.cycle();
                helper.assertTrue(BoardRobotPicker.isTargetted(drop1.getUUID())
                                && !BoardRobotPicker.isTargetted(drop2.getUUID()),
                        "robot A locks the nearest drop, and only that one");

                fetchB.cycle();
                helper.assertTrue(BoardRobotPicker.isTargetted(drop1.getUUID())
                                && BoardRobotPicker.isTargetted(drop2.getUUID()),
                        "robot B cannot take A's drop — the lock steers it to the other one");
            } finally {
                fetchA.abort();
                fetchB.abort();
                drop1.discard(); // world-added drops outlive the test otherwise (cleanup bounds reach only
                drop2.discard(); // the arena corner) and a later batch's picker at these coords would eat them
            }

            helper.assertTrue(!BoardRobotPicker.isTargetted(drop1.getUUID())
                            && !BoardRobotPicker.isTargetted(drop2.getUUID()),
                    "ending a fetch must release its lock — a leaked lock freezes the drop for every robot "
                            + "until the server restarts");
            helper.succeed();
        });
    }

    /** A robot whose chunk unloads mid-fetch (or that is killed outright) never runs its fetch's
     *  {@code end()}, so 7.1.x's static lock set kept that drop's id forever: the reloaded robot — whose fetch
     *  AI does not survive a save — and every other picker then skipped the drop until the server restarted.
     *  A lock only counts while the robot holding it is still in the world. And the stale fetch object
     *  finishing later must not free the lock a live robot has since taken over. */
    public static void fetchItemLockDiesWithItsRobot(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        ItemEntity drop = dropAt(helper, rel, new ItemStack(Items.DIRT, 5));
        EntityRobot robotA = robotAt(helper, rel);
        EntityRobot robotB = robotAt(helper, rel);
        EntityRobot robotC = robotAt(helper, rel);

        whenScannable(helper, List.of(drop), () -> {
            AIRobotFetchItem fetchA = fetch(helper, robotA);
            AIRobotFetchItem fetchB = fetch(helper, robotB);
            AIRobotFetchItem fetchC = fetch(helper, robotC);
            RecordingParent parentB = new RecordingParent(robotB);
            RecordingParent parentC = new RecordingParent(robotC);
            new RecordingParent(robotA).startDelegateAI(fetchA);

            try {
                fetchA.cycle();
                helper.assertTrue(BoardRobotPicker.isTargetted(drop.getUUID()),
                        "precondition: robot A locked the drop");

                // A's chunk unloads: the entity leaves the world, its AI never ends.
                robotA.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);

                parentB.startDelegateAI(fetchB);
                fetchB.cycle();
                helper.assertTrue(parentB.ended == null,
                        "a lock held by a robot that has left the world must not strand the drop — robot B "
                                + "found nothing to fetch");

                // The stale fetch finishing now must not release the lock B holds.
                fetchA.abort();
                parentC.startDelegateAI(fetchC);
                fetchC.cycle();
                helper.assertTrue(parentC.ended == fetchC,
                        "the unloaded robot's fetch ending must not free the lock robot B took over — robot C "
                                + "went for B's drop");
            } finally {
                fetchA.abort();
                fetchB.abort();
                fetchC.abort();
                drop.discard();
            }
            helper.assertTrue(!BoardRobotPicker.isTargetted(drop.getUUID()),
                    "every fetch ended, so nothing may still lock the drop");
            helper.succeed();
        });
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
