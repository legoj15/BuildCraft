/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.Collections;
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
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.ai.AIRobotGotoStation;
import buildcraft.robotics.entity.EntityRobot;

/**
 * {@link RobotRegistry} against LIVE robot entities — the paths {@code RobotRegistryTest} cannot reach because they
 * read the entity: two robots contending for one reservation, a dead holder's claim lapsing, and what each removal
 * path ({@code killRobot}, {@code unloadRobot}, {@code releaseResources}, {@code removeStation}) frees and keeps.
 *
 * <p>Driven synchronously on robots that are constructed but never ADDED to the level: the registry only needs an
 * entity to hold (its id, {@code isAlive()}, its docking fields), and a robot that never enters the world cannot
 * tick, move, run an AI or be seen by any other test. Registration is done by hand ({@code registerRobot} is what
 * the first tick would do), and every robot and station a test registers is killed / removed in a {@code finally},
 * because the registry is a per-level {@code SavedData} shared by every arena in the run. Stations are a plain
 * {@link DockingStation} subclass at this arena's own absolute positions, so they need no pipe and are never
 * written to disk under a type the loader cannot rebuild.
 */
public class RobotRegistryLiveTester {

    // ---------- fixtures ----------

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

        /** A robot the registry knows about (id assigned), that is not in the world. */
        EntityRobot robot() {
            EntityRobot robot = unregisteredRobot();
            registry.registerRobot(robot);
            return robot;
        }

