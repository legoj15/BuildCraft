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

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}
//? if <1.21.10 {
/*import net.neoforged.neoforge.energy.IEnergyStorage;*/
//?}

import buildcraft.api.transport.pipe.IFlowRedstoneFlux;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.RedstoneFluxTransferInfo;
import buildcraft.api.transport.pipe.PipeEventRedstoneFlux;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.CapUtil;

/** Forge Energy / RF ({@code int}) kinesis flow. See {@link AbstractPipeFlowPower} for the shared
 *  distribution engine; this subclass supplies the FE boundary (the version-gated NeoForge energy
 *  handler / {@code IEnergyStorage} capability) and demand sizing via a rolled-back simulated insert
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
    protected String formatMaxPower() {
        return String.valueOf(maxPower);
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
    protected long transferToExternalTile(Direction to, long watts) {
        //? if >=1.21.10 {
        EnergyHandler receiver = pipe.getHolder().getCapabilityFromPipe(
            to, net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK
        );
        if (receiver != null) {
            try (net.neoforged.neoforge.transfer.transaction.Transaction transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                int accepted = receiver.insert((int) watts, transaction);
                transaction.commit();
                return watts - accepted;
            }
        }
        //?} else {
        /*net.neoforged.neoforge.energy.IEnergyStorage receiver = pipe.getHolder().getCapabilityFromPipe(
            to, net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK
        );
        if (receiver != null) {
            int accepted = receiver.receiveEnergy((int) watts, false);
            return watts - accepted;
        }
        *///?}
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
        //? if >=1.21.10 {
        EnergyHandler recv = pipe.getHolder().getCapabilityFromPipe(face, net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK);
        //?} else {
        /*net.neoforged.neoforge.energy.IEnergyStorage recv = pipe.getHolder().getCapabilityFromPipe(face, net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK);*/
        //?}
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

    //? if >=1.21.10 {
    public class Section extends AbstractPipeFlowPower.Section implements EnergyHandler {
    //?} else {
    /*public class Section extends AbstractPipeFlowPower.Section implements net.neoforged.neoforge.energy.IEnergyStorage {*/
    //?}
        public Section(Direction side) {
            super(side);
        }

        //? if >=1.21.10 {
        private final net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Long> powerJournal = new net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Long>() {
            @Override
            protected Long createSnapshot() {
                return internalNextPower;
            }

            @Override
            protected void revertToSnapshot(Long snapshot) {
                internalNextPower = snapshot;
            }
        };

        @Override
        public int insert(int maxReceive, TransactionContext transaction) {
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
                stepSections();
                long maxCanAccept = maxPower - (internalPower + internalNextPower);
                if (maxCanAccept <= 0) return 0;

                int accepted = (int) Math.min(maxCanAccept, maxReceive);
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
            return (int) (internalPower + internalNextPower);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) maxPower;
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
