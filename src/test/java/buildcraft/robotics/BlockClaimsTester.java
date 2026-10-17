/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.api.robots.RobotManager;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.robotics.path.PathFindingSearch;

/**
 * A block a robot works is claimed in one of two ledgers: as a search cell while a robot's search has locked onto it
 * (runtime-only, freed with the robot), then as a {@code ResourceIdBlock} job claim once the robot's AI collects it.
 * {@link BlockClaims} is the one place that pairs them, and the invariant it keeps is that a block is never held by
 * two robots across the two: a search never reserves a block another robot works, a job is never claimed (or
 * re-claimed after a reload) past another robot's search cell, and the hand-over moves the cell into the job ledger
 * rather than copying it.
 *
 * <p>Two registered robots that never enter the world, driven synchronously; everything is released before the test
 * ends, because the registry is shared by every arena.
 */
public class BlockClaimsTester {

    /** Every robot that holds {@code pos} in either ledger. */
    private static Set<Long> holders(IRobotRegistry registry, BlockPos pos, EntityRobot... robots) {
        Set<Long> holders = new HashSet<>();
        for (EntityRobot robot : robots) {
            if (registry.getBlockReservations(robot.getRobotId()).contains(pos)) {
                holders.add(robot.getRobotId());
            }
        }
        long jobHolder = registry.robotIdTaking(new ResourceIdBlock(pos));
        if (jobHolder != EntityRobotBase.NULL_ROBOT_ID) {
            holders.add(jobHolder);
        }
        return holders;
    }

    private static void requireOneHolderAtMost(GameTestHelper helper, IRobotRegistry registry, BlockPos pos,
            String step, EntityRobot a, EntityRobot b) {
        Set<Long> holders = holders(registry, pos, a, b);
        helper.assertTrue(holders.size() <= 1, "after " + step + ": the block is held by " + holders.size()
                + " robots across the two ledgers - " + holders);
        helper.assertTrue(BlockClaims.isClaimed(registry, pos) == !holders.isEmpty(),
                "after " + step + ": isClaimed must answer for both ledgers");
    }

    private static EntityRobot robot(GameTestHelper helper, IRobotRegistry registry) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1)));
        robot.setPos(pos.x, pos.y, pos.z);
        registry.registerRobot(robot);
        return robot;
    }

    public static void aBlockIsNeverHeldByTwoRobotsAcrossTheLedgers(GameTestHelper helper) {
        IRobotRegistry registry = RobotManager.registryProvider.getRegistry(helper.getLevel());
        EntityRobot a = robot(helper, registry);
        EntityRobot b = robot(helper, registry);
        long idA = a.getRobotId();
        long idB = b.getRobotId();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        PathFindingSearch.Reservations searchA = BlockClaims.searchCells(registry, idA);
        PathFindingSearch.Reservations searchB = BlockClaims.searchCells(registry, idB);
        try {
            helper.assertTrue(searchA.reserve(pos), "A's search locks onto a free block");
            requireOneHolderAtMost(helper, registry, pos, "A's search reserved it", a, b);
            helper.assertTrue(searchB.isReserved(pos), "B's search sees A's cell");
            helper.assertFalse(searchB.reserve(pos), "B's search cannot lock onto it");
            helper.assertFalse(BlockClaims.claim(registry, pos, idB),
                    "B cannot claim it as a job (a reloaded job re-claiming its block) past A's search cell");
            requireOneHolderAtMost(helper, registry, pos, "B's attempts", a, b);

            helper.assertTrue(BlockClaims.claim(registry, pos, idA), "A's AI collects its search result as a job");
            helper.assertFalse(registry.getBlockReservations(idA).contains(pos),
                    "the hand-over moves the cell out of the search ledger");
            helper.assertTrue(registry.robotIdTaking(new ResourceIdBlock(pos)) == idA, "...into A's job claim");
            requireOneHolderAtMost(helper, registry, pos, "the hand-over", a, b);

            helper.assertTrue(searchB.isReserved(pos), "B's search sees the block A works");
            helper.assertFalse(searchB.reserve(pos), "...and cannot lock onto it");
            helper.assertFalse(BlockClaims.claim(registry, pos, idB), "...nor claim it as a job");
            requireOneHolderAtMost(helper, registry, pos, "B's attempts on A's job", a, b);

            BlockClaims.release(registry, pos, idB);
            helper.assertTrue(registry.robotIdTaking(new ResourceIdBlock(pos)) == idA,
                    "B's release cannot free A's job");
            BlockClaims.release(registry, pos, idA);
            helper.assertFalse(BlockClaims.isClaimed(registry, pos), "A's release frees the block in both ledgers");

            helper.assertTrue(searchB.reserve(pos), "now B's search can lock onto it");
            helper.assertFalse(BlockClaims.claim(registry, pos, idA), "...and A cannot claim it past B's cell");
            requireOneHolderAtMost(helper, registry, pos, "B's search took it over", a, b);
            searchB.unreserve(pos);
            helper.assertFalse(BlockClaims.isClaimed(registry, pos), "B's search gives it back");
            helper.succeed();
        } finally {
            registry.killRobot(a);
            registry.killRobot(b);
        }
    }
}
