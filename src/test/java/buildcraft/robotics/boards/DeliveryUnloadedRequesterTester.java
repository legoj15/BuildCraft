/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.RobotManager;

import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.StackRequest;
import buildcraft.robotics.ZonePinnedRobot;
import buildcraft.robotics.ai.AIRobotDeliverRequested;
import buildcraft.robotics.ai.AIRobotDisposeItems;
import buildcraft.robotics.ai.AIRobotGotoSleep;

/**
 * A Delivery robot holding the goods for an order whose requester is in a chunk that is not loaded: it unloaded while
 * the robot was on its way, or the robot was reloaded with the order saved and nobody is near the requester. No flight
 * can reach it (robots path only through loaded cells), but the station is not gone, so the order can still be filled
 * once the chunk is back. The robot must keep the order and the cargo and wait, not fly, not drop the goods, and not
 * load the chunk - across a reload too. A requester that was destroyed, or that forbids the robot, is not "away": the
 * order is given up as any lost order is.
 *
 * <p>Driven synchronously on a board the test builds itself (the robot is a body that is never added to the world),
 * so nothing but the cycles below moves it. Its zones are the test's cell, so a disposal finds no station and drops
 * the goods in the cell, where the test can see them.
 */
public class DeliveryUnloadedRequesterTester {

    /** Far enough from the arena grid that its chunk is never loaded during the run. */
    private static final int UNLOADED_OFFSET = 4096;
    private static final BlockPos ROBOT_REL = new BlockPos(2, 3, 2);

    /** A robot with a delivery board holding (and having claimed) an order for 4 diamonds, carried, whose requester
     *  is {@code requester}: a registered station in an unloaded chunk. */
    private static final class Rig implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final IRobotRegistry registry;
        final BlockPos far;
        final DockingStationPipe requester;
        final ZonePinnedRobot robot;
        BoardRobotDelivery board;

        Rig(GameTestHelper helper, DockingStationPipe requester) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.registry = RobotManager.registryProvider.getRegistry(level);
            this.far = helper.absolutePos(ROBOT_REL).offset(UNLOADED_OFFSET, 0, 0);
            helper.assertFalse(level.isLoaded(far), "precondition: the requester's chunk is not loaded");

            // The requester's station, as read back from the save: registered, in an unloaded chunk.
            this.requester = requester;
            CompoundTag record = new CompoundTag();
            record.putIntArray("pos", new int[] { far.getX(), far.getY(), far.getZ() });
            record.putByte("side", (byte) Direction.UP.ordinal());
            requester.readFromNbt(record);
            requester.world = level;
            registry.registerStation(requester);

