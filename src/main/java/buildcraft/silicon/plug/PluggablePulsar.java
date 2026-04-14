/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.plug;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventStatement;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.misc.MathUtil;

import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconStatements;

public class PluggablePulsar extends PipePluggable {
    private static final int PULSE_STAGE = 20;

    private static final AABB[] BOXES = new AABB[6];

    private boolean manuallyEnabled = false;
    /** Increments from 0 to PULSE_STAGE to decide when it should pulse power into the pipe */
    private int pulseStage = 0;
    private int gateEnabledTicks;
    private int gateSinglePulses;
    private boolean lastPulsing = false;

    /** Used on the client to determine if this should render pulsing */
    private boolean isPulsing = false;
    /** Used on the client to determine if this is being activated by a gate */
    private boolean autoEnabled = false;

    static {
        double ll = 2 / 16.0;
        double lu = 4 / 16.0;
        double ul = 12 / 16.0;
        double uu = 14 / 16.0;

        double min = 5 / 16.0;
        double max = 11 / 16.0;

        BOXES[Direction.DOWN.ordinal()] = new AABB(min, ll, min, max, lu, max);
        BOXES[Direction.UP.ordinal()] = new AABB(min, ul, min, max, uu, max);
        BOXES[Direction.NORTH.ordinal()] = new AABB(min, min, ll, max, max, lu);
        BOXES[Direction.SOUTH.ordinal()] = new AABB(min, min, ul, max, max, uu);
        BOXES[Direction.WEST.ordinal()] = new AABB(ll, min, min, lu, max, max);
        BOXES[Direction.EAST.ordinal()] = new AABB(ul, min, min, uu, max, max);
    }

    public PluggablePulsar(PluggableDefinition definition, IPipeHolder holder, Direction side) {
        super(definition, holder, side);
    }

    // Saving + Loading

    public PluggablePulsar(PluggableDefinition definition, IPipeHolder holder, Direction side, CompoundTag nbt) {
        super(definition, holder, side);
        this.manuallyEnabled = nbt.getBooleanOr("manuallyEnabled", false);
        gateEnabledTicks = nbt.getIntOr("gateEnabledTicks", 0);
        gateSinglePulses = nbt.getIntOr("gateSinglePulses", 0);
        pulseStage = MathUtil.clamp(nbt.getIntOr("pulseStage", 0), 0, PULSE_STAGE);
    }

    @Override
    public CompoundTag writeToNbt() {
        CompoundTag nbt = super.writeToNbt();
        nbt.putBoolean("manuallyEnabled", manuallyEnabled);
        nbt.putInt("gateEnabledTicks", gateEnabledTicks);
        nbt.putInt("gateSinglePulses", gateSinglePulses);
        nbt.putInt("pulseStage", pulseStage);
        return nbt;
    }

    // Networking

    public PluggablePulsar(PluggableDefinition definition, IPipeHolder holder, Direction side, FriendlyByteBuf buffer) {
        super(definition, holder, side);
        readData(buffer);
    }

    @Override
    public void writeCreationPayload(FriendlyByteBuf buffer) {
        super.writeCreationPayload(buffer);
        writeData(buffer);
    }

    @Override
    public void readPayload(FriendlyByteBuf buffer, Object side, Object ctx) throws java.io.IOException {
        super.readPayload(buffer, side, ctx);
        readData(buffer);
    }

    @Override
    public void writePayload(FriendlyByteBuf buffer, Object side) {
        super.writePayload(buffer, side);
        writeData(buffer);
    }

    private void writeData(FriendlyByteBuf buffer) {
        buffer.writeBoolean(isPulsing());
        buffer.writeBoolean(gateEnabledTicks > 0 || gateSinglePulses > 0);
        buffer.writeBoolean(manuallyEnabled);
    }

    private void readData(FriendlyByteBuf buffer) {
        isPulsing = buffer.readBoolean();
        autoEnabled = buffer.readBoolean();
        manuallyEnabled = buffer.readBoolean();
    }

    // PipePluggable

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.ordinal()];
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCSiliconItems.PLUG_PULSAR.get());
    }

    @Override
    public void onTick() {
        if (holder.getPipeWorld().isClientSide()) {
            isPulsing = isPulsing(); // UPDATE STATE ON CLIENT TOO!
            if (isPulsing) {
                pulseStage++;
                if (pulseStage == PULSE_STAGE) {
                    pulseStage = 0;
                }
            } else {
                pulseStage = 0;
            }
            return;
        }
        boolean isOn = isPulsing();

        if (isOn) {
            pulseStage++;
        } else {
            pulseStage = 0;
        }
        if (gateEnabledTicks > 0) {
            gateEnabledTicks--;
        }
        if (pulseStage == PULSE_STAGE) {
            pulseStage = 0;
            Object behaviour = holder.getPipe().getBehaviour();
            if (behaviour instanceof IMjRedstoneReceiver rsRec) {
                // TODO: use transport-specific power values when cross-module config is wired up
                long power = MjAPI.MJ;
                if (gateSinglePulses > 0) {
                    long excess = rsRec.receivePower(power, true);
                    if (excess == 0) {
                        rsRec.receivePower(power, false);
                    } else {
                        gateSinglePulses++;
                    }
                } else {
                    rsRec.receivePower(power, false);
                }
            }
            if (gateSinglePulses > 0) {
                gateSinglePulses--;
            }
        }
        if (isOn != lastPulsing) {
            lastPulsing = isOn;
            scheduleNetworkUpdate();
        }
    }

    @Override
    public boolean onPluggableActivate(Player player, HitResult trace, float hitX, float hitY, float hitZ) {
        if (!holder.getPipeWorld().isClientSide()) {
            manuallyEnabled = !manuallyEnabled;
            // Play lever click sound
            holder.getPipeWorld().playSound(null, holder.getPipePos(),
                SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F,
                manuallyEnabled ? 0.6F : 0.5F);
            scheduleNetworkUpdate();
        }
        return true;
    }

    // Gate interaction

    public void enablePulsar() {
        gateEnabledTicks = 10;
    }

    public void addSinglePulse() {
        gateSinglePulses++;
    }

    // Getters for rendering
    public boolean getIsPulsingClient() {
        return isPulsing;
    }

    public boolean getAutoEnabledClient() {
        return autoEnabled;
    }

    public boolean getManuallyEnabledClient() {
        return manuallyEnabled;
    }

    public int getPulseStageClient() {
        return pulseStage;
    }

    private boolean isPulsing() {
        return manuallyEnabled || gateEnabledTicks > 0 || gateSinglePulses > 0;
    }

    @PipeEventHandler
    public void onAddActions(PipeEventStatement.AddActionInternalSided event) {
        if (event.side == this.side) {
            event.actions.add(BCSiliconStatements.ACTION_PULSAR_CONSTANT);
            event.actions.add(BCSiliconStatements.ACTION_PULSAR_SINGLE);
        }
    }

    @Override
    public buildcraft.silicon.client.model.key.KeyPlugSimple getModelRenderKey(Object layer) {
        if (layer == null) return null;
        String name = layer.toString().toLowerCase();
        if (name.contains("cutout")) {
            return new buildcraft.silicon.client.model.key.KeyPlugSimple("pulsar", this.isPulsing, layer, this.side);
        }
        return null;
    }
}
