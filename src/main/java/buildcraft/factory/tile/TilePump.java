/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.tile.ITileOilSpring;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.BCFactoryAttachments;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.RefusalMemory;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.mj.MjRedstoneBatteryReceiver;
import buildcraft.lib.tile.IPumpHost;
import buildcraft.api.tiles.IDebuggable;

/**
 * Pump tile entity. Searches downward for fluids, then uses BFS flood-fill to
 * find connected source blocks and drains them using MJ power. The fill never reads (so never loads) a chunk that is
 * not in memory: fluid in one is out of reach until it loads, and the next rebuild picks it up (an idle pump looks
 * for that every {@link #REBUILD_DELAY} ticks). A cell within {@link BlockUtil#DRAIN_UPDATE_REACH} blocks of a chunk
 * border is queued only when the chunks within that reach are in memory too, because draining it updates the cells
 * around it and the blocks that react to that read further out; the same check runs again when the cell is picked
 * and when it is drained, since a chunk can unload in between. Ported from 1.12.2 TilePump.
 */
public class TilePump extends TileMiner implements IDebuggable {

    private static final Identifier ADVANCEMENT_DRAIN_ANY
        = Identifier.parse("buildcraftunofficial:draining_the_world");
    private static final Identifier ADVANCEMENT_DRAIN_OIL
        = Identifier.parse("buildcraftunofficial:oil_platform");
    private static final Identifier ADVANCEMENT_REFINE_AND_REDEFINE
        = Identifier.parse("buildcraftunofficial:refine_and_redefine");

    private static final Direction[] SEARCH_NORMAL = new Direction[] {
        Direction.UP, Direction.NORTH, Direction.SOUTH,
        Direction.WEST, Direction.EAST
    };

    private static final Direction[] SEARCH_GASEOUS = new Direction[] {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH,
        Direction.WEST, Direction.EAST
    };

    static final class FluidPath {
        public final BlockPos thisPos;
        @Nullable
        public final FluidPath parent;

        public FluidPath(BlockPos thisPos, FluidPath parent) {
            this.thisPos = thisPos;
            this.parent = parent;
        }

        public FluidPath and(BlockPos pos) {
            return new FluidPath(pos, this);
        }
    }

    /**
     * Bumped whenever the COMMON config reloads, so running pumps re-evaluate
     * {@code pumpsConsumeWater} on their next tick instead of waiting for
     * natural queue exhaustion (which can be effectively never if the pump's
     * tank stays full, leaving the cached infinite-source state stuck).
     * See {@link buildcraft.factory.BCFactory#init} for the listener.
     */
    private static final java.util.concurrent.atomic.AtomicLong CONFIG_REVISION
        = new java.util.concurrent.atomic.AtomicLong();

    public static void onConfigReloaded() {
        CONFIG_REVISION.incrementAndGet();
    }

    private final BCFluidTank tank = new BCFluidTank(1, 16 * 1000); // 16 buckets
    private boolean queueBuilt = false;
    private long builtAtRevision = -1;
    private final Map<BlockPos, FluidPath> paths = new HashMap<>();
    private BlockPos fluidConnection;
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private boolean isInfiniteWaterSource;
    private int rebuildDelay = 0;
    /** Consecutive rebuilds that found nothing to pump; sets how long the pump waits before the next one. */
    private int idleRebuilds = 0;
    /** How many times {@link #buildQueue} has run -- package-private so the idle-rebuild test can count them. */
    int rebuildCount = 0;

    /** Ticks between flood-fill rebuilds while the pump has work (and the first wait after it runs dry). */
    static final int REBUILD_DELAY = 30;
    /** The longest the pump waits between rebuilds once they keep finding nothing to pump. The wait doubles from
     *  {@link #REBUILD_DELAY} with every empty rebuild up to this, because a rebuild walks the whole pool out to
     *  {@code pumpMaxDistance} blocks and an idle pump over a big refused or exhausted pool would otherwise redo that
     *  walk every 30 ticks for nothing. The wait is cut short when the pump's column changes: the idle pump re-probes
     *  it (a walk down one column, not the pool) every {@link #REBUILD_DELAY} ticks and whenever a block beside it
     *  changes ({@link #wake}). So fluid reaching the column is found at the old cadence, and only a change out in
     *  the pool that leaves the column as it was (a claim lifted, a source poured far from the bore) waits out the
     *  back-off, at most this many ticks. A chunk the last fill found absent is checked on that same cadence, so
     *  fluid in a chunk that loads later is found as promptly as fluid reaching the column. */
    static final int MAX_IDLE_REBUILD_DELAY = 600;

