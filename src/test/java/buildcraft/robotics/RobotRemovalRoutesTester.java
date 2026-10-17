/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.api.robots.RobotManager;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.RobotRegistryLiveTester.LiveStation;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The END-TO-END removal routes: a TICKING robot that is really in the world, holding one of every kind of
 * reservation, leaves it the way the game takes robots out - and the registry must end up with nothing the robot
 * held and nothing of it in its reverse indexes.
 *
 * <p>{@code RobotRegistryLiveTester} drives {@code killRobot}/{@code unloadRobot} directly on robots that never
 * enter the world, because for those the entity's removal hooks are inert (the registry hand-off is armed by the
 * first tick). This class is the other half: it proves the hooks themselves reach the registry. A robot is killed
 * through {@code discard()} (the {@code remove} seam); it is UNLOADED through {@code setRemoved(UNLOADED_TO_CHUNK)},
 * which is exactly what chunk unloading calls - it is {@code final} and bypasses {@code remove}, so the unload
 * hand-off rides on {@code onRemovedFromLevel}, and a regression there would be invisible to every test that
 * calls the registry directly.
 *
 * <p>Both reservations are made inside one synchronous callback right after the robot is seen registered, and the
 * route is taken in the same callback, so no tick (and no AI backstop such as the sleep AI's own
 * {@code releaseResources}) can run in between and make the assertions pass for the wrong reason.
 */
public class RobotRemovalRoutesTester {

    private static final BlockPos ROBOT_REL = new BlockPos(2, 2, 2);

    /** What one robot holds: a resource, a search cell, a plain reservation, a dock and a home. */
    private static final class Holdings {
        final ServerLevel level;
        final RobotRegistry registry;
        final EntityRobot robot;
        final long id;
        final ResourceId resource;
        final BlockPos cell;
        final LiveStation home;
        final LiveStation dock;
        final LiveStation reserved;

        Holdings(GameTestHelper helper, EntityRobot robot) {
            this.level = helper.getLevel();
            this.registry = (RobotRegistry) RobotManager.registryProvider.getRegistry(level);
            this.robot = robot;
            this.id = robot.getRobotId();
            this.resource = new ResourceIdBlock(helper.absolutePos(new BlockPos(1, 1, 1)));
            this.cell = helper.absolutePos(new BlockPos(3, 1, 1));
            this.home = station(helper, 1, 5);
            this.dock = station(helper, 2, 5);
            this.reserved = station(helper, 3, 5);

            helper.assertTrue(registry.take(resource, robot), "the robot reserves a block");
            helper.assertTrue(registry.getBlockReservations(id).add(cell), "the robot's search reserves a cell");
            helper.assertTrue(home.takeAsMain(robot), "the robot homes at a station");
            helper.assertTrue(dock.take(robot), "the robot reserves a dock...");
            robot.dock(dock);
            helper.assertTrue(reserved.take(robot), "...and one more station on its way somewhere");
            helper.assertTrue(registry.isTaken(resource) && registry.getBlockReservations().contains(cell)
                            && registry.stationsReservedBy(id).size() == 3,
                    "precondition: the robot holds all five reservations");
        }

        private LiveStation station(GameTestHelper helper, int relX, int relZ) {
            LiveStation station = new LiveStation(helper.absolutePos(new BlockPos(relX, 2, relZ)), Direction.UP);
            station.world = level;
            registry.registerStation(station);
            return station;
        }

        void cleanUp() {
            registry.getBlockReservations().remove(cell);
            registry.release(resource);
            for (DockingStation station : new DockingStation[] { home, dock, reserved }) {
                if (registry.getStation(station.getPos(), station.side()) == station) {
                    registry.removeStation(station);
                }
            }
        }
    }

    private static EntityRobot addRobot(GameTestHelper helper) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(ROBOT_REL));
        robot.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    private static boolean registered(GameTestHelper helper, EntityRobot robot) {
        return robot.getRobotId() != EntityRobotBase.NULL_ROBOT_ID
                && RobotManager.registryProvider.getRegistry(helper.getLevel())
                        .getLoadedRobot(robot.getRobotId()) == robot;
    }

    /** A robot destroyed in the world ({@code discard()} -> {@code remove} -> {@code killRobot}). */
    public static void removedRobotReleasesEverythingItHolds(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper, ROBOT_REL);
        EntityRobot robot = addRobot(helper);
        EntityArenaUtil.tickUntil(helper, 60, () -> registered(helper, robot), () -> {
            Holdings h = new Holdings(helper, robot);
            try {
                robot.discard();

                helper.assertFalse(h.registry.isTaken(h.resource), "the block reservation is freed");
                helper.assertTrue(h.registry.resourcesReservedBy(h.id).isEmpty(), "...and its resource index clean");
                helper.assertFalse(h.registry.getBlockReservations().contains(h.cell), "the search cell is freed");
                helper.assertTrue(h.registry.blockCellsReservedBy(h.id).isEmpty(), "...and its cell index clean");
                helper.assertFalse(h.home.isTaken(), "the home station is freed (a kill is forced)");
                helper.assertFalse(h.dock.isTaken(), "the dock is freed");
                helper.assertFalse(h.reserved.isTaken(), "the plain reservation is freed");
                helper.assertTrue(h.registry.stationsReservedBy(h.id).isEmpty(), "...and its station index clean");
                helper.assertTrue(h.registry.getLoadedRobot(h.id) == null, "the registry forgot the robot");
                helper.succeed();
            } finally {
                h.cleanUp();
                if (!robot.isRemoved()) {
                    robot.discard();
                }
            }
        }, "the robot never registered with the RobotRegistry");
    }

    /** A robot whose chunk unloads ({@code setRemoved(UNLOADED_TO_CHUNK)} -> {@code onRemovedFromLevel} ->
     *  {@code unloadRobot}): everything that is not its home or dock goes, and the indexes name exactly those two. */
    public static void unloadedRobotReleasesWhatItDoesNotKeep(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper, ROBOT_REL);
        EntityRobot robot = addRobot(helper);
        EntityArenaUtil.tickUntil(helper, 60, () -> registered(helper, robot), () -> {
            Holdings h = new Holdings(helper, robot);
            try {
                robot.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);

                helper.assertFalse(h.registry.isTaken(h.resource), "the block reservation is freed");
                helper.assertTrue(h.registry.resourcesReservedBy(h.id).isEmpty(), "...and its resource index clean");
                helper.assertFalse(h.registry.getBlockReservations().contains(h.cell), "the search cell is freed");
                helper.assertTrue(h.registry.blockCellsReservedBy(h.id).isEmpty(), "...and its cell index clean");
                helper.assertFalse(h.reserved.isTaken(), "the plain reservation is freed");
                helper.assertTrue(h.home.linkedId() == h.id, "the home stays the robot's, for the reload");
                helper.assertTrue(h.dock.linkedId() == h.id, "so does the dock");
                helper.assertTrue(h.registry.stationsReservedBy(h.id).size() == 2
                                && h.registry.stationsReservedBy(h.id).contains(new StationIndex(h.home))
                                && h.registry.stationsReservedBy(h.id).contains(new StationIndex(h.dock)),
                        "the station index names exactly the home and the dock - got "
                                + h.registry.stationsReservedBy(h.id));
                helper.assertTrue(h.registry.getLoadedRobot(h.id) == null, "the registry forgot the robot entity");
                helper.succeed();
            } finally {
                h.cleanUp();
                if (!robot.isRemoved()) {
                    robot.discard();
                }
            }
        }, "the robot never registered with the RobotRegistry");
    }
}
