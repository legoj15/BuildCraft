/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.test.FarChunk;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/** {@link AIRobotSearchRandomGroundBlock}, the bomber's "somewhere to drop" search, against a real level. It picks
 *  random columns and scans them top-down; a column in an unloaded chunk must be skipped (re-picked, counting as an
 *  attempt) and never read, because reading it loads or generates the chunk on the server thread. A zone with
 *  nothing to pick from must fail the search, not throw. Unadded robot, cycled synchronously; nothing is placed. */
public class GroundSearchTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;
    /** Chunks east and south of the arena's, apart from every other test's far chunk (see {@link FarChunk}). */
    private static final int QUEUED_CHUNK_OFFSET = 1666;

    public static void groundSearchSkipsUnloadedColumns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityRobot robot = robot(helper);
        boolean[] unloadedRead = { false };
        int[] reads = { 0 };
        // A range so wide that practically every column it picks lies in a chunk nobody has loaded.
        AIRobotSearchRandomGroundBlock search = new AIRobotSearchRandomGroundBlock(robot, 1_000_000, pos -> {
            reads[0]++;
            // In memory, the production gate's notion (Level.hasChunk only tests the ticket level).
            if (!BlockUtil.isChunkPresent(level, pos)) {
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

    /**
     * A column whose chunk is only queued to load (a holder at the ticket level of a loaded chunk, not in memory): the
     * search must skip it like any unloaded one. {@code Level.hasChunk} answers yes here, so a gate built on it scanned
     * the column and the first read loaded the chunk on the server thread. The zone offers nothing but a cell of that
     * chunk and the filter accepts anything, so a search that reads the column ends with a find; one that skips it
     * runs out of attempts.
     */
    public static void groundSearchSkipsAQueuedChunk(GameTestHelper helper) {
        FarChunk chunk = FarChunk.queue(helper, QUEUED_CHUNK_OFFSET);
        try {
            EntityRobot robot = robot(helper);
            int[] reads = { 0 };
            AIRobotSearchRandomGroundBlock search = new AIRobotSearchRandomGroundBlock(robot, 100, pos -> {
                reads[0]++;
                return true;
            }, zonePicking(chunk.pos(8, 64, 8)));
            Parent parent = new Parent(robot);
            parent.startDelegateAI(search);
            for (int i = 0; i < 50 && parent.ended == null && reads[0] == 0; i++) {
                parent.cycle();
            }

            helper.assertTrue(reads[0] == 0, "the ground search read a column in a chunk that is only queued to load");
            helper.assertTrue(parent.ended == search && !parent.aborted,
                    "re-picking the queued column counts against the attempt limit, so the search ends");
            helper.assertFalse(search.success(), "...with nothing found");
            helper.assertTrue(chunk.isAbsent(0, 0), "the search must not have loaded the chunk");
            helper.succeed();
        } finally {
            chunk.release();
        }
    }

    public static void groundSearchInAnEmptyZoneFails(GameTestHelper helper) {
        EntityRobot robot = robot(helper);
        AIRobotSearchRandomGroundBlock search = new AIRobotSearchRandomGroundBlock(robot, 100, pos -> true,
                zonePicking(null));
        Parent parent = new Parent(robot);
        parent.startDelegateAI(search);
        parent.cycle();

        helper.assertTrue(parent.ended == search && !parent.aborted,
                "a zone with nothing to pick from ends the search, rather than throwing (and being aborted) every tick");
        helper.assertFalse(search.success(), "...with nothing found");
        helper.succeed();
    }

    /** A zone whose every random pick is {@code only} (null: it has nothing to pick). Only the pick is used by the
     *  search; the rest answers "nowhere". */
    private static IZone zonePicking(BlockPos only) {
        return new IZone() {
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
                return only;
            }
        };
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
