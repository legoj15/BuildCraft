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

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IFlowPower;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.PowerTransferInfo;
import buildcraft.api.transport.pipe.PipeEventPower;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.MathUtil;

/** BuildCraft MJ ({@code long}) kinesis flow. See {@link AbstractPipeFlowPower} for the shared
 *  distribution engine; this subclass supplies the MJ boundary (MjAPI receiver/connector caps) and
 *  the event-driven capacity config (including the intentionally-inert 8.0.x {@code powerLoss}/
 *  {@code powerResistance} scaffolding). */
public class PipeFlowPower extends AbstractPipeFlowPower implements IFlowPower {
    private static final long DEFAULT_MAX_POWER = MjAPI.MJ * 10;

    // powerLoss / powerResistance are computed in reconfigure() but never read anywhere else — pipe
    // transport is unconditionally lossless. This is INTENTIONAL 8.0.x parity, not an oversight; see the
    // note in reconfigure() before deleting these as dead code or "fixing" the missing loss.
    private long powerLoss = -1;
    private long powerResistance = -1;

    public PipeFlowPower(IPipe pipe) {
        super(pipe);
    }

    public PipeFlowPower(IPipe pipe, CompoundTag nbt) {
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
        return other instanceof PipeFlowPower;
    }

    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        // Check if the external tile has MJ receiver or connector capability
        if (pipe.getHolder().getPipeWorld() != null) {
            // Use NeoForge BlockCapability lookup to check the neighbor tile
            net.minecraft.world.level.Level level = pipe.getHolder().getPipeWorld();
            net.minecraft.core.BlockPos neighborPos = pipe.getHolder().getPipePos().relative(face);
            // Check for MJ receiver (engines, machines)
            IMjReceiver receiver = level.getCapability(MjAPI.CAP_RECEIVER, neighborPos, face.getOpposite());
            if (receiver != null) {
                return true;
            }
            // Check for MJ connector (other power entities)
            IMjConnector connector = level.getCapability(MjAPI.CAP_CONNECTOR, neighborPos, face.getOpposite());
            if (connector != null && connector.canConnect(getSection(face))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void reconfigure() {
        PipeEventPower.Configure configure = new PipeEventPower.Configure(pipe.getHolder(), this);
        PowerTransferInfo pti = PipeApi.getPowerTransferInfo(pipe.getDefinition());
        configure.setReceiver(pti.isReceiver);
        configure.setMaxPower(pti.transferPerTick);
        configure.setPowerLoss(pti.lossPerTick);
        configure.setPowerResistance(pti.resistancePerTick);
        pipe.getHolder().fireEvent(configure);
        boolean wasReceiver = isReceiver;
        isReceiver = configure.isReceiver();
        // If isReceiver changed, the pipe's connection checks need to re-run because
        // EngineConnector.canConnect() checks Section.canReceive() which depends on isReceiver.
        // Without this, wooden power pipes placed on engine noses fail to connect on the
        // first tick because reconfigure() runs AFTER the initial updateConnections() call.
        if (wasReceiver != isReceiver) {
            pipe.markForUpdate();
        }
        maxPower = configure.getMaxPower();
        disabled = configure.isTransferDisabled();
        if (maxPower <= 0) {
            maxPower = DEFAULT_MAX_POWER;
        }
        // NOTE: powerLoss / powerResistance are derived here and then NEVER applied — onTick()'s forward
        // distribution moves power untouched, so kinesis pipes lose nothing in transit and maxPower acts
        // only as a request throttle (clamped into nextPowerQuery in requestPower()), not a hard
        // throughput cap. This is faithful to the BuildCraft 8.0.x (Neptune) build this port derives from:
        // the shipping 1.12.2 common/buildcraft/transport/pipe/flow/PipeFlowPower (upstream 8.0.x-1.12.2,
        // last touched 2025-04-01) is byte-for-byte the same shape — Neptune scaffolded the loss config
        // plumbing (PowerTransferInfo.lossPerTick/resistancePerTick) but never wired it into the transfer.
        // So this is CURRENTLY INTENDED, not a regression — leave it unless deliberately adding loss.
        // Re-introducing real loss would be a feature: apply powerResistance in the onTick() transfer step
        // (pre-Neptune src_old_license/buildcraft/transport/PipeTransportPower ~L454-465 is the reference)
        // and optionally clamp forward flow to maxPower so the per-tier rates actually bind. Gate it behind
        // config (default off) to preserve this parity.
        powerLoss = MathUtil.clamp(configure.getPowerLoss(), -1, maxPower);
        powerResistance = MathUtil.clamp(configure.getPowerResistance(), -1, MjAPI.MJ);

        if (powerLoss < 0) {
            if (powerResistance < 0) {
                // 1% resistance
                powerResistance = MjAPI.MJ / 100;
            }
            powerLoss = maxPower * powerResistance / MjAPI.MJ;
        } else if (powerResistance < 0) {
            powerResistance = powerLoss * MjAPI.MJ / maxPower;
        }
    }

    @Override
    protected String formatMaxPower() {
        return LocaleUtil.localizeMj(maxPower);
    }

    @Override
    protected long debugScale() {
        return MjAPI.MJ;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Object capability, Direction facing) {
        if (facing == null) {
            return null;
        } else if (capability == MjAPI.CAP_RECEIVER) {
            return isReceiver ? (T) sections.get(facing) : null;
        } else if (capability == MjAPI.CAP_CONNECTOR) {
            return (T) sections.get(facing);
        } else {
            return null;
        }
    }

    @Override
    protected long transferToExternalTile(Direction to, long watts) {
        IMjReceiver receiver = getReceiver(to);
        if (receiver != null && receiver.canReceive()) {
            return receiver.receivePower(watts, false);
        }
        return watts;
    }

    @Override
    protected long queryTileDemand(Direction face) {
        IMjReceiver recv = getReceiver(face);
        if (recv != null && recv.canReceive()) {
            return recv.getPowerRequested();
        }
        return 0;
    }

    @Override
    protected long applyRequestHook(Direction from, long amount) {
        if (pipe.getBehaviour() instanceof IPipeTransportPowerHook hook) {
            return hook.requestPower(from, amount);
        }
        return amount;
    }

    private IMjReceiver getReceiver(Direction side) {
        IMjReceiver receiver = pipe.getHolder().getCapabilityFromPipe(side, MjAPI.CAP_RECEIVER);
        // RF auto-conversion removed — will be re-added when energy interop is ported
        return receiver;
    }

    public class Section extends AbstractPipeFlowPower.Section implements IMjReceiver {
        public Section(Direction side) {
            super(side);
        }

        @Override
        public boolean canConnect(@Nonnull IMjConnector other) {
            return true;
        }

        @Override
        public long getPowerRequested() {
            return PipeFlowPower.this.getPowerRequested(side);
        }

        @Override
        long receivePowerInternal(long sent) {
            if (sent > 0) {
                stepSections();
                debugPowerOffered += sent;
                internalNextPower += sent;
                return 0;
            }
            return sent;
        }

        @Override
        public long receivePower(long microJoules, boolean simulate) {
            if (isReceiver) {
                if (!simulate) {
                    return this.receivePowerInternal(microJoules);
                }
                return 0;
            }
            return microJoules;
        }

        @Override
        public boolean canReceive() {
            return isReceiver;
        }
    }
}
