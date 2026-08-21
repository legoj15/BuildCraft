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
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.entity.EntityRobot;

/** Ph5 game test for {@link AIRobotSearchBlock}, driven SYNCHRONOUSLY on an unadded robot: the search is
 *  pure block computation (a {@code PathFindingSearch} over the arena), so no entity ticking is needed —
 *  the AI is hand-cycled exactly as the entity's tick would. The robot IS registered with the dimension's
 *  live registry: {@code isTaken} auto-releases a resource whose taker is not a loaded robot, so an
 *  unregistered robot could never pin a reservation. The filter is pinned to the log's own position: in
 *  the arena grid the sibling {@link AIRobotSearchAndGotoBlock} flight test lands exactly one cell (6
 *  blocks) west and places an identical oak log at the same relative (5,2,3) — a nearer legal target than
 *  the log this test places, so a plain "is an oak log" filter deterministically finds the neighbour's
 *  log. The search itself is correct; the test pins the block it owns. Red until the AI step lands the
 *  scanner wiring (the skeleton terminates on the first cycle). */
public class SearchBlockTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    public static void findsTheNearestMatchingBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(1, 2, 2);
        BlockPos logRel = new BlockPos(5, 2, 3);
        BlockPos logPos = helper.absolutePos(logRel);
        helper.setBlock(logRel, Blocks.OAK_LOG);

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(spawn.x, spawn.y, spawn.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);

        IRobotRegistry registry = robot.getRegistry();
        helper.assertTrue(registry != null, "the dimension's robot registry must exist");
        robot.setUniqueRobotId(registry.getNextRobotId());
        registry.registerRobot(robot);

        AIRobotSearchBlock search = new AIRobotSearchBlock(robot, false,
                pos -> level.getBlockState(pos).is(Blocks.OAK_LOG) && pos.equals(logPos), 32);

        try {
            // Bounded, not tick-based: the search is synchronous pathfinding, each cycle advancing it by its
            // iteration budget. 20000 cycles is orders of magnitude past a 4-block search even at the
            // smallest per-cycle budget; the skeleton (red baseline) burns through them in a terminate()
            // each and then fails the success assertion.
            for (int i = 0; i < 20000 && !search.success(); i++) {
                search.cycle();
            }

            helper.assertTrue(search.success(),
                    "the search must find the oak log four blocks away");
            helper.assertTrue(search.blockFound != null && search.blockFound.equals(logPos),
                    "the found block must be the log's own position, got " + search.blockFound);
            helper.assertTrue(search.takeResource(),
                    "the found block must be reserved for this robot");
            helper.assertTrue(registry.isTaken(new ResourceIdBlock(logPos)),
                    "the reservation must stick in the live registry — another robot must see it as taken");
        } finally {
            search.abort();
            registry.releaseResources(robot);
            registry.killRobot(robot);
        }
        helper.succeed();
    }
}
