/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe.flow;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ToLongFunction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.AverageInt;

/**
 * Shared distribution engine for the two kinesis pipe flows — {@link PipeFlowPower} (BuildCraft MJ,
 * {@code long}) and {@link PipeFlowRedstoneFlux} (Forge Energy / RF, {@code int}). The two were
 * line-for-line clones; the only genuine differences are the energy-unit width and the boundary
 * against external machines (MJ's {@code IMjReceiver} vs the version-gated NeoForge energy handler),
 * so this base unifies on {@code long} internal accounting (a safe superset of {@code int}) and pushes
 * the boundary out through four subclass hooks:
 * <ul>
 *   <li>{@link #transferToExternalTile(Direction, long)} — hand power to a neighbouring machine,
 *       returning the leftover;</li>
 *   <li>{@link #queryTileDemand(Direction)} — how much a neighbouring machine wants this tick;</li>
 *   <li>{@link #applyRequestHook(Direction, long)} — let the pipe behaviour reshape a request
 *       ({@code IPipeTransportPowerHook} / {@code IPipeTransportRfHook});</li>
 *   <li>{@link #reconfigure()} + {@link #formatMaxPower()} — event-driven capacity config (MJ also
 *       derives the intentionally-inert {@code powerLoss}/{@code powerResistance} scaffolding there).</li>
 * </ul>
 * The {@code long} arithmetic in the distribution step uses {@link BigInteger} to stay overflow-safe
 * at MJ magnitudes; for the RF subclass (int-range values) this is numerically identical to the old
 * {@code int} math. NBT keys ({@code isReceiver}/{@code displayPower}/{@code displayFlow}), the custom
 * payload format, and the client-resync-through-constructor path are byte-identical to the old classes.
 */
public abstract class AbstractPipeFlowPower extends PipeFlow implements IDebuggable {
    public static final int NET_POWER_AMOUNTS = 2;

    public Vec3 clientDisplayFlowCentre = VecUtil.VEC_HALF;
    public Vec3 clientDisplayFlowCentreLast = VecUtil.VEC_HALF;
    public long clientLastDisplayTime = 0;

    /** Per-tick transfer cap; {@code -1} = not yet configured (triggers a lazy {@link #reconfigure()}). */
    protected long maxPower = -1;
    protected boolean disabled = false;

    private long currentWorldTime;

    protected boolean isReceiver = false;
    protected final EnumMap<Direction, Section> sections = new EnumMap<>(Direction.class);

    @SuppressWarnings("this-escape")
    protected AbstractPipeFlowPower(IPipe pipe) {
        super(pipe);
        for (Direction face : Direction.values()) {
            sections.put(face, createSection(face));
        }
    }

    @SuppressWarnings("this-escape")
    protected AbstractPipeFlowPower(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        for (Direction face : Direction.values()) {
            sections.put(face, createSection(face));
        }
        // The client's first-sync / relog path rebuilds the pipe through THIS constructor
        // (TilePipeHolder.readData -> new Pipe -> loadFlow -> PipeFlowXxx::new), NOT through
        // readFromNbt (which only runs for an already-existing pipe). So the display state serialized
        // into the update tag must be applied here too. Without it, a steady-state straight pipe — which
        // never re-sends a NET_POWER_AMOUNTS delta because didChange stays false — renders its flow
        // invisibly on the client until something forces a change, while jittering pipes (e.g. a
        // splitting junction) self-heal via deltas.
        readFromNbt(nbt);
    }

    /** Creates this flow's concrete {@link Section} (which additionally implements the energy
     *  capability interface for its unit). Called once per face from the constructor. */
    protected abstract Section createSection(Direction side);

    public Section getSection(Direction side) {
        return sections.get(side);
    }

