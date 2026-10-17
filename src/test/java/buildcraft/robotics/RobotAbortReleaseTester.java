/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.api.robots.RobotManager;
import buildcraft.robotics.RobotRegistryLiveTester.LiveStation;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoStation;
import buildcraft.robotics.ai.AIRobotShutdown;
import buildcraft.robotics.ai.AIRobotStraightMoveTo;
import buildcraft.robotics.entity.EntityRobot;

/**
 * A robot's AI can be cut short at any moment (a recharge, a gate action, a power failure), and then no registry
 * release path runs: the robot lives on. Whatever the AI claimed for its job has to go back through the AI's own
 * {@code end()} (or, for the shutdown that never ends, at its start) - otherwise the claim stays until the robot's
 * next sleep or recharge, and a robot that is parked for good keeps it forever.
 *
 * <p>Driven synchronously on robots that are constructed but never added to the level, registered by hand, like
 * {@link RobotRegistryLiveTester}: the stations are plain {@link LiveStation}s at this arena's own positions, and
 * everything a test registers is killed / removed when it closes, because the registry is shared by every arena.
 */
public class RobotAbortReleaseTester {

    /** Holds everything one test registered, and undoes it. */
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final RobotRegistry registry;
        private final List<EntityRobot> robots = new ArrayList<>();
        private final List<DockingStation> stations = new ArrayList<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.registry = (RobotRegistry) RobotManager.registryProvider.getRegistry(helper.getLevel());
        }

        EntityRobot robot() {
            EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
            Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1)));
            robot.setPos(pos.x, pos.y, pos.z);
            robots.add(robot);
            registry.registerRobot(robot);
            return robot;
        }

        LiveStation station(int relX, int relZ) {
            LiveStation station = new LiveStation(helper.absolutePos(new BlockPos(relX, 2, relZ)), Direction.UP);
            station.world = helper.getLevel();
            registry.registerStation(station);
            stations.add(station);
            return station;
        }

        @Override
        public void close() {
            for (EntityRobot robot : robots) {
                if (robot.getRobotId() != EntityRobotBase.NULL_ROBOT_ID) {
                    registry.killRobot(robot);
                }
            }
            for (DockingStation station : stations) {
                if (registry.getStation(station.getPos(), station.side()) == station) {
                    registry.removeStation(station);
                }
            }
        }
    }

    private static StationIndex indexOf(DockingStation station) {
        return new StationIndex(station);
    }

    // ---------- AIRobotGotoStation ----------

    /** The goto claims its station before it flies, so no second robot races for the mount. Cut short mid-flight, it
     *  must give the station back: the robot never arrived, and the station would read as taken by a robot that is
     *  working somewhere else until that robot next sleeps or recharges. */
    public static void abortedGotoStationFreesItsPlainClaim(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot robot = f.robot();
            LiveStation target = f.station(3, 1);
            AIRobotGotoStation visit = new AIRobotGotoStation(robot, target);
            try {
                visit.start();
                helper.assertTrue(target.robotTaking() == robot && visit.getDelegateAI() != null,
                        "precondition: the goto claimed the station and is flying to it");
                helper.assertTrue(f.registry.stationsReservedBy(robot.getRobotId()).contains(indexOf(target)),
                        "precondition: the registry indexes the claim under the robot");

                visit.abort();

                helper.assertFalse(target.isTaken(),
                        "an aborted goto must free the station it claimed - the robot never docked there");
                helper.assertTrue(f.registry.stationsReservedBy(robot.getRobotId()).isEmpty(),
                        "...and the registry must stop indexing it under the robot");
                helper.assertTrue(robot.getDockingStation() == null, "...the robot was never docked");
            } finally {
                visit.abort();
            }
            helper.succeed();
        }
    }

    /** The same claim, ended by the flight FAILING (no path to the station) instead of by an abort. */
    public static void failedGotoStationFreesItsPlainClaim(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot robot = f.robot();
            LiveStation target = f.station(3, 1);
            AIRobotGotoStation visit = new AIRobotGotoStation(robot, target);
            try {
                visit.start();
                helper.assertTrue(target.robotTaking() == robot, "precondition: the goto claimed the station");

                AIRobotGotoBlock failedLeg = new AIRobotGotoBlock(robot, 0, 0, 0) {
                    {
                        setSuccess(false);
                    }
                };
                visit.delegateAIEnded(failedLeg);

                helper.assertFalse(visit.success(), "a goto whose flight failed reports failure");
                helper.assertFalse(target.isTaken(), "...and gives back the station it never reached");
            } finally {
                visit.abort();
            }
            helper.succeed();
        }
    }

    /** The release is for a robot that did not arrive: one that docked keeps the claim, which IS its dock (undocking
     *  is what frees it). */
    public static void dockedGotoStationKeepsItsClaim(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot robot = f.robot();
            LiveStation target = f.station(3, 1);
            AIRobotGotoStation visit = new AIRobotGotoStation(robot, target);
            try {
                visit.start();
                helper.assertTrue(target.robotTaking() == robot, "precondition: the goto claimed the station");

                // The last leg of the flight: the straight move onto the mount ends, and the goto docks the robot.
                visit.delegateAIEnded(new AIRobotStraightMoveTo(robot, 0, 0, 0));

                helper.assertTrue(visit.success() && robot.getDockingStation() == target,
                        "precondition: the goto docked the robot at the station");
                helper.assertTrue(target.robotTaking() == robot,
                        "a goto that arrived keeps its claim - it is the robot's dock now");
                helper.assertTrue(f.registry.stationsReservedBy(robot.getRobotId()).contains(indexOf(target)),
                        "...and the registry still indexes it under the robot");

                robot.undock();
                helper.assertFalse(target.isTaken(), "undocking is what frees a visited station");
            } finally {
                visit.abort();
            }
            helper.succeed();
        }
    }

    /** The robot's home station is never the goto's to give back: aborting a flight to another station frees only
     *  that one, and aborting the flight HOME (what {@code AIRobotGotoSleep} flies) leaves the link in place - the
     *  goto did not take the home claim, the robot held it already. */
    public static void abortedGotoStationKeepsTheHomeLink(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot robot = f.robot();
            LiveStation home = f.station(1, 5);
            LiveStation other = f.station(4, 1);
            helper.assertTrue(home.takeAsMain(robot) && robot.getLinkedStation() == home,
                    "precondition: the station is the robot's home");

            AIRobotGotoStation toOther = new AIRobotGotoStation(robot, other);
            try {
                toOther.start();
                helper.assertTrue(other.robotTaking() == robot, "precondition: the goto claimed the other station");
                toOther.abort();
                helper.assertFalse(other.isTaken(), "the visited station is freed");
                requireHome(helper, f, robot, home, "after aborting a flight to another station");
            } finally {
                toOther.abort();
            }

            AIRobotGotoStation toHome = new AIRobotGotoStation(robot, home);
            try {
                toHome.start();
                helper.assertTrue(toHome.getDelegateAI() != null, "precondition: the robot is flying home");
                toHome.abort();
                requireHome(helper, f, robot, home, "after aborting the flight home");
            } finally {
                toHome.abort();
            }
            helper.succeed();
        }
    }

    /** A take-as-main goto (a gate's "go to station" action) makes the target the robot's new home at its START, so
     *  aborting it midway leaves the new home as the home: the link is the robot's, not a visit claim to give back. */
    public static void abortedTakeAsMainGotoKeepsTheNewHome(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot robot = f.robot();
            LiveStation newHome = f.station(4, 1);
            AIRobotGotoStation relink = new AIRobotGotoStation(robot, newHome, true);
            try {
                relink.start();
                helper.assertTrue(robot.getLinkedStation() == newHome, "precondition: the relink moved the home");

                relink.abort();

                requireHome(helper, f, robot, newHome, "after aborting the relink");
            } finally {
                relink.abort();
            }
            helper.succeed();
        }
    }

    private static void requireHome(GameTestHelper helper, Fixture f, EntityRobot robot, LiveStation home,
            String when) {
        helper.assertTrue(robot.getLinkedStation() == home, "the robot keeps its home " + when);
        helper.assertTrue(home.robotTaking() == robot && home.isMainStation(),
                "the home station is still the robot's, as the main link " + when);
        helper.assertTrue(f.registry.stationsReservedBy(robot.getRobotId()).contains(indexOf(home)),
                "the registry still indexes the home under the robot " + when);
    }

    // ---------- AIRobotShutdown ----------

    /** A robot with no power parks where it falls, and the shutdown AI never ends: whatever the robot still holds is
     *  held for good, since a parked robot is loaded and alive and so no unload or death ever frees it. The start of
     *  the shutdown must give back everything a plain release gives back - resource locks, search cells, visit claims
     *  and the dock - and nothing more: the home link stays. */
    public static void shutdownReleasesWhatTheRobotHolds(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot robot = f.robot();
            long id = robot.getRobotId();
            LiveStation home = f.station(1, 5);
            LiveStation visited = f.station(4, 1);
            LiveStation dock = f.station(4, 3);
            ResourceId block = new ResourceIdBlock(helper.absolutePos(new BlockPos(2, 1, 2)));
            BlockPos cell = helper.absolutePos(new BlockPos(2, 1, 4));
            helper.assertTrue(home.takeAsMain(robot), "precondition: the robot has a home");
            helper.assertTrue(visited.take(robot), "precondition: the robot holds a plain claim on a station");
            helper.assertTrue(dock.take(robot), "precondition: the robot claims another station...");
            robot.dock(dock);
            helper.assertTrue(f.registry.take(block, robot), "precondition: the robot holds a block reservation");
            helper.assertTrue(f.registry.getBlockReservations(id).add(cell),
                    "precondition: the robot holds a search cell");

            new AIRobotShutdown(robot).start();

            helper.assertFalse(f.registry.isTaken(block), "a shut-down robot must not keep its block reservation");
            helper.assertFalse(f.registry.getBlockReservations().contains(cell),
                    "...nor its search cell");
            helper.assertFalse(visited.isTaken(), "...nor a claim on a station it was only visiting");
            helper.assertTrue(robot.getDockingStation() == null && !dock.isTaken(),
                    "...and it is undocked, the dock freed");
            requireHome(helper, f, robot, home, "- a shutdown parks the robot, it does not orphan it");
            helper.succeed();
        }
    }
}
