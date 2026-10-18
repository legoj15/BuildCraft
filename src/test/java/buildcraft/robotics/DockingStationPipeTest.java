/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.RobotManager;

/**
 * Dock-ref round-trip characterization for {@link DockingStationPipe}: the (pos, side) identity, the
 * unlinked sentinel, and the "pipe" registration name {@link RobotRegistry#writeToNbt} needs to avoid
 * silently dropping a pipe-hosted station on save (missing registration -> logged warning + skip).
 */
public class DockingStationPipeTest {

    @BeforeAll
    static void registerRealDockingStationType() {
        // BCRobotics.init does this in production; tests run without mod init, so register directly —
        // this is the exact registration whose absence would make RobotRegistry.writeToNbt drop the station.
        if (RobotManager.getDockingStationByName("pipe") == null) {
            RobotManager.registerDockingStation(DockingStationPipe.class, "pipe");
        }
    }

    @Test
    public void pipeDockingStationTypeIsRegisteredUnderPipeName() {
        Assertions.assertEquals(DockingStationPipe.class, RobotManager.getDockingStationByName("pipe"),
                "the \"pipe\" name must resolve back to DockingStationPipe, or RobotRegistry can't reconstruct it on load");
        Assertions.assertEquals("pipe", RobotManager.getDockingStationName(DockingStationPipe.class),
                "and the reverse lookup (used by writeToNbt) must agree");
    }

    @Test
    public void noArgConstructorLeavesPosAndSideUnsetUntilNbtIsRead() {
        // RobotRegistry.readFromNbt reconstructs stations via cls.getDeclaredConstructor().newInstance()
        // then calls readFromNbt — the no-arg ctor must exist and must not touch pos/side/world itself.
        DockingStationPipe station = new DockingStationPipe();
        Assertions.assertNull(station.getPos(), "pos is only set by readFromNbt on this path");
    }

    @Test
    public void nbtRoundTripPreservesPosAndSide() {
        BlockPos pos = new BlockPos(12, 64, -3);
        Direction side = Direction.SOUTH;

        // A positioned DockingStationPipe normally needs a live IPipeHolder; writeToNbt/readFromNbt
        // never touch the holder, so a stub with just pos+side (via the protected DockingStation ctor
        // through RobotRegistryTest.TestDockingStation) characterizes the same base-class round trip
        // DockingStationPipe inherits unmodified.
        DockingStation before = new RobotRegistryTest.TestDockingStation(pos, side);
        CompoundTag tag = new CompoundTag();
        before.writeToNbt(tag);

        DockingStationPipe after = new DockingStationPipe();
        after.readFromNbt(tag);

        Assertions.assertEquals(pos, after.getPos());
        Assertions.assertEquals(side, after.side());
    }

    @Test
    public void missingRobotIdFieldReadsAsFree() {
        // "No robot" is the null id (Long.MAX_VALUE) and nothing else - every build that wrote a station wrote it
        // for a free one - so a record without the key (hand-built, damaged) is a free station. It used to default
        // to 0L, which read as taken by a robot that never existed and refused every robot (take() decides by id).
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("pos", new int[] { 1, 2, 3 });
        tag.putByte("side", (byte) Direction.UP.ordinal());
        // Deliberately no "robotId" key.

        DockingStationPipe station = new DockingStationPipe();
        station.readFromNbt(tag);

        Assertions.assertEquals(EntityRobotBase.NULL_ROBOT_ID, station.linkedId(), "missing robotId is the sentinel");
        Assertions.assertFalse(station.isTaken(), "...so the station is free");
    }

    @Test
    public void explicitSentinelRobotIdReadsAsNotTaken() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("pos", new int[] { 1, 2, 3 });
        tag.putByte("side", (byte) Direction.DOWN.ordinal());
        tag.putLong("robotId", EntityRobotBase.NULL_ROBOT_ID);

        DockingStationPipe station = new DockingStationPipe();
        station.readFromNbt(tag);

        Assertions.assertFalse(station.isTaken(), "the explicit sentinel is the only NBT-representable \"never taken\" state");
    }
}
