/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.StatementSlot;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * Pure-JUnit characterization of {@link RobotRegistry}'s reservation bookkeeping and NBT round-trip — the
 * reservation maps and their reverse indices, exercised without a live robot entity. The entity-gated paths
 * ({@code robotIdTaking}/{@code isTaken} loaded-and-alive checks, two-robot exclusivity, and the
 * {@code killRobot}/{@code unloadRobot}/{@code removeStation} removal paths) live in the
 * {@link RobotRegistryLiveTester} game tests; here we drive the id-keyed core through {@code take}/{@code release}, the
 * package-private raw inspectors, and the decoupled {@code writeToNbt}/{@code readFromNbt} (seam c).
 */
public class RobotRegistryTest {

    private static final long ROBOT_A = 1000L;
    private static final long ROBOT_B = 2000L;

    @BeforeAll
    static void registerTestStationType() {
        RobotManager.registerDockingStation(TestDockingStation.class, "test_station");
    }

    private static ResourceId resource(int x, int y, int z) {
        return new ResourceIdBlock(new BlockPos(x, y, z));
    }

    // ---- reservation invariants (no entity, no round-trip) ----

    @Test
    public void testTakeClaimsAndRejectsDoubleClaim() {
        RobotRegistry r = new RobotRegistry(null);
        ResourceId id = resource(1, 2, 3);

        Assertions.assertTrue(r.take(id, ROBOT_A), "first claim succeeds");
        Assertions.assertFalse(r.take(id, ROBOT_B), "a second robot cannot claim the same resource");
        Assertions.assertEquals(ROBOT_A, r.rawHolderOf(id), "the holder remains the first claimant");
    }

    @Test
    public void testReClaimBySameRobotIsRejected() {
        // 7.1.x characterization: take() keys purely on the resource, so even the holder re-taking gets false.
        RobotRegistry r = new RobotRegistry(null);
        ResourceId id = resource(1, 2, 3);

        Assertions.assertTrue(r.take(id, ROBOT_A));
        Assertions.assertFalse(r.take(id, ROBOT_A), "re-taking an already-held resource returns false");
    }

    @Test
    public void testReleaseFreesForReclaim() {
        RobotRegistry r = new RobotRegistry(null);
        ResourceId id = resource(1, 2, 3);

        r.take(id, ROBOT_A);
        r.release(id);

        Assertions.assertEquals(EntityRobotBase.NULL_ROBOT_ID, r.rawHolderOf(id), "release clears the holder");
        Assertions.assertFalse(r.resourcesReservedBy(ROBOT_A).contains(id), "release clears the reverse index");
        Assertions.assertTrue(r.take(id, ROBOT_B), "a freed resource can be re-claimed by another robot");
    }

    @Test
    public void testReverseIndexTracksAllOfARobotsResources() {
        RobotRegistry r = new RobotRegistry(null);
        ResourceId idA = resource(1, 2, 3);
        ResourceId idB = resource(4, 5, 6);

        r.take(idA, ROBOT_A);
        r.take(idB, ROBOT_A);

        Assertions.assertEquals(2, r.resourcesReservedBy(ROBOT_A).size(),
                "the reverse index lists every resource a robot holds");
        Assertions.assertTrue(r.resourcesReservedBy(ROBOT_A).contains(idA));
        Assertions.assertTrue(r.resourcesReservedBy(ROBOT_A).contains(idB));
    }

