/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjToRfAutoConvertor;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * Abstract base class for all BuildCraft engines.
 * Provides tick logic, heat management, MJ power accumulation,
 * piston animation state, redstone sensitivity, and NBT persistence.
 */
public abstract class TileEngineBase_BC8 extends BlockEntity {

    public static final float MIN_HEAT = 20f;
    public static final float MAX_HEAT = 250f;

    // --- Engine state ---
    protected Direction orientation = Direction.UP;
    protected long power = 0;
    public long currentOutput = 0;

    /** Getter for the stored power in micro-MJ. */
    public long getPower() { return power; }
    protected float heat = MIN_HEAT;
    protected float progress = 0;
    protected int progressPart = 0;
    protected boolean isPumping = false;
    protected boolean isRedstonePowered = false;

    // Client-side animation state
    private float lastProgress = 0;
    private float clientProgress = 0;
    private boolean clientIsPumping = false;

    // Track previous render-relevant state for detecting changes
    Direction prevOrientation = Direction.UP;
    boolean prevIsPumping = false;
    EnumPowerStage prevPowerStage = EnumPowerStage.BLUE;

    protected boolean checkOrientation = true;
    protected boolean checkRedstonePower = true;
    protected int redstonePollTimer = 0;

    private EnumPowerStage powerStage = EnumPowerStage.BLUE;

    // Lazy-initialized connector
    private IMjConnector mjConnector;

