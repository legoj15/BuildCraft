/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
//? if >=1.21.10 {
import net.minecraft.util.profiling.Profiler;
//?}
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjToRfAutoConvertor;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.BCLibConfig;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.LocaleUtil;

import net.neoforged.neoforge.capabilities.Capabilities;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
//?} else {
/*import net.neoforged.neoforge.energy.IEnergyStorage;*/
//?}

/**
 * Abstract base class for all BuildCraft engines.
 * Provides tick logic, heat management, MJ power accumulation,
 * piston animation state, redstone sensitivity, and NBT persistence.
 */
public abstract class TileEngineBase_BC8 extends BlockEntity implements IDebuggable {

    public static final Identifier ADVANCEMENT_TO_MUCH_POWER =
        Identifier.parse("buildcraftunofficial:to_much_power");

    public static final float MIN_HEAT = 20f;
    public static final float MAX_HEAT = 250f;

    // --- Owner tracking (ported from 1.12.2 TileBC_Neptune) ---
    @Nullable
    private GameProfile owner;

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

    protected int orientationChecksRemaining = 1;
    protected boolean checkRedstonePower = true;
    protected int redstonePollTimer = 0;

    private EnumPowerStage powerStage = EnumPowerStage.BLUE;

    /** Client-side model variable data for animated model rendering. */
    public final buildcraft.lib.misc.data.ModelVariableData clientModelData = new buildcraft.lib.misc.data.ModelVariableData();

    // Lazy-initialized connector
    private IMjConnector mjConnector;

    public TileEngineBase_BC8(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Abstract methods for subclasses ---

    /** @return true if this engine is actively generating power (e.g. has fuel, is redstone powered).
     *  This drives the GUI flame / render "burning" indicator ONLY — it is <em>not</em> the power-output
     *  gate (see {@link #isActive()}). Keep it honest about fuel state; do not repurpose it for output. */
    public abstract boolean isBurning();

    /**
     * The power-transmission gate (1.12.2 parity). While redstone-powered, an engine sends its stored
     * MJ buffer to the receiver whenever this returns true — independent of {@link #isBurning()}, so a
     * Stirling/FE engine keeps delivering accumulated power across the gaps between fuel items instead
     * of freezing its buffer. The base default is {@code true} (the Stirling engine relies on this);
     * the Combustion engine overrides it to stall output during its {@code penaltyCooling} window.
     */
    public boolean isActive() {
        return true;
    }

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

    // --- Owner tracking ---

    /**
     * Called when the block is placed by a living entity.
     * Sets the owner to the placing player's GameProfile.
     * Matches 1.12.2 TileBC_Neptune.onPlacedBy() behavior.
     */
    public void onPlacedBy(@Nullable LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (placer instanceof net.minecraft.world.entity.player.Player player) {
            owner = player.getGameProfile();
            // Mark the chunk dirty so the owner persists even if the engine is never mutated
            // again before the chunk unloads (matches TileBC_Neptune.onPlacedBy).
            setChanged();
        }
    }

    @Nullable
    public GameProfile getOwner() {
        return owner;
    }

    public void setOwner(@Nullable GameProfile owner) {
        this.owner = owner;
    }

    // --- Overridable methods ---

    /**
     * @return how many further engines of the <em>same type</em> this engine may send power
     *         through to reach a receiver — see {@link #getReceiverToPower}. The base default is 2
     *         (1.12.2 parity); subclasses override (Combustion/FE 4, MJ Dynamo 3), and {@code 0}
     *         disables chaining entirely — the Redstone Engine returns 0 so its free trickle of
     *         power can't be stacked into a single machine.
     */
    protected int getMaxChainLength() {
        return 2;
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
            // The stage tracks live heat (1.12.2 parity — no permanent latch), but the OVERHEAT exit
            // is hysteretic: once overheated, the engine must cool down through the band to
            // overheatExitLevel() before the stage may recompute downward. Without this band, the
            // 1 MJ/tick overheat bleed dips just below the single 0.85 threshold, the burner re-arms,
            // and the stage flaps RED<->OVERHEAT in a 4-tick limit cycle (the RC5 "oscillating
            // between red and black" report) — also breaking the wrench, whose clearOverheat() only
            // fires on the 1-in-4 OVERHEAT tick. Keyed on the powerStage field itself (no extra
            // state), so clearOverheat()'s vent + direct recompute clears it naturally.
            if (powerStage == EnumPowerStage.OVERHEAT && getHeatLevel() >= overheatExitLevel()) {
                return powerStage;
            }
            EnumPowerStage newStage = computePowerStage();
            if (powerStage != newStage) {
                powerStage = newStage;
                if (powerStage == EnumPowerStage.OVERHEAT) {
                    overheat();
                }
                setChanged();
                // Push render state to the client on every stage change (1.12.2 NET_RENDER_DATA parity).
                // The serverTick OVERHEAT branch early-returns before the tail sync, so without this the
                // RED->OVERHEAT (black trunk) transition would never reach the client until an unrelated
                // block update (e.g. a wrench rotation). Skip if overheat() just exploded the block away.
                if (!isRemoved()) {
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }
            }
        }
        return powerStage;
    }

