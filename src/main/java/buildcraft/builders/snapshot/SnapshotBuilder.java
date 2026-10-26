/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.RefusalMemory;
import buildcraft.lib.misc.RegistryUtilBC;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.net.PacketBufferBC;

public abstract class SnapshotBuilder<T extends ITileForSnapshotBuilder> {
    private static final int MAX_QUEUE_SIZE = 16;
    @SuppressWarnings("WeakerAccess")
    protected static final byte CHECK_RESULT_UNKNOWN = 0;
    @SuppressWarnings("WeakerAccess")
    protected static final byte CHECK_RESULT_CORRECT = 1;
    @SuppressWarnings("WeakerAccess")
    protected static final byte CHECK_RESULT_TO_BREAK = 2;
    @SuppressWarnings("WeakerAccess")
    protected static final byte CHECK_RESULT_TO_PLACE = 3;
    private static final byte REQUIRED_UNKNOWN = 0;
    private static final byte REQUIRED_TRUE = 1;
    private static final byte REQUIRED_FALSE = 2;
    private static final int CHECKS_PER_TICK = 10;
    /** Ticks a whole-box fluid sweep result stays good for the place-task gate (see {@code areaFluid}). Vanilla
     *  water spreads one cell per 5 ticks, so a result this old is at most one flow step behind the world. */
    public static final int FLUID_SCAN_INTERVAL = 5;
    private static final long MAX_POWER_PER_TICK = 256 * MjAPI.MJ;

    /** Whether the last {@link #tick()} did real work: drew power into a task, or finished one. A queue of tasks with
     *  nothing drawn (no power, nothing arriving) is not work. */
    private boolean madeProgress;

    protected final T tile;
    // Replaced WorldEventListenerAdapter with polling approach (approved in plan review)
    public final Queue<BreakTask> breakTasks = new ArrayDeque<>();
    public final Queue<BreakTask> clientBreakTasks = new ArrayDeque<>();
    public final java.util.Set<BreakTask> clientBreakTasksCache = new java.util.HashSet<>();
    @SuppressWarnings("WeakerAccess")
    public final Queue<BreakTask> prevClientBreakTasks = new ArrayDeque<>();
    public final Queue<PlaceTask> placeTasks = new ArrayDeque<>();
    public final Queue<PlaceTask> clientPlaceTasks = new ArrayDeque<>();
    @SuppressWarnings("WeakerAccess")
    public final Queue<PlaceTask> prevClientPlaceTasks = new ArrayDeque<>();
    @SuppressWarnings("WeakerAccess")
    protected byte[] checkResults;
    private byte[] requiredCache;
    private int[] breakOrder;
    private int[] placeOrder;
    private int[] checkOrder;
    private int currentCheckIndex;
    /** The visual position of the robot for rendering. Updated over the network every 5 ticks. */
    public Vec3 robotPos;
    public Vec3 prevRobotPos;

    /** The smoothed local representation of the robot on the client. */
    public Vec3 visualRobotPos;
    public Vec3 visualPrevRobotPos;
    public int leftToBreak = 0;
    public int leftToPlace = 0;
    /** How many full box sweeps {@link #scanAreaFluid()} has run. A diagnostic, so a test can pin that the sweep is
     *  not repeated needlessly; not persisted. */
    private int fluidSweeps;
    /** The last {@link #scanAreaFluid()} result the place-task gate used, or null when there is none to reuse. */
    private AreaFluid cachedAreaFluid;
    /** The fluid mode {@link #cachedAreaFluid} was taken under: tolerance, and so the result, depends on it. */
    private EnumFluidHandlingMode cachedAreaFluidMode;
    /** Ticks since {@link #cachedAreaFluid} was taken. */
    private int cachedAreaFluidAge;
    /** Whether {@link #replaceCensus} holds the census of the last REPLACE/NO_REPLACE sweep that found fluid. */
    private boolean replaceCensusKnown;
    /** The fluid census of the last REPLACE/NO_REPLACE sweep ({@link #scanReplaceFluid}). */
    private long replaceCensus;
    /** Game time at which {@link #replaceCensus} was first read as it is now. */
    private long replaceCensusSince;
    /** Cells a protection mod refused this builder's placement in ({@link PlaceResult#REFUSED}), parked: they are
     *  not queued again until the block there changes or the {@link RefusalMemory#BUILDER_PLACE} window ends, so a
     *  build inside a claim neither re-asks every pass nor drops the cell for good (the build stays unfinished and
     *  goes on once the claim is lifted). Server side only, never saved: a reloaded builder asks once more. */
    protected final RefusalMemory placeRefusals = new RefusalMemory(RefusalMemory.BUILDER_PLACE);
    /** Cells a protection mod forbids this builder to break (see {@link #isBreakBlocked}), parked the same way as
     *  {@link #placeRefusals}: not asked about again until the block there changes or the window ends. */
    private final RefusalMemory breakRefusals = new RefusalMemory(RefusalMemory.BUILDER_PLACE);

    @SuppressWarnings("WeakerAccess")
    protected SnapshotBuilder(T tile) {
        this.tile = tile;
    }

    protected abstract Snapshot.BuildingInfo getBuildingInfo();

    /**
     * Current fluid-handling mode from the tile. Concrete subclasses may override if they
     * want different semantics. The Filler's tile keeps the interface default, NO_REPLACE,
     * since template placement doesn't touch fluids.
     */
    protected EnumFluidHandlingMode getFluidMode() {
        return tile.getFluidMode();
    }

    /**
     * Tier value for sorting break-task candidates: 0 = source fluid, 1 = flowing fluid, 2 =
     * non-fluid block. Used by {@link #tick()} so that source fluids go down before flowing in
     * mixed-fluid scenarios and the flowing drains naturally after.
     */
    private int breakPriorityTier(BlockPos pos) {
        net.minecraft.world.level.material.FluidState fs = tile.getWorldBC().getFluidState(pos);
        if (fs.isEmpty()) return 2;
        return fs.isSource() ? 0 : 1;
    }

    /**
     * Returns true if the schematic block at {@code blockPos} is "fragile" — i.e. fluid flowing
     * into it would wash it away (snow_layer, carpet, button, redstone wire, sapling, torch, …;
     * see {@link SchematicBlockDefault#isWashedAwayBy}). Used by the place-task-add filter to
     * defer fragile placements while fluid in the area could still reach them; the per-build
     * fragile defer catches most cases but has a blind spot for fluid 2+ cells away that flows
     * in over a few ticks (timing race), and the visible symptom is a bouncing inventory slot
     * count as items get repeatedly extracted-then-refunded by the place-then-defer loop.
     * Default: false (no schematic awareness in the abstract base). Overridden in
     * {@link BlueprintBuilder}.
     */
    protected boolean isFragileSchematicAt(BlockPos blockPos) {
        return false;
    }

    /**
     * Would fluid already beside or above {@code blockPos} wash the schematic block there away the moment it is
     * placed? The gate's per-cell fragile check once only fluid that stays put is left (see the place-task gate in
     * {@link #tick()}). Default: false. Overridden in {@link BlueprintBuilder}.
     */
    protected boolean isFragileWashedAwayAt(BlockPos blockPos) {
        return false;
    }

    /**
     * Is the fluid in schematic-air cell {@code blockPos} fluid the Builder should leave alone: the fluid mode never
     * removes fluid, or mopping it is futile (vanilla refills it at once from fluid the Builder can never remove)?
     * Such a cell counts as {@code CORRECT} in {@link #check} and as tolerated in {@link #scanAreaFluid()}.
     *
     * <p>This is the first half, which every builder shares: outside CLEAR nothing removes fluid (the break queue
     * refuses every fluid-only cell, {@link #shouldBreakQueueAcceptFluid}), so a cell holding nothing but fluid
     * ({@link #isFluidOnly}) is as clear as the mode ever makes it. Without it such a cell stayed "to break" for
     * ever and the build never finished. A real block holding fluid (a waterlogged fence, seagrass, kelp) is not
     * covered: it is still a block to break, and the fluid it leaves then counts. The mopping half belongs to
     * {@link BlueprintBuilder}, the only builder whose mode can be CLEAR.
     */
    protected boolean isToleratedFluidAt(BlockPos blockPos) {
        if (getFluidMode() == EnumFluidHandlingMode.CLEAR) return false;
        BlockState state = tile.getWorldBC().getBlockState(blockPos);
        return !state.getFluidState().isEmpty() && isFluidOnly(state);
    }

    /** What the fluid in the build area means to the place-task gate; see {@link #scanAreaFluid()}. */
    protected enum AreaFluid {
        /** No fluid anywhere in the build box (either mode). */
        NONE,
        /** CLEAR: fluid only inside cells the check sweep accepted as correct (a block the schematic wants
         *  waterlogged, or a block or air cell vanilla re-floods from fluid the Builder cannot remove). It will
         *  never be mopped, but it can still wash a fragile block beside it away. */
        TOLERATED_ONLY,
        /** CLEAR: fluid that mopping is still to clear (an air cell, or a block the check has not accepted). */
        UNCLEARED,
        /** REPLACE and NO_REPLACE: fluid that has stopped changing (a walled pond, or flowing fluid that has filled
         *  every cell it can reach); see {@link #scanReplaceFluid}. It can wash away only a fragile block placed
         *  beside or below it. */
        SETTLED,
        /** REPLACE and NO_REPLACE: fluid that has changed lately, or whose open surroundings have: it may reach any
         *  cell of the box a few flow steps from now. */
        MOVING
    }