        EntityRobot unregisteredRobot() {
            EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
            Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1)));
            robot.setPos(pos.x, pos.y, pos.z);
            robots.add(robot);
            return robot;
        }

        LiveStation station(int relX, int relZ, Direction side) {
            LiveStation station = new LiveStation(helper.absolutePos(new BlockPos(relX, 2, relZ)), side);
            station.world = helper.getLevel();
            registry.registerStation(station);
            stations.add(station);
            return station;
        }

        ResourceId resource(int relX, int relZ) {
            return new ResourceIdBlock(helper.absolutePos(new BlockPos(relX, 1, relZ)));
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

    /** A pipe-less station at a real position in this level. */
    public static final class LiveStation extends DockingStation {
        public LiveStation(BlockPos pos, Direction side) {
            super(pos, side);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }
    }

    private static StationIndex indexOf(DockingStation station) {
        return new StationIndex(station);
    }

    // ---------- reservation exclusivity ----------

    /** Two robots, one block reservation and one station: whoever claims first holds it, the other is refused
     *  until it is released — and the loser's own bookkeeping is never touched by the refused claim. */
    public static void twoRobotsNeverShareAReservation(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            EntityRobot b = f.robot();
            helper.assertTrue(a.getRobotId() != b.getRobotId(), "precondition: two distinct robot ids");

            ResourceId block = f.resource(1, 1);
            helper.assertTrue(f.registry.take(block, a), "the first claim on a free block succeeds");
            helper.assertFalse(f.registry.take(block, b), "a second robot is refused the same block");
            helper.assertTrue(f.registry.robotTaking(block) == a, "the block stays with its first claimant");
            helper.assertTrue(f.registry.resourcesReservedBy(b.getRobotId()).isEmpty(),
                    "a refused claim must not leak into the loser's reverse index");

            f.registry.releaseResources(a);
            helper.assertFalse(f.registry.isTaken(block), "releasing A's resources frees the block");
            helper.assertTrue(f.registry.take(block, b), "…and B may now claim it");
            helper.assertFalse(f.registry.take(block, a), "…after which A is the one refused");

            LiveStation station = f.station(2, 1, Direction.UP);
            helper.assertTrue(station.take(a), "the first robot reserves a free station");
            helper.assertFalse(station.take(b), "a second robot cannot reserve a reserved station");
            helper.assertFalse(station.takeAsMain(b), "…nor link it as home");
            helper.assertTrue(station.take(a), "the holder re-taking its own station is a harmless yes");
            helper.assertTrue(station.robotTaking() == a && station.linkedId() == a.getRobotId(),
                    "the station still names A after B's refused attempts");
            helper.assertFalse(f.registry.stationsReservedBy(b.getRobotId()).contains(indexOf(station)),
                    "a refused station claim must not leak into the loser's reverse index");

            station.release(a);
            helper.assertTrue(station.take(b), "a released station can be reserved by the other robot");
            helper.assertFalse(f.registry.stationsReservedBy(a.getRobotId()).contains(indexOf(station)),
                    "the release removed it from A's reverse index");
            helper.succeed();
        }
    }

    /** {@code AIRobotGotoStation} reserves before it flies: a robot sent to a station another robot holds is
     *  refused at once — no flight, no success — so two robots never race for one dock. */
    public static void gotoStationRefusesAStationAnotherRobotHolds(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            EntityRobot b = f.robot();
            LiveStation station = f.station(3, 1, Direction.UP);
            helper.assertTrue(station.take(a), "precondition: A holds the station");

            AIRobotGotoStation refused = new AIRobotGotoStation(b, station);
            refused.start();

            helper.assertFalse(refused.success(), "B's goto to A's station must fail");
            helper.assertTrue(refused.getDelegateAI() == null, "…without ever starting the approach flight");
            helper.assertTrue(station.robotTaking() == a, "…and without disturbing A's claim");

            station.release(a);
            AIRobotGotoStation again = new AIRobotGotoStation(b, station);
            again.start();
            helper.assertTrue(station.robotTaking() == b, "once free, B's goto reserves the station itself");
            helper.assertTrue(again.getDelegateAI() != null, "…and starts flying there");
            again.abort();
            helper.succeed();
        }
    }

    /** A reservation is only as alive as its holder: once the holding robot is dead, the next query releases
     *  the claim instead of leaving the block locked by a robot that no longer exists. */
    public static void aDeadHoldersReservationLapses(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            EntityRobot b = f.robot();
            ResourceId block = f.resource(4, 1);
            f.registry.take(block, a);

            // Removed WITHOUT going through the registry (it was never added, so its removal hook is inert) —
            // exactly the state a crash or a missed hook would leave: registered, holding, but dead.
            a.discard();
            helper.assertTrue(f.registry.rawHolderOf(block) == a.getRobotId(), "precondition: A still holds it");

            helper.assertFalse(f.registry.isTaken(block), "a dead holder's claim reads as free");
            helper.assertTrue(f.registry.rawHolderOf(block) == EntityRobotBase.NULL_ROBOT_ID,
                    "…and the query released it outright");
            helper.assertTrue(f.registry.take(block, b), "so a living robot can claim it");
            helper.succeed();
        }
    }

    // ---------- removal paths ----------

    /** {@code killRobot} (a robot destroyed): every resource and every station — home, dock and plain
     *  reservation alike — is released, and the robot is forgotten. */
    public static void killRobotReleasesEverything(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            long id = a.getRobotId();
            ResourceId block = f.resource(1, 3);
            LiveStation home = f.station(1, 4, Direction.UP);
            LiveStation dock = f.station(2, 4, Direction.UP);
            LiveStation reserved = f.station(3, 4, Direction.UP);
            f.registry.take(block, a);
            home.takeAsMain(a);
            dock.take(a);
            a.dock(dock);
            reserved.take(a);

            f.registry.killRobot(a);

            helper.assertFalse(f.registry.isTaken(block), "a killed robot's block reservation is freed");
            helper.assertTrue(f.registry.resourcesReservedBy(id).isEmpty(), "…and its resource index cleared");
            helper.assertFalse(home.isTaken(), "a killed robot's HOME station is freed (forced — it is a main)");
            helper.assertFalse(dock.isTaken(), "the station it was docked at is freed (forced — it is docked)");
            helper.assertFalse(reserved.isTaken(), "a merely reserved station is freed");
            helper.assertTrue(f.registry.stationsReservedBy(id).isEmpty(), "…and its station index cleared");
            helper.assertTrue(f.registry.getLoadedRobot(id) == null, "the robot is no longer loaded");
            helper.succeed();
        }
    }

    /** {@code unloadRobot} (the chunk went away): resources and plain reservations are released, but the home
     *  station and the dock the robot sits on stay HIS — that is what lets the robot come back with them — and a
     *  reloaded entity carrying the same id picks them straight back up. */
    public static void unloadRobotKeepsHomeAndDock(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            long id = a.getRobotId();
            ResourceId block = f.resource(1, 5);
            LiveStation home = f.station(1, 6, Direction.UP);
            LiveStation dock = f.station(2, 6, Direction.UP);
            LiveStation reserved = f.station(3, 6, Direction.UP);
            f.registry.take(block, a);
            home.takeAsMain(a);
            dock.take(a);
            a.dock(dock);
            reserved.take(a);

            f.registry.unloadRobot(a);

            helper.assertFalse(f.registry.isTaken(block), "an unloaded robot's block reservation is freed");
            helper.assertFalse(reserved.isTaken(), "a merely reserved station is freed on unload");
            helper.assertTrue(home.linkedId() == id && home.isMainStation(),
                    "the home station stays linked to the unloaded robot's id");
            helper.assertTrue(dock.linkedId() == id, "the station it is docked at stays its");
            helper.assertTrue(home.robotTaking() == null && dock.robotTaking() == null,
                    "…but neither hands out the unloaded entity any more");
            helper.assertTrue(f.registry.getLoadedRobot(id) == null, "the robot is no longer loaded");
            helper.assertTrue(f.registry.stationsReservedBy(id).contains(indexOf(home)),
                    "the home stays in the robot's station index, for the kill that may come later");

            // The chunk comes back: a fresh entity, same saved id.
            EntityRobot reloaded = f.unregisteredRobot();
            reloaded.setUniqueRobotId(id);
            f.registry.registerRobot(reloaded);
            helper.assertTrue(home.robotTaking() == reloaded, "the home station resolves to the reloaded entity");
            helper.assertTrue(dock.robotTaking() == reloaded, "so does the dock");
            helper.succeed();
        }
    }

    /** {@code releaseResources} (what the AIs call between jobs): frees resources and plain reservations but
     *  never the home station or the dock under the robot — both would strand it. */
    public static void releaseResourcesKeepsHomeAndDock(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            long id = a.getRobotId();
            ResourceId block = f.resource(4, 3);
            LiveStation home = f.station(4, 4, Direction.UP);
            LiveStation dock = f.station(5, 4, Direction.UP);
            LiveStation reserved = f.station(4, 5, Direction.UP);
            f.registry.take(block, a);
            home.takeAsMain(a);
            dock.take(a);
            a.dock(dock);
            reserved.take(a);

            f.registry.releaseResources(a);

            helper.assertFalse(f.registry.isTaken(block), "resources are released between jobs");
            helper.assertFalse(reserved.isTaken(), "a station it only reserved is released");
            helper.assertTrue(home.robotTaking() == a, "its home is kept");
            helper.assertTrue(dock.robotTaking() == a, "the dock it sits on is kept");
            helper.assertTrue(f.registry.getLoadedRobot(id) == a, "and the robot stays loaded");
            helper.succeed();
        }
    }

    /** {@code removeStation} with a LIVE robot on each kind of claim: a robot docked at a visited station is
     *  undocked; a robot whose HOME it was loses its home (and its dock, if it sat there); and in every case the
     *  station leaves the robot's station index, rather than lingering there as a stale claim on a station that no
     *  longer exists (and on whatever is later registered at the same pos+side). */
    public static void removeStationReleasesTheRobotsClaim(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            // A robot docked at a station it visits.
            EntityRobot visitor = f.robot();
            LiveStation visited = f.station(1, 1, Direction.NORTH);
            visited.take(visitor);
            visitor.dock(visited);
            f.registry.removeStation(visited);
            helper.assertTrue(f.registry.getStation(visited.getPos(), visited.side()) == null, "the station is gone");
            helper.assertTrue(visitor.getDockingStation() == null, "its docked robot is undocked");
            helper.assertFalse(f.registry.stationsReservedBy(visitor.getRobotId()).contains(indexOf(visited)),
                    "and it leaves the robot's station index");

            // A robot that reserved a station (on its way there) but has not docked yet.
            EntityRobot flyer = f.robot();
            LiveStation target = f.station(2, 1, Direction.NORTH);
            target.take(flyer);
            f.registry.removeStation(target);
            helper.assertFalse(f.registry.stationsReservedBy(flyer.getRobotId()).contains(indexOf(target)),
                    "a reserved-but-not-docked station also leaves the robot's station index");

            // A robot docked at its own home.
            EntityRobot homebody = f.robot();
            LiveStation home = f.station(3, 1, Direction.NORTH);
            home.takeAsMain(homebody);
            homebody.dock(home);
            f.registry.removeStation(home);
            helper.assertTrue(homebody.getLinkedStation() == null, "the robot loses the home that was removed");
            helper.assertTrue(homebody.getDockingStation() == null,
                    "and is not left docked at a station that no longer exists");
            helper.assertFalse(f.registry.stationsReservedBy(homebody.getRobotId()).contains(indexOf(home)),
                    "the removed home leaves the robot's station index");

            // A home whose robot is unloaded (the station outlives the entity): only the index can be trimmed.
            EntityRobot away = f.robot();
            long awayId = away.getRobotId();
            LiveStation awayHome = f.station(4, 1, Direction.NORTH);
            awayHome.takeAsMain(away);
            f.registry.unloadRobot(away);
            helper.assertTrue(f.registry.stationsReservedBy(awayId).contains(indexOf(awayHome)),
                    "precondition: the unloaded robot's home is still indexed");
            f.registry.removeStation(awayHome);
            helper.assertFalse(f.registry.stationsReservedBy(awayId).contains(indexOf(awayHome)),
                    "removing an unloaded robot's home trims its station index");
            helper.succeed();
        }
    }
}
