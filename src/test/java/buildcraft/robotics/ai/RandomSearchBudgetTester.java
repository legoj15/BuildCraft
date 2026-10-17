/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.lib.misc.data.Box;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.RobotRegistryLiveTester.LiveStation;
import buildcraft.robotics.boards.BoardRobotPlanter;
import buildcraft.robotics.entity.EntityRobot;

/** A RANDOM block search (the planter's, the stripes board's) draws from a scanner that never runs out, so with no
 *  candidate anywhere it must still give up: 7.1.x ran it for a fixed number of rounds ({@code IterableAlgorithmRunner}'s
 *  1000) and then reported nothing found, which is what sends an idle planter to sleep. Without that budget the
 *  robot searched forever and never slept. Both tests drive unadded robots synchronously; the planter is pinned to a
 *  work zone inside its own arena cell and parks on a station of its own, so it never touches a sibling's blocks or
 *  stations. */
public class RandomSearchBudgetTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;
    /** Past the 1000-round budget, with room for the composed AIs' own start-up cycles. */
    private static final int CYCLE_BOUND = 1200;

    public static void randomSearchWithNoCandidateFails(GameTestHelper helper) {
        EntityRobot robot = robot(helper, new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel()));
        IRobotRegistry registry = robot.getRegistry();
        Parent parent = new Parent(robot);
        AIRobotSearchBlock search = new AIRobotSearchBlock(robot, true, pos -> false, 0);
        try {
            parent.startDelegateAI(search);
            for (int i = 0; i < CYCLE_BOUND && parent.ended == null; i++) {
                parent.cycle();
            }
            helper.assertTrue(parent.ended == search,
                    "a random search with nothing to find must give up within its round budget");
            helper.assertFalse(search.success(), "...and report nothing found");
        } finally {
            parent.abortDelegateAI();
            registry.killRobot(robot);
        }
        helper.succeed();
    }

    public static void planterWithNothingToPlantSleeps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LiveStation home = new LiveStation(helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP);
        home.world = level;
        IZone zone = new Box(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(5, 4, 6)));
        EntityRobot robot = robot(helper, new ParkedRobot(level, home, zone));
        IRobotRegistry registry = robot.getRegistry();
        registry.registerStation(home);
        robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS));
        BoardRobotPlanter board = new BoardRobotPlanter(robot);
        try {
            for (int i = 0; i < CYCLE_BOUND && !(board.getActiveAI() instanceof AIRobotSleep); i++) {
                board.cycle();
            }
            helper.assertTrue(board.getActiveAI() instanceof AIRobotSleep,
                    "a planter with nowhere to plant must go to sleep, got " + board.getActiveAI());
        } finally {
            board.abortDelegateAI();
            registry.removeStation(home);
            registry.killRobot(robot);
        }
        helper.succeed();
    }

    private static EntityRobot robot(GameTestHelper helper, EntityRobot robot) {
        Vec3 spawn = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 3)));
        robot.setPos(spawn.x, spawn.y, spawn.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        IRobotRegistry registry = robot.getRegistry();
        robot.setUniqueRobotId(registry.getNextRobotId());
        registry.registerRobot(robot);
        return robot;
    }

    /** Docked at, and linked to, its own station, and working a zone inside the arena cell. */
    private static final class ParkedRobot extends EntityRobot {
        private final DockingStation home;
        private final IZone zone;

        ParkedRobot(Level level, DockingStation home, IZone zone) {
            super(BCRoboticsEntities.ROBOT.get(), level);
            this.home = home;
            this.zone = zone;
        }

        @Override
        public DockingStation getDockingStation() {
            return home;
        }

        @Override
        public DockingStation getLinkedStation() {
            return home;
        }

        @Override
        public IZone getZoneToWork() {
            return zone;
        }
    }

    /** Records the delegate that ended. */
    private static final class Parent extends AIRobot {
        AIRobot ended;

        Parent(EntityRobot robot) {
            super(robot);
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            ended = ai;
        }
    }
}
