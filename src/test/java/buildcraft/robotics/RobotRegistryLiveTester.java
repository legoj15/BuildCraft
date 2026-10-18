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
import net.minecraft.nbt.CompoundTag;
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

        /** A station read back from {@code tag} the way the registry's load path builds one (no-arg constructor,
         *  {@code readFromNbt}, then the level), registered so the fixture removes it again. */
        LiveStation loadedStation(CompoundTag tag) {
            LiveStation station = new LiveStation();
            station.readFromNbt(tag);
            station.world = helper.getLevel();
            registry.registerStation(station);
            stations.add(station);
            return station;
        }

        /** What a station at this arena's cell wrote when no robot held it, minus the robot-id key. */
        CompoundTag savedWithoutARobotId(int relX, int relZ, Direction side) {
            CompoundTag tag = new CompoundTag();
            new LiveStation(helper.absolutePos(new BlockPos(relX, 2, relZ)), side).writeToNbt(tag);
            tag.remove("robotId");
            return tag;
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
        public LiveStation() {
            super();
        }

        public LiveStation(BlockPos pos, Direction side) {
            super(pos, side);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }

        /** Always reachable. The tests that use this double are about reservations, and they run synchronously on
         *  the first tick, when an arena-relative position can still sit in a neighbouring chunk that is not loaded
         *  (force-loading is not synchronous); the searches, Goto Station and the delivery leg treat a station in an
         *  unloaded chunk as unreachable, which would turn such a test into a coin flip on the arena's alignment.
         *  The unloaded-station behaviour has its own tests, on {@code DockingStationPipe}s in unloaded chunks. */
        @Override
        public boolean isChunkLoaded() {
            return true;
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

    // ---------- the id counter ----------

    /** A robot that loads with an id the counter has not passed (the registry file that counted it was lost or
     *  reset, so the counter restarted behind the robots already in the world) raises the counter past that id when
     *  it registers: the next robot placed must not be handed the id of one that is still out there. */
    public static void aRobotLoadedAheadOfTheCounterRaisesTheCounter(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot loaded = f.unregisteredRobot();
            long ahead = f.registry.nextRobotIdPeek() + 1000;
            loaded.setUniqueRobotId(ahead); // what EntityRobot.readData restores from the entity's NBT

            f.registry.registerRobot(loaded);

            helper.assertTrue(loaded.getRobotId() == ahead, "a robot that already has an id keeps it");
            helper.assertTrue(f.registry.nextRobotIdPeek() > ahead,
                    "the counter moves past the id of the robot that just registered");
            EntityRobot placed = f.robot();
            helper.assertTrue(placed.getRobotId() > ahead,
                    "a robot placed afterwards gets an id past it (got " + placed.getRobotId() + ", live robot has "
                            + ahead + ")");
            helper.succeed();
        }
    }

    /** The case the counter cannot see coming: the registry was reset, a robot with no registry footprint (its home
     *  station removed, so nothing in the data names its id) sat in an unloaded chunk, and a robot placed meanwhile
     *  was handed that same id. When the old robot's chunk loads, the two must not end up sharing the id: the
     *  robot already registered keeps it (its station link and reservations are filed under it) and the arriving
     *  one is given a fresh id. */
    public static void aRobotArrivingWithALiveRobotsIdIsGivenAFreshOne(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot placed = f.robot();
            long id = placed.getRobotId();
            LiveStation home = f.station(1, 5, Direction.UP);
            helper.assertTrue(home.takeAsMain(placed), "precondition: the placed robot is linked to its home");
            EntityRobot arriving = f.unregisteredRobot();
            arriving.setUniqueRobotId(id); // the id the old robot was saved with, re-issued by the reset counter

            f.registry.registerRobot(arriving);

            helper.assertTrue(placed.getRobotId() == id, "the robot that registered first keeps its id");
            helper.assertTrue(arriving.getRobotId() != id && arriving.getRobotId() != EntityRobotBase.NULL_ROBOT_ID,
                    "the arriving robot is given a fresh id (got " + arriving.getRobotId() + ")");
            helper.assertTrue(f.registry.getLoadedRobot(id) == placed, "the id still resolves to the first holder");
            helper.assertTrue(f.registry.getLoadedRobot(arriving.getRobotId()) == arriving,
                    "the fresh id resolves to the arriving robot");
            helper.assertTrue(home.linkedId() == id && home.robotTaking() == placed,
                    "the first holder's station link is untouched");
            EntityRobot next = f.robot();
            helper.assertTrue(next.getRobotId() != id && next.getRobotId() != arriving.getRobotId(),
                    "the counter is past both ids");
            helper.succeed();
        }
    }

    /** The other half of the collision rule: a holder that is no longer alive (removed without passing through the
     *  registry, the state a missed removal hook leaves) is a stale entry, not a live robot, so the robot arriving
     *  with its id keeps the id and replaces it. */
    public static void aRobotArrivingWithADeadRobotsIdKeepsIt(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot stale = f.robot();
            long id = stale.getRobotId();
            stale.discard(); // never added, so its removal hook is inert: the registry still holds it
            helper.assertTrue(f.registry.getLoadedRobot(id) == stale, "precondition: the stale entry is still held");
            EntityRobot arriving = f.unregisteredRobot();
            arriving.setUniqueRobotId(id);

            f.registry.registerRobot(arriving);

            helper.assertTrue(arriving.getRobotId() == id, "a robot replacing a dead holder keeps its own id");
            helper.assertTrue(f.registry.getLoadedRobot(id) == arriving, "...and the id now resolves to it");
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

    /** A kill that comes after an unload: the unload kept the home and the dock linked to the robot's id but dropped
     *  their cached entity, so the kill must free them by id. Two shapes: the reloaded entity is destroyed before
     *  anything re-resolved the stations (its first tick), and the unloaded entity itself is killed. */
    public static void killRobotAfterAnUnloadFreesItsStations(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            long id = a.getRobotId();
            LiveStation home = f.station(1, 1, Direction.UP);
            LiveStation dock = f.station(2, 1, Direction.UP);
            home.takeAsMain(a);
            dock.take(a);
            a.dock(dock);
            f.registry.unloadRobot(a);
            helper.assertTrue(home.linkedId() == id && dock.linkedId() == id,
                    "precondition: the unload kept the home and the dock");

            EntityRobot reloaded = f.unregisteredRobot();
            reloaded.setUniqueRobotId(id);
            f.registry.registerRobot(reloaded);
            f.registry.killRobot(reloaded);

            helper.assertFalse(home.isTaken(), "a robot killed after a reload frees its home, by id");
            helper.assertFalse(dock.isTaken(), "...and its dock");
            helper.assertTrue(f.registry.stationsReservedBy(id).isEmpty(), "...and its station index is empty");

            EntityRobot b = f.robot();
            long idB = b.getRobotId();
            LiveStation homeB = f.station(3, 1, Direction.UP);
            LiveStation dockB = f.station(4, 1, Direction.UP);
            homeB.takeAsMain(b);
            dockB.take(b);
            b.dock(dockB);
            f.registry.unloadRobot(b);
            f.registry.killRobot(b);

            helper.assertFalse(homeB.isTaken(), "an unloaded robot that is then killed frees its home, by id");
            helper.assertFalse(dockB.isTaken(), "...and its dock");
            helper.assertTrue(f.registry.stationsReservedBy(idB).isEmpty(), "...and its station index is empty");
            helper.succeed();
        }
    }

    /** A reloaded robot undocking from the station it was docked at when it unloaded: the station's cached entity was
     *  dropped by the unload, and the release must still find its holder by id. */
    public static void undockAfterAReloadFreesTheDock(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            long id = a.getRobotId();
            LiveStation dock = f.station(1, 3, Direction.UP);
            dock.take(a);
            a.dock(dock);
            f.registry.unloadRobot(a);

            EntityRobot reloaded = f.unregisteredRobot();
            reloaded.setUniqueRobotId(id);
            f.registry.registerRobot(reloaded);
            reloaded.dock(dock);
            reloaded.undock();

            helper.assertFalse(dock.isTaken(), "undocking frees the dock even after a reload");
            helper.assertFalse(f.registry.stationsReservedBy(id).contains(indexOf(dock)),
                    "...and the registry stops indexing it under the robot");
            helper.succeed();
        }
    }

    /** Station ownership is decided by the saved robot id, never by the cached entity, across a reload: an unload
     *  (and reading a station back from NBT) drops the cache, but the station is still the robot's. Another robot
     *  must be refused a home or dock whose holder is merely unloaded, the holder's own re-take must leave its home
     *  link as it was, and a reloaded robot undocking from a NON-home station read back from NBT (its cached holder
     *  null) must still free it - 7.1.x tested the cached entity, so the unloaded robot's stations were up for grabs
     *  and the reloaded robot's undock left its dock reserved for good. */
    public static void stationOwnershipFollowsTheSavedRobotId(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            EntityRobot b = f.robot();
            long id = a.getRobotId();
            LiveStation home = f.station(1, 3, Direction.UP);
            LiveStation dock = f.station(2, 3, Direction.UP);
            helper.assertTrue(home.takeAsMain(a), "precondition: a homes at its station");
            helper.assertTrue(dock.take(a), "precondition: a takes a second, non-home station");
            a.dock(dock);
            f.registry.unloadRobot(a);
            helper.assertTrue(home.robotTaking() == null && dock.robotTaking() == null,
                    "precondition: the unload dropped the stations' cached holder");

            helper.assertFalse(dock.take(b), "another robot cannot take the dock of an unloaded robot");
            helper.assertFalse(home.take(b), "...nor its home");
            helper.assertFalse(home.takeAsMain(b), "...nor re-home onto it");
            helper.assertTrue(dock.linkedId() == id && home.linkedId() == id,
                    "the refused claims leave both stations with the unloaded robot");

            EntityRobot reloaded = f.unregisteredRobot();
            reloaded.setUniqueRobotId(id);
            f.registry.registerRobot(reloaded);
            helper.assertTrue(home.take(reloaded), "the holder's own re-take of its home succeeds");
            helper.assertTrue(home.isMainStation(), "...and leaves the home link a home link");

            // The station as a saved registry reads it back: holder id set, cached holder null.
            CompoundTag saved = new CompoundTag();
            dock.writeToNbt(saved);
            LiveStation restored = new LiveStation();
            restored.world = helper.getLevel();
            restored.readFromNbt(saved);
            helper.assertTrue(restored.robotTaking() == reloaded, "the restored dock resolves to the reloaded robot");
            restored.invalidateRobotTakingEntity();
            helper.assertTrue(restored.isTaken() && !restored.isMainStation(),
                    "precondition: the restored dock is a plain claim");

            restored.release(reloaded);

            helper.assertFalse(restored.isTaken(), "undocking frees a non-home station whose cached holder is gone");
            helper.assertTrue(home.isTaken(), "...and the robot's home is untouched");
            helper.succeed();
        }
    }

    /** A station whose saved record has no robot id reads back FREE: "no robot" is the null id, and the old default
     *  of 0 made it look held by a robot that never existed - {@code take()} decides by the holder id, so every robot
     *  was refused it for good (the old entity comparison took it anyway). Both the key-less record and the 7.1.x
     *  shape of a free station (an {@code index} compound, the null id written out) must take a robot, as a plain
     *  claim and as a home, and then hold against the next one. */
    public static void stationSavedWithoutARobotIdAcceptsARobot(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            EntityRobot b = f.robot();

            LiveStation visit = f.loadedStation(f.savedWithoutARobotId(1, 1, Direction.UP));
            helper.assertFalse(visit.isTaken(), "a station saved without a robot id reads back free");
            helper.assertTrue(visit.take(a), "...and accepts a robot's plain claim");
            helper.assertTrue(visit.robotTaking() == a && visit.linkedId() == a.getRobotId(),
                    "...which it then names as its holder");
            helper.assertFalse(visit.take(b), "...and holds against the next robot");
            helper.assertTrue(f.registry.stationsReservedBy(a.getRobotId()).contains(indexOf(visit)),
                    "the claim is in the holder's station index");

            LiveStation home = f.loadedStation(f.savedWithoutARobotId(2, 1, Direction.UP));
            helper.assertTrue(home.takeAsMain(a), "a key-less station also accepts a robot as its home");
            helper.assertTrue(home.isMainStation() && home.linkedId() == a.getRobotId(),
                    "...as a home link to that robot");
            helper.assertFalse(home.takeAsMain(b), "...which a second robot cannot take over");

            BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 1));
            CompoundTag upstream = new CompoundTag();
            CompoundTag index = new CompoundTag();
            index.putInt("i", pos.getX());
            index.putInt("j", pos.getY());
            index.putInt("k", pos.getZ());
            upstream.put("index", index);
            upstream.putByte("side", (byte) Direction.UP.ordinal());
            upstream.putBoolean("isMain", false);
            upstream.putLong("robotId", EntityRobotBase.NULL_ROBOT_ID);
            LiveStation old = f.loadedStation(upstream);
            helper.assertTrue(old.getPos().equals(pos) && old.side() == Direction.UP,
                    "precondition: the 7.1.x record still gives its position and side");
            helper.assertFalse(old.isTaken(), "a free station in the 7.1.x shape reads back free");
            helper.assertTrue(old.take(b), "...and accepts a robot");
            helper.assertTrue(old.robotTaking() == b, "...which it names as its holder");
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

    /** The station reverse index must say exactly what the robot still holds. {@code releaseResources} frees the
     *  stations a robot only reserved and keeps its home and dock; a freed station that stayed in the index was a
     *  stale claim, and {@code removeStation} could not trim it later (its holder is no longer the robot). */
    public static void releaseResourcesTrimsFreedStationsFromTheIndex(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            long id = a.getRobotId();
            LiveStation home = f.station(1, 1, Direction.UP);
            LiveStation dock = f.station(2, 1, Direction.UP);
            LiveStation reserved = f.station(3, 1, Direction.UP);
            home.takeAsMain(a);
            dock.take(a);
            a.dock(dock);
            reserved.take(a);

            f.registry.releaseResources(a);

            helper.assertFalse(reserved.isTaken(), "precondition: the merely reserved station was freed");
            helper.assertFalse(f.registry.stationsReservedBy(id).contains(indexOf(reserved)),
                    "a station the release freed must leave the robot's station index");
            helper.assertTrue(f.registry.stationsReservedBy(id).contains(indexOf(home)),
                    "the home it keeps stays indexed");
            helper.assertTrue(f.registry.stationsReservedBy(id).contains(indexOf(dock)),
                    "the dock it keeps stays indexed");

            // Removing the freed station afterwards must leave nothing behind either.
            f.registry.removeStation(reserved);
            helper.assertFalse(f.registry.stationsReservedBy(id).contains(indexOf(reserved)),
                    "removing a freed station leaves no ghost claim");
            helper.succeed();
        }
    }

    /** Same invariant on the unload path: the plain reservation is freed AND de-indexed, the home and dock stay. */
    public static void unloadRobotTrimsFreedStationsFromTheIndex(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            long id = a.getRobotId();
            LiveStation home = f.station(1, 2, Direction.UP);
            LiveStation dock = f.station(2, 2, Direction.UP);
            LiveStation reserved = f.station(3, 2, Direction.UP);
            home.takeAsMain(a);
            dock.take(a);
            a.dock(dock);
            reserved.take(a);

            f.registry.unloadRobot(a);

            helper.assertFalse(reserved.isTaken(), "precondition: the merely reserved station was freed");
            helper.assertFalse(f.registry.stationsReservedBy(id).contains(indexOf(reserved)),
                    "an unloaded robot's freed station must leave its station index");
            helper.assertTrue(f.registry.stationsReservedBy(id).contains(indexOf(home)),
                    "the home stays indexed for the reload");
            helper.assertTrue(f.registry.stationsReservedBy(id).contains(indexOf(dock)),
                    "the dock stays indexed for the reload");

            // The station is free, so a different robot can take it and the first robot's later death must not
            // reach into the new holder's claim.
            EntityRobot b = f.robot();
            helper.assertTrue(reserved.take(b), "a freed station can be reserved by another robot");
            f.registry.killRobot(a);
            helper.assertTrue(reserved.robotTaking() == b, "the first robot's death leaves the new holder alone");
            helper.assertTrue(f.registry.stationsReservedBy(b.getRobotId()).contains(indexOf(reserved)),
                    "...and the new holder's index entry");
            helper.succeed();
        }
    }

    /** Re-homing a robot frees its old main station. That release is deliberately {@code unsafeRelease} (the plain
     *  one refuses a main), which does not touch the registry — so the old station used to stay in the robot's
     *  station index as a claim it no longer holds. */
    public static void replacingTheMainStationTrimsTheOldOneFromTheIndex(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            EntityRobot a = f.robot();
            long id = a.getRobotId();
            LiveStation oldHome = f.station(1, 3, Direction.UP);
            LiveStation newHome = f.station(2, 3, Direction.UP);
            helper.assertTrue(oldHome.takeAsMain(a), "precondition: the robot homes at the first station");

            helper.assertTrue(newHome.takeAsMain(a), "the robot re-homes at the second station");

            helper.assertFalse(oldHome.isTaken(), "the old home is free");
            helper.assertFalse(f.registry.stationsReservedBy(id).contains(indexOf(oldHome)),
                    "...and must leave the robot's station index");
            helper.assertTrue(f.registry.stationsReservedBy(id).contains(indexOf(newHome)),
                    "the new home is indexed");

            f.registry.killRobot(a);
            helper.assertFalse(newHome.isTaken(), "killing the robot frees the new home");
            helper.assertTrue(f.registry.stationsReservedBy(id).isEmpty(), "...and leaves the index empty");
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
