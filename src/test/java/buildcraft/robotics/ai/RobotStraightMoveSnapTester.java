/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The arrival snap of {@link AIRobotStraightMoveTo} against a REAL robot. The unit test pins the AI's half against a
 * mock; this pins that {@code IRobotAccess#setPos} - a default no-op on the interface - really lands on vanilla's
 * {@code Entity#setPos} for the concrete robot (the class method must win), because a snap that silently resolved to
 * the no-op would pass every mock test and still leave robots a step off target in the game.
 *
 * <p>The robot is never added to the level; the entity tick that integrates velocity is stood in for by hand, one
 * 0.1-block step per AI cycle, so there is nothing to gate on a tick.
 */
public class RobotStraightMoveSnapTester {

    public static void straightMoveSnapsARealRobotOntoItsTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        // 3.37 blocks along x and 1.23 along z: not a multiple of the 0.1 step, so the last step overshoots.
        Vec3 target = start.add(3.37, 0, 1.23);
        robot.setPos(start.x, start.y, start.z);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);

        boolean[] ended = { false };
        AIRobotStraightMoveTo move = new AIRobotStraightMoveTo(robot, target.x, target.y, target.z) {
            @Override
            public void end() {
                ended[0] = true;
            }
        };
        move.start();
        for (int step = 0; step < 200 && !ended[0]; step++) {
            Vec3 velocity = robot.getDeltaMovement();
            robot.setPos(robot.getX() + velocity.x, robot.getY() + velocity.y, robot.getZ() + velocity.z);
            move.cycle();
        }

        helper.assertTrue(ended[0], "the flight ended");
        helper.assertTrue(robot.position().distanceToSqr(target) < 1.0E-12,
                "the robot must stand exactly on the target " + target + ", not a step short or past it: it is at "
                        + robot.position());
        helper.assertTrue(robot.getDeltaMovement().equals(Vec3.ZERO), "...and is no longer moving");
        helper.succeed();
    }
}
