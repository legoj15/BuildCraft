/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.ai.AIRobotStripesHandler;
import buildcraft.robotics.ai.MockRobotAccess;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The Stripes board's give-up path against a real robot: after {@link BoardRobotStripes#GIVE_UP_AFTER_FAILED_USES}
 * failed uses in a row the held item leaves the hand through the robot's real transfer slots and the disposal AI
 * runs to the end - with no station anywhere that means the item is dropped at the robot's feet, not kept for
 * ever and not deleted.
 *
 * <p>The robot is never added to the level (nothing ticks it behind the test's back): the board is driven by hand.
 * Two things still guard it, as in {@code RobotDisposeItemsTester}: an empty station registry keeps "no station
 * anywhere" true while concurrent tests register stations that accept items (one would turn the spill into a flight
 * the unadded robot never finishes), and the spill and the scan for it wait for the robot's chunk to be
 * entity-ticking (an item dropped into a chunk whose entities are not loaded yet is invisible to the scan, and
 * leaks).
 */
public class RobotStripesGiveUpTester {

    public static void stripesBoardSpillsAnItemItGivesUpOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 2, 2);
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 3));
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        List<ItemEntity> dropped = new ArrayList<>();
        EntityArenaUtil.tickUntil(helper, 80, () -> EntityArenaUtil.isEntityTicking(helper, robotRel), () -> {
            IRobotRegistry noStations = new MockRobotAccess.InertRobotRegistry();
            EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level) {
                @Override
                public IRobotRegistry getRegistry() {
                    return noStations;
                }
            };
            Vec3 at = Vec3.atCenterOf(helper.absolutePos(robotRel));
            robot.setPos(at.x, at.y, at.z);
            robot.getBattery().addPower(5000L * MjAPI.MJ, false);
            AABB dropZone = new AABB(at, at).inflate(1.5);
            for (ItemEntity stale : level.getEntitiesOfClass(ItemEntity.class, dropZone)) {
                stale.discard();
            }

            robot.setItemInUse(new ItemStack(Items.STICK, 3));
            BoardRobotStripes board = new BoardRobotStripes(robot);

            for (int use = 1; use <= BoardRobotStripes.GIVE_UP_AFTER_FAILED_USES; use++) {
                board.delegateAIEnded(new AIRobotStripesHandler(robot, target) {
                    {
                        setSuccess(false);
                    }
                });
            }
            // The disposal is a chain of searches; drive it until the board is idle (no station exists, so it ends
            // in a handful of cycles with the cargo on the floor).
            for (int guard = 0; guard < 400 && board.getDelegateAI() != null; guard++) {
                board.cycle();
            }

            dropped.addAll(level.getEntitiesOfClass(ItemEntity.class, dropZone,
                    entity -> entity.getItem().is(Items.STICK)));
            helper.assertTrue(board.getDelegateAI() == null, "the disposal ran to its end");
            helper.assertTrue(robot.getHeldItem().isEmpty(), "the item left the robot's hand, holds " + robot.getHeldItem());
            helper.assertFalse(robot.containsItems(), "...and its cargo slots are empty again");

            int sticks = dropped.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(sticks == 3, "all three sticks were dropped at the robot's feet, found " + sticks);
            helper.assertFalse(board.worthEquipping(new ItemStack(Items.STICK)),
                    "and the board will not fetch a stick again");
            helper.succeed();
        }, "the robot's chunk never became entity-ticking", () -> dropped.forEach(ItemEntity::discard));
    }
}