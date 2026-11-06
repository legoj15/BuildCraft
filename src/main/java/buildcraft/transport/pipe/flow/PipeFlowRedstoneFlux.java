/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe.flow;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;


import buildcraft.api.capability.BCCapability;
import buildcraft.api.transport.pipe.IFlowRedstoneFlux;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.RedstoneFluxTransferInfo;
import buildcraft.api.transport.pipe.PipeEventRedstoneFlux;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.CapUtil;
import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

/** Forge Energy / RF ({@code int}) kinesis flow. See {@link AbstractPipeFlowPower} for the shared
 *  distribution engine; this subclass supplies the FE boundary (the platform layer's {@link BCEnergyStorage},
 *  {@link CapUtil#CAP_ENERGY}) and demand sizing via a rolled-back simulated insert
 *  ({@link #queryEnergyDemand}). RF has no loss scaffolding (kinesis is lossless either way). */
public class PipeFlowRedstoneFlux extends AbstractPipeFlowPower implements IFlowRedstoneFlux {
    private static final int DEFAULT_MAX_POWER = 100;

    public PipeFlowRedstoneFlux(IPipe pipe) {
        super(pipe);
    }

    public PipeFlowRedstoneFlux(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
    }

    @Override
    protected Section createSection(Direction side) {
        return new Section(side);
    }

    @Override
    public Section getSection(Direction side) {
        return (Section) super.getSection(side);
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof PipeFlowRedstoneFlux;
    }

    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        // Match 1.12.2: connect to any tile that exposes FE/Energy capability.
        // The receiver flag only affects internal flow direction, not connectivity.
        BCEnergyStorage handler = pipe.getHolder().getCapabilityFromPipe(face, CapUtil.CAP_ENERGY);
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
    protected String formatMaxPower() {
        return String.valueOf(maxPower);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull BCCapability<T> capability, Direction facing) {
        if (facing == null) {
            return null;
        }
        // disableRfPipe: withhold the energy capability so the pipe can neither receive nor be
        // extracted from — a placed RF pipe is fully inert when the feature is turned off.
        if (buildcraft.transport.BCTransportConfig.disableRfPipe.get()) {
            return null;
        }
        if (capability == CapUtil.CAP_ENERGY) {
            return (T) sections.get(facing);
        }
        return null;
    }

    @Override
    protected long transferToExternalTile(Direction to, long watts) {
        BCEnergyStorage receiver = pipe.getHolder().getCapabilityFromPipe(to, CapUtil.CAP_ENERGY);
        if (receiver != null) {
            try (BCTransaction transaction = BCTransaction.openRoot()) {
                int accepted = receiver.insert((int) watts, transaction);
                transaction.commit();
                return watts - accepted;
            }
        }
        return watts;
    }

    @Override
    protected long queryTileDemand(Direction face) {
        // We ASK each receiver how much it will actually take (via a rolled-back simulated insert in
        // queryEnergyDemand) rather than inferring demand from buffer headroom (capacity - stored).
        // NeoForge's EnergyHandler contract is explicit that the capacity hint can read 0 — or even below
        // the current amount — while the handler still accepts power: "the only way to know if a handler
        // will accept a resource is to try to insert it." AE2's Energy Acceptor is exactly that case — a
        // bufferless FE->AE converter (internalMaxPower 0) that funnels straight into the ME grid, so
        // capacity-stored was always 0 and the pipe never fed it (whereas the ME Controller, which carries
        // an 8000 AE buffer, worked).
        BCEnergyStorage recv = pipe.getHolder().getCapabilityFromPipe(face, CapUtil.CAP_ENERGY);
        if (recv != null) {
            return queryEnergyDemand(recv, (int) maxPower);
        }
        return 0;
    }

    @Override
    protected long applyRequestHook(Direction from, long amount) {
        if (pipe.getBehaviour() instanceof IPipeTransportRfHook hook) {
            return hook.requestPower(from, (int) amount);
        }
        return amount;
    }

    /** Measures how much power {@code recv} will actually accept right now by performing a
     *  rolled-back simulated insert of up to {@code max}. This is the contract-correct way to
     *  size demand: NeoForge's EnergyHandler explicitly notes the capacity hint can read 0 (or
     *  below the current amount) while the handler still accepts power, so {@code capacity -
     *  stored} computed 0 demand for bufferless pass-through receivers such as AE2's Energy
     *  Acceptor and they were never fed. Package-visible for {@code PipeFlowRedstoneFluxDemandTester}. */
    static int queryEnergyDemand(BCEnergyStorage recv, int max) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            return recv.insert(max, tx);
            // No commit — the transaction rolls back; this is a measurement, not a transfer.
        }
    }

    public class Section extends AbstractPipeFlowPower.Section implements BCEnergyStorage {
        public Section(Direction side) {
            super(side);
        }

        // On 1.21.1 the F3 "OFFERED" counter is journalled with the power it counts, so a rolled-back insert (every
        // demand probe is one) counts nothing: the classic receiveEnergy counted only a non-simulated insert. The
        // transaction nodes always counted every insert, probes and aborts included, and still do (debug only).
        private final BCSnapshotJournal<long[]> powerJournal = new BCSnapshotJournal<long[]>() {
            @Override
            protected long[] createSnapshot() {
                return new long[] { internalNextPower, debugPowerOffered };
            }

            @Override
            protected void revertToSnapshot(long[] snapshot) {
                internalNextPower = snapshot[0];
                //? if <1.21.10 {
                /*debugPowerOffered = snapshot[1];*/
                //?}
            }
        };

        @Override
        public int insert(int maxReceive, BCTransactionContext transaction) {
            if (isReceiver && maxReceive > 0) {
                stepSections();
                long maxCanAccept = maxPower - (internalPower + internalNextPower);
                if (maxCanAccept <= 0) return 0;

                int accepted = (int) Math.min(maxCanAccept, maxReceive);
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
        public int extract(int maxExtract, BCTransactionContext transaction) {
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

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canInsert() {
            return isReceiver;
        }

        @Override
        long receivePowerInternal(long sent) {
            if (sent > 0) {
                stepSections();
                long max = maxPower - (internalPower + internalNextPower);
                if (max <= 0) {
                    return sent;
                }
                long accepted = Math.min(max, sent);
                debugPowerOffered += accepted;
                internalNextPower += accepted;
                return sent - accepted;
            }
            return sent;
        }
    }
}