    // NBT — identical shape for both units; display fields ride the update tag for chunk-load / resync.

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.putBoolean("isReceiver", isReceiver);
        // Include display data for initial chunk-load sync (NBT path); the custom networking handles
        // incremental updates after this.
        int[] powers = new int[6];
        int[] flows = new int[6];
        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            powers[face.ordinal()] = s.displayPower;
            flows[face.ordinal()] = s.displayFlow.ordinal();
        }
        nbt.putIntArray("displayPower", powers);
        nbt.putIntArray("displayFlow", flows);
        return nbt;
    }

    @Override
    public void readFromNbt(CompoundTag nbt) {
        isReceiver = NBTUtilBC.getBoolean(nbt, "isReceiver", false);
        int[] powers = NBTUtilBC.getIntArray(nbt, "displayPower", new int[6]);
        int[] flows = NBTUtilBC.getIntArray(nbt, "displayFlow", new int[6]);
        for (Direction face : Direction.values()) {
            int i = face.ordinal();
            Section s = sections.get(face);
            if (i < powers.length) s.displayPower = powers[i];
            if (i < flows.length) {
                int flowIdx = flows[i];
                EnumFlow[] vals = EnumFlow.values();
                s.displayFlow = (flowIdx >= 0 && flowIdx < vals.length) ? vals[flowIdx] : EnumFlow.STATIONARY;
            }
        }
    }

    @Override
    public void writePayload(int id, FriendlyByteBuf buffer, Object side) {
        super.writePayload(id, buffer, side);
        if (id == NET_POWER_AMOUNTS || id == NET_ID_FULL_STATE) {
            for (Direction face : Direction.values()) {
                Section s = sections.get(face);
                buffer.writeInt(s.displayPower);
                buffer.writeEnum(s.displayFlow);
            }
        }
    }

    @Override
    public void readPayload(int id, FriendlyByteBuf buffer, Object side) throws IOException {
        super.readPayload(id, buffer, side);
        if (id == NET_POWER_AMOUNTS || id == NET_ID_FULL_STATE) {
            for (Direction face : Direction.values()) {
                Section s = sections.get(face);
                s.displayPower = buffer.readInt();
                s.displayFlow = buffer.readEnum(EnumFlow.class);
            }
        }
    }

    // Debug

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("maxPower = " + formatMaxPower());
        left.add("isReceiver = " + isReceiver);
        left.add(
            "internalPower = " + arrayToString(s -> s.internalPower) + " <- " + arrayToString(s -> s.internalNextPower)
        );
        left.add("- powerQuery: " + arrayToString(s -> s.powerQuery) + " <- " + arrayToString(s -> s.nextPowerQuery));
        left.add("- power: OUT " + arrayToString(s -> s.debugPowerOutput));
        left.add("- power: OFFERED " + arrayToString(s -> s.debugPowerOffered));
    }

    private String arrayToString(ToLongFunction<Section> getter) {
        long[] arr = new long[6];
        long scale = debugScale();
        for (Direction face : Direction.values()) {
            arr[face.ordinal()] = getter.applyAsLong(sections.get(face)) / scale;
        }
        return Arrays.toString(arr);
    }

    /** Divisor applied to the debug power readouts (MJ prints in whole MJ; RF prints raw). */
    protected long debugScale() {
        return 1;
    }

    /** Human-readable form of {@link #maxPower} for the debug overlay. */
    protected abstract String formatMaxPower();

    // Tick / distribution

    @Override
    public void onTick() {
        if (maxPower == -1) {
            reconfigure();
        }
        if (pipe.getHolder().getPipeWorld().isClientSide()) {
            clientDisplayFlowCentreLast = clientDisplayFlowCentre;
            for (Direction face : Direction.values()) {
                Section s = sections.get(face);
                s.clientDisplayFlowLast = s.clientDisplayFlow;
                double diff = s.displayFlow.value * 2.4 * face.getAxisDirection().getStep();
                s.clientDisplayFlow += 16 + diff;
                s.clientDisplayFlow %= 16;

                double cVal = VecUtil.getValue(clientDisplayFlowCentre, face.getAxis());
                cVal += 16 + diff / 2;
                cVal %= 16;
                clientDisplayFlowCentre = VecUtil.replaceValue(clientDisplayFlowCentre, face.getAxis(), cVal);
            }
            return;
        }

        EnumFlow[] lastFlows = new EnumFlow[6];
        int[] lastDisplayPower = new int[6];

        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            int i = face.ordinal();
            lastFlows[i] = s.displayFlow;
            lastDisplayPower[i] = s.displayPower;
        }

        stepSections();

        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            if (s.internalPower > 0) {
                long totalPowerQuery = 0;
                for (Direction face2 : Direction.values()) {
                    if (face != face2) {
                        totalPowerQuery += sections.get(face2).powerQuery;
                    }
                }

                boolean returnPower = false;
                if (totalPowerQuery <= 0 && s.powerQuery > 0) {
                    totalPowerQuery = s.powerQuery;
                    returnPower = true;
                }

                if (totalPowerQuery > 0) {
                    long unusedPowerQuery = totalPowerQuery;
                    for (Direction face2 : Direction.values()) {
                        if (face == face2 && !returnPower) {
                            continue;
                        }
                        Section s2 = sections.get(face2);
                        if (s2.powerQuery > 0) {
                            long watts = Math.min(
                                BigInteger.valueOf(s.internalPower).multiply(BigInteger.valueOf(s2.powerQuery)).divide(
                                    BigInteger.valueOf(unusedPowerQuery)
                                ).longValue(), s.internalPower
                            );
                            unusedPowerQuery -= s2.powerQuery;
                            IPipe neighbour = pipe.getConnectedPipe(face2);
                            PipeFlow neighbourFlow = neighbour == null ? null : neighbour.getFlow();
                            long leftover;
                            if (
                                neighbour != null && getClass().isInstance(neighbourFlow) && neighbour
                                    .isConnected(face2.getOpposite())
                            ) {
                                AbstractPipeFlowPower oFlow = (AbstractPipeFlowPower) neighbourFlow;
                                leftover = oFlow.sections.get(face2.getOpposite()).receivePowerInternal(watts);
                            } else {
                                leftover = transferToExternalTile(face2, watts);
                            }
                            long used = watts - leftover;
                            s.internalPower -= used;
                            s2.debugPowerOutput += used;

                            s.powerAverage.push((int) used);
                            s2.powerAverage.push((int) used);

                            s.displayFlow = EnumFlow.OUT;
                            s2.displayFlow = EnumFlow.IN;
                        }
                    }
                }
            }
        }
        // Render compute
        for (Section s : sections.values()) {
            s.powerAverage.tick();
            double value = s.powerAverage.getAverage() / maxPower;
            value = Math.sqrt(value);
            s.displayPower = (int) (value * MjAPI.MJ);
        }

        // Ask each connected non-pipe tile how much power it wants this tick.
        for (Direction face : Direction.values()) {
            if (pipe.getConnectedType(face) != ConnectedType.TILE) {
                continue;
            }
            long demand = queryTileDemand(face);
            if (demand > 0) {
                requestPower(face, demand);
            }
        }

        // Sum the amount of power requested on each side
        long[] transferQuery = new long[6];
        for (Direction face : Direction.values()) {
            if (!pipe.isConnected(face)) {
                continue;
            }
            long query = 0;
            for (Direction face2 : Direction.values()) {
                if (face != face2) {
                    query += sections.get(face2).powerQuery;
                }
            }
            transferQuery[face.ordinal()] = query;
        }

        // Transfer requested power to neighbouring pipes
        for (Direction face : Direction.values()) {
            if (disabled) {
                continue;
            }
            if (transferQuery[face.ordinal()] <= 0 || !pipe.isConnected(face)) {
                continue;
            }
            IPipe oPipe = pipe.getHolder().getNeighbourPipe(face);
            if (oPipe == null || !getClass().isInstance(oPipe.getFlow())) {
                continue;
            }
            AbstractPipeFlowPower oFlow = (AbstractPipeFlowPower) oPipe.getFlow();
            oFlow.requestPower(face.getOpposite(), transferQuery[face.ordinal()]);
        }
        // Networking
        boolean didChange = false;
        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            int i = face.ordinal();
            if (lastFlows[i] != s.displayFlow || lastDisplayPower[i] != s.displayPower) {
                didChange = true;
                break;
            }
        }

        if (didChange) {
            sendPayload(NET_POWER_AMOUNTS);
        }
    }

    /** Ticks every section once per world-time change (nextPowerQuery -> powerQuery,
     *  internalNextPower -> internalPower). Named distinctly from {@link Section#step()} to keep the
     *  flow-vs-section call sites unambiguous inside the inner class. */
    protected void stepSections() {
        long now = pipe.getHolder().getPipeWorld().getGameTime();
        if (currentWorldTime != now) {
            currentWorldTime = now;
            sections.values().forEach(Section::step);
        }
    }

    protected void requestPower(Direction from, long amount) {
        stepSections();

        Section s = sections.get(from);
        s.nextPowerQuery += applyRequestHook(from, amount);
        s.nextPowerQuery = Math.min(s.nextPowerQuery, maxPower);
    }

    public long getPowerRequested(Direction side) {
        long req = 0;
        for (Direction face : Direction.values()) {
            if (side == null || face != side) {
                req += sections.get(face).powerQuery;
            }
        }
        return req;
    }

    // Subclass boundary hooks

    /** Pushes up to {@code watts} into the machine on {@code to} (if any) and returns the leftover
     *  that could not be accepted. Returns {@code watts} when there is no receiver. */
    protected abstract long transferToExternalTile(Direction to, long watts);

    /** How much power the machine on {@code face} wants this tick ({@code 0} if none / not a receiver). */
    protected abstract long queryTileDemand(Direction face);

    /** Lets the pipe behaviour reshape a power request before it is queued. Default returns {@code amount}. */
    protected abstract long applyRequestHook(Direction from, long amount);

    /** Event-driven capacity config (sets {@link #maxPower}/{@link #isReceiver}/{@link #disabled}).
     *  Declared here so {@link #onTick()} can lazily trigger it; each unit fires its own event type
     *  (and MJ also derives its inert loss scaffolding), and the concrete subclass's {@code IFlowPower}/
     *  {@code IFlowRedstoneFlux} interface reuses this method. */
    public abstract void reconfigure();

    public abstract class Section {
        public final Direction side;

        public double clientDisplayFlow, clientDisplayFlowLast;

        /** Range: 0 to {@link MjAPI#MJ} */
        public int displayPower;
        public EnumFlow displayFlow = EnumFlow.STATIONARY;
        public long nextPowerQuery;
        public long internalNextPower;
        public final AverageInt powerAverage = new AverageInt(1);

        long powerQuery;
        long internalPower;

        /** Debugging fields */
        long debugPowerOutput, debugPowerOffered;

        public Section(Direction side) {
            this.side = side;
            clientDisplayFlow = (side.getAxisDirection() == AxisDirection.POSITIVE ? 7 : 1) / 8.0;
        }

        void step() {
            powerQuery = nextPowerQuery;
            nextPowerQuery = 0;

            internalPower += internalNextPower;
            internalNextPower = 0;
        }

        /** Accepts power pushed in from a neighbouring same-unit flow's opposite section; returns the
         *  leftover that could not be accepted. */
        abstract long receivePowerInternal(long sent);
    }

    public enum EnumFlow {
        IN(-1),
        OUT(1),
        STATIONARY(0);

        public final int value;

        EnumFlow(int value) {
            this.value = value;
        }
    }
}