    /** What the last rebuild saw down the pump's column; an idle pump rebuilds early when the column no longer
     *  matches it. Null until the first rebuild. */
    @Nullable
    private ColumnSignature idleColumn;
    /** Set by {@link #wake}: re-probe the column on the next idle tick instead of waiting for the next period. */
    private boolean columnCheckPending;
    /** The chunks ({@link BlockUtil#chunkKey}) the last fill found absent, whether a cell in one was skipped or a cell
     *  beside one was not queued. An idle pump rebuilds once any of them is in memory. Small: only a chunk the pool
     *  touches lands here. */
    private final LongSet absentChunks = new LongOpenHashSet();

    /** The column probe plus the fluid states at the cells it found: a probe alone cannot see flowing water turning
     *  into a source in the same cell, which is how a refilled pond often first reaches the bore. FluidStates are
     *  interned, so record equality compares them correctly. */
    private record ColumnSignature(ColumnProbe probe, @Nullable FluidState atFirstFluid,
            @Nullable FluidState atFirstOil) {
        static ColumnSignature of(Level level, ColumnProbe probe) {
            return new ColumnSignature(probe,
                    probe.firstFluid() == null ? null : level.getFluidState(probe.firstFluid()),
                    probe.firstOil() == null ? null : level.getFluidState(probe.firstOil()));
        }
    }

    /**
     * Cells that refused to be drained (a claim mod cancelled the break probe, or a modded {@code BucketPickup}
     * refused {@code pickupBlock}), skipped by the queue builders while they stay unchanged — see
     * {@link RefusalMemory} (policy {@link RefusalMemory#DRAIN}). Transient on purpose: never saved, so a reloaded
     * pump asks once more. Its permit is the cell protection mods cleared when the current bucket started (none
     * between buckets): it only spares the per-tick start check while the bucket charges, the fluid is never taken
     * on it alone (see {@link #mine}).
     */
    private final RefusalMemory refusals = new RefusalMemory(RefusalMemory.DRAIN);

    /** The position just below the bottom of the pump tube. */
    private BlockPos targetPos;

    @Nullable
    private BlockPos oilSpringPos;

