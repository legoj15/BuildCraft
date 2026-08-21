/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/** Ph5 game test for {@link AIRobotSearchAndGotoBlock}: the composed search-then-fly, driven SYNCHRONOUSLY
 *  on an unadded robot. The goto leg moves the robot by writing its delta movement every cycle (exactly as
 *  {@code AIRobotGoto} does for the entity tick to apply), so the test applies the same delta by hand —
 *  {@code robot.move(MovementType.SELF, delta)} — after each cycle. The log is solid, so the robot stops at
 *  its face rather than in its cell: the arrival gate is the same 1.5-block radius the picker E2E uses for a
 *  reached item. Red until the AI step lands the real search-then-goto logic. */
public class SearchAndGotoBlockTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    public static void reachesTheFoundBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(1, 2, 2);
        BlockPos logRel = new BlockPos(5, 2, 3);
        Vec3 logCenter = Vec3.atCenterOf(helper.absolutePos(logRel));
        helper.setBlock(logRel, Blocks.OAK_LOG);

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(spawn.x, spawn.y, spawn.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);

        AIRobotSearchAndGotoBlock ai = new AIRobotSearchAndGotoBlock(robot, false,
                pos -> level.getBlockState(pos).is(Blocks.OAK_LOG));

        try {
            // Search cycles (synchronous pathfinding) + ~40 goto cycles at 0.1 blocks/cycle over 4 blocks.
            for (int i = 0; i < 2000 && robot.position().distanceToSqr(logCenter) >= 2.25; i++) {
                ai.cycle();
                robot.move(MoverType.SELF, robot.getDeltaMovement());
            }

            helper.assertTrue(ai.success(),
                    "the search-and-goto must end with a found block");
            helper.assertTrue(robot.position().distanceToSqr(logCenter) < 2.25,
                    "the robot must FLY to the log (the search alone would succeed from the spawn): robot "
                            + "at " + robot.position() + " but log at " + logCenter);
        } finally {
            ai.abort();
        }
        helper.succeed();
    }
}
