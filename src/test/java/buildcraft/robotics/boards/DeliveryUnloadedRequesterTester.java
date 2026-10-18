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
 * load the chunk.
 *
 * <p>Before this was handled, the delivery claimed the requester, the flight failed at the loaded edge, the order was
 * given up and the next cycle disposed of the still-carried cargo (dropping it when no station took it).
 *
 * <p>Driven synchronously on a board the test builds itself (the robot is a body that is never added to the world),
 * so nothing but the cycles below moves it.
 */
public class DeliveryUnloadedRequesterTester {

    /** Far enough from the arena grid that its chunk is never loaded during the run. */
    private static final int UNLOADED_OFFSET = 4096;

    public static void deliveryKeepsCargoForAnUnloadedRequester(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 3, 2);
        BlockPos far = helper.absolutePos(robotRel).offset(UNLOADED_OFFSET, 0, 0);
        helper.assertFalse(level.isLoaded(far), "precondition: the requester's chunk is not loaded");

        // The requester's station, as read back from the save: registered, in an unloaded chunk.
        DockingStationPipe requester = new DockingStationPipe();
        CompoundTag record = new CompoundTag();
        record.putIntArray("pos", new int[] { far.getX(), far.getY(), far.getZ() });
        record.putByte("side", (byte) Direction.UP.ordinal());
        requester.readFromNbt(record);
        requester.world = level;
        RobotManager.registryProvider.getRegistry(level).registerStation(requester);

        ZonePinnedRobot robot = new ZonePinnedRobot(helper, null);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);
        robot.setUniqueRobotId(RobotManager.registryProvider.getRegistry(level).getNextRobotId());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 4));

        BoardRobotDelivery board = new BoardRobotDelivery(robot);
        try {
            StackRequest order = new StackRequest(null, 0, new ItemStack(Items.DIAMOND, 4));
            order.setStation(requester);
            CompoundTag orderTag = new CompoundTag();
            order.writeToNBT(orderTag);
            CompoundTag boardTag = new CompoundTag();
            boardTag.put("currentRequest", orderTag);
            board.loadSelfFromNBT(boardTag);
            board.preempt(null);
            helper.assertTrue(board.currentRequest() != null, "precondition: the board holds the order");

            // The delivery leg, loaded and holding the goods - as a reloaded robot resumes it.
            board.startDelegateAI(new AIRobotDeliverRequested(robot, board.currentRequest()));
            for (int i = 0; i < 5; i++) {
                board.cycle();
            }
            assertWaiting(helper, robot, board, far);

            // The wait ends (the sleep's timer): the robot tries the delivery again rather than disposing of the goods.
            board.abortDelegateAI();
            board.cycle();
            helper.assertFalse(board.getDelegateAI() instanceof AIRobotDisposeItems,
                    "waking up still holding an order's goods, the robot must not dispose of them");
            assertWaiting(helper, robot, board, far);
            helper.succeed();
        } finally {
            board.abortDelegateAI();
            board.end();
            RobotManager.registryProvider.getRegistry(level).removeStation(requester);
        }
    }

    private static void assertWaiting(GameTestHelper helper, ZonePinnedRobot robot, BoardRobotDelivery board,
                                      BlockPos far) {
        helper.assertTrue(ItemStack.matches(robot.getInventoryStack(0), new ItemStack(Items.DIAMOND, 4)),
                "the robot must keep the order's goods while the requester is away, holding "
                        + robot.getInventoryStack(0));
        helper.assertTrue(board.currentRequest() != null, "the robot must keep the order while the requester is away");
        helper.assertTrue(board.getDelegateAI() instanceof AIRobotGotoSleep,
                "with the requester away the robot waits (sleeps), got " + board.getDelegateAI());
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                EntityArenaUtil.cellBounds(helper)).isEmpty(), "nothing may be dropped");
        helper.assertFalse(helper.getLevel().isLoaded(far), "nothing may load the requester's chunk");
    }
}
