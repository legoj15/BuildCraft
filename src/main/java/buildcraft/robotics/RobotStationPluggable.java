/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IDockingStationProvider;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.api.transport.pluggable.PluggableModelKey;

import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.robotics.client.model.key.KeyPlugRobotStation;

import org.jspecify.annotations.Nullable;

/**
 * Docking-station pluggable, ported from 7.1.x {@code buildcraft.robotics.RobotStationPluggable}.
 * Registers/deregisters its {@link DockingStationPipe} against the {@link buildcraft.api.robots.RobotRegistry}
 * lazily on first server-side tick (modern {@link PipePluggable} has no {@code validate()} hook to piggyback
 * on the way 7.1.x did) and on {@link #onRemove()}, and exposes an MJ receiver capability that only accepts
 * power while a robot is actually <em>docked</em> (not merely reserving) — {@code station.robotTaking()} can
 * be non-null for a robot that has reserved but not yet arrived.
 */
public class RobotStationPluggable extends PipePluggable implements IDockingStationProvider {

    /** (min, max) lateral span, and (near, far) protrusion depth from the mounting face — a small
     *  post distinct from {@code PluggablePowerAdaptor}'s wider slab. */
    private static final AABB[] BOXES = new AABB[6];
    static {
        double min = 5 / 16.0;
        double max = 11 / 16.0;
        double near = 0 / 16.0;
        double far = 4 / 16.0;

        BOXES[Direction.DOWN.ordinal()] = new AABB(min, near, min, max, far, max);
        BOXES[Direction.UP.ordinal()] = new AABB(min, 1 - far, min, max, 1 - near, max);
        BOXES[Direction.NORTH.ordinal()] = new AABB(min, min, near, max, max, far);
        BOXES[Direction.SOUTH.ordinal()] = new AABB(min, min, 1 - far, max, max, 1 - near);
        BOXES[Direction.WEST.ordinal()] = new AABB(near, min, min, far, max, max);
        BOXES[Direction.EAST.ordinal()] = new AABB(1 - far, min, min, 1 - near, max, max);
    }

    public enum RobotStationState {
        NONE,
        AVAILABLE,
        RESERVED,
        LINKED
    }

    /** Package-private (not private) so pure-JUnit tests in this package can inject a station directly,
     *  bypassing {@link #onTick()}'s {@code RobotRegistry}/{@code Level} dependency. */
    @Nullable DockingStationPipe station;
    private boolean isValid = false;
    private RobotStationState renderState;

    public RobotStationPluggable(PluggableDefinition definition, IPipeHolder holder, Direction side) {
        super(definition, holder, side);
    }

    /** Static lookup for the per-side {@linkplain #getBoundingBox() bounding box} — used by the
     *  placement-preview outline to size the highlight correctly without instantiating. */
    public static AABB boundingBoxFor(Direction side) {
        return BOXES[side.ordinal()];
    }

    @Override
    public AABB getBoundingBox() {
        return boundingBoxFor(side);
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCRoboticsItems.ROBOT_STATION.get());
    }

    @Override
    public DockingStation getStation() {
        return station;
    }

    @Override
    public void onTick() {
        if (isValid) {
            return;
        }
        Level world = holder.getPipeWorld();
        if (world == null || world.isClientSide()) {
            return;
        }
        DockingStationPipe existing =
            (DockingStationPipe) RobotManager.registryProvider.getRegistry(world).getStation(holder.getPipePos(), side);
        if (existing == null) {
            station = new DockingStationPipe(holder, side);
            RobotManager.registryProvider.getRegistry(world).registerStation(station);
        } else {
            station = existing;
        }
        isValid = true;
    }

    @Override
    public void onRemove() {
        if (!isValid || station == null) {
            return;
        }
        Level world = holder.getPipeWorld();
        if (world != null && !world.isClientSide()) {
            RobotManager.registryProvider.getRegistry(world).removeStation(station);
        }
        isValid = false;
    }

    /** @return The robot actually docked here (not merely reserving the station), or null. */
    @Nullable
    private EntityRobotBase dockedRobot() {
        if (station == null) {
            return null;
        }
        EntityRobotBase robot = station.robotTaking();
        if (robot == null || robot.getBattery() == null) {
            return null;
        }
        return robot.getDockingStation() == station ? robot : null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(@Nonnull Object cap) {
        if (cap == MjAPI.CAP_RECEIVER) {
            EntityRobotBase robot = dockedRobot();
            if (robot != null) {
                return (T) new MjBatteryReceiver(robot.getBattery());
            }
        }
        return null;
    }

    private void refreshRenderState() {
        if (station == null) {
            renderState = RobotStationState.NONE;
            return;
        }
        renderState = station.isTaken()
            ? (station.isMainStation() ? RobotStationState.LINKED : RobotStationState.RESERVED)
            : RobotStationState.AVAILABLE;
    }

    public RobotStationState getRenderState() {
        refreshRenderState();
        return renderState == null ? RobotStationState.NONE : renderState;
    }

    @Nullable
    @Override
    public PluggableModelKey getModelRenderKey(Object layer) {
        if ("cutout".equals(layer)) {
            return new KeyPlugRobotStation(side);
        }
        return null;
    }
}
