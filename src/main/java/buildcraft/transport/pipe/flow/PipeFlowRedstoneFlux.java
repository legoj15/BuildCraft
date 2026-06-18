/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe.flow;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ToIntFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}
//? if <1.21.10 {
/*import net.neoforged.neoforge.energy.IEnergyStorage;*/
//?}

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IFlowRedstoneFlux;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.RedstoneFluxTransferInfo;
import buildcraft.api.transport.pipe.PipeEventRedstoneFlux;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.CapUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.AverageInt;

@SuppressWarnings("this-escape")
public class PipeFlowRedstoneFlux extends PipeFlow implements IFlowRedstoneFlux, IDebuggable {
    private static final int DEFAULT_MAX_POWER = 100;
    public static final int NET_POWER_AMOUNTS = 2;

    public Vec3 clientDisplayFlowCentre = VecUtil.VEC_HALF;
    public Vec3 clientDisplayFlowCentreLast = VecUtil.VEC_HALF;
    public long clientLastDisplayTime = 0;

    private int maxPower = -1;
    private boolean disabled = false;

    private long currentWorldTime;

    private boolean isReceiver = false;
    private final EnumMap<Direction, Section> sections;

    public PipeFlowRedstoneFlux(IPipe pipe) {
        super(pipe);
        sections = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            sections.put(face, new Section(face));
        }
    }

    public PipeFlowRedstoneFlux(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        isReceiver = NBTUtilBC.getBoolean(nbt, "isReceiver", false);
        sections = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            sections.put(face, new Section(face));
        }
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.putBoolean("isReceiver", isReceiver);
        return nbt;
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

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof PipeFlowRedstoneFlux;
    }

    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        // Match 1.12.2: connect to any tile that exposes FE/Energy capability.
        // The receiver flag only affects internal flow direction, not connectivity.
        //? if >=1.21.10 {
        net.neoforged.neoforge.transfer.energy.EnergyHandler handler =
            pipe.getHolder().getCapabilityFromPipe(face, net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK);
        //?} else {
        /*net.neoforged.neoforge.energy.IEnergyStorage handler =
            pipe.getHolder().getCapabilityFromPipe(face, net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK);*/
        //?}
        return handler != null;
    }

    @Override
    public void reconfigure() {
        PipeEventRedstoneFlux.Configure configure = new PipeEventRedstoneFlux.Configure(pipe.getHolder(), this);
        RedstoneFluxTransferInfo pti = PipeApi.getRfTransferInfo(pipe.getDefinition());
        configure.setReceiver(pti.isReceiver);
        configure.setMaxPower(pti.transferPerTick);
        pipe.getHolder().fireEvent(configure);
        boolean wasReceiver = isReceiver;
        isReceiver = configure.isReceiver();
        if (wasReceiver != isReceiver) {
            pipe.markForUpdate();
        }
        maxPower = configure.getMaxPower();
        disabled = configure.isTransferDisabled();
        if (maxPower <= 0) {
            maxPower = DEFAULT_MAX_POWER;
        }
    }

    @Override
    public int tryExtractPower(int maxExtracted, Direction from) {
        if (!isReceiver || disabled) {
            return 0;
        }
        //? if >=1.21.10 {
        EnergyHandler source = pipe.getHolder().getCapabilityFromPipe(
            from, net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK
        );
        if (source == null) {
            return 0;
        }
        try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int extracted = source.extract(maxExtracted, transaction);
            transaction.commit();
            return extracted;
        }
        //?} else {
        /*net.neoforged.neoforge.energy.IEnergyStorage source = pipe.getHolder().getCapabilityFromPipe(
            from, net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK
        );
        if (source == null) {
            return 0;
        }
        return source.extractEnergy(maxExtracted, false);*/
        //?}
    }

    @Override
    public boolean onFlowActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ,
        EnumPipePart part) {
        return super.onFlowActivate(player, trace, hitX, hitY, hitZ, part);
    }

    public Section getSection(Direction side) {
        return sections.get(side);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Object capability, Direction facing) {
        if (facing == null) {
            return null;
        }
        // disableRfPipe: withhold the energy capability so the pipe can neither receive nor be
        // extracted from — a placed RF pipe is fully inert when the feature is turned off.
        if (buildcraft.transport.BCTransportConfig.disableRfPipe.get()) {
            return null;
        }
        //? if >=1.21.10 {
        if (capability == net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK || capability == CapUtil.CAP_ENERGY) {
        //?} else {
        /*if (capability == net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK || capability == CapUtil.CAP_ENERGY) {*/
        //?}
            return (T) sections.get(facing);
        }
        return null;
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("maxPower = " + maxPower);
        left.add("isReceiver = " + isReceiver);
        left.add(
            "internalPower = " + arrayToString(s -> s.internalPower) + " <- " + arrayToString(s -> s.internalNextPower)
        );
        left.add("- powerQuery: " + arrayToString(s -> s.powerQuery) + " <- " + arrayToString(s -> s.nextPowerQuery));
        left.add("- power: OUT " + arrayToString(s -> s.debugPowerOutput));
        left.add("- power: OFFERED " + arrayToString(s -> s.debugPowerOffered));
    }

    private String arrayToString(ToIntFunction<Section> getter) {
        long[] arr = new long[6];
        for (Direction face : Direction.values()) {
            arr[face.ordinal()] = getter.applyAsInt(sections.get(face));
        }
        return Arrays.toString(arr);
    }

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

        step();

        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            if (s.internalPower > 0) {
                int totalPowerQuery = 0;
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
                    int unusedPowerQuery = totalPowerQuery;
                    for (Direction face2 : Direction.values()) {
                        if (face == face2 && !returnPower) {
                            continue;
                        }
                        Section s2 = sections.get(face2);
                        if (s2.powerQuery > 0) {
                            int watts = (int) Math.min(s.internalPower * (long) s2.powerQuery / unusedPowerQuery, s.internalPower);
                            unusedPowerQuery -= s2.powerQuery;
                            IPipe neighbour = pipe.getConnectedPipe(face2);
                            int leftover = watts;
                            if (
                                neighbour != null && neighbour.getFlow() instanceof PipeFlowRedstoneFlux && neighbour
                                    .isConnected(face2.getOpposite())
                            ) {
                                PipeFlowRedstoneFlux oFlow = (PipeFlowRedstoneFlux) neighbour.getFlow();
                                leftover = oFlow.sections.get(face2.getOpposite()).receivePowerInternal(watts);
                            } else {
                                //? if >=1.21.10 {
                                EnergyHandler receiver = pipe.getHolder().getCapabilityFromPipe(
                                    face2, net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK
                                );
                                if (receiver != null) {
                                    try (net.neoforged.neoforge.transfer.transaction.Transaction transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                                        int accepted = receiver.insert(watts, transaction);
                                        leftover = watts - accepted;
                                        transaction.commit();
                                    }
                                }
                                //?} else {
                                /*net.neoforged.neoforge.energy.IEnergyStorage receiver = pipe.getHolder().getCapabilityFromPipe(
                                    face2, net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK
                                );
                                if (receiver != null) {
                                    int accepted = receiver.receiveEnergy(watts, false);
                                    leftover = watts - accepted;
                                }
                                *///?}
                            }
                            int used = watts - leftover;
                            s.internalPower -= used;
                            s2.debugPowerOutput += used;

                            s.powerAverage.push(used);
                            s2.powerAverage.push(used);

                            s.displayFlow = EnumFlow.OUT;
                            s2.displayFlow = EnumFlow.IN;
                        }
                    }
                }
            }
        }
        // Render compute goes here
        for (Section s : sections.values()) {
            s.powerAverage.tick();
            double value = s.powerAverage.getAverage() / maxPower;
            value = Math.sqrt(value);
            s.displayPower = (int) (value * MjAPI.MJ);
        }

        // Compute the power that connected non-pipe tiles are willing to accept this tick.
        // We ASK each receiver how much it will actually take (via a rolled-back simulated
        // insert in queryEnergyDemand) rather than inferring demand from buffer headroom
        // (capacity - stored). NeoForge's EnergyHandler contract is explicit that the capacity
        // hint can read 0 — or even below the current amount — while the handler still accepts
        // power: "the only way to know if a handler will accept a resource is to try to insert
        // it." AE2's Energy Acceptor is exactly that case — a bufferless FE->AE converter
        // (internalMaxPower 0) that funnels straight into the ME grid, so capacity-stored was
        // always 0 and the pipe never fed it (whereas the ME Controller, which carries an
        // 8000 AE buffer, worked).
        for (Direction face : Direction.values()) {
            if (pipe.getConnectedType(face) != ConnectedType.TILE) {
                continue;
            }
            //? if >=1.21.10 {
            EnergyHandler recv = pipe.getHolder().getCapabilityFromPipe(face, net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK);
            //?} else {
            /*net.neoforged.neoforge.energy.IEnergyStorage recv = pipe.getHolder().getCapabilityFromPipe(face, net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK);*/
            //?}
            if (recv != null) {
                int demand = queryEnergyDemand(recv, maxPower);
                if (demand > 0) {
                    requestPower(face, demand);
                }
            }
        }

        // Sum the amount of power requested on each side
        int[] transferQueryTemp = new int[6];
        for (Direction face : Direction.values()) {
            if (!pipe.isConnected(face)) {
                continue;
            }
            int query = 0;
            for (Direction face2 : Direction.values()) {
                if (face != face2) {
                    query += sections.get(face2).powerQuery;
                }
            }
            transferQueryTemp[face.ordinal()] = query;
        }

        // Transfer requested power to neighbouring pipes
        for (Direction face : Direction.values()) {
            if (disabled) {
                continue;
            }
            if (transferQueryTemp[face.ordinal()] <= 0 || !pipe.isConnected(face)) {
                continue;
            }
            IPipe oPipe = pipe.getHolder().getNeighbourPipe(face);
            if (oPipe == null || !(oPipe.getFlow() instanceof PipeFlowRedstoneFlux)) {
                continue;
            }
            PipeFlowRedstoneFlux oFlow = (PipeFlowRedstoneFlux) oPipe.getFlow();
            oFlow.requestPower(face.getOpposite(), transferQueryTemp[face.ordinal()]);
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

    private void step() {
        long now = pipe.getHolder().getPipeWorld().getGameTime();
        if (currentWorldTime != now) {
            currentWorldTime = now;
            sections.values().forEach(Section::step);
        }
    }

    private void requestPower(Direction from, int amount) {
        step();

        Section s = sections.get(from);
        if (pipe.getBehaviour() instanceof IPipeTransportRfHook) {
            s.nextPowerQuery += ((IPipeTransportRfHook) pipe.getBehaviour()).requestPower(from, amount);
        } else {
            s.nextPowerQuery += amount;
        }
        s.nextPowerQuery = Math.min(s.nextPowerQuery, maxPower);
    }

    /** Measures how much power {@code recv} will actually accept right now by performing a
     *  rolled-back simulated insert of up to {@code max}. This is the contract-correct way to
     *  size demand: NeoForge's EnergyHandler explicitly notes the capacity hint can read 0 (or
     *  below the current amount) while the handler still accepts power, so {@code capacity -
     *  stored} computed 0 demand for bufferless pass-through receivers such as AE2's Energy
     *  Acceptor and they were never fed. Package-visible for {@code PipeFlowRedstoneFluxDemandTester}. */
    //? if >=1.21.10 {
    static int queryEnergyDemand(EnergyHandler recv, int max) {
        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                 net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            return recv.insert(max, tx);
            // No commit — the transaction rolls back; this is a measurement, not a transfer.
        }
    }
    //?} else {
    /*static int queryEnergyDemand(net.neoforged.neoforge.energy.IEnergyStorage recv, int max) {
        return recv.receiveEnergy(max, true);
    }*/
    //?}

    public int getPowerRequested(@Nullable Direction side) {
        int req = 0;
        for (Direction face : Direction.values()) {
            if (side == null || face != side) {
                req += sections.get(face).powerQuery;
            }
        }
        return req;
    }

    //? if >=1.21.10 {
    public class Section implements EnergyHandler {
    //?} else {
    /*public class Section implements net.neoforged.neoforge.energy.IEnergyStorage {*/
    //?}
        public final Direction side;

        public double clientDisplayFlow, clientDisplayFlowLast;

        /** Range: 0 to {@link MjAPI#MJ} */
        public int displayPower;
        public EnumFlow displayFlow = EnumFlow.STATIONARY;
        public int nextPowerQuery;
        public int internalNextPower;
        public final AverageInt powerAverage = new AverageInt(1);

        int powerQuery;
        int internalPower;

        /** Debugging fields */
        int debugPowerOutput, debugPowerOffered;

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

        //? if >=1.21.10 {
        private final net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Integer> powerJournal = new net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Integer>() {
            @Override
            protected Integer createSnapshot() {
                return internalNextPower;
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                internalNextPower = snapshot;
            }
        };

        @Override
        public int insert(int maxReceive, TransactionContext transaction) {
            if (isReceiver && maxReceive > 0) {
                PipeFlowRedstoneFlux.this.step();
                int maxCanAccept = maxPower - (internalPower + internalNextPower);
                if (maxCanAccept <= 0) return 0;

                int accepted = Math.min(maxCanAccept, maxReceive);
                if (accepted > 0) {
                    powerJournal.updateSnapshots(transaction);
                    debugPowerOffered += accepted;
                    internalNextPower += accepted;
                    return accepted;
                }
            }
            return 0;
        }

        @Override
        public int extract(int maxExtract, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long getAmountAsLong() {
            return internalPower + internalNextPower;
        }

        @Override
        public long getCapacityAsLong() {
            return maxPower;
        }
        //?} else {
        /*@Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (isReceiver && maxReceive > 0) {
                PipeFlowRedstoneFlux.this.step();
                int maxCanAccept = maxPower - (internalPower + internalNextPower);
                if (maxCanAccept <= 0) return 0;

                int accepted = Math.min(maxCanAccept, maxReceive);
                if (accepted > 0) {
                    if (!simulate) {
                        debugPowerOffered += accepted;
                        internalNextPower += accepted;
                    }
                    return accepted;
                }
            }
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return internalPower + internalNextPower;
        }

        @Override
        public int getMaxEnergyStored() {
            return maxPower;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return isReceiver;
        }
        *///?}

        int receivePowerInternal(int sent) {
            if (sent > 0) {
                PipeFlowRedstoneFlux.this.step();
                int max = maxPower - (internalPower + internalNextPower);
                if (max <= 0) {
                    return sent;
                }
                int accepted = Math.min(max, sent);
                debugPowerOffered += accepted;
                internalNextPower += accepted;
                return sent - accepted;
            }
            return sent;
        }
    }

    public enum EnumFlow {
        IN(-1),
        OUT(1),
        STATIONARY(0);

        public final int value;

        private EnumFlow(int value) {
            this.value = value;
        }
    }
}
