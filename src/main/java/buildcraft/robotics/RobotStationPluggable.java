/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IDockingStationProvider;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.api.transport.pluggable.PluggableModelKey;

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

    /** (min, max) lateral span, and (near, far) protrusion depth from the mounting face. Laterally
     *  identical to {@code PluggableBlocker}'s and deeper — it wraps the pedestal's 8×8 plate. 7.1.x
     *  boxed the plate alone, leaving the post unselectable; covering the whole pedestal instead is a
     *  deliberate divergence, so a click anywhere on the visible shape hits the station. */
    private static final AABB[] BOXES = new AABB[6];
    static {
        double min = 4 / 16.0;
        double max = 12 / 16.0;
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

    /** Client-side mirror of {@link #getRenderState()}. The client never resolves a {@link #station}
     *  ({@link #onTick()} is server-only), so without this the state is permanently
     *  {@link RobotStationState#NONE} and a reserved or linked station never looks any different from
     *  a free one. */
    private RobotStationState syncedState = RobotStationState.NONE;
    /** Last state pushed to clients, so {@link #onTick()} only sends on an actual transition. */
    private RobotStationState lastSentState = RobotStationState.NONE;

    public RobotStationPluggable(PluggableDefinition definition, IPipeHolder holder, Direction side) {
        super(definition, holder, side);
    }

    /** NBT path. The indicator is derived from the {@code RobotRegistry} server-side and re-sent to
     *  clients, so there is deliberately nothing of it to persist. */
    public RobotStationPluggable(PluggableDefinition definition, IPipeHolder holder, Direction side,
        CompoundTag nbt) {
        super(definition, holder, side);
    }

    /** Network path — this is the constructor the client actually gets. */
    public RobotStationPluggable(PluggableDefinition definition, IPipeHolder holder, Direction side,
        FriendlyByteBuf buffer) {
        super(definition, holder, side);
        readData(buffer);
    }

    private void writeData(FriendlyByteBuf buffer) {
        buffer.writeByte(getRenderState().ordinal());
    }

    private void readData(FriendlyByteBuf buffer) {
        int ordinal = buffer.readByte() & 0xFF;
        RobotStationState[] values = RobotStationState.values();
        syncedState = ordinal < values.length ? values[ordinal] : RobotStationState.NONE;
    }

    @Override
    public void writeCreationPayload(FriendlyByteBuf buffer) {
        super.writeCreationPayload(buffer);
        writeData(buffer);
    }

    @Override
    public void writePayload(FriendlyByteBuf buffer, Object side) {
        super.writePayload(buffer, side);
        writeData(buffer);
    }

    @Override
    public void readPayload(FriendlyByteBuf buffer, Object side, Object ctx) throws java.io.IOException {
        super.readPayload(buffer, side, ctx);
        readData(buffer);
        // Deliberately NO scheduleRenderUpdate() here, unlike PluggablePulsar: the reserved/linked
        // pedestal is re-emitted per-frame by PlugRobotStationRenderer and is kept OUT of the baked
        // model key on purpose (see KeyPlugRobotStation), so a dock/undock must not trigger a chunk
        // re-mesh.
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
        Level world = holder.getPipeWorld();
        if (world == null || world.isClientSide()) {
            return;
        }
        if (!isValid) {
            DockingStationPipe existing = (DockingStationPipe) RobotManager.registryProvider.getRegistry(world)
                .getStation(holder.getPipePos(), side);
            if (existing == null) {
                station = new DockingStationPipe(holder, side);
                RobotManager.registryProvider.getRegistry(world).registerStation(station);
            } else {
                station = existing;
            }
            isValid = true;
        }
        // Push the indicator colour on transition only. Cheap (one enum compare per tick) and the
        // only way the client ever learns it — the station lives in a server-side SavedData that the
        // client has no copy of, and none of it is written to the pluggable's NBT.
        RobotStationState current = getRenderState();
        if (current != lastSentState) {
            lastSentState = current;
            holder.sendMessage(IPipeHolder.PipeMessageReceiver.PLUGGABLES[side.ordinal()], this::writeData);
        }
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

    /** The docked robot's own charge receiver, or null if nothing is docked (or the robot declines to supply
     *  one). Going through the robot rather than wrapping its battery here is what lets {@code EntityRobot}
     *  latch "I am charging right now" for the sleep indicator — and the null-guard chain has to extend all
     *  the way to the receiver, because an NPE on this path surfaces as a pipe-tick crash rather than
     *  anything that looks like a robot bug. Identity is not cached anywhere: every call site refetches. */
    @Nullable
    private IMjReceiver chargeReceiver() {
        EntityRobotBase robot = dockedRobot();
        return robot == null ? null : robot.getChargeReceiver();
    }

    /** Outward face: what a machine placed directly against this station queries. */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(@Nonnull Object cap) {
        if (cap == MjAPI.CAP_RECEIVER) {
            IMjReceiver receiver = chargeReceiver();
            if (receiver != null) {
                return (T) receiver;
            }
        }
        return null;
    }

    /** Inward face: what the HOST PIPE queries, via {@code TilePipeHolder.getCapabilityFromPipe}.
     * Without this a kinesis pipe can never deliver to a docked robot — {@code getCapability} above
     * is only ever consulted from outside the pipe, so the pipe's own {@code PipeFlowPower} sees
     * nothing here and the robot silently never charges. Note that lookup deliberately checks this
     * method <em>before</em> short-circuiting on {@link #isBlocking()}, which is what lets a blocking
     * pluggable still be a power destination.
     *
     * <p>Gated on {@link #dockedRobot()} exactly like {@link #getCapability}, so a robot that has
     * merely reserved the station (but not arrived) still receives nothing. */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getInternalCapability(@Nonnull Object cap) {
        if (cap == MjAPI.CAP_RECEIVER) {
            IMjReceiver receiver = chargeReceiver();
            if (receiver != null) {
                return (T) receiver;
            }
        }
        return null;
    }

    private void refreshRenderState() {
        if (station == null) {
            // No station resolved: either the client's copy (which never resolves one) or a
            // server-side pluggable whose first onTick() hasn't run yet. Fall back to whatever the
            // network last told us — NONE until the first push arrives.
            renderState = syncedState;
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
