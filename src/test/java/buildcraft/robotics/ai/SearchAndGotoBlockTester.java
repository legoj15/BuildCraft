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
 *  on an unadded robot. The search reports its real approach path (7.1.x's board-facing default,
 *  {@code maxDistanceToEnd = 0}: the pathfind ends on the target cell, and the target cell itself is
 *  dropped), and the goto leg flies the robot down it cell by cell — the robot parks one cell short of the
 *  log, exactly 1.0 from its centre. The test applies the delta the AI writes by hand
 *  ({@code robot.move(MoverType.SELF, delta)}) after each cycle, the way the entity tick would. The arrival
 *  gate is 1.5 — the same radius the picker E2E uses for a reached item — so it passes with the one-cell
 *  margin to spare. This test also pins the double-precision flight targets: the game-test arenas sit at
 *  |z| ≈ 1.4e7, where float's ulp is 1.0 and the +0.5 cell-centre offset would be dropped. The test runs in
 *  a PRIVATE test environment (the manifest's "environment" field): the search scans 64 blocks, and in the
 *  shared minecraft:default world a neighbouring test's leftover oak log — two cells away — is a legal,
 *  closer target than the log this test places, so the flight must be hermetic. */
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
            // A few search cycles (synchronous pathfinding) + ~40 goto cycles at 0.1 blocks/cycle over 4
            // blocks — 2000 is generous headroom, not a prediction.
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