    public TilePump(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.PUMP.get(), pos, state);
    }

    @Override
    protected boolean spillsContentsOnRemoval() {
        return true;
    }

    @Override
    protected buildcraft.lib.fluid.BCFluidTank[] getDropTanks() {
        return new buildcraft.lib.fluid.BCFluidTank[] { getTank() };
    }

    @Override
    protected IMjReceiver makeReceiver(MjBattery battery) {
        return new MjRedstoneBatteryReceiver(battery);
    }

    public BCFluidTank getTank() {
        return tank;
    }

    // --- Queue Building (BFS flood-fill) ---

    private void buildQueue() {
        rebuildCount++;
        builtAtRevision = CONFIG_REVISION.get();
        queue.clear();
        paths.clear();
        absentChunks.clear();
        isInfiniteWaterSource = false;
        oilSpringPos = null;
        targetPos = worldPosition.below();

        ColumnProbe probe = probeColumn();
        idleColumn = ColumnSignature.of(level, probe);
        BlockPos oilPos = probe.firstOil();
        BlockPos springPos = probe.spring();

        // Decide what to drain. Oil anywhere in the column wins — the tube drills
        // straight past any water sitting on top of it. A spring with no oil yet
        // means "idle until it regenerates" rather than locking onto (and bricking
        // on) the surrounding ocean water. Otherwise fall back to the first fluid
        // found, which is the historical first-fluid-down behaviour.
        BlockPos seed;
        if (oilPos != null) {
            seed = oilPos;
            oilSpringPos = springPos;
        } else if (springPos != null) {
            oilSpringPos = springPos;
            return;
        } else if (probe.firstFluid() != null) {
            seed = probe.firstFluid();
        } else {
            return;
        }

        Fluid queueFluid = BlockUtil.getFluidWithFlowing(level, seed);
        if (queueFluid == null) {
            return;
        }

        targetPos = seed;
        fluidConnection = seed;
        LongSet checked = new LongOpenHashSet();
        List<BlockPos> nextPosesToCheck = new ArrayList<>();
        nextPosesToCheck.add(seed);
        paths.put(seed, new FluidPath(seed, null));
        checked.add(seed.asLong());
        if (isQueueable(seed)) {
            queue.add(seed);
        }

        buildQueue0(queueFluid, nextPosesToCheck, checked);
    }

    /**
     * Credits the owner's {@code refine_and_redefine} tracker with the pumped crude
     * oil and fires the advancement on the rising completion edge. Crude oil is the
     * only one of the ten {@link BCEnergyFluids#BASE_NAMES} the Distiller never
     * produces (it is the recipe input), so the Pump is the sole way to fill that
     * counter — without this hook the advancement would be unreachable.
     *
     * <p>No-ops if no owner, no server (client-side or non-logical), or owner offline.
     */
    private void creditRefineAndRedefineFromPumpedOil(FluidStack drain) {
        if (getOwner() == null || level == null || level.isClientSide()) return;
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return;
        net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(GameProfileUtil.getId(getOwner()));
        if (player == null) return;
        String baseName = BCEnergyFluids.getBaseName(drain.getFluid());
        if (baseName == null) return;
        var tracker = player.getData(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION.get());
        String justSaturated = tracker.recordProduction(baseName, drain.getAmount());
        if (justSaturated != null) {
            AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_REFINE_AND_REDEFINE, justSaturated);
        }
    }

    /** Returns true if the fluid is crude oil: any heat variant, source or flowing. (A name test missed cool crude's
     *  flowing fluid, {@code oil_flowing}, which a pump on a miner meets whenever the miner stopped at an oil front.) */
    private static boolean isOil(Fluid fluid) {
        return "oil".equals(BCEnergyFluids.getBaseName(fluid));
    }

    /**
     * The result of probing straight down from a pump. Any field may be {@code null}.
     *
     * @param firstFluid the topmost fluid block in the column
     * @param firstOil   the topmost BuildCraft-oil block in the column
     * @param spring     an oil spring block standing in the column
     */
    public record ColumnProbe(BlockPos firstFluid, BlockPos firstOil, BlockPos spring) {
    }

    /**
     * Probes straight down from {@code pumpPos} for what a pump there should drain.
     * Walks through air, pump tubes and fluid alike, stopping at the first solid
     * obstruction, an oil spring block, the build-height floor, or {@code maxDepth}
     * blocks below the pump.
     *
     * <p>Unlike a plain first-fluid scan this keeps descending <em>through</em>
     * fluid, so BuildCraft oil sitting beneath ocean water — and the spring block
     * beneath that — are still found. The caller uses that to drill past water to
     * the oil, and to idle on a dry spring rather than locking onto the water.
     */
    public static ColumnProbe probeDown(Level level, BlockPos pumpPos, int maxDepth) {
        BlockPos firstFluid = null;
        BlockPos firstOil = null;
        for (BlockPos pos = pumpPos.below(); !level.isOutsideBuildHeight(pos); pos = pos.below()) {
            if (pumpPos.getY() - pos.getY() > maxDepth) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            // A tube is the miner's own plumbing, never the fluid to pump -- even when it is waterlogged
            // (a bore drilled through a pond keeps the pond's water inside the tube).
            if (BlockUtil.isMinerTube(state)) {
                continue;
            }
            Fluid fluid = BlockUtil.getFluidWithFlowing(level, pos);
            if (fluid != null) {
                if (firstFluid == null) {
                    firstFluid = pos;
                }
                if (firstOil == null && isOil(fluid)) {
                    firstOil = pos;
                }
                continue;
            }
            if (state.is(BCCoreBlocks.SPRING_OIL.get())) {
                return new ColumnProbe(firstFluid, firstOil, pos);
            }
            if (level.isEmptyBlock(pos)) {
                continue;
            }
            break;
        }
        return new ColumnProbe(firstFluid, firstOil, null);
    }

    /** The miner this pump stands on, or null when it stands on anything else. Both sides (the renderer asks too). */
    @Nullable
    public IPumpHost getHost() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof IPumpHost host ? host : null;
    }

    /**
     * What this pump should drain. On a miner it is the fluid the miner stopped at, standing in for the column the
     * miner blocks (the oil-first and spring rules of {@link #probeDown} have nothing to choose between there; the
     * spring search in {@link #buildQueue0} still runs for oil). Anywhere else it is the pump's own column.
     */
    private ColumnProbe probeColumn() {
        IPumpHost host = getHost();
        if (host == null) {
            return probeDown(level, worldPosition, BCCoreConfig.miningMaxDepth.get());
        }
        BlockPos blocking = host.getBlockingFluid();
        // A Quarry's cell can sit in a chunk the finished quarry let unload: never read (so load) it.
        Fluid fluid = blocking == null || !BlockUtil.isChunkPresent(level, blocking) ? null
                : BlockUtil.getFluidWithFlowing(level, blocking);
        // The host's cell is cached between its scans: fluid the miner would pass (water flowed in after the lava
        // was scooped) is not in its way, so it is nothing to drain.
        if (fluid == null || BlockUtil.isPassableFluid(fluid)) {
            return new ColumnProbe(null, null, null);
        }
        return new ColumnProbe(blocking, isOil(fluid) ? blocking : null, null);
    }

    /** Takes up to {@code wanted} MJ from the pump's own battery, then the rest from its host's while the host is not
     *  digging (stopped by the fluid, or finished): a digging host keeps its power for itself. */
    private long drawPower(long wanted) {
        long got = battery.extractPower(0, wanted);
        IPumpHost host = getHost();
        if (got < wanted && host != null && !host.hasWork() && sharesOwner(host)) {
            host.onPumpWantsPower();
            got += host.getBattery().extractPower(0, wanted - got);
        }
        return got;
    }

    /** Whether the host is this pump's owner's (or both are unowned): a pump set on someone else's miner must not run
     *  on their power. */
    private boolean sharesOwner(IPumpHost host) {
        GameProfile mine = getOwner();
        GameProfile theirs = host.getOwner();
        if (mine == null || theirs == null) {
            return mine == theirs;
        }
        return GameProfileUtil.getId(mine).equals(GameProfileUtil.getId(theirs));
    }

    private void buildQueue0(Fluid queueFluid, List<BlockPos> nextPosesToCheck, LongSet checked) {
        Direction[] directions = FluidUtilBC.isGaseous(queueFluid) ? SEARCH_GASEOUS : SEARCH_NORMAL;
        boolean isWater = !BCCoreConfig.pumpsConsumeWater.get()
                && FluidUtilBC.areFluidsEqual(queueFluid, Fluids.WATER);
        // Anchor-block rule: the strip is only "infinite" when the tube lands on a
        // position that would regenerate under vanilla's own water-source rule
        // (≥2 horizontal source neighbours, water/solid below). Pumping from an
        // edge of a 1×N strip therefore falls through to a full BFS so the finite
        // tail blocks land in the queue and get consumed, matching what a player
        // bucketing the edge would experience.
        boolean targetPosIsInfinite = isWater && isInfiniteSourceAt(level, targetPos);
        isInfiniteWaterSource = targetPosIsInfinite;
        final int maxLengthSquared = BCCoreConfig.pumpMaxDistance.get() * BCCoreConfig.pumpMaxDistance.get();

        outer:
        while (!nextPosesToCheck.isEmpty()) {
            List<BlockPos> nextPosesToCheckCopy = new ArrayList<>(nextPosesToCheck);
            nextPosesToCheck.clear();
            for (BlockPos posToCheck : nextPosesToCheckCopy) {
                for (Direction side : directions) {
                    BlockPos offsetPos = posToCheck.relative(side);
                    if (offsetPos.distSqr(targetPos) > maxLengthSquared) {
                        continue;
                    }
                    boolean isNew = checked.add(offsetPos.asLong());
                    if (isNew) {
                        // Never read a cell in a chunk that is not in memory: getBlockState there loads (or
                        // generates) the chunk on the server thread, and this walk probes every cell around every
                        // fluid cell out to pumpMaxDistance. Fluid in such a chunk is simply out of reach until it is
                        // loaded; the next rebuild finds it then.
                        if (!isPresent(offsetPos)) {
                            continue;
                        }
                        // The miner's own plumbing is transparent to the fill, exactly as in probeDown: a tube
                        // drilled through water is waterlogged -- a source the fill would otherwise queue, path
                        // through and let the pump un-waterlog bucket by bucket.
                        if (BlockUtil.isMinerTube(level.getBlockState(offsetPos))) {
                            continue;
                        }
                        Fluid fluidAt = BlockUtil.getFluidWithFlowing(level, offsetPos);
                        boolean eq = FluidUtilBC.areFluidsEqual(fluidAt, queueFluid);
                        if (eq) {
                            FluidPath oldPath = paths.get(posToCheck);
                            FluidPath path = new FluidPath(offsetPos, oldPath);
                            paths.put(offsetPos, path);
                            // Queue only what drainBlock can take; the path still runs through
                            // non-drainable wet cells (kelp) and cells that refused (isQueueable) so
                            // fluid beyond them is reachable.
                            if (isQueueable(offsetPos)) {
                                queue.add(offsetPos);
                            }
                            nextPosesToCheck.add(offsetPos);
                        }
                    }
                }
                // Once anchored on a regenerable position the pump will sit on a
                // single block forever, so further BFS is wasted work — short-circuit.
                if (targetPosIsInfinite) {
                    break outer;
                }
            }
        }

        // Oil spring search — matches 1.12.2 logic. Fallback only: runs when the
        // downward probe did not already find a spring directly under the tube,
        // sweeping a 21×21 area so a pump offset from the spring still credits
        // pumped oil toward its advancement tracking. The spring block sits at the
        // world floor (minY) — one below the oil source it force-places.
        if (isOil(queueFluid) && oilSpringPos == null) {
            List<BlockPos> springPositions = new ArrayList<>();
            BlockPos center = VecUtil.replaceValue(worldPosition, Axis.Y, level.getMinY());
            for (BlockPos spring : BlockPos.betweenClosed(center.offset(-10, 0, -10), center.offset(10, 0, 10))) {
                // Defensive: the sweep (+-10 blocks) crosses chunk borders, but a ticking pump's 3x3 chunk
                // neighbourhood is normally in memory already, so this rarely skips anything. It only guarantees that
                // finding a spring is never what loads a chunk.
                if (!BlockUtil.isChunkPresent(level, spring)) {
                    continue;
                }
                if (level.getBlockState(spring).is(BCCoreBlocks.SPRING_OIL.get())) {
                    BlockEntity tile = level.getBlockEntity(spring);
                    if (tile instanceof ITileOilSpring) {
                        springPositions.add(spring.immutable());
                    }
                }
            }
            switch (springPositions.size()) {
                case 0:
                    break;
                case 1:
                    oilSpringPos = springPositions.get(0);
                    break;
                default:
                    springPositions.sort(Comparator.comparingDouble(worldPosition::distSqr));
                    oilSpringPos = springPositions.get(0);
            }
        }
    }

    /** Whether the pump should line up a bucket from {@code pos}: its drain stays inside loaded chunks,
     *  {@code drainBlock} can take it, and it has not refused (and stayed the same) since. A refusing cell still
     *  carries the BFS through it, just not the queue. The reach check comes first: judging whether a waterlogged block
     *  can take the drain ({@code getDrainableFluid}) runs its {@code canSurvive}, which can read a neighbour, and a
     *  read in an absent chunk loads it. */
    private boolean isQueueable(BlockPos pos) {
        return drainStaysInLoadedChunks(pos) && BlockUtil.getDrainableFluid(level, pos) != null
                && !refusals.isRefused(level, pos, getOwner());
    }

    /** Whether the chunk holding {@code pos} is in memory, which is what makes reading it safe (a read of an absent
     *  chunk loads it). An absent chunk is remembered in {@link #absentChunks}, so an idle pump notices when it
     *  loads. */
    private boolean isPresent(BlockPos pos) {
        return BlockUtil.isChunkPresent(level, pos, absentChunks::add);
    }

    /** Whether draining {@code pos} stays inside chunks that are in memory: its own, and every one within
     *  {@link BlockUtil#DRAIN_UPDATE_REACH} blocks (see {@link BlockUtil#drainStaysInLoadedChunks}, which the robot pump
     *  asks too). A cell that would not is not queued, the same as fluid in an absent chunk: the walk still runs
     *  through it. Every absent chunk it meets is remembered, for the idle wake-up. */
    private boolean drainStaysInLoadedChunks(BlockPos pos) {
        return BlockUtil.drainStaysInLoadedChunks(level, pos, absentChunks::add);
    }

    /** Whether a chunk the last fill found absent is in memory now, so fluid it could not reach may be reachable. */
    private boolean skippedChunkLoaded() {
        for (LongIterator it = absentChunks.iterator(); it.hasNext();) {
            long key = it.nextLong();
            if (BlockUtil.isChunkPresent(level, ((int) key) << 4, ((int) (key >> 32)) << 4)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the last fill found the chunk at these chunk coordinates absent -- for the tests, which cannot see
     *  inside the fill otherwise. */
    boolean foundAbsent(int chunkX, int chunkZ) {
        return absentChunks.contains(BlockUtil.chunkKey(chunkX, chunkZ));
    }

    /** The cell the pump is working on (null when it has none) -- for the tests, which cannot see inside the queue
     *  otherwise. */
    @Nullable
    BlockPos workingOn() {
        return currentPos;
    }

    /**
     * Asks protection mods whether the pump may take fluid from {@code pos}, as the pump's owner (the generic
     * "[BuildCraft]" identity for an unowned pump), through the same probe the quarry and mining well use. Only the
     * cell the pump is working on is ever asked about, never the whole pool it could reach.
     */
    private boolean mayDrain(BlockPos pos) {
        return !(level instanceof ServerLevel serverLevel) || BlockUtil.canMachineBreak(serverLevel, pos, getOwner());
    }

    /**
     * Forgets {@code currentPos} after it refused (a protection mod, or its own pickup) and moves to the next cell.
     * The power charged for the refused bucket is gone with it, whichever refusal it was, so the next cell pays its
     * own 10 MJ in full rather than finishing on a charge it did not earn.
     */
    private void skipRefusedCell() {
        progress = 0;
        refusals.remember(level, currentPos, getOwner());
        paths.remove(currentPos);
        nextPos();
    }

    private boolean canDrain(BlockPos blockPos) {
        Fluid fluid = BlockUtil.getDrainableFluid(level, blockPos);
        if (tank.getAmountMb(0) == 0) {
            return fluid != null;
        }
        return FluidUtilBC.areFluidsEqual(fluid, tank.getFluidStack(0).getFluid());
    }

    /**
     * Returns true if {@code pos} would naturally regenerate as a water source under
     * vanilla rules: at least two horizontally adjacent source-water neighbours plus
     * a water-or-solid block below to support the new source. Caller is responsible
     * for the {@code pumpsConsumeWater} config gate and the fluid-is-water check.
     *
     * <p>Vertical neighbours are deliberately ignored — vanilla's regen check is
     * horizontal-only (a water source above does not seed a regenerated source
     * below; water just flows down). A neighbouring miner tube never counts, waterlogged or not.
     */
    public static boolean isInfiniteSourceAt(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null || !BlockUtil.isChunkPresent(level, pos)) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        Fluid fluidBelow = BlockUtil.getFluidWithFlowing(level, pos.below());
        // The legacy isSolid() is deprecated, but it is exactly what vanilla's FlowingFluid#getNewLiquid tests for
        // "can support a new source" — matching vanilla's regen rule is the point, so keep vanilla's test.
        @SuppressWarnings("deprecation")
        boolean supportsSource = below.isSolid();
        if (!FluidUtilBC.areFluidsEqual(fluidBelow, Fluids.WATER) && !supportsSource) {
            return false;
        }
        int sources = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(dir);
            // A neighbour past a chunk border may be unloaded: reading it would load it, and it is no source we know of.
            if (!BlockUtil.isChunkPresent(level, neighbourPos)) {
                continue;
            }
            // A miner's tube is plumbing, as everywhere else the pump looks (probeDown, the flood fill): a bore
            // drilled through a pond is waterlogged -- a source in vanilla's eyes -- but a world from before the tube
            // was waterloggable had a dry bore there, and a neighbouring well must not decide whether this cell
            // regenerates. (Vanilla's own rule would count it, as it counts any waterlogged neighbour that lets
            // water through; the pump deliberately does not.)
            if (BlockUtil.isMinerTube(level.getBlockState(neighbourPos))) {
                continue;
            }
            Fluid neighbour = BlockUtil.getFluid(level, neighbourPos);
            if (FluidUtilBC.areFluidsEqual(neighbour, Fluids.WATER)) {
                if (++sources >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private void nextPos() {
        // Moving to another cell ends the permit: a later re-queue of this one must ask again at the start. The
        // other way a bucket is abandoned (mine() waiting out rebuildDelay on a scooped or cut-off cell) clears it
        // itself. Neither is what keeps a claim out: the drain itself always asks again (see mine()).
        refusals.revokePermit();
        while (!queue.isEmpty()) {
            currentPos = queue.removeLast();
            // A queued cell whose chunk, or a chunk its drain would update, has unloaded since the rebuild is dropped,
            // not read (which would load it). The absent chunk is remembered, so an idle pump wakes when it loads.
            if (drainStaysInLoadedChunks(currentPos) && canDrain(currentPos)) {
                updateLength();
                return;
            }
        }
        currentPos = null;
        updateLength();
    }

    @Override
    @Nullable
    protected BlockPos getTargetPos() {
        // Return targetPos as long as the pump has work: either queued blocks or
        // a block being actively drained. Fixes tube not spawning for small volumes
        // where nextPos() empties the queue before updateLength() is called.
        // A pump on a miner reaches down through the miner's own tube: it lays none of its own.
        if ((queue.isEmpty() && currentPos == null) || getHost() != null) {
            return null;
        }
        return targetPos;
    }

    // --- Ticking ---

    /**
     * The world next to the pump changed (its block's neighbour update): an idle pump re-probes its column on the
     * next {@code mine()} and rebuilds at once if the column changed. A change that leaves the column as it was
     * (flowing water flickering beside the pump) costs one column probe and keeps the back-off. Server thread.
     */
    public void wake() {
        columnCheckPending = true;
    }

    /** Whether the column no longer matches what the last rebuild saw. Walks one column, never the pool. */
    private boolean columnChanged() {
        return !ColumnSignature.of(level, probeColumn()).equals(idleColumn);
    }

    @Override
    public void serverTick() {
        if (wantedLength != 0 && getHost() != null) {
            // A pump that had laid a tube and now stands on a miner (the miner placed in its bore): the tube cells below
            // belong to the miner now, so forget them rather than let the next updateLength retract them.
            wantedLength = 0;
            currentLength = 0;
            setChanged();
            MessageUtil.sendUpdateToTrackingPlayers(this); // clients stop drawing the old tube now, not on the next sync
        }
        if (!queueBuilt || builtAtRevision != CONFIG_REVISION.get()) {
            buildQueue();
            queueBuilt = true;
            // Whatever the pump was waiting for is moot: the queue it just built is what mine() works from now.
            rebuildDelay = 0;
            idleRebuilds = 0;
        }

        super.serverTick();

        FluidUtilBC.pushFluidToNeighbors(level, worldPosition, tank);
    }

    @Override
    protected void mine() {
        if (tank.getAmountMb(0) > tank.getCapacityMb(0) / 2) {
            return;
        }

        if (rebuildDelay > 0) {
            rebuildDelay--;
        }

        long target = 10 * MjAPI.MJ;
        if (currentPos != null && paths.containsKey(currentPos) && !drainStaysInLoadedChunks(currentPos)) {
            // The cell's chunk, or one beside it that its drain would update, unloaded since the rebuild that queued
            // it (something else was keeping it loaded). Reading the cell, and draining it, would load that chunk, so
            // the bucket is abandoned like a scooped cell's (permit and charge gone with it) and the fill is rebuilt
            // now: it leaves such cells out, so this happens once per unload.
            refusals.revokePermit();
            progress = 0;
            rebuildAndPickNext();
            return;
        }
        if (currentPos != null && paths.containsKey(currentPos)) {
            // Ask when the bucket starts, before spending power on the cell, so a claimed cell costs nothing and
            // the pump moves straight on to the next one (one cell per tick, so a fully claimed pool is not probed
            // all at once). The permit only spares this check while the bucket charges.
            boolean askedThisTick = false;
            if (canDrain(currentPos)
                    && !refusals.isPermitted(currentPos, level.getBlockState(currentPos), getOwner())) {
                if (!mayDrain(currentPos)) {
                    skipRefusedCell();
                    return;
                }
                refusals.permit(currentPos, level.getBlockState(currentPos), getOwner());
                askedThisTick = true;
            }
            progress += (int) drawPower(target - progress);
            if (progress < target) {
                return;
            }

            FluidStack drain = BlockUtil.drainBlock(level, currentPos, false);

            drain_attempt: {
                if (drain == null) {
                    break drain_attempt;
                }

                BlockPos invalid = getFirstInvalidPointOnPath(currentPos);
                if (invalid != null) {
                    break drain_attempt;
                } else if (!canDrain(currentPos)) {
                    break drain_attempt;
                }

                // Re-evaluate against the live world state so state changes since the
                // queue build (player scooped a neighbour, mob placed a block) flip the
                // pump back to consume mode on the very next tick instead of the next
                // queue rebuild.
                isInfiniteWaterSource = !BCCoreConfig.pumpsConsumeWater.get()
                        && FluidUtilBC.areFluidsEqual(drain.getFluid(), Fluids.WATER)
                        && isInfiniteSourceAt(level, targetPos);

                // Ask again now, at the moment the fluid is taken, unless the start check just asked in this same
                // tick: a bucket that charged over several ticks was cleared before a claim placed since then. The
                // charge is spent on the refused attempt (skipRefusedCell), like a refused pickup's.
                if (!askedThisTick && !mayDrain(currentPos)) {
                    skipRefusedCell();
                    return;
                }

                // Take the world's bucket BEFORE crediting the tank: a pickup can refuse
                // (a modded BucketPickup), and crediting first would mint fluid every pass.
                // Skip a refusing cell rather than stalling on it until the next rebuild; the
                // power spent on the refused attempt is gone, so the next cell pays in full.
                // The refusal is remembered, or the rebuild would queue the same cell again.
                if (!isInfiniteWaterSource && BlockUtil.drainBlock(level, currentPos, true) == null) {
                    skipRefusedCell();
                    return;
                }

                tank.fill(0, drain, false);
                progress = 0;
                refusals.revokePermit(); // the next bucket asks again
                IPumpHost host = getHost();
                if (host != null && host.getBlockingFluid() != null) {
                    host.onBlockingFluidDrained();
                }

                if (getOwner() != null) {
                    AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, ADVANCEMENT_DRAIN_ANY);
                }

                if (!isInfiniteWaterSource) {
                    if (isOil(drain.getFluid())) {
                        if (getOwner() != null) {
                            AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, ADVANCEMENT_DRAIN_OIL);
                        }
                        creditRefineAndRedefineFromPumpedOil(drain);
                        if (oilSpringPos != null) {
                            BlockEntity tile = level.getBlockEntity(oilSpringPos);
                            if (tile instanceof ITileOilSpring oilSpring) {
                                oilSpring.onPumpOil(getOwner(), currentPos);
                            }
                        }
                    }
                    paths.remove(currentPos);
                    nextPos();
                }
                return;
            }

            // The bucket is abandoned (the cell was scooped, or its path cut): its permit ends here too, or
            // the cell coming back before the rebuild would skip the start check. The power charged for it is
            // gone as well, like a refused cell's (skipRefusedCell): a charge kept would let the next cell, or this
            // one once the water returns, finish with no power of its own.
            refusals.revokePermit();
            progress = 0;
            if (rebuildDelay > 0) {
                return;
            }
        } else if (currentPos == null && rebuildDelay > 0) {
            // Idle and waiting: look down the column every REBUILD_DELAY ticks (and when woken); only a changed
            // column, or a chunk the fill had to leave out that has loaded since, ends the wait early, and it starts
            // the back-off over.
            boolean look = columnCheckPending || rebuildDelay % REBUILD_DELAY == 0;
            columnCheckPending = false;
            if (!look || !(skippedChunkLoaded() || columnChanged())) {
                return;
            }
            idleRebuilds = 0;
        }
        columnCheckPending = false;
        rebuildAndPickNext();
    }

    /**
     * Rebuilds the flood fill and lines up the next cell, then sets how long to wait before rebuilding again: the
     * fixed {@link #REBUILD_DELAY} while there is work, and a doubling wait (capped at
     * {@link #MAX_IDLE_REBUILD_DELAY}) for as long as rebuilds keep finding nothing to pump.
     */
    private void rebuildAndPickNext() {
        buildQueue();
        nextPos();
        if (currentPos != null) {
            idleRebuilds = 0;
            rebuildDelay = REBUILD_DELAY;
        } else {
            rebuildDelay = Math.min(REBUILD_DELAY << Math.min(idleRebuilds, 5), MAX_IDLE_REBUILD_DELAY);
            idleRebuilds++;
        }
    }

    @Nullable
    private BlockPos getFirstInvalidPointOnPath(BlockPos from) {
        FluidPath path = paths.get(from);
        if (path == null) {
            return from;
        }
        do {
            // A path cell in a chunk that has unloaded since the rebuild cuts the path, as a scooped one does.
            if (!BlockUtil.isChunkPresent(level, path.thisPos)
                    || BlockUtil.getFluidWithFlowing(level, path.thisPos) == null) {
                return path.thisPos;
            }
        } while ((path = path.parent) != null);
        return null;
    }

    // --- Save / Load ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        tank.serialize(output.raw);
        if (oilSpringPos != null) {
            output.putBoolean("hasOilSpring", true);
            output.putInt("oilSpringX", oilSpringPos.getX());
            output.putInt("oilSpringY", oilSpringPos.getY());
            output.putInt("oilSpringZ", oilSpringPos.getZ());
        }
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        tank.deserialize(input.raw);
        if (input.getBooleanOr("hasOilSpring", false)) {
            oilSpringPos = new BlockPos(
                input.getIntOr("oilSpringX", 0),
                input.getIntOr("oilSpringY", 0),
                input.getIntOr("oilSpringZ", 0));
        } else {
            oilSpringPos = null;
        }
    }

    // --- IDebuggable ---

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        // TileMiner fields (matches 1.12.2 super.getDebugInfo)
        left.add("battery = " + battery.getDebugString());
        left.add("current = " + currentPos);
        left.add("wantedLength = " + wantedLength);
        left.add("currentLength = " + currentLength);
        left.add("lastLength = " + lastLength);
        left.add("isComplete = " + isComplete());
        left.add("progress = " + MjAPI.formatMj((long) progress));
        // TilePump-specific fields
        left.add("fluid = " + FluidUtilBC.getDebugString(tank.getFluidStack(0)));
        left.add("queue size = " + queue.size());
        left.add("infinite = " + isInfiniteWaterSource);
        IPumpHost host = getHost();
        BlockPos blocking = host == null ? null : host.getBlockingFluid();
        left.add("host = " + (host == null ? "none" : blocking == null ? "no fluid in its way" : "blocked at " + blocking));
    }

    @Override
    public void getClientDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("current = " + currentPos);
        left.add("wantedLength = " + wantedLength);
        left.add("currentLength = " + currentLength);
        left.add("isComplete = " + isComplete());
        left.add("progress = " + MjAPI.formatMj((long) progress));
        left.add("fluid = " + FluidUtilBC.getDebugString(tank.getFluidStack(0)));
        left.add("queue size = " + queue.size());
        left.add("infinite = " + isInfiniteWaterSource);
    }

    // --- Cleanup ---

    @Override
    public void onRemove() {
        if (getHost() != null) {
            // The column below is the miner's: never walk into it on an old tube length (serverTick forgets the length
            // too, but only on the pump's next tick).
            wantedLength = 0;
        }
        super.onRemove();
    }

    @Override
    public void setRemoved() {
        // Do NOT call onRemove() here — setRemoved() is also called during chunk
        // unload/save, and removing tubes mid-serialization causes a save hang.
        // Tube cleanup is handled by BlockPump.playerWillDestroy() instead.
        super.setRemoved();
    }

    @Override
    protected long getBatteryCapacity() {
        return 50 * MjAPI.MJ;
    }

}
