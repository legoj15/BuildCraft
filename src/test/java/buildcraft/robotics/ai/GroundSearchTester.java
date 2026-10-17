/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/** {@link AIRobotSearchRandomGroundBlock}, the bomber's "somewhere to drop" search, against a real level. It picks
 *  random columns and scans them top-down; a column in an unloaded chunk must be skipped (re-picked, counting as an
 *  attempt) and never read, because reading it loads or generates the chunk on the server thread. A zone with
 *  nothing to pick from must fail the search, not throw. Unadded robot, cycled synchronously; nothing is placed. */
public class GroundSearchTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    public static void groundSearchSkipsUnloadedColumns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityRobot robot = robot(helper);
        boolean[] unloadedRead = { false };
        int[] reads = { 0 };
        // A range so wide that practically every column it picks lies in a chunk nobody has loaded.
        AIRobotSearchRandomGroundBlock search = new AIRobotSearchRandomGroundBlock(robot, 1_000_000, pos -> {
            reads[0]++;
            if (!level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
                unloadedRead[0] = true;
            }
            return false;
        }, null);
        Parent parent = new Parent(robot);
        parent.startDelegateAI(search);
        for (int i = 0; i < 50 && parent.ended == null && !unloadedRead[0]; i++) {
            parent.cycle();
        }

        helper.assertFalse(unloadedRead[0], "the ground search read a column in an unloaded chunk");
        helper.assertTrue(parent.ended == search,
                "re-picking unloaded columns counts against the attempt limit, so the search ends - read " + reads[0]
                        + " cells");
        helper.assertFalse(search.success(), "...with nothing found");
        helper.succeed();
    }

    public static void groundSearchInAnEmptyZoneFails(GameTestHelper helper) {
        EntityRobot robot = robot(helper);
        IZone nothing = new IZone() {
            @Override
            public double distanceTo(BlockPos pos) {
                return Double.MAX_VALUE;
            }

            @Override
            public double distanceToSquared(BlockPos pos) {
                return Double.MAX_VALUE;
            }

            @Override
            public boolean contains(Vec3 point) {
                return false;
            }

            @Override
            public BlockPos getRandomBlockPos(Random rand) {
                return null;
            }
        };
        AIRobotSearchRandomGroundBlock search = new AIRobotSearchRandomGroundBlock(robot, 100, pos -> true, nothing);
        Parent parent = new Parent(robot);
        parent.startDelegateAI(search);
        parent.cycle();

        helper.assertTrue(parent.ended == search && !parent.aborted,
                "a zone with nothing to pick from ends the search, rather than throwing (and being aborted) every tick");
        helper.assertFalse(search.success(), "...with nothing found");
        helper.succeed();
    }

    private static EntityRobot robot(GameTestHelper helper) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 3)));
        robot.setPos(spawn.x, spawn.y, spawn.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        return robot;
    }

    /** Records how its delegate went. */
    private static final class Parent extends AIRobot {
        AIRobot ended;
        boolean aborted;

        Parent(EntityRobot robot) {
            super(robot);
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            ended = ai;
        }

        @Override
        public void delegateAIAborted(AIRobot ai) {
            aborted = true;
        }
    }
}
