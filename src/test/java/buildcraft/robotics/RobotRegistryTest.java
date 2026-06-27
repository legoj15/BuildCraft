/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.security.InvalidParameterException;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.ResourceId;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.StatementSlot;

/**
 * Pure-JUnit characterization of {@link RobotRegistry}'s reservation bookkeeping and NBT round-trip — the
 * reservation maps and their reverse indices, exercised without a live robot entity. The entity-gated paths
 * ({@code robotIdTaking}/{@code isTaken} loaded-and-alive checks, {@code killRobot} death-frees-all) are
 * deferred to Ph3 game tests; here we drive the id-keyed core through {@code take}/{@code release}, the
 * package-private raw inspectors, and the decoupled {@code writeToNBT}/{@code readFromNBT} (seam c).
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
    public void testReleaseUnknownResourceIsNoOp() {
        RobotRegistry r = new RobotRegistry(null);
        Assertions.assertDoesNotThrow(() -> r.release(resource(9, 9, 9)), "releasing an unheld resource is harmless");
        Assertions.assertDoesNotThrow(() -> r.release(null), "releasing a null resource is harmless");
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

    // ---- SavedData NBT round-trip incl. reverse-index rebuild (seam c) ----

    @Test
    public void testResourceReservationsSurviveRoundTripWithReverseIndexRebuilt() {
        RobotRegistry before = new RobotRegistry(null);
        ResourceId idA = resource(1, 2, 3);
        ResourceId idB = resource(4, 5, 6);
        before.take(idA, ROBOT_A);
        before.take(idB, ROBOT_B);
        before.getNextRobotId(); // advance nextRobotID off its Long.MIN_VALUE default
        long expectedNext = before.nextRobotIdPeek();

        CompoundTag tag = new CompoundTag();
        before.writeToNBT(tag);

        RobotRegistry after = new RobotRegistry(null);
        after.readFromNBT(tag);

        Assertions.assertEquals(expectedNext, after.nextRobotIdPeek(), "nextRobotID persists");
        Assertions.assertEquals(ROBOT_A, after.rawHolderOf(idA), "resource A's holder persists");
        Assertions.assertEquals(ROBOT_B, after.rawHolderOf(idB), "resource B's holder persists");
        // The reverse index is not persisted directly; readFromNBT must rebuild it via take().
        Assertions.assertTrue(after.resourcesReservedBy(ROBOT_A).contains(idA), "reverse index rebuilt for A");
        Assertions.assertTrue(after.resourcesReservedBy(ROBOT_B).contains(idB), "reverse index rebuilt for B");
    }

    @Test
    public void testLinkedStationSurvivesRoundTripAndRebuildsStationReverseIndex() {
        BlockPos pos = new BlockPos(8, 64, 9);
        Direction side = Direction.SOUTH;

        // Craft a station persisted as linked to ROBOT_A. A real link is set by an entity (Ph2/Ph3); here we
        // hand-build the NBT so the load path's `if linkedId != NULL -> take(station, linkedId)` is exercised.
        TestDockingStation seed = new TestDockingStation(pos, side);
        CompoundTag stationTag = new CompoundTag();
        seed.writeToNBT(stationTag);
        stationTag.putLong("robotId", ROBOT_A);
        TestDockingStation linked = new TestDockingStation();
        linked.readFromNBT(stationTag);
        Assertions.assertEquals(ROBOT_A, linked.linkedId(), "precondition: the crafted station reads back as linked");

        RobotRegistry before = new RobotRegistry(null);
        before.registerStation(linked);

        CompoundTag tag = new CompoundTag();
        before.writeToNBT(tag);

        RobotRegistry after = new RobotRegistry(null);
        after.readFromNBT(tag);

        DockingStation reloaded = after.getStation(pos, side);
        Assertions.assertNotNull(reloaded, "the station persists and reloads at its pos+side");
        Assertions.assertEquals(ROBOT_A, reloaded.linkedId(), "its link id persists");
        Assertions.assertTrue(after.stationsReservedBy(ROBOT_A).contains(new StationIndex(side, pos)),
                "the station reverse index is rebuilt on load");
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