    public TileEngineBase_BC8(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Abstract methods for subclasses ---

    /** @return true if this engine is actively generating power (e.g. has fuel, is redstone powered) */
    public abstract boolean isBurning();

    /** Called every server tick for subclass-specific engine logic (fuel consumption, power generation, etc.) */
    protected abstract void engineUpdate();

    /** @return The maximum power this engine can store, in micro-MJ */
    public abstract long getMaxPower();

    /** @return Minimum power a receiver must accept */
    public abstract long minPowerReceived();

    /** @return Maximum power a receiver should accept at once */
    public abstract long maxPowerReceived();

    /** @return Maximum power this engine can extract and send per cycle */
    public abstract long maxPowerExtracted();

    /** @return The current output rate in micro-MJ */
    public abstract long getCurrentOutput();

    /** @return The explosion range if the engine overheats, or 0 for no explosion */
    public abstract float explosionRange();

    /** Creates the MJ connector for this engine. Called once lazily. */
    @Nonnull
    protected abstract IMjConnector createConnector();

    // --- Overridable methods ---

    /** @return The maximum chain length for engine chaining, 0 to disable */
    protected int getMaxChainLength() {
        return 0;
    }

    /** @return The piston animation speed, in progress-per-tick.
     *  From 1.12.2: stage-based speeds. */
    public double getPistonSpeed() {
        switch (getPowerStage()) {
            case BLUE:   return 0.02;
            case GREEN:  return 0.04;
            case YELLOW: return 0.08;
            case RED:    return 0.12;
            default:     return 0;
        }
    }

    /** Update heat level. In 1.12, heat is directly proportional to stored power. */
    public void updateHeatLevel() {
        heat = (float) (((MAX_HEAT - MIN_HEAT) * getEnergyLevel()) + MIN_HEAT);
    }

    /** Compute the power stage based on current heat. */
    protected EnumPowerStage computePowerStage() {
        float heatLevel = getHeatLevel();
        if (heatLevel < 0.25f) return EnumPowerStage.BLUE;
        if (heatLevel < 0.5f) return EnumPowerStage.GREEN;
        if (heatLevel < 0.75f) return EnumPowerStage.YELLOW;
        if (heatLevel < 0.85f) return EnumPowerStage.RED;
        return EnumPowerStage.OVERHEAT;
    }

    // --- Heat helpers ---

    /** @return The raw heat value in °C (MIN_HEAT to MAX_HEAT, i.e. 20-250). */
    public float getHeat() {
        return heat;
    }

    /** @return Normalized heat level as a 0.0-1.0 ratio of (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT). */
    public float getHeatLevel() {
        return (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT);
    }

    public double getEnergyLevel() {
        long max = getMaxPower();
        if (max <= 0) return 0;
        return (double) power / max;
    }

    // --- Power stage ---

    public final EnumPowerStage getPowerStage() {
        if (level != null && !level.isClientSide()) {
            if (powerStage == EnumPowerStage.OVERHEAT) {
                return powerStage;
            }
            EnumPowerStage newStage = computePowerStage();
            if (powerStage != newStage) {
                powerStage = newStage;
                if (powerStage == EnumPowerStage.OVERHEAT) {
                    overheat();
                }
                setChanged();
            }
        }
        return powerStage;
    }

    protected void overheat() {
        isPumping = false;
        float range = explosionRange();
        if (range > 0 && level != null) {
            level.explode(null, getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5,
                getBlockPos().getZ() + 0.5, range, Level.ExplosionInteraction.BLOCK);
            level.removeBlock(getBlockPos(), false);
        }
    }

    // --- Connector ---

    public IMjConnector getMjConnector() {
        if (mjConnector == null) {
            mjConnector = createConnector();
        }
        return mjConnector;
    }

    // --- Power extraction & transfer (ported from 1.12.2) ---

    /**
     * Extract power from the engine's internal buffer.
     * @param min Minimum amount to extract (returns 0 if buffer is below this)
     * @param max Maximum amount to extract
     * @param doExtract If true, actually deduct the power
     * @return The amount extracted
     */
    public long extractPower(long min, long max, boolean doExtract) {
        if (power < min) return 0;
        long actualMax = Math.min(max, maxPowerExtracted());
        if (actualMax < min) return 0;
        long extracted = Math.min(power, actualMax);
        if (doExtract) {
            power -= extracted;
        }
        return extracted;
    }

    /**
     * Look up the MJ receiver on the adjacent block in the given direction.
     * Uses NeoForge BlockCapability lookup, with FE/RF fallback for cross-mod compatibility.
     */
    @Nullable
    public IMjReceiver getReceiverToPower(Direction side) {
        if (level == null) return null;
        BlockPos targetPos = getBlockPos().relative(side);
        BlockEntity tile = level.getBlockEntity(targetPos);
        if (tile == null) return null;

        // Engine chaining: if the adjacent tile is the same engine type facing the same way, skip
        if (tile.getClass() == getClass()) {
            return null;
        }

        // 1. Try native MJ capability
        IMjReceiver receiver = level.getCapability(MjAPI.CAP_RECEIVER, targetPos, side.getOpposite());
        if (receiver != null && receiver.canConnect(getMjConnector()) && getMjConnector().canConnect(receiver)) {
            return receiver;
        }

        // 2. Fallback: try NeoForge FE/RF energy and auto-convert
        EnergyHandler feHandler = level.getCapability(Capabilities.Energy.BLOCK, targetPos, side.getOpposite());
        if (feHandler != null) {
            IMjReceiver feReceiver = MjToRfAutoConvertor.createReceiver(feHandler);
            if (feReceiver != null && feReceiver.canConnect(getMjConnector())) {
                return feReceiver;
            }
        }

        return null;
    }

    /**
     * Send power from this engine to the given receiver.
     * Matches the 1.12.2 sendPower() behavior.
     */
    protected void sendPower(@Nullable IMjReceiver receiver) {
        if (receiver == null) {
            currentOutput = 0;
            return;
        }
        long requested = receiver.getPowerRequested();
        long extracted = extractPower(0, requested, false);
        if (extracted > 0) {
            long excess = receiver.receivePower(extracted, false);
            long actualSent = extracted - excess;
            extractPower(actualSent, actualSent, true);
            currentOutput = actualSent;
        } else {
            currentOutput = 0;
        }
    }

    // --- Tick logic ---

    public static <T extends TileEngineBase_BC8> void serverTick(Level level, BlockPos pos, BlockState state, T engine) {
        // Periodically poll redstone state every 10 ticks as workaround for
        // neighborChanged not being called in NeoForge 1.21.11
        engine.redstonePollTimer++;
        if (engine.redstonePollTimer >= 10) {
            engine.redstonePollTimer = 0;
            engine.checkRedstonePower = true;
        }

        if (engine.checkRedstonePower) {
            engine.checkRedstoneLevel();
        }

        if (engine.checkOrientation) {
            engine.checkOrientation = false;
            // Auto-orient towards a valid receiver (1.12.2 parity)
            for (Direction dir : Direction.values()) {
                IMjReceiver receiver = engine.getReceiverToPower(dir);
                if (receiver != null) {
                    engine.orientation = dir;
                    level.setBlock(pos, state.setValue(
                            buildcraft.api.properties.BuildCraftProperties.BLOCK_FACING_6, dir), 3);
                    level.sendBlockUpdated(pos, state, state, 3);
                    break;
                }
            }
        }

        engine.updateHeatLevel();
        engine.getPowerStage(); // updates + checks overheat

        if (engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
            engine.power = Math.max(engine.power - 10, 0);
            return;
        }

        // Bleed off power when not redstone-powered (1.12.2 parity)
        if (!engine.isRedstonePowered) {
            if (engine.power > MjAPI.MJ) {
                engine.power -= MjAPI.MJ;
            } else if (engine.power > 0) {
                engine.power = 0;
            }
        }

        engine.engineUpdate();

        // --- Power transfer to adjacent receiver ---
        IMjReceiver receiver = engine.getReceiverToPower(engine.orientation);
        boolean pulsedPower = receiver instanceof IMjRedstoneReceiver;

        // Piston animation (server side)
        if (engine.progressPart != 0) {
            engine.progress += (float) engine.getPistonSpeed();
            if (engine.progress > 0.5f && engine.progressPart == 1) {
                engine.progressPart = 2;
                // Pulsed power: send at piston midpoint
                if (pulsedPower) {
                    engine.sendPower(receiver);
                }
            } else if (engine.progress >= 1.0f) {
                engine.progress = 0;
                engine.progressPart = 0;
            }
        } else if (engine.isRedstonePowered && engine.isBurning()) {
            if (engine.extractPower(0, engine.maxPowerExtracted(), false) > 0) {
                engine.progressPart = 1;
                engine.setPumping(true);
            } else {
                engine.setPumping(false);
            }
        } else {
            engine.setPumping(false);
        }

        // Constant power: send every tick (when not pulsed)
        if (!pulsedPower) {
            if (engine.isRedstonePowered && engine.isBurning()) {
                engine.sendPower(receiver);
            } else {
                engine.currentOutput = 0;
            }
        }

        engine.setChanged();

        // Sync render-relevant state changes to client
        boolean needsSync = false;
        if (engine.orientation != engine.prevOrientation) {
            engine.prevOrientation = engine.orientation;
            needsSync = true;
        }
        if (engine.isPumping != engine.prevIsPumping) {
            engine.prevIsPumping = engine.isPumping;
            needsSync = true;
        }
        if (engine.getPowerStage() != engine.prevPowerStage) {
            engine.prevPowerStage = engine.getPowerStage();
            needsSync = true;
        }
        if (needsSync) {
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // --- Redstone ---

    public void checkRedstoneLevel() {
        checkRedstonePower = false;
        if (level != null) {
            isRedstonePowered = level.hasNeighborSignal(getBlockPos());
        }
    }

    public void onNeighborUpdate() {
        checkRedstonePower = true;
        checkOrientation = true;
    }

    // --- Pumping ---

    protected final void setPumping(boolean active) {
        if (isPumping == active) return;
        isPumping = active;
        setChanged();
    }

    public boolean isPumping() {
        return isPumping;
    }

    // --- Orientation ---

    public Direction getOrientation() {
        return orientation;
    }

    public void setOrientation(Direction dir) {
        orientation = dir;
        checkOrientation = true;
        setChanged();
    }

    /**
     * Freely rotate to the next direction (cycles all 6).
     * Used internally for auto-orientation.
     */
    public void rotateOrientation() {
        int next = (orientation.ordinal() + 1) % 6;
        setOrientation(Direction.values()[next]);
    }

    /**
     * Attempt to rotate to the next valid receiver direction.
     * Matches 1.12.2 TileEngineBase_BC8.attemptRotation():
     * iterates all 6 directions starting from current, only rotates
     * if a valid MJ receiver exists in the target direction.
     * @return true if rotation succeeded
     */
    public boolean attemptRotation() {
        Direction current = orientation;
        Direction[] dirs = Direction.values();
        for (int i = 0; i < 6; i++) {
            current = dirs[(current.ordinal() + 1) % 6];
            if (isFacingReceiver(current)) {
                if (current != orientation) {
                    setOrientation(current);
                    return true;
                }
                return false; // same direction, no change
            }
        }
        return false; // no valid receiver found
    }

    private boolean isFacingReceiver(Direction dir) {
        return getReceiverToPower(dir) != null;
    }

    // --- Client tick ---

    public void clientTick() {
        lastProgress = clientProgress;
        clientIsPumping = isPumping;
        if (clientIsPumping) {
            clientProgress += (float) getPistonSpeed();
            if (clientProgress >= 1.0f) {
                clientProgress = 0;
            }
        } else {
            // When not pumping, smoothly retract to 0
            if (clientProgress > 0) {
                clientProgress -= 0.02f;
                if (clientProgress < 0) clientProgress = 0;
            }
        }
    }

    /**
     * Get the interpolated piston progress for rendering.
     * @param partialTicks fractional tick for smooth animation
     * @return progress value 0.0 to 1.0
     */
    public float getProgressClient(float partialTicks) {
        // Handle wrapping (progress reset from ~1.0 to 0)
        if (lastProgress > 0.8f && clientProgress < 0.2f) {
            // Wrapping case: interpolate through 1.0
            float interp = lastProgress + (1.0f + clientProgress - lastProgress) * partialTicks;
            return interp >= 1.0f ? interp - 1.0f : interp;
        }
        return lastProgress + (clientProgress - lastProgress) * partialTicks;
    }

    // --- Network sync ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putByte("orientation", (byte) orientation.ordinal());
        output.putLong("power", power);
        output.putFloat("heat", heat);
        output.putFloat("progress", progress);
        output.putBoolean("isPumping", isPumping);
        output.putBoolean("isRedstonePowered", isRedstonePowered);
        output.putByte("powerStage", (byte) powerStage.ordinal());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int ord = input.getByteOr("orientation", (byte) Direction.UP.ordinal());
        orientation = Direction.values()[Math.min(ord, 5)];
        power = input.getLongOr("power", 0L);
        heat = input.getFloatOr("heat", MIN_HEAT);
        progress = input.getFloatOr("progress", 0f);
        isPumping = input.getBooleanOr("isPumping", false);
        isRedstonePowered = input.getBooleanOr("isRedstonePowered", false);
        int ps = input.getByteOr("powerStage", (byte) 0);
        powerStage = EnumPowerStage.VALUES[Math.min(ps, EnumPowerStage.VALUES.length - 1)];
    }
}
