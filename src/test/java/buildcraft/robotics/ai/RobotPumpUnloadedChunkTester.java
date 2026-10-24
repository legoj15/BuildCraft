/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.FarChunk;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.boards.BoardRobotPump;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The pump robot must never load a chunk: taking a bucket updates the cells around the source, and a read of a cell in a
 * chunk that is not in memory loads (or generates) it on the server thread. The AI leaves a source whose drain would
 * reach an absent chunk alone, and the board's search does not offer one.
 *
 * <p>The source stands on the last column of a chunk loaded alone ({@link FarChunk}), so the chunk east of it, across the
 * border, is absent: draining the source sets it to air, and the update that follows reads the cell across the border.
 * The robot is driven by hand on an unadded entity, as in {@link RobotPumpProtectionTester}.
 */
public class RobotPumpUnloadedChunkTester {

    /** Chunks east and south of the arena's, apart from every other test's far chunk (see {@link FarChunk}). */
    private static final int CHUNK_OFFSET = 1444;
    private static final int BUCKET = 1000;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static EntityRobot unaddedRobot(GameTestHelper helper) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);
        return robot;
    }

    /** Cycles a pump AI until it has pumped, been refused, or run out of cycles (its 40-cycle wait included). */
    private static void runPumpAi(AIRobotPumpBlock pumper) {
        for (int cycles = 0; cycles < 120 && pumper.pumped == 0 && !pumper.refused(); cycles++) {
            pumper.cycle();
        }
    }

    /**
     * With the chunk across the border absent, the robot is not offered the source, and sent to it anyway it pumps
     * nothing, is not refused (the chunk can load later) and leaves the chunk absent. Once the chunk is loaded the
     * source is a candidate again and the same robot pumps its bucket.
     */
    public static void pumpLeavesASourceBesideAnAbsentChunkAlone(GameTestHelper helper) {
        FarChunk chunk = null;
        BlockPos well = null;
        try {
            ServerLevel level = helper.getLevel();
            chunk = FarChunk.load(helper, CHUNK_OFFSET);
            well = chunk.pos(15, level.getMinY() + 40, 8);
            chunk.setRaw(well, Blocks.WATER.defaultBlockState());
            check(chunk.isAbsent(1, 0), "precondition: the chunk across the border must be absent");
            EntityRobot robot = unaddedRobot(helper);
            BoardRobotPump board = new BoardRobotPump(robot);

            check(!board.isSearchCandidate(level, well),
                    "the board must not offer a source whose drain would read an absent chunk");
            AIRobotPumpBlock sent = new AIRobotPumpBlock(robot, well);
            runPumpAi(sent);
            check(sent.pumped == 0 && !sent.success(), "the robot must pump nothing beside an absent chunk");
            check(!sent.refused(), "an absent chunk is not a refusal: the board must not give the source up for good");
            check(EntityArenaUtil.tankAmount(robot.getFluidHandler()) == 0, "the robot's tank stays empty");
            check(level.getFluidState(well).isSource(), "the source is left alone");
            check(chunk.isAbsent(1, 0),
                    "the pump must not load the chunk across the border: the block update of the drain reads it");

            chunk.loadBeside(1, 0);
            check(board.isSearchCandidate(level, well), "liveness: with the chunk loaded the source is a candidate");
            AIRobotPumpBlock allowed = new AIRobotPumpBlock(robot, well);
            runPumpAi(allowed);
            check(allowed.pumped == BUCKET, "liveness: with the chunk loaded the robot pumps its bucket, got "
                    + allowed.pumped);
            check(level.getFluidState(well).isEmpty(), "and the source is drained");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            if (chunk != null && well != null) {
                chunk.clearRaw(well);
            }
        }
    }
}