            robot = new ZonePinnedRobot(helper, null);
            robot.getBattery().addPower(5000L * MjAPI.MJ, false);
            robot.setUniqueRobotId(registry.getNextRobotId());
            registry.registerRobot(robot);
            Vec3 pos = Vec3.atCenterOf(helper.absolutePos(ROBOT_REL));
            robot.setPos(pos.x, pos.y, pos.z);
            robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 4));

            board = new BoardRobotDelivery(robot);
            StackRequest order = new StackRequest(null, 0, new ItemStack(Items.DIAMOND, 4));
            order.setStation(requester);
            CompoundTag orderTag = new CompoundTag();
            order.writeToNBT(orderTag);
            CompoundTag boardTag = new CompoundTag();
            boardTag.put("currentRequest", orderTag);
            board.loadSelfFromNBT(boardTag);
            board.preempt(null);
            helper.assertTrue(board.currentRequest() != null, "precondition: the board holds the order");
        }

        /** The delivery leg, loaded and holding the goods - as a reloaded robot resumes it - then a few cycles. */
        void deliver() {
            board.startDelegateAI(new AIRobotDeliverRequested(robot, board.currentRequest()));
            for (int i = 0; i < 5; i++) {
                board.cycle();
            }
        }

        /** The board ends (a recharge or a gate override aborting it: its claims go back, the order and its cached
         *  station stay), so the next cycle's preempt has to RECLAIM the order through {@code OrderClaimer.reclaim}.
         *  Without this the robot still holds its claims when it wakes and reclaim is never asked. */
        void releaseClaims() {
            board.end();
        }

        /** The wait ends (the sleep's timer), and the board cycles on. */
        void wake(int cycles) {
            board.abortDelegateAI();
            for (int i = 0; i < cycles; i++) {
                board.cycle();
            }
        }

        boolean holdsGoods() {
            return ItemStack.matches(robot.getInventoryStack(0), new ItemStack(Items.DIAMOND, 4));
        }

        void assertWaiting(String when) {
            helper.assertTrue(holdsGoods(), when + ": the robot must keep the order's goods while the requester is "
                    + "away, holding " + robot.getInventoryStack(0));
            helper.assertTrue(board.currentRequest() != null,
                    when + ": the robot must keep the order while the requester is away");
            helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoSleep,
                    when + ": with the requester away the robot waits (sleeps), got " + board.getDelegateAI());
            helper.assertTrue(droppedInCell() == 0, when + ": nothing may be dropped");
            helper.assertFalse(level.isLoaded(far), when + ": nothing may load the requester's chunk");
        }

        void assertGaveUp(String why) {
            helper.assertTrue(board.currentRequest() == null, "the robot must give up an order whose requester " + why);
            helper.assertFalse(holdsGoods(), "...and dispose of its goods as for any lost order, on the first cycle it "
                    + "wakes, before any delivery to it is tried");
            helper.assertTrue(droppedInCell() > 0, "...which, with no station in its zone to take them, drops them");
            helper.assertFalse(level.isLoaded(far), "nothing may load the requester's chunk");
        }

        int droppedInCell() {
            return level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper)).size();
        }

        @Override
        public void close() {
            board.abortDelegateAI();
            board.end();
            if (registry.getStation(requester.getPos(), requester.side()) == requester) {
                registry.removeStation(requester);
            }
            registry.killRobot(robot);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, EntityArenaUtil.cellBounds(helper))) {
                item.discard();
            }
        }
    }

    /** The robot waits with the goods, tries again when it wakes, and still does after a reload mid-wait (the wait is
     *  saved with the board, and the reload frees every reservation as an unload does). */
    public static void deliveryKeepsCargoForAnUnloadedRequester(GameTestHelper helper) {
        try (Rig rig = new Rig(helper, new DockingStationPipe())) {
            rig.deliver();
            rig.assertWaiting("after the delivery attempt");

            rig.wake(1);
            helper.assertFalse(rig.board.getDelegateAI() instanceof AIRobotDisposeItems,
                    "waking up still holding an order's goods, the robot must not dispose of them");
            rig.assertWaiting("after waking up");

            CompoundTag saved = new CompoundTag();
            rig.board.writeToNbt(saved);
            rig.board.abortDelegateAI();
            rig.registry.releaseResources(rig.robot);
            AIRobot loaded = AIRobot.loadAI(saved, rig.robot);
            helper.assertTrue(loaded instanceof BoardRobotDelivery, "precondition: the board reloads, got " + loaded);
            rig.board = (BoardRobotDelivery) loaded;
            rig.wake(1);
            rig.assertWaiting("after a reload mid-wait");
            helper.succeed();
        }
    }

    /** The requester was destroyed while its chunk was away (its station left the registry): the order can never be
     *  filled, so it is not "away", and the robot gives it up rather than wait for it forever. The claims are given
     *  back first, so the wake reaches {@code OrderClaimer.reclaim} holding a station that is no longer registered:
     *  its identity guard gives the order up there, in the first cycle. */
    public static void deliveryGivesUpAnOrderWhoseRequesterWasDestroyed(GameTestHelper helper) {
        try (Rig rig = new Rig(helper, new DockingStationPipe())) {
            rig.deliver();
            rig.assertWaiting("after the delivery attempt");

            rig.releaseClaims();
            rig.registry.removeStation(rig.requester);
            rig.wake(1);
            rig.assertGaveUp("was destroyed");
            helper.succeed();
        }
    }

    /** The requester forbids the robot (a Forbid Robot action added during the wait): the robot may not deliver there,
     *  so it gives the order up when it wakes, as a station search would have skipped the station. Its claims are
     *  given back first, so it is {@code OrderClaimer.reclaim}'s forbid guard that gives the order up. */
    public static void deliveryGivesUpAnOrderWhoseRequesterForbidsTheRobot(GameTestHelper helper) {
        boolean[] forbidden = { false };
        DockingStationPipe requester = new DockingStationPipe() {
            @Override
            public boolean isRobotForbidden(IRobotAccess robot) {
                return forbidden[0];
            }
        };
        try (Rig rig = new Rig(helper, requester)) {
            rig.deliver();
            rig.assertWaiting("after the delivery attempt");

            rig.releaseClaims();
            forbidden[0] = true;
            rig.wake(1);
            rig.assertGaveUp("forbids the robot");
            helper.succeed();
        }
    }
}