    @Test
    public void testOwnerCheckedReleaseLeavesAnotherRobotsClaim() {
        // A robot that lost a block while it was unloaded (the registry freed it, another robot took it) still
        // remembers the block and releases it when its job ends; that release must not drop the new holder's claim.
        RobotRegistry r = new RobotRegistry(null);
        ResourceId id = resource(1, 2, 3);
        r.take(id, ROBOT_B);

        r.release(id, ROBOT_A);

        Assertions.assertEquals(ROBOT_B, r.rawHolderOf(id), "a release by a non-holder leaves the claim alone");
        Assertions.assertTrue(r.resourcesReservedBy(ROBOT_B).contains(id), "...and the holder's reverse index");

        r.release(id, ROBOT_B);

        Assertions.assertEquals(EntityRobotBase.NULL_ROBOT_ID, r.rawHolderOf(id), "the holder's own release frees it");
        Assertions.assertFalse(r.resourcesReservedBy(ROBOT_B).contains(id), "...and clears its reverse index");
        Assertions.assertDoesNotThrow(() -> r.release(resource(9, 9, 9), ROBOT_A), "an unheld resource is harmless");
        Assertions.assertDoesNotThrow(() -> r.release((ResourceId) null, ROBOT_A), "so is a null resource");
    }

    @Test
    public void testReleaseUnknownResourceIsNoOp() {
        RobotRegistry r = new RobotRegistry(null);
        Assertions.assertDoesNotThrow(() -> r.release(resource(9, 9, 9)), "releasing an unheld resource is harmless");
        Assertions.assertDoesNotThrow(() -> r.release(null), "releasing a null resource is harmless");
    }

    // ---- search-phase block cells: owned, so a dying robot can give them back ----

    @Test
    public void testBlockCellsAreOwnedByTheRobotThatReservedThem() {
        RobotRegistry r = new RobotRegistry(null);
        BlockPos cell = new BlockPos(1, 2, 3);
        Set<BlockPos> mine = r.getBlockReservations(ROBOT_A);
        Set<BlockPos> theirs = r.getBlockReservations(ROBOT_B);

        Assertions.assertTrue(mine.add(cell), "a free cell can be reserved");
        Assertions.assertTrue(r.getBlockReservations().contains(cell),
                "the dimension-wide set every concurrent search reads must see the cell");
        Assertions.assertFalse(theirs.contains(cell), "another robot's view holds only that robot's cells");
        Assertions.assertFalse(theirs.add(cell), "a second robot cannot reserve a reserved cell");
        Assertions.assertFalse(theirs.remove(cell), "a robot cannot unreserve a cell another robot reserved");
        Assertions.assertTrue(r.getBlockReservations().contains(cell), "...so the cell is still reserved");
        Assertions.assertTrue(r.blockCellsReservedBy(ROBOT_A).contains(cell),
                "the registry remembers whose cell it is");

        Assertions.assertTrue(mine.remove(cell), "the owner can unreserve its own cell");
        Assertions.assertFalse(r.getBlockReservations().contains(cell), "...which frees it for everyone");
        Assertions.assertTrue(r.blockCellsReservedBy(ROBOT_A).isEmpty(), "...and clears the owner index");
    }

    /** The owned view is a set of the robot's own cells, read and written alike: contains, size, iteration and
     *  removal agree with what add put there. (It used to answer size and iteration for the whole dimension while
     *  remove refused another robot's cells, so {@code isEmpty}, {@code clear} and {@code removeAll} lied.) The
     *  dimension-wide reads are the ownerless view's. */
    @Test
    public void testOwnedViewIsConsistentlyTheRobotsOwnCells() {
        RobotRegistry r = new RobotRegistry(null);
        BlockPos a1 = new BlockPos(1, 2, 3);
        BlockPos a2 = new BlockPos(4, 5, 6);
        BlockPos b1 = new BlockPos(7, 8, 9);
        Set<BlockPos> mine = r.getBlockReservations(ROBOT_A);
        mine.add(a1);
        mine.add(a2);
        r.getBlockReservations(ROBOT_B).add(b1);

        Assertions.assertEquals(2, mine.size(), "size counts the robot's own cells only");
        Assertions.assertEquals(Set.of(a1, a2), Set.copyOf(mine), "iteration yields them and nothing else");
        Assertions.assertFalse(mine.contains(b1), "contains agrees with iteration");
        Assertions.assertEquals(3, r.getBlockReservations().size(), "the ownerless view reads the whole dimension");

        java.util.Iterator<BlockPos> it = mine.iterator();
        BlockPos first = it.next();
        it.remove();
        Assertions.assertFalse(r.getBlockReservations().contains(first), "removing through the iterator frees the cell");

        mine.clear();
        Assertions.assertTrue(mine.isEmpty(), "clear frees the robot's own cells");
        Assertions.assertEquals(Set.of(b1), Set.copyOf(r.getBlockReservations()), "...and only those");
    }

