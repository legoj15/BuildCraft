/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.ai.AIRobotBreak;
import buildcraft.robotics.entity.EntityRobot;

/** A break board that was mid-break when its robot unloaded comes back from NBT remembering the block
 *  ({@code blockFound}) and resuming its {@code AIRobotBreak} - but the unload freed the registry reservation, and
 *  another robot's search may have taken the block in the meantime. Before the fix the reloaded robot broke the
 *  block anyway, unreserved, and on finishing freed the OTHER robot's claim with the owner-unchecked
 *  {@code release(ResourceId)}, so a third robot could pile onto a block still being broken.
 *
 *  <p>The reload is real NBT: a board holding the block with a running break is written with
 *  {@code writeToNbt} and read back with {@code AIRobot.loadAI}, exactly the round trip the robot entity's
 *  save/load makes. Robots are unadded and registered by hand (as {@code SearchCellReservationTester}), and the
 *  board is cycled synchronously, so no tick or sibling test can interfere. */
public class BreakBoardReloadReservationTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    private static final BlockPos ROBOT_A_REL = new BlockPos(1, 2, 3);
    private static final BlockPos ROBOT_B_REL = new BlockPos(2, 2, 3);
    private static final BlockPos LOG_REL = new BlockPos(4, 2, 4);

    /** Two registered robots, a log, and robot A's lumberjack board reloaded mid-break on that log. */
    private static final class Reload implements AutoCloseable {
        final IRobotRegistry registry;
        final EntityRobot robotA;
        final EntityRobot robotB;
        final BlockPos logPos;
        final ResourceIdBlock log;
        final BoardRobotLumberjack board;

        Reload(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            logPos = helper.absolutePos(LOG_REL);
            log = new ResourceIdBlock(logPos);
            helper.setBlock(LOG_REL, Blocks.OAK_LOG);

            robotA = robot(helper, ROBOT_A_REL);
            robotB = robot(helper, ROBOT_B_REL);
            registry = robotA.getRegistry();

            BoardRobotLumberjack before = new BoardRobotLumberjack(robotA);
            CompoundTag self = new CompoundTag();
            self.putIntArray("indexStored", new int[] { logPos.getX(), logPos.getY(), logPos.getZ() });
            before.loadSelfFromNBT(self);
            before.startDelegateAI(new AIRobotBreak(robotA, logPos));
            CompoundTag saved = new CompoundTag();
            before.writeToNbt(saved);
            before.abortDelegateAI();

            // What the unload left behind: the registry freed every ResourceId robot A held.
            registry.releaseResources(robotA);

            AIRobot loaded = AIRobot.loadAI(saved, robotA);
            board = loaded instanceof BoardRobotLumberjack lumberjack ? lumberjack : null;
            try {
                helper.assertTrue(board != null && logPos.equals(board.blockFound())
                                && board.getDelegateAI() instanceof AIRobotBreak,
                        "precondition: the reloaded board remembers the log and resumes breaking it - got " + loaded);
                helper.assertFalse(registry.isTaken(log), "precondition: the unload left the log unreserved");
            } catch (RuntimeException e) {
                close(); // a constructor that throws is never closed by try-with-resources
                throw e;
            }
        }

        private EntityRobot robot(GameTestHelper helper, BlockPos rel) {
            EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
            Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(rel));
            robot.setPos(spawn.x, spawn.y, spawn.z);
            robot.getBattery().addPower(SEEDED_CHARGE, false);
            IRobotRegistry reg = robot.getRegistry();
            robot.setUniqueRobotId(reg.getNextRobotId());
            reg.registerRobot(robot);
            return robot;
        }

        @Override
        public void close() {
            if (board != null) {
                board.abortDelegateAI();
            }
            registry.release(log);
            registry.killRobot(robotA);
            registry.killRobot(robotB);
        }
    }

    /** Robot B's search took the log while A was unloaded. A must let it go, not break it unreserved. */
    public static void reloadedBreakBoardYieldsABlockAnotherRobotTook(GameTestHelper helper) {
        try (Reload r = new Reload(helper)) {
            helper.assertTrue(r.registry.take(r.log, r.robotB), "robot B claims the log while A is away");

            // preempt alone: the update() a full cycle runs next fetches a tool through the dimension-wide station
            // registry, which could reserve a sibling test's station.
            r.board.preempt(r.board.getDelegateAI());

            helper.assertTrue(r.registry.robotIdTaking(r.log) == r.robotB.getRobotId(),
                    "robot B still holds the log");
            helper.assertTrue(r.board.blockFound() == null,
                    "the reloaded board must drop a block another robot now holds");
            helper.assertFalse(r.board.getDelegateAI() instanceof AIRobotBreak,
                    "...and stop breaking it - two robots would otherwise break the same block");
            helper.succeed();
        }
    }

    /** Nobody took the log while A was unloaded: A re-claims it and carries on breaking. */
    public static void reloadedBreakBoardReclaimsAFreeBlock(GameTestHelper helper) {
        try (Reload r = new Reload(helper)) {
            r.board.cycle();

            helper.assertTrue(r.registry.robotIdTaking(r.log) == r.robotA.getRobotId(),
                    "the reloaded board must re-reserve its block - unreserved, any other search can take it");
            helper.assertTrue(r.logPos.equals(r.board.blockFound()), "the board keeps its block");
            helper.assertTrue(r.board.getDelegateAI() instanceof AIRobotBreak, "...and keeps breaking it");
            helper.succeed();
        }
    }

    /** The finish path itself: a board whose remembered block is held by another robot releases it when its job
     *  ends. That release must not free the other robot's claim. */
    public static void finishedBreakDoesNotFreeAnotherRobotsClaim(GameTestHelper helper) {
        try (Reload r = new Reload(helper)) {
            helper.assertTrue(r.registry.take(r.log, r.robotB), "robot B claims the log while A is away");

            r.board.releaseBlockFound();

            helper.assertTrue(r.registry.robotIdTaking(r.log) == r.robotB.getRobotId(),
                    "robot A's release must leave robot B's claim alone - otherwise a third robot can take a block "
                            + "B is still breaking");
            helper.succeed();
        }
    }
}