    /**
     * O(n) sweep of the build area for fluid (source or flowing), classified for the place-task-add gate of the
     * current fluid mode.
     *
     * <p>CLEAR splits it into what mopping must still clear and what the check sweep already accepted: the gate
     * defers placements while any fluid is {@link AreaFluid#UNCLEARED}, and once only tolerated fluid is left defers
     * just the fragile blocks that fluid touches. Counting accepted cells as "still to mop" would hold the gate shut
     * for every other block for as long as one tolerated wet block stands, since mopping can never remove its water.
     *
     * <p>REPLACE and NO_REPLACE never remove fluid, so they split it into fluid that can still move
     * ({@link AreaFluid#MOVING}) and fluid that has settled ({@link AreaFluid#SETTLED}); see {@link #scanReplaceFluid}.
     * Moving fluid holds every fragile placement back, settled fluid only the fragile blocks it touches. Holding them
     * back on any fluid at all meant a REPLACE build with one still pond in its box never placed its torches.
     *
     * <p>The CLEAR sweep stops at the first uncleared fluid, so it is cheap while there is any, but a clear area, and
     * one holding only tolerated fluid, are only known after every cell has been read; the other modes' sweep reads
     * every cell. That is why the gate does not call this directly: {@link #areaFluid} asks for it only once a placement
     * actually reaches the gate, and reuses the answer for {@link #FLUID_SCAN_INTERVAL} ticks, except to let a
     * fragile placement through (see {@link #areaFluid} for what a reused answer may get wrong). The walk itself
     * allocates nothing per cell (one reused cursor); a fluid-holding cell costs a few position objects while it is
     * classified.
     */
    protected AreaFluid scanAreaFluid() {
        fluidSweeps++;
        Snapshot.BuildingInfo info = getBuildingInfo();
        if (info == null) return AreaFluid.NONE;
        BlockPos min = info.box.min();
        BlockPos max = info.box.max();
        if (min == null || max == null) return AreaFluid.NONE;
        Level world = tile.getWorldBC();
        if (getFluidMode() != EnumFluidHandlingMode.CLEAR) return scanReplaceFluid(world, min, max);
        replaceCensusKnown = false;
        if (!mayHoldFluid(world, min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ())) {
            return AreaFluid.NONE;
        }
        AreaFluid result = AreaFluid.NONE;
        // y outermost, x innermost: the order a chunk section stores its blocks in.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    cursor.set(x, y, z);
                    if (world.getFluidState(cursor).isEmpty()) continue;
                    // The tolerance checks below may hand the position on, so give them one that stays put.
                    BlockPos pos = cursor.immutable();
                    // A non-air cell: CORRECT means the check accepted the block, fluid and all (a stale CORRECT
                    // is re-checked within a sweep). An air cell's CORRECT may be stale from before the fluid
                    // arrived, so its tolerance is re-derived from the world here rather than read off
                    // checkResults.
                    boolean tolerated = isAir(pos)
                        ? isToleratedFluidAt(pos)
                        : checkResults[posToIndex(pos)] == CHECK_RESULT_CORRECT;
                    if (!tolerated) {
                        return AreaFluid.UNCLEARED;
                    }
                    result = AreaFluid.TOLERATED_ONLY;
                }
            }
        }
        return result;
    }

    /**
     * The REPLACE and NO_REPLACE half of {@link #scanAreaFluid()}: has the fluid in and around the build box stopped
     * changing?
     *
     * <p>Whether fluid will move cannot be read off one cell. Flowing fluid that has filled every cell it can reach
     * stays as it is for as long as its source does (a waterlogged block spilling into the air cell beside it, the
     * shallow rim of a pond), and a source walled in on every side never moves, while a source placed this tick, or
     * still water beside a wall broken this tick, has simply not moved YET. So the sweep takes a census: every fluid
     * cell in the box and in the one-cell shell beside and above it (fluid outside can flow in), by position, fluid
     * state, and which of the cells it spreads to (below and beside) are open to it (holding no fluid, and
     * {@link SchematicBlockDefault#isWashedAwayBy washed away} by it). The fluid has settled once the census has read
     * the same for longer than any fluid in it can wait between two flow steps, {@link #SETTLE_TICK_DELAYS} of its
     * tick delays (lava's spread can wait four). Until then it is MOVING.
     *
     * <p>A new source, fluid entering, a cell filling or draining, and a wall opening beside fluid all change the
     * census on the first sweep that sees them, so the fresh re-sweep before a fragile placement
     * ({@link #passesFreshFluidGate}) still refuses one on water that entered the box after the last sweep. What the
     * census leaves out never moves fluid: sugar cane growing beside a pond changes its block state, not whether
     * water can flow into it. The walk allocates nothing per cell (two reused cursors).
     */
    private AreaFluid scanReplaceFluid(Level world, BlockPos min, BlockPos max) {
        // Fluid never flows up, so the shell needs no layer below the box.
        if (!mayHoldFluid(world, min.getX() - 1, min.getY(), min.getZ() - 1,
                max.getX() + 1, max.getY() + 1, max.getZ() + 1)) {
            replaceCensusKnown = false;
            return AreaFluid.NONE;
        }
        long census = 0;
        boolean any = false;
        int maxDelay = 0;
        Fluid lastType = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY() + 1; y++) {
            for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                for (int x = min.getX() - 1; x <= max.getX() + 1; x++) {
                    cursor.set(x, y, z);
                    FluidState fluid = world.getFluidState(cursor);
                    if (fluid.isEmpty()) continue;
                    any = true;
                    Fluid type = fluid.getType();
                    if (type != lastType) {
                        lastType = type;
                        maxDelay = Math.max(maxDelay, type.getTickDelay(world));
                    }
                    int open = 0;
                    for (int i = 0; i < SPREAD_DIRECTIONS.length; i++) {
                        neighbour.setWithOffset(cursor, SPREAD_DIRECTIONS[i]);
                        BlockState there = world.getBlockState(neighbour);
                        if (there.getFluidState().isEmpty() && SchematicBlockDefault.isWashedAwayBy(there, type)) {
                            open |= 1 << i;
                        }
                    }
                    census = mixCensus(census, cursor.asLong(), Fluid.FLUID_STATE_REGISTRY.getId(fluid), open);
                }
            }
        }
        if (!any) {
            replaceCensusKnown = false;
            return AreaFluid.NONE;
        }
        long now = world.getGameTime();
        if (!replaceCensusKnown || census != replaceCensus) {
            replaceCensusKnown = true;
            replaceCensus = census;
            replaceCensusSince = now;
            return AreaFluid.MOVING;
        }
        return now - replaceCensusSince > (long) SETTLE_TICK_DELAYS * maxDelay ? AreaFluid.SETTLED : AreaFluid.MOVING;
    }

    /**
     * Can any cell from {@code (x0, y0, z0)} to {@code (x1, y1, z1)} hold fluid? Reads only the block-state palettes
     * of the chunk sections the region touches, not its cells, so a dry area (the usual case: NO_REPLACE, the default
     * mode, sweeps for every fragile placement too) costs a handful of palette reads instead of a walk of every cell.
     * A palette can list a state no cell holds any more, so {@code true} means only "maybe": the walk decides.
     */
    private static boolean mayHoldFluid(Level world, int x0, int y0, int z0, int x1, int y1, int z1) {
        int sections = world.getSectionsCount();
        for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
            for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
                LevelChunkSection[] column = world.getChunk(cx, cz).getSections();
                for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                    int index = world.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= sections || index >= column.length) continue;
                    if (column[index].maybeHas(HOLDS_FLUID)) return true;
                }
            }
        }
        return false;
    }

    private static final Predicate<BlockState> HOLDS_FLUID = state -> !state.getFluidState().isEmpty();

    /** Folds one fluid cell into a {@link #scanReplaceFluid} census (order-dependent; the walk order is fixed). */
    private static long mixCensus(long census, long pos, int state, int open) {
        long h = (pos * 0x9E3779B97F4A7C15L) ^ ((long) state << 8 | open);
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        return census * 31 + (h ^ (h >>> 29));
    }

    /** The directions a fluid spreads in: down, and the four horizontals (never up). */
    private static final Direction[] SPREAD_DIRECTIONS = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /** How many of its own tick delays fluid may wait between two flow steps: vanilla lava waits up to four
     *  ({@code LavaFluid#getSpreadDelay}), water one. A census unchanged for longer than this is settled. */
    private static final int SETTLE_TICK_DELAYS = 4;

    /**
     * What the fluid in the build area means to the place-task gate, from the last sweep if it is recent enough.
     * Only a placement that reaches the gate asks, so a Builder with nothing to place never sweeps; and the result
     * is reused for {@link #FLUID_SCAN_INTERVAL} ticks (a sweep reads every cell of the box and, with tolerated or
     * settled fluid standing, never stops early). A change of fluid mode, or of snapshot, discards the result.
     *
     * <p>What a stale result can and cannot do:
     * <ul>
     * <li>A stale answer that holds a placement back only delays it, by at most one interval.</li>
     * <li>A stale answer that lets a FRAGILE placement through is never trusted:
     *     {@link #passesFreshFluidGate} re-sweeps before its items are taken. Without that, water entering the box
     *     after the sweep could reach the block a few flow steps after it is placed and wash it away (its item
     *     drops): {@code SchematicBlockDefault#build} refuses only when fluid is already beside or above it.</li>
     * <li>A stale answer CAN let a non-fragile placement through up to one interval after fluid entered the box
     *     (in CLEAR, before the mop has started). That is accepted: such a block is not washed away, and
     *     {@code BlueprintBuilder#canPlace} still refuses, live, a CLEAR placement into a cell holding fluid.</li>
     * </ul>
     */
    private AreaFluid areaFluid(EnumFluidHandlingMode mode) {
        if (cachedAreaFluid == null || cachedAreaFluidMode != mode || cachedAreaFluidAge >= FLUID_SCAN_INTERVAL) {
            cachedAreaFluid = scanAreaFluid();
            cachedAreaFluidMode = mode;
            cachedAreaFluidAge = 0;
        }
        return cachedAreaFluid;
    }

    /**
     * The build-area-fluid gate of the place-task add: may a placement at {@code pos} be queued while the area holds
     * the fluid {@link #areaFluid} reports? See the long comment at the call site for the modes' reasoning.
     */
    private boolean passesFluidGate(EnumFluidHandlingMode mode, BlockPos pos) {
        if (mode == EnumFluidHandlingMode.CLEAR) {
            AreaFluid fluid = areaFluid(mode);
            if (fluid == AreaFluid.UNCLEARED) return false;
            return !(fluid == AreaFluid.TOLERATED_ONLY && isFragileSchematicAt(pos) && isFragileWashedAwayAt(pos));
        }
        // REPLACE and NO_REPLACE: only fragile blocks wait, so only they need the sweep.
        if (!isFragileSchematicAt(pos)) return true;
        AreaFluid fluid = areaFluid(mode);
        if (fluid == AreaFluid.MOVING) return false;
        return !(fluid == AreaFluid.SETTLED && isFragileWashedAwayAt(pos));
    }
    /**
     * The last step before a placement that passed {@link #passesFluidGate} takes its items: a fragile placement is
     * let through only on a sweep taken this tick (re-sweeping if the reused one is older), so water that entered
     * the box since cannot wash it away (see {@link #areaFluid}). It runs after every other queue filter, so only a
     * placement about to be queued pays for the sweep, and at most once per tick: a fragile block that is refused,
     * or that cannot stand yet, keeps to one sweep per {@link #FLUID_SCAN_INTERVAL}.
     */
    private boolean passesFreshFluidGate(EnumFluidHandlingMode mode, BlockPos pos) {
        if (cachedAreaFluid != null && cachedAreaFluidMode == mode && cachedAreaFluidAge == 0) return true;
        if (!isFragileSchematicAt(pos)) return true;
        cachedAreaFluid = null;
        return passesFluidGate(mode, pos);
    }

    /** Full box sweeps {@link #scanAreaFluid()} has run so far (diagnostic; see {@code fluidSweeps}). */
    public int getFluidSweepCount() {
        return fluidSweeps;
    }

    // ── Stall diagnosis ──────────────────────────────────────────────────────────────────────────
    // A build is STALLED when it is unfinished, has nothing queued and did no work for STALL_TICKS ticks in a row.
    // (A build with tasks queued but no power is not stalled: it is waiting on power, which the machine already shows.)
    // While stalled, every STALL_DIAGNOSE_INTERVAL ticks the builder walks the cells it is still to break or place and
    // notes the first thing in the way of each, in the order the queue filters ask. The diagnosis runs on the server only
    // and is no part of the builder's saved state (serializeNBT); the client reads the same list back from the sync
    // (loadClientNBT) so it can outline the cells.

    /** Consecutive ticks a build must be unfinished with nothing queued and nothing done before it counts as stalled. */
    public static final int STALL_TICKS = 40;
    /** How often a stalled build is looked over again for the cells it is skipping. */
    public static final int STALL_DIAGNOSE_INTERVAL = 20;
    /** The most skipped cells a stalled build lists (and so syncs); the per-reason totals still count every one. */
    public static final int STALL_CELL_CAP = 64;

    private static final EnumStallReason[] STALL_REASONS = EnumStallReason.values();

    /** Consecutive ticks the build has been idle (saturates at {@link #STALL_TICKS}). */
    private int idleTicks;
    /** Ticks until a stalled build is diagnosed again; at or below 0 it is diagnosed on its next tick. */
    private int diagnoseIn;
    /** The skipped cells: the diagnosis on the server, the last sync on the client. At most {@link #STALL_CELL_CAP},
     *  the red reasons first (the order of {@link EnumStallReason}). */
    private final List<StalledCell> stalledCells = new ArrayList<>();
    private final List<StalledCell> stalledCellsView = Collections.unmodifiableList(stalledCells);
    /** How many cells are skipped for each reason, past the cap too. */
    private final int[] stalledCounts = new int[STALL_REASONS.length];

    /** The cells a stalled build is skipping and why: empty unless the build is stalled (and the machine reports
     *  stalls, see {@link ITileForSnapshotBuilder#reportsStalls()}), and then at most {@link #STALL_CELL_CAP} of them. */
    public List<StalledCell> getStalledCells() {
        return stalledCellsView;
    }

    /** How many cells are skipped for {@code reason}, counting the ones past the list's cap too. */
    public int getStalledCount(EnumStallReason reason) {
        return stalledCounts[reason.ordinal()];
    }

    private void clearStall() {
        if (!stalledCells.isEmpty()) {
            stalledCells.clear();
            Arrays.fill(stalledCounts, 0);
        }
    }

    /** Forgets the stalled cells (a client whose Builder lost its snapshot has nothing left to outline). */
    public void clearStalledCells() {
        clearStall();
    }

    /** One tick's worth of stall tracking, run at the end of {@link #tick()}. */
    private void trackStall(boolean idle) {
        if (!tile.reportsStalls()) {
            return;
        }
        if (!idle) {
            idleTicks = 0;
            clearStall();
            return;
        }
        if (idleTicks < STALL_TICKS) {
            idleTicks++;
            if (idleTicks < STALL_TICKS) {
                // Also drops a list that arrived with a save or a sync while the build was not (yet) stalled.
                clearStall();
                return;
            }
            diagnoseIn = 0;
        }
        if (diagnoseIn <= 0) {
            diagnoseIn = STALL_DIAGNOSE_INTERVAL;
            diagnoseStall();
        }
        diagnoseIn--;
    }

    /** Looks over every cell still to break or place and notes why the Builder is skipping it. Runs only while
     *  stalled, so both queues are empty and every cell it finds was filtered out of them. */
    private void diagnoseStall() {
        if (getBuildingInfo() == null || checkResults == null || placeOrder == null || breakOrder == null) {
            clearStall();
            return;
        }
        StallTally tally = new StallTally(STALL_CELL_CAP);
        if (tile.canExcavate()) {
            for (int i : breakOrder) {
                if (checkResults[i] != CHECK_RESULT_TO_BREAK) continue;
                BlockPos pos = indexToPos(i);
                // The break queue's own filters, in its order: fluid the mode never removes, then a block that cannot
                // be broken.
                tally.note(!shouldBreakQueueAcceptFluid(pos) ? EnumStallReason.FLUID
                    : isBreakBlocked(pos) ? EnumStallReason.UNBREAKABLE : null, pos);
            }
        }
        EnumFluidHandlingMode fluidMode = getFluidMode();
        for (int i : placeOrder) {
            if (checkResults[i] != CHECK_RESULT_TO_PLACE) continue;
            BlockPos pos = indexToPos(i);
            EnumStallReason reason;
            // The place queue's own filters, in its order. A cell that only waits on another cell (a dependency) is
            // ordinary, and left unreported (placementStallReason says null).
            if (!hasRequiredItems(i)) {
                reason = EnumStallReason.MISSING_ITEMS;
            } else if (isPlaceParked(pos)) {
                reason = EnumStallReason.REFUSED;
            } else if (!passesFluidGate(fluidMode, pos)) {
                reason = EnumStallReason.FLUID;
            } else {
                reason = placementStallReason(pos);
            }
            tally.note(reason, pos);
        }
        stalledCells.clear();
        stalledCells.addAll(tally.cells());
        for (EnumStallReason reason : STALL_REASONS) {
            stalledCounts[reason.ordinal()] = tally.count(reason);
        }
    }

    /**
     * Why the Builder will not queue the placement at {@code blockPos} once it has the items, is not parked and the
     * fluid gate lets it through, or {@code null} when nothing but a passing wait is in the way: a cell that only waits
     * for the cells it depends on is ordinary, and a build is not stalled by it. Default none; a Blueprint reports a
     * block that cannot stand there.
     */
    protected EnumStallReason placementStallReason(BlockPos blockPos) {
        return null;
    }

    /** The cached "has the Builder what this cell needs" of the place queue (see {@link #resourcesChanged}). */
    private boolean hasRequiredItems(int i) {
        if (requiredCache[i] != REQUIRED_UNKNOWN) {
            return requiredCache[i] == REQUIRED_TRUE;
        }
        boolean has = hasEnoughToPlaceItems(indexToPos(i));
        requiredCache[i] = has ? REQUIRED_TRUE : REQUIRED_FALSE;
        return has;
    }

    /**
     * Whether the cell may not be broken as things stand: an unbreakable block (bedrock), or one a protection mod
     * forbids breaking (asked once, then remembered for {@link RefusalMemory#BUILDER_PLACE}'s window like a refused
     * placement, so a protected area is not asked about on every tick). The break queue skips such a cell: queuing it
     * only to drain it as impossible on the next step left the queue never empty, and a placement waits for an empty
     * break queue, so one bedrock block in the box stopped every placement.
     */
    private boolean isBreakBlocked(BlockPos pos) {
        Level world = tile.getWorldBC();
        if (BlockUtil.isUnbreakableBlock(world, pos, tile.getOwner())) {
            return true;
        }
        if (!(world instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (breakRefusals.isRefused(serverLevel, pos, tile.getOwner())) {
            return true;
        }
        if (!BlockUtil.canMachineBreak(serverLevel, pos, tile.getOwner())) {
            breakRefusals.remember(serverLevel, pos, tile.getOwner());
            return true;
        }
        return false;
    }

    public void validate() {
        // No-op: Using polling approach instead of WorldEventListener
    }

    public void invalidate() {
        // No-op: Using polling approach instead of WorldEventListener
    }

    protected abstract boolean isAir(BlockPos blockPos);

    protected abstract boolean canPlace(BlockPos blockPos);

    protected abstract boolean isReadyToPlace(BlockPos blockPos);

    protected abstract boolean hasEnoughToPlaceItems(BlockPos blockPos);

    protected abstract List<ItemStack> getToPlaceItems(BlockPos blockPos);

    /** What {@link #doPlaceTask} came to. */
    protected enum PlaceResult {
        /** The block was placed. */
        PLACED,
        /** Nothing was placed (no room, a block that cannot stand, a fluid defer): the cell is queued again. */
        FAILED,
        /** A protection mod refused the placement: the cell is parked in {@link #placeRefusals}. */
        REFUSED
    }

    /**
     * Places the task's block. Anything but {@link PlaceResult#PLACED} refunds the task through
     * {@link #cancelPlaceTask}.
     */
    protected abstract PlaceResult doPlaceTask(PlaceTask placeTask);

    /** Whether a protection mod refused this builder's placement at {@code pos} and the cell is still parked. */
    protected boolean isPlaceParked(BlockPos pos) {
        return !placeRefusals.isEmpty() && placeRefusals.isRefused(tile.getWorldBC(), pos, tile.getOwner());
    }

    /**
     * Executed if break task failed
     */
    private void cancelBreakTask(BreakTask breakTask) {
        if (tile.getWorldBC() != null && !tile.getWorldBC().isClientSide()) {
            tile.getBattery().addPower(
                Math.min(breakTask.power, tile.getBattery().getCapacity() - tile.getBattery().getStored()),
                false
            );
        }
    }

    /**
     * Executed if {@link #doPlaceTask} failed
     */
    protected void cancelPlaceTask(PlaceTask placeTask) {
        if (tile.getWorldBC() != null && !tile.getWorldBC().isClientSide()) {
            tile.getBattery().addPower(
                Math.min(placeTask.power, tile.getBattery().getCapacity() - tile.getBattery().getStored()),
                false
            );
        }
    }

    /** Drops on top of the Builder what a cancelled placement's refund could not put back into its resources (the
     *  grid filled since the items were taken, by a robot or a pipe), where it used to be deleted. On top, not inside:
     *  an item popped inside the machine's block is pushed out of whichever side is nearest. */
    protected void dropRefundLeftover(ItemStack leftover) {
        if (!leftover.isEmpty()) {
            Block.popResourceFromFace(tile.getWorldBC(), tile.getBuilderPos(), Direction.UP, leftover);
        }
    }

    /**
     * @return true if block in world is correct (is not to break) according to snapshot, false otherwise
     */
    protected abstract boolean isBlockCorrect(BlockPos blockPos);

    /**
     * @return Pos where flying item should be rendered
     */
    public Vec3 getPlaceTaskItemPos(PlaceTask placeTask) {
        Vec3 height = Vec3.atLowerCornerOf(placeTask.pos.subtract(tile.getBuilderPos()));
        double progress = placeTask.power * 1D / placeTask.getTarget();
        return Vec3.atLowerCornerOf(tile.getBuilderPos())
            .add(height.scale(progress))
            .add(new Vec3(0, Math.sin(progress * Math.PI) * (Math.abs(height.y) + 1), 0))
            .add(new Vec3(0.5, 1, 0.5));
    }

    public void updateSnapshot() {
        checkResults = new byte[
            getBuildingInfo().box.size().getX() *
                getBuildingInfo().box.size().getY() *
                getBuildingInfo().box.size().getZ()
            ];
        Arrays.fill(checkResults, CHECK_RESULT_UNKNOWN);
        cachedAreaFluid = null;
        replaceCensusKnown = false;
        // A new snapshot is a new build: what the last one was stalled on says nothing about it.
        idleTicks = 0;
        clearStall();
        requiredCache = new byte[
            getBuildingInfo().box.size().getX() *
                getBuildingInfo().box.size().getY() *
                getBuildingInfo().box.size().getZ()
            ];
        Arrays.fill(requiredCache, REQUIRED_UNKNOWN);
        breakOrder = getBuildingInfo().box.getBlocksInArea().stream()
            .sorted(BlockUtil.uniqueBlockPosComparator(Comparator.comparingDouble(blockPos ->
                Math.pow(blockPos.getX() - getBuildingInfo().box.center().getX(), 2) +
                    Math.pow(blockPos.getZ() - getBuildingInfo().box.center().getZ(), 2) +
                    100_000 - Math.abs(blockPos.getY() - tile.getBuilderPos().getY()) * 100_000
            )))
            .mapToInt(this::posToIndex)
            .toArray();
        placeOrder = getBuildingInfo().box.getBlocksInArea().stream()
            .sorted(BlockUtil.uniqueBlockPosComparator(Comparator.comparingDouble(blockPos ->
                100_000 - (Math.pow(blockPos.getX() - tile.getBuilderPos().getX(), 2) +
                    Math.pow(blockPos.getZ() - tile.getBuilderPos().getZ(), 2)) +
                    Math.abs(blockPos.getY() - tile.getBuilderPos().getY()) * 100_000
            )))
            .mapToInt(this::posToIndex)
            .toArray();
        checkOrder = getBuildingInfo().box.getBlocksInArea().stream()
            .sorted(BlockUtil.uniqueBlockPosComparator(Comparator.comparingDouble(blockPos ->
                Math.pow(blockPos.getX() - getBuildingInfo().box.center().getX(), 2) +
                    Math.pow(blockPos.getY() - getBuildingInfo().box.center().getY(), 2) +
                    Math.pow(blockPos.getZ() - getBuildingInfo().box.center().getZ(), 2)
            )))
            .mapToInt(this::posToIndex)
            .toArray();
    }

    public void resourcesChanged() {
        // requiredCache is only allocated inside updateSnapshot() once a buildingInfo exists.
        // Calls that arrive before the first updateSnapshot() (e.g. container-content sync on a
        // client that doesn't have the full snapshot) would otherwise NPE here.
        if (requiredCache != null) {
            Arrays.fill(requiredCache, REQUIRED_UNKNOWN);
        }
    }

    public void cancel() {
        breakTasks.forEach(this::cancelBreakTask);
        placeTasks.forEach(this::cancelPlaceTask);
        breakTasks.clear();
        clientBreakTasks.clear();
        prevClientBreakTasks.clear();
        placeTasks.clear();
        clientPlaceTasks.clear();
        prevClientPlaceTasks.clear();
        checkResults = null;
        cachedAreaFluid = null;
        replaceCensusKnown = false;
        requiredCache = null;
        breakOrder = null;
        placeOrder = null;
        checkOrder = null;
        currentCheckIndex = 0;
        robotPos = null;
        prevRobotPos = null;
        leftToBreak = 0;
        leftToPlace = 0;
        // Refusals parked under the torn-down build belong to it: a new build (new snapshot, pattern, box or base
        // pos) asks its cells afresh instead of waiting out the old window.
        placeRefusals.clear();
        breakRefusals.clear();
        idleTicks = 0;
        diagnoseIn = 0;
        clearStall();
    }

    /** @return whether the last {@link #tick()} drew power into a task or finished one */
    public boolean madeProgress() {
        return madeProgress;
    }

    /**
     * @return true if building is finished, false otherwise
     */
    public boolean tick() {

        if (cachedAreaFluidAge < Integer.MAX_VALUE) {
            cachedAreaFluidAge++;
        }

        madeProgress = false;
        boolean checkResultsChanged = false;

        for (int i = 0; i < CHECKS_PER_TICK; i++) {
            if (check(indexToPos(checkOrder[currentCheckIndex]))) {
                checkResultsChanged = true;
            }
            currentCheckIndex = (currentCheckIndex + 1) % checkOrder.length;
        }

        // Remove tasks for blocks that are now correct
        for (Iterator<BreakTask> iterator = breakTasks.iterator(); iterator.hasNext(); ) {
            BreakTask breakTask = iterator.next();
            if (checkResults[posToIndex(breakTask.pos)] == CHECK_RESULT_CORRECT) {
                iterator.remove();
                cancelBreakTask(breakTask);
            }
        }
        for (Iterator<PlaceTask> iterator = placeTasks.iterator(); iterator.hasNext(); ) {
            PlaceTask placeTask = iterator.next();
            if (checkResults[posToIndex(placeTask.pos)] == CHECK_RESULT_CORRECT) {
                iterator.remove();
                cancelPlaceTask(placeTask);
            }
        }

        boolean isDone = true;

        // Can't be done if any blocks haven't been scanned yet
        boolean unclassified = false;
        for (byte result : checkResults) {
            if (result == CHECK_RESULT_UNKNOWN) {
                isDone = false;
                unclassified = true;
                break;
            }
        }

        // Can't be done if there are tasks still being processed
        if (isDone && (!breakTasks.isEmpty() || !placeTasks.isEmpty())) {
            isDone = false;
        }

        // Add break tasks
        if (tile.canExcavate()) {
            Set<Integer> breakTasksIndexes = breakTasks.stream()
                .map(breakTask -> posToIndex(breakTask.pos))
                .collect(Collectors.toSet());
            int[] blocks = Arrays.stream(breakOrder)
                .filter(i -> checkResults[i] == CHECK_RESULT_TO_BREAK && !breakTasksIndexes.contains(i))
                .toArray();
            leftToBreak = blocks.length;
            if (blocks.length != 0) {
                isDone = false;
            }
            // Source-first break ordering. The base breakOrder sorts by distance from the
            // build-area centre; that's fine for solid blocks but suboptimal for fluids: in a
            // mixed source+flowing pool, breaking flowing first is wasted work because the
            // flowing immediately gets re-fed by the upstream sources, while breaking sources
            // first makes the flowing drain naturally and infinite-water regen networks (a 2x2
            // pool of sources where any 3-source-neighbour empty cell re-sources the broken one)
            // get disrupted faster as several sources can break in the same tick once queued.
            // Re-sort the candidates so source-fluid positions go first, then flowing-fluid
            // positions, then everything else; within each tier the original distance order is
            // preserved (Stream sort is stable).
            // A block that can never be broken (bedrock, or a protection mod's refusal) is left out here: it stays
            // "left to break" and keeps the build unfinished, but must not be queued. Queuing it only to drain it as
            // impossible at execution (below) put it back every tick, so the break queue was never empty and the
            // place-task add (which waits for an empty break queue) never ran. The filter sits after the sort and
            // before the limit so it asks about no more cells than the queue has room for.
            Arrays.stream(blocks)
                .mapToObj(this::indexToPos)
                .filter(this::shouldBreakQueueAcceptFluid)
                .sorted(Comparator.comparingInt(this::breakPriorityTier))
                .filter(blockPos -> !isBreakBlocked(blockPos))
                .map(blockPos ->
                    new BreakTask(
                        blockPos,
                        0
                    )
                )
                .limit(MAX_QUEUE_SIZE - breakTasks.size())
                .forEach(breakTasks::add);
        } else {
            leftToBreak = 0;
        }

        // Add place tasks
        {
            Set<Integer> placeTasksIndexes = placeTasks.stream()
                .map(placeTask -> posToIndex(placeTask.pos))
                .collect(Collectors.toSet());
            int[] blocks = Arrays.stream(placeOrder)
                .filter(i -> checkResults[i] == CHECK_RESULT_TO_PLACE && !placeTasksIndexes.contains(i))
                .toArray();
            leftToPlace = blocks.length;
            // CLEAR mode is "clear all fluids first, then build" by user-stated intent. If we
            // queue normal place tasks while fluid still exists in the build area, even fragile
            // blocks (snow_layer, carpet, button, redstone wire, …) sometimes pass the per-tick
            // fragile-defer in SchematicBlockDefault.build because the defer can only see
            // immediate-neighbour fluid at the moment of placement — water flowing in from one
            // cell over within the same or next tick lands on the freshly placed block and
            // destroys it (the dropped item entity is lost; the position re-classifies as
            // TO_PLACE; the place-then-destroy loop wastes one item per cycle). Sequencing the
            // operations defers normal placements until the mop finishes. (A waterlogged block the
            // schematic wants dry needs no exception here: BlueprintBuilder#canPlace refuses every
            // fluid cell under CLEAR, so such a block is broken with the mop, not dried in place.)
            // Build-area-fluid gates (mode-dependent):
            // - CLEAR: defer everything while fluid is left to mop. Fluid the check sweep ACCEPTED (a
            //   wet block the schematic wants wet, or a block / air cell CLEAR tolerates because vanilla
            //   re-floods it from fluid the Builder cannot remove, see
            //   BlueprintBuilder#isResourcedByUnmoppableFluid) can never be mopped, so it must not hold
            //   the gate shut: once nothing else is left to mop, a fragile placement waits only while
            //   that fluid is beside or above its own cell (the same test SchematicBlockDefault.build
            //   applies, done here so no items are extracted for a placement build would refuse).
            //   Gating every fragile block on any tolerated fluid in the box would defer them for ever,
            //   since that fluid never goes away. A fragile cell whose washing fluid is ALL fluid the
            //   Builder will never remove (water, lava or any other) can never be built at all. When the
            //   cell itself holds nothing to break (air, flowing fluid, or a source that fluid's
            //   infinite-source rule keeps re-making from that permanent fluid) the check accepts it as
            //   done (BlueprintBuilder#isFragileUnbuildableAt) rather than leaving it here, deferred for
            //   ever with the build reported busy. A source that will not come back is still mopped.
            // - REPLACE and NO_REPLACE: defer fragile placements only. Solid placements need no gate:
            //   in REPLACE they displace the water on placement and aren't washed away, and NO_REPLACE
            //   never places one into fluid at all (BlueprintBuilder#canPlace/#isBlockCorrect skip a
            //   fluid cell, except a block that keeps that very fluid as its own). Without this, fragile
            //   schematic blocks (snow_layer in the user's case) at positions where water flows
            //   in over a few ticks get stuck in an extract-then-defer-then-refund loop: the
            //   per-build fragile defer in SchematicBlockDefault.build catches the placement,
            //   but only after items have already been extracted from inventory by
            //   getToPlaceItems at queue time. The user-visible symptom is the inventory slot
            //   counter bouncing up and down as items repeatedly cycle through extract+refund.
            //   Gating at queue-add stops the cycle entirely — items never leave inventory if
            //   the placement would defer. Only fluid that can reach the cell holds it back:
            //   MOVING fluid in or beside the box (fluid that has changed lately, or whose open
            //   surroundings have, see scanReplaceFluid), or SETTLED fluid beside or above the cell
            //   itself. A still pond, or flowing water that has stopped spreading, elsewhere in the
            //   box no longer holds every torch back for ever. A fragile cell that fluid which stays
            //   put washes can never be built in these modes at all; the check accepts it as done
            //   (BlueprintBuilder#isFragileUnbuildableAt) instead of leaving it deferred here for ever.
            //
            // The gate itself is passesFluidGate; it reads the area's fluid only when a placement reaches it, from
            // a sweep up to FLUID_SCAN_INTERVAL ticks old. passesFreshFluidGate, the last filter before items are
            // taken, re-sweeps before a fragile placement is let through on an old answer.
            EnumFluidHandlingMode fluidMode = getFluidMode();
            if (!tile.canExcavate() || breakTasks.isEmpty()) {
                if (blocks.length != 0) {
                    isDone = false;
                }
                Arrays.stream(blocks)
                    .filter(this::hasRequiredItems)
                    .mapToObj(this::indexToPos)
                    .filter(pos -> !isPlaceParked(pos))
                    .filter(pos -> passesFluidGate(fluidMode, pos))
                    .filter(this::isReadyToPlace)
                    .limit(MAX_QUEUE_SIZE - placeTasks.size())
                    .filter(this::canPlace)
                    .filter(pos -> passesFreshFluidGate(fluidMode, pos))
                    .map(blockPos ->
                        new PlaceTask(
                            blockPos,
                            getToPlaceItems(blockPos),
                            0
                        )
                    )
                    .filter(placeTask -> placeTask.items != null)
                    .forEach(placeTasks::add);
            }
        }



        // Execute tasks.
        // Ramp the per-tick power budget up to the rated MAX_POWER_PER_TICK as the battery fills,
        // reaching full rate at half charge — the same soft-start curve every powered machine uses.
        long max = tile.getBattery().rampedExtractLimit(MAX_POWER_PER_TICK);

        // Break tasks
        if (!breakTasks.isEmpty()) {
            for (Iterator<BreakTask> iterator = breakTasks.iterator(); iterator.hasNext(); ) {
                BreakTask breakTask = iterator.next();
                if (breakTask.isImpossible()) {
                    // Drain the task: refund any accumulated power and drop it from the queue
                    // so the builder isn't stuck retrying an unbreakable / protected block.
                    cancelBreakTask(breakTask);
                    iterator.remove();
                    continue;
                }
                long target = breakTask.getTarget();
                long drawn = tile.getBattery().extractPower(
                    0,
                    Math.min(
                        target - breakTask.power,
                        max / breakTasks.size()
                    )
                );
                breakTask.power += drawn;
                if (drawn > 0) {
                    madeProgress = true;
                }
                if (breakTask.power >= target) {
                    madeProgress = true;
                    clientBreakTasksCache.add(breakTask);
                    tile.getWorldBC().destroyBlockProgress(
                        breakTask.pos.hashCode(),
                        breakTask.pos,
                        -1
                    );
                    // Tool tier and drop-routing both come from the tile so Builder and Filler
                    // can apply their iron-tier nerf + custom drop destinations (invResources for
                    // the Builder, adjacent non-pipe inventory else world for the Filler) without
                    // SnapshotBuilder needing to know about either tile type.
                    Optional<BlockUtil.BreakResult> result = BlockUtil.breakBlockAndGetDropsWithXp(
                        (ServerLevel) tile.getWorldBC(),
                        breakTask.pos,
                        tile.getBreakingTool(),
                        tile.getOwner()
                    );
                    // Returns Optional.of(emptyList) for fluid sources broken under CLEAR mode
                    // — the Optional is present (just holds no drops), so isEmpty() here tests
                    // presence, not drop count. No refund, no cancel.
                    if (result.isEmpty()) {
                        cancelBreakTask(breakTask);
                    } else {
                        BlockUtil.BreakResult br = result.get();
                        tile.onBlockBroken(breakTask.pos, br.drops(), br.xp(), br.capturedFluid());
                    }
                    if (check(breakTask.pos)) {
                        checkResultsChanged = true;
                    }
                    iterator.remove();
                } else {
                    clientBreakTasksCache.add(breakTask);
                    tile.getWorldBC().destroyBlockProgress(
                        breakTask.pos.hashCode(),
                        breakTask.pos,
                        (int) ((breakTask.power * 9) / target)
                    );
                }
            }
        }

        // Place tasks
        if (!placeTasks.isEmpty()) {
            for (Iterator<PlaceTask> iterator = placeTasks.iterator(); iterator.hasNext(); ) {
                PlaceTask placeTask = iterator.next();
                long target = placeTask.getTarget();
                long drawn = tile.getBattery().extractPower(
                    0,
                    Math.min(
                        target - placeTask.power,
                        max / placeTasks.size()
                    )
                );
                placeTask.power += drawn;
                if (drawn > 0) {
                    madeProgress = true;
                }
                if (placeTask.power >= target) {
                    madeProgress = true;
                    PlaceResult placed = doPlaceTask(placeTask);
                    if (placed != PlaceResult.PLACED) {
                        cancelPlaceTask(placeTask);
                    }
                    if (placed == PlaceResult.REFUSED) {
                        placeRefusals.remember(tile.getWorldBC(), placeTask.pos, tile.getOwner());
                    }
                    if (check(placeTask.pos)) {
                        checkResultsChanged = true;
                    }
                    if (placed == PlaceResult.PLACED && recheckAfterPlacing(placeTask.pos)) {
                        checkResultsChanged = true;
                    }
                    iterator.remove();
                }
            }
        }

        if (checkResultsChanged) {
            afterChecks();
        }

        // Unfinished, nothing queued and nothing done this tick: the build is not going anywhere on its own. Not while
        // the check sweep is still classifying the box for the first time (a big freshly loaded build spends many ticks
        // with nothing found to queue yet), or that would pass for a stall.
        trackStall(!unclassified && !isDone && !madeProgress && breakTasks.isEmpty() && placeTasks.isEmpty());

        return isDone;
    }


    /**
     * Client-side tick: extrapolates power between 5-tick server syncs for smooth animation.
     * The prev/current snapshots are taken each tick for sub-tick render interpolation.
     * Power never goes backward thanks to the max() merge in {@link #receiveServerTaskData}.
     *
     * IMPORTANT: We must NOT call extractPower() on the client battery — it's a read-only mirror
     * of the server's state. Instead we estimate the power increment from the stored value.
     */
    public void clientTick() {
        long stored = tile.getBattery().getStored();
        // Mirror the server-side ramp curve exactly (see serverTick).
        long max = tile.getBattery().rampedExtractLimit(MAX_POWER_PER_TICK);

        prevClientBreakTasks.clear();
        for (BreakTask task : clientBreakTasks) {
            prevClientBreakTasks.add(new BreakTask(task.pos, task.power));
            long target = task.getTarget();
            if (stored > 0 && task.power < target) {
                long increment = Math.min(target - task.power, max / Math.max(1, clientBreakTasks.size()));
                task.power += Math.min(increment, stored);
            }
        }

        prevClientPlaceTasks.clear();
        for (PlaceTask task : clientPlaceTasks) {
            prevClientPlaceTasks.add(new PlaceTask(task.pos, task.items, task.power));
            long target = task.getTarget();
            if (stored > 0 && clientBreakTasks.isEmpty() && task.power < target) {
                long increment = Math.min(target - task.power, max / Math.max(1, clientPlaceTasks.size()));
                task.power += Math.min(increment, stored);
            }
        }

        prevRobotPos = robotPos;
        if (!clientBreakTasks.isEmpty()) {
            Vec3 newRobotPos = clientBreakTasks.stream()
                .map(breakTask -> breakTask.pos)
                .map(Vec3::atLowerCornerOf)
                .map(VecUtil.VEC_HALF::add)
                .reduce(Vec3.ZERO, Vec3::add)
                .scale(1D / clientBreakTasks.size());
            newRobotPos = new Vec3(
                newRobotPos.x,
                clientBreakTasks.stream()
                    .map(breakTask -> breakTask.pos)
                    .mapToDouble(BlockPos::getY)
                    .max()
                    .orElse(newRobotPos.y),
                newRobotPos.z
            );
            newRobotPos = newRobotPos.add(new Vec3(0, 3, 0));
            Vec3 oldRobotPos = robotPos;
            robotPos = newRobotPos;
            if (oldRobotPos != null) {
                robotPos = oldRobotPos.add(newRobotPos.subtract(oldRobotPos).scale(1 / 4D));
            }
        } else if (!clientPlaceTasks.isEmpty()) {
            Vec3 newRobotPos = clientPlaceTasks.stream()
                .map(placeTask -> placeTask.pos)
                .map(Vec3::atLowerCornerOf)
                .map(VecUtil.VEC_HALF::add)
                .reduce(Vec3.ZERO, Vec3::add)
                .scale(1D / clientPlaceTasks.size());
            newRobotPos = newRobotPos.add(new Vec3(0, 3, 0));
            Vec3 oldRobotPos = robotPos;
            robotPos = newRobotPos;
            if (oldRobotPos != null) {
                robotPos = oldRobotPos.add(newRobotPos.subtract(oldRobotPos).scale(1 / 4D));
            }
        } else {
            robotPos = null;
        }

        visualPrevRobotPos = visualRobotPos;

        if (robotPos != null) {
            if (visualRobotPos == null) {
                visualRobotPos = robotPos;
                visualPrevRobotPos = robotPos;
            } else {
                visualRobotPos = visualRobotPos.add(robotPos.subtract(visualRobotPos).scale(0.25D));
            }
        } else {
            visualRobotPos = null;
            visualPrevRobotPos = null;
        }
    }

    /**
     * Called on the client when new task data arrives from the server (via loadAdditional / block entity sync).
     * For tasks that exist on both client and server, uses max(client, server) power so the animation
     * never jumps backwards. New server tasks are added; tasks the server removed are dropped.
     * This prevents the loop on power-cut (server stalls → max() keeps client's position) while
     * still allowing the client to receive new/completed task transitions.
     */
    public void receiveServerTaskData(Queue<BreakTask> serverBreakTasks, Queue<PlaceTask> serverPlaceTasks) {
        receiveServerTaskData(serverBreakTasks, serverPlaceTasks, clientBreakTasks, clientPlaceTasks);
    }

    /**
     * Overload that accepts pre-saved client tasks for merging. This is needed when loadAdditional()
     * calls updateBuildingInfo() (which clears all task lists via cancel()) before the merge runs.
     * The caller saves clientBreakTasks/clientPlaceTasks beforehand and passes them here.
     */
    public void receiveServerTaskData(
            Queue<BreakTask> serverBreakTasks, Queue<PlaceTask> serverPlaceTasks,
            Iterable<BreakTask> savedClientBreak, Iterable<PlaceTask> savedClientPlace) {
        // Merge break tasks: keep max(client, server) power for matching positions
        Queue<BreakTask> mergedBreak = new ArrayDeque<>();
        for (BreakTask serverTask : serverBreakTasks) {
            long mergedPower = serverTask.power;
            for (BreakTask clientTask : savedClientBreak) {
                if (clientTask.pos.equals(serverTask.pos)) {
                    mergedPower = Math.max(mergedPower, clientTask.power);
                    break;
                }
            }
            mergedBreak.add(new BreakTask(serverTask.pos, mergedPower));
        }
        clientBreakTasks.clear();
        clientBreakTasks.addAll(mergedBreak);

        // Merge place tasks: keep max(client, server) power for matching positions
        Queue<PlaceTask> mergedPlace = new ArrayDeque<>();
        for (PlaceTask serverTask : serverPlaceTasks) {
            long mergedPower = serverTask.power;
            for (PlaceTask clientTask : savedClientPlace) {
                if (clientTask.pos.equals(serverTask.pos)) {
                    mergedPower = Math.max(mergedPower, clientTask.power);
                    break;
                }
            }
            mergedPlace.add(new PlaceTask(serverTask.pos, serverTask.items, mergedPower));
        }
        clientPlaceTasks.clear();
        clientPlaceTasks.addAll(mergedPlace);
    }

    /**
     * Break-queue admission filter, mode-aware.
     * <ul>
     *   <li>NO_REPLACE — skip any position that is only fluid (flowing or source; {@link #isFluidOnly}). A block
     *       holding fluid (waterlogged, seagrass, kelp) is broken, and its fluid stays.</li>
     *   <li>REPLACE — also skip fluid-only positions; schematic-solid over fluid-only is handled through
     *       the place path ({@link SchematicBlockDefault#build(net.minecraft.world.level.Level,
     *       BlockPos, EnumFluidHandlingMode)}), which can waterlog instead of destroying. A block holding
     *       fluid (a waterlogged slab, stair or chest) is broken first, like any block in the way.</li>
     *   <li>CLEAR — allow ALL fluid (both sources and flowing) through. The earlier "sources
     *       only" filter assumed flowing fluid would drain naturally once every contributing
     *       source was inside the build area, but that's not true at a leaky boundary (a build
     *       area at the edge of a pool, river, or any fluid body whose source extends outside).
     *       Without breaking flowing fluid in that case, schematic-air positions classified as
     *       TO_BREAK would sit there forever — no break beam fires because the flowing-fluid
     *       filter rejects them, and the user sees the Builder appear stuck after one or two
     *       break beams. Now CLEAR will perpetually mop flowing fluid that keeps refilling from
     *       outside the build area; that does drain the battery indefinitely on a leaky boundary,
     *       but the activity is visible (beams keep firing) so the user can stop the Builder /
     *       extend the build area / deal with the external source rather than wondering why
     *       nothing's happening.</li>
     * </ul>
     */
    private boolean shouldBreakQueueAcceptFluid(BlockPos blockPos) {
        BlockState state = tile.getWorldBC().getBlockState(blockPos);
        if (state.getFluidState().isEmpty()) return true;
        if (getFluidMode() == EnumFluidHandlingMode.CLEAR) return true;
        // REPLACE / NO_REPLACE never remove fluid, but a real block holding it (a waterlogged fence, seagrass, kelp)
        // is still a block to break: BlockUtil's break leaves the fluid behind, which these modes then leave alone.
        // Refusing it too left such a cell "to break" for ever, and the build never finished.
        return !isFluidOnly(state);
    }

    /**
     * Is {@code state} fluid and nothing else: a {@link LiquidBlock} (water, lava, BC oil, a modded fluid), or a
     * bubble column (water that soul sand or magma below keeps turning back into one, so breaking it only swaps it
     * for the water it stands in)? A waterlogged block, seagrass or kelp is not: it is a block that holds fluid.
     */
    protected static boolean isFluidOnly(BlockState state) {
        return state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BubbleColumnBlock;
    }

    /**
     * Called from the tile when the player cycles the fluid-handling mode. Resets any
     * check-result slots whose world position currently holds a fluid so the next tick
     * re-classifies them under the new mode — otherwise an already-CORRECT water tile would
     * never get rescheduled as TO_BREAK under CLEAR, and an already-TO_BREAK fluid tile under
     * NO_REPLACE would stay stuck as TO_BREAK forever.
     */
    public void invalidateChecksForFluidPositions() {
        cachedAreaFluid = null;
        replaceCensusKnown = false;
        if (checkResults == null || getBuildingInfo() == null) return;
        for (int i = 0; i < checkResults.length; i++) {
            BlockPos pos = indexToPos(i);
            if (!tile.getWorldBC().getFluidState(pos).isEmpty()) {
                checkResults[i] = CHECK_RESULT_UNKNOWN;
            }
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected int posToIndex(BlockPos blockPos) {
        return getBuildingInfo().getSnapshot().posToIndex(getBuildingInfo().fromWorld(blockPos));
    }

    @SuppressWarnings("WeakerAccess")
    protected BlockPos indexToPos(int i) {
        return getBuildingInfo().toWorld(getBuildingInfo().getSnapshot().indexToPos(i));
    }

    /**
     * @return true if changed, false otherwise
     */
    protected boolean check(BlockPos blockPos) {
        int i = posToIndex(blockPos);
        byte prev = checkResults[i];
        if (isAir(blockPos)) {
            if (tile.getWorldBC().isEmptyBlock(blockPos) || isToleratedFluidAt(blockPos)) {
                checkResults[i] = CHECK_RESULT_CORRECT;
            } else {
                checkResults[i] = CHECK_RESULT_TO_BREAK;
            }
        } else {
            if (isBlockCorrect(blockPos)) {
                checkResults[i] = CHECK_RESULT_CORRECT;
            } else if (canPlace(blockPos)) {
                checkResults[i] = CHECK_RESULT_TO_PLACE;
            } else {
                checkResults[i] = CHECK_RESULT_TO_BREAK;
            }
        }
        return prev != checkResults[i];
    }

    protected void afterChecks() {
    }

    /** After a block is placed at {@code blockPos}, classifies again at once the cells whose answer may hang on it, and
     *  says whether any result changed. None here; the Builder asks the placed block's neighbours. */
    protected boolean recheckAfterPlacing(BlockPos blockPos) {
        return false;
    }

    public void writeToByteBuf(PacketBufferBC buffer) {
        buffer.writeInt(breakTasks.size());
        breakTasks.forEach(breakTask -> breakTask.writePayload(buffer));
        buffer.writeInt(placeTasks.size());
        placeTasks.forEach(placeTask -> placeTask.writePayload(buffer));
        buffer.writeInt(leftToBreak);
        buffer.writeInt(leftToPlace);
    }

    public void readFromByteBuf(PacketBufferBC buffer) {
        breakTasks.clear();
        IntStream.range(0, buffer.readInt()).mapToObj(i -> new BreakTask(buffer)).forEach(breakTasks::add);
        placeTasks.clear();
        IntStream.range(0, buffer.readInt()).mapToObj(i -> new PlaceTask(buffer)).forEach(placeTasks::add);
        leftToBreak = buffer.readInt();
        leftToPlace = buffer.readInt();
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        if (checkResults != null) {
            nbt.putByteArray("checkResults", checkResults);
        } else {
            nbt.putByteArray("checkResults", new byte[0]);
        }
        nbt.put("breakTasks", NBTUtilBC.writeCompoundList(breakTasks.stream().map(BreakTask::writeToNBT)));
        nbt.put("placeTasks", NBTUtilBC.writeCompoundList(placeTasks.stream().map(PlaceTask::writeToNBT)));
        nbt.putInt("currentCheckIndex", currentCheckIndex);
        return nbt;
    }

    public CompoundTag serializeClientNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("breakTasks", NBTUtilBC.writeCompoundList(clientBreakTasksCache.stream().map(BreakTask::writeToNBT)));
        nbt.put("placeTasks", NBTUtilBC.writeCompoundList(placeTasks.stream().map(PlaceTask::writeToNBT)));
        // Absent unless stalled, so the steady-state sync carries nothing extra. (This tag is also what the tile saves
        // as its client data, so a stalled Builder's list is saved with it; the server drops what it loads again on its
        // first tick, see trackStall.)
        if (!stalledCells.isEmpty()) {
            int[] positions = new int[stalledCells.size() * 3];
            byte[] reasons = new byte[stalledCells.size()];
            for (int i = 0; i < stalledCells.size(); i++) {
                StalledCell cell = stalledCells.get(i);
                positions[i * 3] = cell.pos().getX();
                positions[i * 3 + 1] = cell.pos().getY();
                positions[i * 3 + 2] = cell.pos().getZ();
                reasons[i] = (byte) cell.reason().ordinal();
            }
            nbt.putIntArray("stalledPos", positions);
            nbt.putByteArray("stalledReason", reasons);
            nbt.putIntArray("stalledCounts", stalledCounts.clone());
        }
        return nbt;
    }

    public void onNetworkSync() {
        clientBreakTasksCache.clear();
    }

    public void deserializeNBT(CompoundTag nbt) {
        if (getBuildingInfo() == null) {
            return;
        }
        updateSnapshot();
        byte[] loadedCheckResults = NBTUtilBC.getByteArray(nbt, "checkResults", new byte[0]);
        if (loadedCheckResults.length == checkResults.length) {
            System.arraycopy(loadedCheckResults, 0, checkResults, 0, checkResults.length);
        }
        breakTasks.clear();
        NBTUtilBC.readCompoundList(nbt.get("breakTasks")).map(BreakTask::new).forEach(breakTasks::add);
        placeTasks.clear();
        NBTUtilBC.readCompoundList(nbt.get("placeTasks")).map(PlaceTask::new).forEach(placeTasks::add);
        // checkOrder was just rebuilt from the live snapshot, so a saved index from a different (or edited) one can lie
        // past its end, and tick() would index out of bounds on every call.
        int loadedCheckIndex = NBTUtilBC.getInt(nbt, "currentCheckIndex", 0);
        currentCheckIndex = checkOrder != null && loadedCheckIndex >= 0 && loadedCheckIndex < checkOrder.length
            ? loadedCheckIndex
            : 0;
    }

    /**
     * Client-side: given a {@code builderClientData} tag produced by {@link #serializeClientNBT()},
     * decode the server's break/place task queues and merge them with the previously-cached
     * client tasks using max(client, server) power per position.
     * <p>
     * Encapsulated here (rather than forcing callers to build the inner-class queues themselves)
     * because {@code BreakTask}/{@code PlaceTask} are non-static inner classes tied to the enclosing
     * {@code SnapshotBuilder} instance, and a wildcard-typed reference can't instantiate them
     * without ugly raw-type casts. Callers pass the previously-held client queues so the power
     * merge can survive the {@code updateSnapshot()} reset that happens during tile load.
     */
    public void loadClientNBT(CompoundTag tag,
            Iterable<BreakTask> savedClientBreak, Iterable<PlaceTask> savedClientPlace) {
        Queue<BreakTask> serverBreak = new ArrayDeque<>();
        Queue<PlaceTask> serverPlace = new ArrayDeque<>();
        NBTUtilBC.readCompoundList(tag.get("breakTasks")).map(BreakTask::new).forEach(serverBreak::add);
        NBTUtilBC.readCompoundList(tag.get("placeTasks")).map(PlaceTask::new).forEach(serverPlace::add);
        receiveServerTaskData(serverBreak, serverPlace, savedClientBreak, savedClientPlace);
        loadStalledCells(tag);
    }

    /** Reads the stalled cells {@link #serializeClientNBT()} wrote (none when the tag carries none, which empties the
     *  list: a sync from a build that is no longer stalled must clear the client's outlines). */
    private void loadStalledCells(CompoundTag tag) {
        stalledCells.clear();
        Arrays.fill(stalledCounts, 0);
        int[] positions = NBTUtilBC.getIntArray(tag, "stalledPos", new int[0]);
        byte[] reasons = NBTUtilBC.getByteArray(tag, "stalledReason", new byte[0]);
        int count = Math.min(Math.min(positions.length / 3, reasons.length), STALL_CELL_CAP);
        for (int i = 0; i < count; i++) {
            EnumStallReason reason = EnumStallReason.fromOrdinal(reasons[i]);
            if (reason != null) {
                stalledCells.add(new StalledCell(
                    new BlockPos(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]), reason));
            }
        }
        int[] counts = NBTUtilBC.getIntArray(tag, "stalledCounts", new int[0]);
        for (int i = 0; i < Math.min(counts.length, stalledCounts.length); i++) {
            stalledCounts[i] = Math.max(0, counts[i]);
        }
    }

    public class BreakTask {
        public final BlockPos pos;
        public long power;

        @SuppressWarnings("WeakerAccess")
        public BreakTask(BlockPos pos, long power) {
            this.pos = pos;
            this.power = power;
        }

        @SuppressWarnings("WeakerAccess")
        public BreakTask(PacketBufferBC buffer) {
            pos = buffer.readBlockPos();
            power = buffer.readLong();
        }

        @SuppressWarnings("WeakerAccess")
        public BreakTask(CompoundTag nbt) {
            pos = new BlockPos(
                (int) NBTUtilBC.getLong(nbt, "pos_x", 0),
                (int) NBTUtilBC.getLong(nbt, "pos_y", 0),
                (int) NBTUtilBC.getLong(nbt, "pos_z", 0)
            );
            power = NBTUtilBC.getLong(nbt, "power", 0L);
        }

        @SuppressWarnings("WeakerAccess")
        public boolean isImpossible() {
            if (BlockUtil.isUnbreakableBlock(tile.getWorldBC(), pos, tile.getOwner())) {
                return true;
            }
            // Honor third-party player-protection mods (gated by BCCoreConfig.minePlayerProtected).
            // Cancelled BreakEvent → task is impossible → drained from the queue with a refund.
            if (tile.getWorldBC() instanceof ServerLevel serverLevel
                    && !BlockUtil.canMachineBreak(serverLevel, pos, tile.getOwner())) {
                return true;
            }
            return false;
        }

        public long getTarget() {
            return BlockUtil.computeBlockBreakPower(tile.getWorldBC(), pos);
        }

        public void writePayload(PacketBufferBC buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeLong(power);
        }

        public CompoundTag writeToNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.putLong("pos_x", pos.getX());
            nbt.putLong("pos_y", pos.getY());
            nbt.putLong("pos_z", pos.getZ());
            nbt.putLong("power", power);
            return nbt;
        }
    }

    /**
     * One place-task item as NBT, for both the save and the client sync: the full stack, data components
     * included, under {@code stack} at count 1 (ItemStack.CODEC caps the count it accepts, and a blueprint's
     * merged required-item entry can exceed a stack) with the real count beside it. The items are what a
     * Template/Filler places and what a cancelled Blueprint task refunds, so a component lost here is a gate
     * variant, a name, or a Blueprint fluid marker (a bucket tagged with the fluid it stands for) lost too.
     */
    private static CompoundTag placeItemToNbt(ItemStack stack, DynamicOps<Tag> ops) {
        CompoundTag tag = new CompoundTag();
        if (!stack.isEmpty()) {
            ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1))
                .resultOrPartial()
                .ifPresent(payload -> tag.put("stack", payload));
            tag.putInt("count", stack.getCount());
        }
        return tag;
    }

    /**
     * Reverse of {@link #placeItemToNbt}. Also reads the pre-codec {@code {id, count}} entries that worlds
     * saved before it still hold (their components were already lost; item and count are kept).
     */
    private static ItemStack placeItemFromNbt(CompoundTag tag, DynamicOps<Tag> ops) {
        int count = NBTUtilBC.getInt(tag, "count", 1);
        ItemStack stack = ItemStack.EMPTY;
        Tag payload = tag.get("stack");
        if (payload != null) {
            stack = ItemStack.CODEC.parse(ops, payload).resultOrPartial().orElse(ItemStack.EMPTY);
        } else if (tag.contains("id")) {
            Identifier id = Identifier.tryParse(NBTUtilBC.getString(tag, "id", ""));
            Item item = id == null ? null : RegistryUtilBC.getValue(BuiltInRegistries.ITEM, id);
            if (item != null && item != Items.AIR) {
                stack = new ItemStack(item);
            }
        }
        return stack.isEmpty() || count <= 0 ? ItemStack.EMPTY : stack.copyWithCount(count);
    }

    public class PlaceTask {
        public final BlockPos pos;
        public final List<ItemStack> items;
        public long power;

        @SuppressWarnings("WeakerAccess")
        public PlaceTask(BlockPos pos, List<ItemStack> items, long power) {
            this.pos = pos;
            this.items = Optional.ofNullable(items).map(ImmutableList::copyOf).orElse(null);
            this.power = power;
        }

        @SuppressWarnings("WeakerAccess")
        public PlaceTask(PacketBufferBC buffer) {
            pos = buffer.readBlockPos();
            DynamicOps<Tag> ops = NBTUtilBC.registryAwareOps();
            items = IntStream.range(0, buffer.readInt())
                .mapToObj(j -> {
                    CompoundTag itemTag = buffer.readNbt();
                    return itemTag == null ? ItemStack.EMPTY : placeItemFromNbt(itemTag, ops);
                })
                .collect(Collectors.toList());
            power = buffer.readLong();
        }

        @SuppressWarnings("WeakerAccess")
        public PlaceTask(CompoundTag nbt) {
            this(nbt, NBTUtilBC.registryAwareOps());
        }

        @SuppressWarnings("WeakerAccess")
        public PlaceTask(CompoundTag nbt, DynamicOps<Tag> ops) {
            pos = new BlockPos(
                (int) NBTUtilBC.getLong(nbt, "pos_x", 0),
                (int) NBTUtilBC.getLong(nbt, "pos_y", 0),
                (int) NBTUtilBC.getLong(nbt, "pos_z", 0)
            );
            items = ImmutableList.copyOf(
                NBTUtilBC.readCompoundList(nbt.get("items"))
                    .map(tag -> placeItemFromNbt(tag, ops))
                    .filter(stack -> !stack.isEmpty())
                    .collect(Collectors.toList())
            );
            power = NBTUtilBC.getLong(nbt, "power", 0L);
        }

        public long getTarget() {
            return (long) (Math.sqrt(pos.distSqr(tile.getBuilderPos())) * 10 * MjAPI.MJ);
        }

        public void writePayload(PacketBufferBC buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeInt(items.size());
            DynamicOps<Tag> ops = NBTUtilBC.registryAwareOps();
            items.forEach(item -> buffer.writeNbt(placeItemToNbt(item, ops)));
            buffer.writeLong(power);
        }

        public CompoundTag writeToNBT() {
            return writeToNBT(NBTUtilBC.registryAwareOps());
        }

        public CompoundTag writeToNBT(DynamicOps<Tag> ops) {
            CompoundTag nbt = new CompoundTag();
            nbt.putLong("pos_x", pos.getX());
            nbt.putLong("pos_y", pos.getY());
            nbt.putLong("pos_z", pos.getZ());
            nbt.put("items", NBTUtilBC.writeCompoundList(
                items.stream().filter(stack -> !stack.isEmpty()).map(stack -> placeItemToNbt(stack, ops))));
            nbt.putLong("power", power);
            return nbt;
        }
    }
}