    @Test
    public void testReleaseBlockReservationsFreesOnlyThatRobotsCells() {
        RobotRegistry r = new RobotRegistry(null);
        BlockPos a1 = new BlockPos(1, 2, 3);
        BlockPos a2 = new BlockPos(4, 5, 6);
        BlockPos b1 = new BlockPos(7, 8, 9);
        r.getBlockReservations(ROBOT_A).add(a1);
        r.getBlockReservations(ROBOT_A).add(a2);
        r.getBlockReservations(ROBOT_B).add(b1);

        r.releaseBlockReservations(ROBOT_A);

        Assertions.assertFalse(r.getBlockReservations().contains(a1), "A's first cell is freed");
        Assertions.assertFalse(r.getBlockReservations().contains(a2), "A's second cell is freed");
        Assertions.assertTrue(r.getBlockReservations().contains(b1), "B's cell is untouched");
        Assertions.assertTrue(r.blockCellsReservedBy(ROBOT_A).isEmpty(), "A's owner index is clean");
        Assertions.assertTrue(r.blockCellsReservedBy(ROBOT_B).contains(b1), "B's owner index is intact");
        Assertions.assertTrue(r.getBlockReservations(ROBOT_B).add(a1),
                "a freed cell can be reserved by someone else");
    }

    /** The ownerless accessor is a read-only window: a cell added or removed through it would bypass the owner index,
     *  so {@link RobotRegistry#releaseBlockReservations} (which frees by position) could later free a cell that
     *  another robot had re-reserved in between, or never free one at all. */
    @Test
    public void testRawBlockReservationSetIsReadOnly() {
        RobotRegistry r = new RobotRegistry(null);
        BlockPos cell = new BlockPos(1, 2, 3);
        BlockPos other = new BlockPos(4, 5, 6);
        Set<BlockPos> raw = r.getBlockReservations();
        r.getBlockReservations(ROBOT_A).add(cell);

        Assertions.assertTrue(raw.contains(cell), "the raw window sees an owned reservation");
        Assertions.assertEquals(1, raw.size(), "...and counts it");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> raw.add(other),
                "a cell reserved through the raw window would have no owner to free it");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> raw.remove(cell),
                "a cell freed through the raw window would stay in its owner's index");
        Assertions.assertThrows(UnsupportedOperationException.class, raw::clear,
                "...and so would every cell a bulk clear dropped");
        Assertions.assertTrue(r.blockCellsReservedBy(ROBOT_A).contains(cell) && raw.contains(cell),
                "the reservation and its owner index are untouched");
    }

    // ---- station reservation bookkeeping ----

    @Test
    public void testStationTakeAndReleaseTrackReverseIndex() {
        RobotRegistry r = new RobotRegistry(null);
        TestDockingStation station = new TestDockingStation(new BlockPos(10, 64, 10), Direction.NORTH);
        StationIndex index = new StationIndex(station);

        r.take(station, ROBOT_A);
        Assertions.assertTrue(r.stationsReservedBy(ROBOT_A).contains(index), "take records the station for the robot");

        r.release(station, ROBOT_A);
        Assertions.assertFalse(r.stationsReservedBy(ROBOT_A).contains(index), "release removes it");
    }

    @Test
    public void testRegisterAndLookUpStation() {
        RobotRegistry r = new RobotRegistry(null);
        BlockPos pos = new BlockPos(3, 5, 7);
        TestDockingStation station = new TestDockingStation(pos, Direction.EAST);

        r.registerStation(station);

        Assertions.assertSame(station, r.getStation(pos, Direction.EAST), "a registered station is found by pos+side");
        Assertions.assertNull(r.getStation(pos, Direction.WEST), "the wrong side does not match");
        Assertions.assertNull(r.getStation(new BlockPos(0, 0, 0), Direction.EAST), "the wrong pos does not match");
    }

    @Test
    public void testRegisterDuplicateStationThrows() {
        RobotRegistry r = new RobotRegistry(null);
        BlockPos pos = new BlockPos(3, 5, 7);
        r.registerStation(new TestDockingStation(pos, Direction.EAST));

        Assertions.assertThrows(InvalidParameterException.class,
                () -> r.registerStation(new TestDockingStation(pos, Direction.EAST)),
                "two stations at the same pos+side cannot both register");
    }

    // ---- "holds no content": what lets the 1.21.x data replace the empty file a 26.x build wrote ----

    @Test
    public void testOnlyStationsAndReservationsCountAsContent() {
        RobotRegistry r = new RobotRegistry(null);
        Assertions.assertTrue(r.hasNoContent(), "a new registry holds nothing");
        r.getNextRobotId();
        Assertions.assertTrue(r.hasNoContent(), "the id counter alone is not content");

        r.registerStation(new TestDockingStation(new BlockPos(3, 5, 7), Direction.EAST));
        Assertions.assertFalse(r.hasNoContent(), "a docking station is content");

        RobotRegistry reserving = new RobotRegistry(null);
        reserving.take(resource(1, 2, 3), ROBOT_A);
        Assertions.assertFalse(reserving.hasNoContent(), "a resource reservation is content");
        reserving.release(resource(1, 2, 3));
        Assertions.assertTrue(reserving.hasNoContent(), "...and no longer once it is released");
    }

    @Test
    public void testAbsorbingAnEmptyRegistryOnlyEverRaisesTheCounter() {
        RobotRegistry recovered = new RobotRegistry(null);
        RobotRegistry empty = new RobotRegistry(null);
        empty.getNextRobotId();
        empty.getNextRobotId();
        empty.getNextRobotId();

        recovered.absorbBlank(empty);
        Assertions.assertEquals(empty.nextRobotIdPeek(), recovered.nextRobotIdPeek(),
                "the three ids the empty registry handed out are not given out again");

        recovered.getNextRobotId();
        long ahead = recovered.nextRobotIdPeek();
        recovered.absorbBlank(empty);
        Assertions.assertEquals(ahead, recovered.nextRobotIdPeek(), "a counter further along is never lowered");
    }

    // ---- SavedData NBT round-trip incl. reverse-index rebuild (seam c) ----

    @Test
    public void testResourceReservationsSurviveRoundTripWithReverseIndexRebuilt() {
        RobotRegistry before = new RobotRegistry(null);
        ResourceId idA = resource(1, 2, 3);
        ResourceId idB = resource(4, 5, 6);
        // Holders are ids the counter really issued: a registry that names an id the counter has not passed is a
        // damaged one, and the load raises the counter past it (see the counter tests below).
        long robotA = before.getNextRobotId();
        long robotB = before.getNextRobotId();
        before.take(idA, robotA);
        before.take(idB, robotB);
        long expectedNext = before.nextRobotIdPeek();

        CompoundTag tag = new CompoundTag();
        before.writeToNbt(tag);

        RobotRegistry after = new RobotRegistry(null);
        after.readFromNbt(tag);

        Assertions.assertEquals(expectedNext, after.nextRobotIdPeek(), "nextRobotID persists");
        Assertions.assertEquals(robotA, after.rawHolderOf(idA), "resource A's holder persists");
        Assertions.assertEquals(robotB, after.rawHolderOf(idB), "resource B's holder persists");
        // The reverse index is not persisted directly; readFromNbt must rebuild it via take().
        Assertions.assertTrue(after.resourcesReservedBy(robotA).contains(idA), "reverse index rebuilt for A");
        Assertions.assertTrue(after.resourcesReservedBy(robotB).contains(idB), "reverse index rebuilt for B");
    }

    // ---- the id counter must never fall behind an id the world still uses ----

    /** A world whose registry file was lost or reset (a 1.21.x -> 26.x upgrade used to orphan it) restarts the counter
     *  at its default while the robots keep the ids they were saved with. The data the registry DID load still names
     *  some of those ids (a reservation, a station link); the next robot must not be handed one of them. */
    @Test
    public void testLoadRaisesTheCounterPastEveryIdTheDataStillNames() {
        long heldResource = Long.MIN_VALUE + 5;
        long linkedStation = Long.MIN_VALUE + 9;
        BlockPos pos = new BlockPos(8, 64, 9);
        TestDockingStation station = new TestDockingStation(pos, Direction.SOUTH);
        CompoundTag stationTag = new CompoundTag();
        station.writeToNbt(stationTag);
        stationTag.putLong("robotId", linkedStation);
        TestDockingStation linked = new TestDockingStation();
        linked.readFromNbt(stationTag);

        RobotRegistry saved = new RobotRegistry(null);
        saved.take(resource(1, 2, 3), heldResource);
        saved.registerStation(linked);
        CompoundTag tag = new CompoundTag();
        saved.writeToNbt(tag);
        Assertions.assertEquals(Long.MIN_VALUE, NBTUtilBC.getLong(tag, "nextRobotID", 0L),
                "precondition: the saved counter is back at its default, behind the ids the data names");

        RobotRegistry loaded = new RobotRegistry(null);
        loaded.readFromNbt(tag);

        Assertions.assertEquals(linkedStation + 1, loaded.nextRobotIdPeek(),
                "the counter moves to one past the highest id the registry names");
        Assertions.assertEquals(linkedStation + 1, loaded.getNextRobotId(), "...so that is the next id handed out");
    }

    @Test
    public void testLoadKeepsAHigherPersistedCounter() {
        RobotRegistry saved = new RobotRegistry(null);
        long robot = saved.getNextRobotId();
        saved.take(resource(1, 2, 3), robot);
        for (int i = 0; i < 100; i++) {
            saved.getNextRobotId(); // robots that have since been killed: their ids are spent, not reusable
        }
        long counter = saved.nextRobotIdPeek();
        CompoundTag tag = new CompoundTag();
        saved.writeToNbt(tag);

        RobotRegistry loaded = new RobotRegistry(null);
        loaded.readFromNbt(tag);

        Assertions.assertEquals(counter, loaded.nextRobotIdPeek(),
                "the persisted counter wins when it is already past every id in the data");
    }

    /** The null id is {@code Long.MAX_VALUE}: counting it as "the highest id seen" would overflow the counter. */
    @Test
    public void testLoadIgnoresTheNullRobotId() {
        RobotRegistry saved = new RobotRegistry(null);
        saved.take(resource(1, 2, 3), EntityRobotBase.NULL_ROBOT_ID);
        CompoundTag tag = new CompoundTag();
        saved.writeToNbt(tag);

        RobotRegistry loaded = new RobotRegistry(null);
        loaded.readFromNbt(tag);

        Assertions.assertEquals(Long.MIN_VALUE, loaded.nextRobotIdPeek(), "the null id is not a robot");
    }

    @Test
    public void testLoadWithoutAnyRobotIdsKeepsTheDefaultCounter() {
        RobotRegistry loaded = new RobotRegistry(null);
        loaded.readFromNbt(new CompoundTag());

        Assertions.assertEquals(Long.MIN_VALUE, loaded.nextRobotIdPeek());
    }

    @Test
    public void testLinkedStationSurvivesRoundTripAndRebuildsStationReverseIndex() {
        BlockPos pos = new BlockPos(8, 64, 9);
        Direction side = Direction.SOUTH;

        // Craft a station persisted as linked to ROBOT_A. A real link is set by an entity (Ph2/Ph3); here we
        // hand-build the NBT so the load path's `if linkedId != NULL -> take(station, linkedId)` is exercised.
        TestDockingStation seed = new TestDockingStation(pos, side);
        CompoundTag stationTag = new CompoundTag();
        seed.writeToNbt(stationTag);
        stationTag.putLong("robotId", ROBOT_A);
        TestDockingStation linked = new TestDockingStation();
        linked.readFromNbt(stationTag);
        Assertions.assertEquals(ROBOT_A, linked.linkedId(), "precondition: the crafted station reads back as linked");

        RobotRegistry before = new RobotRegistry(null);
        before.registerStation(linked);

        CompoundTag tag = new CompoundTag();
        before.writeToNbt(tag);

        RobotRegistry after = new RobotRegistry(null);
        after.readFromNbt(tag);

        DockingStation reloaded = after.getStation(pos, side);
        Assertions.assertNotNull(reloaded, "the station persists and reloads at its pos+side");
        Assertions.assertEquals(ROBOT_A, reloaded.linkedId(), "its link id persists");
        Assertions.assertTrue(after.stationsReservedBy(ROBOT_A).contains(new StationIndex(side, pos)),
                "the station reverse index is rebuilt on load");
    }

    // ---- station tag shapes: "free" must read back as free ----

    /** A station tag as 7.1.x wrote it (and so every 1.7.10 world a robot ever lived in): the position as an
     *  {@code index} compound of {@code i/j/k}, then side, isMain and robotId, with {@code Long.MAX_VALUE} - the null
     *  robot id - standing for "no robot". Every shipped build wrote the sentinel; none ever wrote 0 or -1. */
    private static CompoundTag upstreamShapedStationTag(BlockPos pos, int side, boolean main, long robotId) {
        CompoundTag index = new CompoundTag();
        index.putInt("i", pos.getX());
        index.putInt("j", pos.getY());
        index.putInt("k", pos.getZ());
        CompoundTag tag = new CompoundTag();
        tag.put("index", index);
        tag.putByte("side", (byte) side);
        tag.putBoolean("isMain", main);
        tag.putLong("robotId", robotId);
        return tag;
    }

    /** The record without the robot-id key. The null id is the only meaning "no robot" ever had, so an absent key
     *  must read as the null id too: reading it as 0 made the station look held by a robot that never existed, and
     *  {@code take()} - which decides by the holder id - refused every robot for good. */
    private static CompoundTag stationTagWithoutARobotId(BlockPos pos, Direction side) {
        CompoundTag tag = new CompoundTag();
        new TestDockingStation(pos, side).writeToNbt(tag);
        tag.remove("robotId");
        Assertions.assertFalse(tag.contains("robotId"), "precondition: the key is really gone");
        return tag;
    }

    private static void assertFree(DockingStation station, String what) {
        Assertions.assertFalse(station.isTaken(), what + ": not taken");
        Assertions.assertEquals(EntityRobotBase.NULL_ROBOT_ID, station.linkedId(), what + ": links no robot");
        Assertions.assertEquals(EntityRobotBase.NULL_ROBOT_ID, station.robotIdTaking(), what + ": has no holder id");
        Assertions.assertNull(station.robotTaking(), what + ": resolves to no robot");
        Assertions.assertFalse(station.isMainStation(), what + ": is nobody's home");
    }

    @Test
    public void testStationTagWithoutARobotIdLoadsFree() {
        BlockPos pos = new BlockPos(8, 64, 9);
        TestDockingStation loaded = new TestDockingStation();
        loaded.readFromNbt(stationTagWithoutARobotId(pos, Direction.SOUTH));

        assertFree(loaded, "a station saved without a robot id");
        Assertions.assertEquals(pos, loaded.getPos(), "the rest of the record still reads");
        Assertions.assertEquals(Direction.SOUTH, loaded.side(), "...its side too");
    }

    @Test
    public void testFreeStationRoundTripsFree() {
        TestDockingStation free = new TestDockingStation(new BlockPos(8, 64, 9), Direction.EAST);
        CompoundTag tag = new CompoundTag();
        free.writeToNbt(tag);
        Assertions.assertEquals(EntityRobotBase.NULL_ROBOT_ID, NBTUtilBC.getLong(tag, "robotId", 0L),
                "a free station is written with the null id, the sentinel every build has written");

        TestDockingStation loaded = new TestDockingStation();
        loaded.readFromNbt(tag);

        assertFree(loaded, "a free station read back");
    }

    @Test
    public void testUpstreamShapedFreeStationTagLoadsFree() {
        BlockPos pos = new BlockPos(-3, 70, 12);
        TestDockingStation loaded = new TestDockingStation();
        loaded.readFromNbt(upstreamShapedStationTag(pos, Direction.WEST.ordinal(), false,
                EntityRobotBase.NULL_ROBOT_ID));

        assertFree(loaded, "a free station in the 7.1.x tag shape");
        Assertions.assertEquals(pos, loaded.getPos(), "the legacy index compound still gives the position");
        Assertions.assertEquals(Direction.WEST, loaded.side());
    }

    @Test
    public void testUpstreamShapedLinkedStationTagsKeepTheirLink() {
        BlockPos pos = new BlockPos(-3, 70, 12);
        long robot = Long.MIN_VALUE + 7;

        TestDockingStation home = new TestDockingStation();
        home.readFromNbt(upstreamShapedStationTag(pos, Direction.UP.ordinal(), true, robot));
        Assertions.assertTrue(home.isTaken(), "a station saved with a robot id is taken");
        Assertions.assertEquals(robot, home.linkedId(), "...by that robot");
        Assertions.assertTrue(home.isMainStation(), "...as its home when saved as one");

        TestDockingStation visit = new TestDockingStation();
        visit.readFromNbt(upstreamShapedStationTag(pos, Direction.UP.ordinal(), false, robot));
        Assertions.assertEquals(robot, visit.linkedId());
        Assertions.assertFalse(visit.isMainStation(), "...and as a plain claim otherwise");
    }

    /** Loaded into a registry, a station with no robot id must not be filed as held by a robot: the load path
     *  indexes every station whose link is not the null id, and raises the id counter past every id it finds. */
    @Test
    public void testRegistryLoadsAStationWithoutARobotIdAsFreeAndUnindexed() {
        BlockPos pos = new BlockPos(8, 64, 9);
        CompoundTag stationTag = stationTagWithoutARobotId(pos, Direction.SOUTH);
        stationTag.putString("stationType", "test_station");
        ListTag stationList = new ListTag();
        stationList.add(stationTag);
        CompoundTag registryTag = new CompoundTag();
        registryTag.put("stationList", stationList);

        RobotRegistry loaded = new RobotRegistry(null);
        loaded.readFromNbt(registryTag);

        DockingStation station = loaded.getStation(pos, Direction.SOUTH);
        Assertions.assertNotNull(station, "the station loads");
        assertFree(station, "the loaded station");
        Assertions.assertTrue(loaded.stationsReservedBy(0L).isEmpty(), "no robot 0 is credited with it");
        Assertions.assertEquals(Long.MIN_VALUE, loaded.nextRobotIdPeek(),
                "the id counter is not dragged up to a robot id that was never issued");
    }

    /** Minimal concrete {@link DockingStation} for registry tests: a public no-arg ctor for NBT reload plus a
     * positioned ctor, and an empty action list. No {@code Level} is required for any path exercised here. */
    public static class TestDockingStation extends DockingStation {
        public TestDockingStation() {
            super();
        }

        public TestDockingStation(BlockPos pos, Direction side) {
            super(pos, side);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }
    }
}
