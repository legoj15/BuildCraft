/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IDockingStationProvider;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pluggable.PipePluggable;

/**
 * Pure-JUnit characterization of {@link RobotUtils#getStations}, ported from 7.1.x
 * {@code buildcraft.robotics.RobotUtils#getStations}. Exercises both discovery paths — the tile itself
 * providing a station, and per-side pluggables on an {@link IPipeHolder} providing one — without a live
 * {@code Level}, mirroring {@link RobotRegistryTest}'s {@code TestDockingStation} stub pattern.
 */
public class RobotUtilsTest {

    private static DockingStation station(BlockPos pos, Direction side) {
        return new RobotRegistryTest.TestDockingStation(pos, side);
    }

    @Test
    public void tileItselfProvidingAStationIsFound() {
        DockingStation station = station(new BlockPos(1, 2, 3), Direction.UP);
        IDockingStationProvider provider = () -> station;

        List<DockingStation> found = RobotUtils.getStations(provider);

        Assertions.assertEquals(List.of(station), found);
    }

    @Test
    public void tileItselfWithNoStationYieldsEmpty() {
        IDockingStationProvider provider = () -> null;

        Assertions.assertTrue(RobotUtils.getStations(provider).isEmpty());
    }

    @Test
    public void objectThatIsNeitherProviderNorHolderYieldsEmpty() {
        Assertions.assertTrue(RobotUtils.getStations(new Object()).isEmpty());
    }

    @Test
    public void pluggableProvidingAStationOnOneSideIsFound() {
        DockingStation station = station(new BlockPos(4, 5, 6), Direction.NORTH);
        StubHolder holder = new StubHolder();
        holder.pluggables.put(Direction.NORTH, stationPluggable(station));

        List<DockingStation> found = RobotUtils.getStations(holder);

        Assertions.assertEquals(List.of(station), found);
    }

    @Test
    public void pluggablesOnMultipleSidesAreAllFound() {
        DockingStation stationA = station(new BlockPos(0, 0, 0), Direction.UP);
        DockingStation stationB = station(new BlockPos(0, 0, 0), Direction.DOWN);
        StubHolder holder = new StubHolder();
        holder.pluggables.put(Direction.UP, stationPluggable(stationA));
        holder.pluggables.put(Direction.DOWN, stationPluggable(stationB));
        holder.pluggables.put(Direction.NORTH, nonProvidingPluggable());

        List<DockingStation> found = RobotUtils.getStations(holder);

        Assertions.assertEquals(2, found.size());
        Assertions.assertTrue(found.contains(stationA));
        Assertions.assertTrue(found.contains(stationB));
    }

    @Test
    public void holderWithNoProvidingPluggablesYieldsEmpty() {
        StubHolder holder = new StubHolder();
        holder.pluggables.put(Direction.EAST, nonProvidingPluggable());

        Assertions.assertTrue(RobotUtils.getStations(holder).isEmpty());
    }

    private static PipePluggable stationPluggable(DockingStation station) {
        return new StubPluggable(Direction.UP) {
            @Override
            public DockingStation getStationForTest() {
                return station;
            }
        };
    }

    private static PipePluggable nonProvidingPluggable() {
        return new StubPluggable(Direction.UP);
    }

    /** Minimal {@link PipePluggable} that optionally implements {@link IDockingStationProvider} via an
     *  overridable hook — a real docking pluggable would return non-null from {@code getStation()}. */
    private static class StubPluggable extends PipePluggable implements IDockingStationProvider {
        StubPluggable(Direction side) {
            super(null, null, side);
        }

        DockingStation getStationForTest() {
            return null;
        }

        @Override
        public DockingStation getStation() {
            return getStationForTest();
        }

        @Override
        public net.minecraft.world.phys.AABB getBoundingBox() {
            return null;
        }
    }

    /** Minimal {@link IPipeHolder} stub — RobotUtils only calls {@link IPipeHolder#getPluggable}, so
     *  every other method is unreachable and throws if ever invoked. */
    private static class StubHolder implements IPipeHolder {
        final java.util.Map<Direction, PipePluggable> pluggables = new java.util.EnumMap<>(Direction.class);

        @Override
        public PipePluggable getPluggable(Direction side) {
            return pluggables.get(side);
        }

        @Override
        public int getRedstoneInput(Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setRedstoneOutput(Direction side, int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Level getPipeWorld() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockPos getPipePos() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockEntity getPipeTile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IPipe getPipe() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canPlayerInteract(Player player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockEntity getNeighbourTile(Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IPipe getNeighbourPipe(Direction side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getCapabilityFromPipe(Direction side, Object capability) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IWireManager getWireManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.mojang.authlib.GameProfile getOwner() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean fireEvent(PipeEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void scheduleRenderUpdate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void scheduleNetworkUpdate(PipeMessageReceiver... parts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void scheduleNetworkGuiUpdate(PipeMessageReceiver... parts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendMessage(PipeMessageReceiver to, IWriter writer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendGuiMessage(PipeMessageReceiver to, IWriter writer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPlayerOpen(Player player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPlayerClose(Player player) {
            throw new UnsupportedOperationException();
        }
    }
}