    /**
     * The OVERHEAT-exit hysteresis floor: once overheated, the stage stays OVERHEAT until
     * {@link #getHeatLevel()} falls below this. Entry stays {@link #computePowerStage()}'s 0.85.
     * <p>
     * <b>Override contract:</b> the value MUST sit above the engine's powered cooling-equilibrium
     * heat level, or a running engine can never leave OVERHEAT. The base default (0.75, the bottom
     * of the RED band) suits engines whose heat derives from stored power and drains freely (the
     * Stirling engine's overheat bleed). The Combustion engine overrides to 0.84 because its
     * coolant only cools toward IDEAL_HEAT (= heatLevel 0.80) while redstone-powered — a 0.75 exit
     * would be unreachable and would brick it.
     */
    protected float overheatExitLevel() {
        return 0.75f;
    }

    protected void overheat() {
        isPumping = false;
        if (!BCLibConfig.canEnginesExplode.get()) return;
        float range = explosionRange();
        if (range > 0 && level != null) {
            level.explode(null, getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5,
                getBlockPos().getZ() + 0.5, range, Level.ExplosionInteraction.BLOCK);
            level.removeBlock(getBlockPos(), false);
        }
    }

    /**
     * Wrench-clear path: drop heat back to MIN_HEAT and recompute the power stage.
     * Awards the {@code to_much_power} advancement to the player if non-null and
     * the engine was actually overheated.
     * @return true iff the engine was overheated and got cleared.
     */
    public boolean clearOverheat(@Nullable Player player) {
        if (powerStage != EnumPowerStage.OVERHEAT) return false;
        heat = MIN_HEAT;
        // Vent the stored buffer to the PID target band so the clear actually sticks. Stone-class
        // engines derive heat from power (updateHeatLevel), so without dropping power the next tick
        // would re-derive max heat and re-enter OVERHEAT — the reported "turns blue, then black again"
        // bug. 3/8 maxPower is the Stirling engine's natural operating point, so it resumes at normal
        // running temperature; harmless for engines whose heat is independent of power (Combustion).
        power = Math.min(power, 3 * getMaxPower() / 8);
        powerStage = computePowerStage();
        isPumping = false;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            if (player != null) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_TO_MUCH_POWER);
            }
        }
        return true;
    }

    /**
     * True iff at least one direction OTHER than the current orientation has a valid MJ
     * receiver. Used by block-side wrench handling to detect the "nothing to rotate to" UX
     * case: either zero receivers exist, or the only valid receiver is the current
     * orientation (and rotating would just land back on it).
     */
    public boolean hasAlternateReceiver() {
        for (Direction d : Direction.values()) {
            if (d == orientation) continue;
            if (getReceiverToPower(d) != null) return true;
        }
        return false;
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
     * Finds the MJ receiver this engine should power, in the given direction.
     * <p>
     * Implements 1.12.2 engine chaining: the search steps through up to {@link #getMaxChainLength()}
     * further engines of the <em>same type</em>, all facing this same direction, so a row of
     * engines can all feed a single machine at the end of the line. Anything that is not a
     * same-type engine — a different engine, a machine, a pipe — is treated as the receiver
     * candidate and looked up through the NeoForge MJ capability, with an FE/RF auto-convert
     * fallback for cross-mod compatibility.
     */
    @Nullable
    public IMjReceiver getReceiverToPower(Direction side) {
        if (level == null) return null;

        // Walk the chain: hop through same-type engines facing `side` until some other tile (the
        // receiver) is reached. Each engine costs one hop, so up to getMaxChainLength() engines
        // may sit between this engine and the receiver.
        BlockPos pos = getBlockPos();
        for (int len = 0; len <= getMaxChainLength(); len++) {
            BlockPos targetPos = pos.relative(side);
            BlockEntity tile = level.getBlockEntity(targetPos);
            if (tile == null) {
                return null;
            }
            if (tile.getClass() == getClass()) {
                // Same engine type — only chains through it if it faces the same way.
                if (((TileEngineBase_BC8) tile).orientation != side) {
                    return null;
                }
                pos = targetPos;
                continue;
            }

            // Any other tile is the receiver at the end of the chain.
            // 1. Try the native MJ capability.
            IMjReceiver receiver = level.getCapability(MjAPI.CAP_RECEIVER, targetPos, side.getOpposite());
            if (receiver != null && receiver.canConnect(getMjConnector()) && getMjConnector().canConnect(receiver)) {
                return receiver;
            }
            // 2. Fallback: NeoForge FE/RF energy, auto-converted.
            //? if >=1.21.10 {
            EnergyHandler feHandler = level.getCapability(Capabilities.Energy.BLOCK, targetPos, side.getOpposite());
            //?} else {
            /*IEnergyStorage feHandler = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, side.getOpposite());*/
            //?}
            if (feHandler != null) {
                IMjReceiver feReceiver = MjToRfAutoConvertor.createReceiver(feHandler);
                if (feReceiver != null && feReceiver.canConnect(getMjConnector())) {
                    return feReceiver;
                }
            }
            return null;
        }
        // Ran out of chain length while still on engines — no receiver within reach.
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
        //? if >=1.21.10 {
        ProfilerFiller _profiler = Profiler.get();
        //?} else {
        /*// 1.21.1 has no thread-local Profiler.get(); profiling is a non-gameplay dev tool, so use a no-op.
        ProfilerFiller _profiler = net.minecraft.util.profiling.InactiveProfiler.INSTANCE;*/
        //?}
        _profiler.push("buildcraft:engine_serverTick");
        try {
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

        if (engine.orientationChecksRemaining > 0) {
            engine.orientationChecksRemaining--;
            // 1.12.2 parity: rotateIfInvalid() — only rotate if current direction is invalid
            if (engine.getReceiverToPower(engine.orientation) == null) {
                // Current direction invalid, try to find a valid one
                if (engine.attemptRotation()) {
                    engine.orientationChecksRemaining = 0;
                    level.setBlock(pos, state.setValue(
                            buildcraft.api.properties.BuildCraftProperties.BLOCK_FACING_6,
                            engine.orientation), 3);
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            } else {
                // Current orientation is valid, stop retrying
                engine.orientationChecksRemaining = 0;
            }
        }

        engine.updateHeatLevel();
        engine.getPowerStage(); // updates + checks overheat

        if (engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
            // Self-cool: bleed the buffer at 1 MJ/tick so a stone-class engine (heat = f(power)) cools
            // through the hysteresis band (0.85 entry -> overheatExitLevel() exit) in ~100 ticks
            // instead of bricking. Combustion ignores this and recovers via coolant in its own
            // updateHeatLevel above; the bleed merely trims its buffer. Fuel consumption stays paused
            // (engineUpdate below is skipped) so no fuel is wasted while hot.
            engine.power = engine.power > MjAPI.MJ ? engine.power - MjAPI.MJ : 0;
            // 1.12.2 parity: an overheated engine still RELEASES stored power — only fuel burning is
            // paused. A connected constant-power receiver drains the buffer far faster than the bleed
            // (up to maxPowerExtracted/tick), so connected engines recover in ticks, not seconds.
            // Pulsed (IMjRedstoneReceiver) sends stay paused with the piston, as in 1.12.2.
            IMjReceiver overheatReceiver = engine.getReceiverToPower(engine.orientation);
            if (!(overheatReceiver instanceof IMjRedstoneReceiver)
                && engine.isRedstonePowered && engine.isActive()) {
                engine.sendPower(overheatReceiver);
            } else {
                engine.currentOutput = 0;
            }
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
        } else if (engine.isRedstonePowered && engine.isActive() && receiver != null) {
            long requested = receiver.getPowerRequested();
            if (requested > 0 && engine.extractPower(0, requested, false) > 0) {
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
            if (engine.isRedstonePowered && engine.isActive()) {
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
        // Power-stage changes are now synced inside getPowerStage() the moment they happen (1.12.2
        // parity), so they no longer ride this tail block — keep prevPowerStage current to avoid a
        // stale comparison, but don't request a second redundant sendBlockUpdated for it here.
        engine.prevPowerStage = engine.getPowerStage();
        if (needsSync) {
            level.sendBlockUpdated(pos, state, state, 3);
        }
        } finally {
            _profiler.pop();
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
        // Retry orientation for several ticks to give newly placed pipes
        // time to initialize their MJ receiver capability (PipeFlowPower.isReceiver
        // is only set after the pipe's first onTick/reconfigure cycle).
        orientationChecksRemaining = 5;
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
        orientationChecksRemaining = 1;
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

    // --- IDebuggable ---

    @Override
    public void getDebugInfo(java.util.List<String> left, java.util.List<String> right, Direction side) {
        left.add("facing = " + orientation);
        left.add("heat = " + LocaleUtil.localizeHeat(heat) + " -- " + String.format("%.2f %%", getHeatLevel() * 100f));
        left.add("power = " + LocaleUtil.localizeMj(power));
        left.add("stage = " + getPowerStage());
        left.add("progress = " + progress);
        left.add("last = " + LocaleUtil.localizeMjFlow(currentOutput));
    }

    @Override
    public void getClientDebugInfo(java.util.List<String> left, java.util.List<String> right, Direction side) {
        left.add("Current Model Variables:");
        clientModelData.addDebugInfo(left);
    }

    // --- NBT ---

    // Platform bridge — TileEngineBase_BC8 extends BlockEntity directly (not TileBC_Neptune), so it carries
    // its own copy of the load/save signature directive (see TileBC_Neptune for the rationale). Subclasses
    // override writeData/readData (NOT the platform methods).
    //? if >=1.21.10 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeData(new BCValueOutput(output));
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        readData(new BCValueInput(input));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(new BCValueOutput(tag));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(new BCValueInput(tag));
    }*/
    //?}

    protected void writeData(BCValueOutput output) {
        output.putByte("orientation", (byte) orientation.ordinal());
        output.putLong("power", power);
        output.putFloat("heat", heat);
        output.putFloat("progress", progress);
        output.putInt("progressPart", progressPart);
        output.putBoolean("isPumping", isPumping);
        output.putBoolean("isRedstonePowered", isRedstonePowered);
        output.putByte("powerStage", (byte) powerStage.ordinal());
        // Owner persistence (matches 1.12.2 TileBC_Neptune)
        if (owner != null && GameProfileUtil.getId(owner) != null) {
            output.putString("ownerUUID", GameProfileUtil.getId(owner).toString());
            if (GameProfileUtil.getName(owner) != null) {
                output.putString("ownerName", GameProfileUtil.getName(owner));
            }
        }
    }

    protected void readData(BCValueInput input) {
        int ord = input.getByteOr("orientation", (byte) Direction.UP.ordinal());
        orientation = Direction.values()[Math.min(ord, 5)];
        power = input.getLongOr("power", 0L);
        heat = input.getFloatOr("heat", MIN_HEAT);
        progress = input.getFloatOr("progress", 0f);
        progressPart = input.getIntOr("progressPart", 0);
        isPumping = input.getBooleanOr("isPumping", false);
        isRedstonePowered = input.getBooleanOr("isRedstonePowered", false);
        int ps = input.getByteOr("powerStage", (byte) 0);
        powerStage = EnumPowerStage.VALUES[Math.min(ps, EnumPowerStage.VALUES.length - 1)];
        // Owner persistence (matches 1.12.2 TileBC_Neptune)
        String uuidStr = input.getStringOr("ownerUUID", "");
        if (!uuidStr.isEmpty()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String name = input.getStringOr("ownerName", "Unknown");
                owner = new GameProfile(uuid, name);
            } catch (IllegalArgumentException e) {
                owner = null;
            }
        }
    }
}
