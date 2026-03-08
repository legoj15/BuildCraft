/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

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

    /** Getter for the stored power in micro-MJ. */
    public long getPower() { return power; }
    protected float heat = MIN_HEAT;
    protected float progress = 0;
    protected int progressPart = 0;
    protected boolean isPumping = false;
    protected boolean isRedstonePowered = false;

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

    /** @return The piston animation speed, in progress-per-tick */
    public double getPistonSpeed() {
        return Math.max(0.16f * getHeatLevel(), 0.01f);
    }

    /** Update the heat level. Default: passive cooling towards MIN_HEAT. */
    public void updateHeatLevel() {
        // Default: no-op; subclasses manage heat themselves
    }

    /** Compute the power stage based on current heat. */
    protected EnumPowerStage computePowerStage() {
        float heatLevel = getHeatLevel();
        if (heatLevel < 0.25f) return EnumPowerStage.BLUE;
        if (heatLevel < 0.5f) return EnumPowerStage.GREEN;
        if (heatLevel < 0.75f) return EnumPowerStage.YELLOW;
        if (heatLevel < 1.0f) return EnumPowerStage.RED;
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
            // Could auto-orient towards a valid receiver here
        }

        engine.updateHeatLevel();
        engine.getPowerStage(); // updates + checks overheat

        if (engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
            engine.power = Math.max(engine.power - 10, 0);
            return;
        }

        engine.engineUpdate();

        // Piston animation (server side)
        if (engine.progressPart != 0) {
            engine.progress += (float) engine.getPistonSpeed();
            if (engine.progress > 0.5f && engine.progressPart == 1) {
                engine.progressPart = 2;
            } else if (engine.progress >= 1.0f) {
                engine.progress = 0;
                engine.progressPart = 0;
            }
        } else if (engine.isRedstonePowered && engine.isBurning()) {
            engine.progressPart = 1;
            engine.setPumping(true);
        } else {
            engine.setPumping(false);
        }

        engine.setChanged();
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

    public void rotateOrientation() {
        int next = (orientation.ordinal() + 1) % 6;
        setOrientation(Direction.values()[next]);
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
