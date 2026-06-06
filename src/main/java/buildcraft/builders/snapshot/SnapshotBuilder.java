/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.NBTUtilBC;
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
    private static final long MAX_POWER_PER_TICK = 256 * MjAPI.MJ;

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

    @SuppressWarnings("WeakerAccess")
    protected SnapshotBuilder(T tile) {
        this.tile = tile;
    }

    protected abstract Snapshot.BuildingInfo getBuildingInfo();

    /**
     * Current fluid-handling mode from the tile. Concrete subclasses may override if they
     * want different semantics (e.g. {@link TemplateBuilder} always returns NO_REPLACE since
     * template placement doesn't touch fluids).
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
     * Per-position carve-out for the CLEAR-mode "wait for mop to finish before placing" gate.
     * Default: never allowed (regular placements wait). Subclasses override to return true for
     * positions whose placement is itself part of mopping — specifically waterlog-clear-only
     * tasks that toggle WATERLOGGED off on already-existing world blocks. Without this exception
     * a build area containing waterlogged blocks deadlocks: the waterlogged blocks emit water to
     * neighbours, the corner-flow break tasks fire forever as water keeps refilling, and the
     * place tasks that would dry the source-emitting blocks sit deferred behind the gate.
     */
    protected boolean isAllowedDuringFluidMop(BlockPos blockPos) {
        return false;
    }

    /**
     * Returns true if the schematic block at {@code blockPos} is "fragile" — i.e. its
     * BlockBehaviour reports {@code canBeReplaced(state, fluid)} = true (snow_layer, carpet,
     * button, redstone wire, sapling, torch, sign, lever, …). Used by the REPLACE-mode place-
     * task-add filter to defer fragile placements when the area has any fluid; the per-build
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
     * O(n) scan of the build area for any fluid (source or flowing). Used by the place-task-add
     * gate to defer placements in CLEAR mode until every fluid is mopped. Iteration short-
     * circuits on the first fluid found, so the cost is a few microseconds when fluid is present
     * (typical case while mopping) and the full O(n) only when fluid is fully cleared (one
     * verification per tick at that point — negligible).
     */
    protected boolean buildAreaHasAnyFluid() {
        Snapshot.BuildingInfo info = getBuildingInfo();
        if (info == null) return false;
        for (BlockPos pos : info.box.getBlocksInArea()) {
            if (!tile.getWorldBC().getFluidState(pos).isEmpty()) {
                return true;
            }
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

    /**
     * @return true if task done successfully, false otherwise
     */
    protected abstract boolean doPlaceTask(PlaceTask placeTask);

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
        requiredCache = null;
        breakOrder = null;
        placeOrder = null;
        checkOrder = null;
        currentCheckIndex = 0;
        robotPos = null;
        prevRobotPos = null;
        leftToBreak = 0;
        leftToPlace = 0;
    }

    /**
     * @return true if building is finished, false otherwise
     */
    public boolean tick() {

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
        for (byte result : checkResults) {
            if (result == CHECK_RESULT_UNKNOWN) {
                isDone = false;
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
            Arrays.stream(blocks)
                .mapToObj(this::indexToPos)
                .filter(this::shouldBreakQueueAcceptFluid)
                .sorted(Comparator.comparingInt(this::breakPriorityTier))
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
            // operations defers normal placements until the mop finishes. Trade-off at a leaky
            // boundary (source outside the build area): the Builder mops forever and never
            // places — visible activity, user can extend the area / switch to REPLACE.
            //
            // EXCEPTION: waterlog-clear-only tasks bypass the gate. These positions hold a
            // waterlogged block whose schematic counterpart is dry — drying them in-place
            // *contributes* to mopping (a waterlogged block emits water to neighbours, so the
            // block IS a fluid source as far as the build area's fluid presence is concerned).
            // Waiting for fluid to clear before drying them is a deadlock: the area never goes
            // dry while the waterlogged blocks keep emitting, and the waterlogged blocks never
            // get dried while the area has fluid. Carve them out of the gate so they queue
            // alongside fluid-break tasks.
            // Build-area-fluid gates (mode-dependent):
            // - CLEAR: defer everything except waterlog-clear-only (those contribute to mopping).
            // - REPLACE: defer fragile placements only — solid placements proceed because they
            //   displace water on placement and aren't replaceable. Without this, fragile
            //   schematic blocks (snow_layer in the user's case) at positions where water flows
            //   in over a few ticks get stuck in an extract-then-defer-then-refund loop: the
            //   per-build fragile defer in SchematicBlockDefault.build catches the placement,
            //   but only after items have already been extracted from inventory by
            //   getToPlaceItems at queue time. The user-visible symptom is the inventory slot
            //   counter bouncing up and down as items repeatedly cycle through extract+refund.
            //   Gating at queue-add stops the cycle entirely — items never leave inventory if
            //   the placement would defer.
            boolean areaHasFluid = (getFluidMode() == EnumFluidHandlingMode.CLEAR
                    || getFluidMode() == EnumFluidHandlingMode.REPLACE)
                && buildAreaHasAnyFluid();
            boolean clearStillMopping = areaHasFluid
                && getFluidMode() == EnumFluidHandlingMode.CLEAR;
            boolean replaceFragileGated = areaHasFluid
                && getFluidMode() == EnumFluidHandlingMode.REPLACE;
            if (!tile.canExcavate() || breakTasks.isEmpty()) {
                if (blocks.length != 0) {
                    isDone = false;
                }
                Arrays.stream(blocks)
                    .filter(i -> {
                        if (requiredCache[i] != REQUIRED_UNKNOWN) {
                            return requiredCache[i] == REQUIRED_TRUE;
                        }
                        boolean has = hasEnoughToPlaceItems(indexToPos(i));
                        requiredCache[i] = has ? REQUIRED_TRUE : REQUIRED_FALSE;
                        return has;
                    })
                    .mapToObj(this::indexToPos)
                    .filter(pos -> {
                        if (clearStillMopping) return isAllowedDuringFluidMop(pos);
                        if (replaceFragileGated && isFragileSchematicAt(pos)) return false;
                        return true;
                    })
                    .filter(this::isReadyToPlace)
                    .limit(MAX_QUEUE_SIZE - placeTasks.size())
                    .filter(this::canPlace)
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



        // Execute tasks
        long max = Math.min(
            (long) (
                MAX_POWER_PER_TICK *
                    (double) (tile.getBattery().getStored() + MAX_POWER_PER_TICK / 10) /
                    (tile.getBattery().getCapacity() * 2)
            ),
            MAX_POWER_PER_TICK
        );

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
                breakTask.power += tile.getBattery().extractPower(
                    0,
                    Math.min(
                        target - breakTask.power,
                        max / breakTasks.size()
                    )
                );
                if (breakTask.power >= target) {
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
                placeTask.power += tile.getBattery().extractPower(
                    0,
                    Math.min(
                        target - placeTask.power,
                        max / placeTasks.size()
                    )
                );
                if (placeTask.power >= target) {
                    if (!doPlaceTask(placeTask)) {
                        cancelPlaceTask(placeTask);
                    }
                    if (check(placeTask.pos)) {
                        checkResultsChanged = true;
                    }
                    iterator.remove();
                }
            }
        }

        if (checkResultsChanged) {
            afterChecks();
        }

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
        long max = Math.min(
            (long) (
                MAX_POWER_PER_TICK *
                    (double) (stored + MAX_POWER_PER_TICK / 10) /
                    (tile.getBattery().getCapacity() * 2)
            ),
            MAX_POWER_PER_TICK
        );

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
     *   <li>NO_REPLACE — skip any position with a fluid (flowing or source). Original behavior.</li>
     *   <li>REPLACE — also skip fluid positions; schematic-solid over fluid is handled through
     *       the place path ({@link SchematicBlockDefault#build(net.minecraft.world.level.Level,
     *       BlockPos, EnumFluidHandlingMode)}), which can waterlog instead of destroying.</li>
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
        FluidState fs = tile.getWorldBC().getFluidState(blockPos);
        if (fs.isEmpty()) return true;
        return getFluidMode() == EnumFluidHandlingMode.CLEAR;
    }

    /**
     * Called from the tile when the player cycles the fluid-handling mode. Resets any
     * check-result slots whose world position currently holds a fluid so the next tick
     * re-classifies them under the new mode — otherwise an already-CORRECT water tile would
     * never get rescheduled as TO_BREAK under CLEAR, and an already-TO_BREAK fluid tile under
     * NO_REPLACE would stay stuck as TO_BREAK forever.
     */
    public void invalidateChecksForFluidPositions() {
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
            if (tile.getWorldBC().isEmptyBlock(blockPos)) {
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
        currentCheckIndex = NBTUtilBC.getInt(nbt, "currentCheckIndex", 0);
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
            items = IntStream.range(0, buffer.readInt())
                .mapToObj(j -> {
                    CompoundTag itemTag = buffer.readNbt();
                    Tag payload = itemTag == null ? null : itemTag.get("stack");
                    ItemStack stack = payload == null
                        ? ItemStack.EMPTY
                        : ItemStack.CODEC.parse(NBTUtilBC.registryAwareOps(), payload)
                            .resultOrPartial()
                            .orElse(ItemStack.EMPTY);
                    int count = buffer.readInt();
                    return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(count);
                })
                .collect(Collectors.toList());
            power = buffer.readLong();
        }

        @SuppressWarnings("WeakerAccess")
        public PlaceTask(CompoundTag nbt) {
            pos = new BlockPos(
                (int) NBTUtilBC.getLong(nbt, "pos_x", 0),
                (int) NBTUtilBC.getLong(nbt, "pos_y", 0),
                (int) NBTUtilBC.getLong(nbt, "pos_z", 0)
            );
            items = ImmutableList.copyOf(
                NBTUtilBC.readCompoundList(nbt.get("items"))
                    .map(tag -> {
                        if (tag.contains("id")) {
                            String idStr = NBTUtilBC.getString(tag, "id", "");
                            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(idStr);
                            if (id != null) {
                                net.minecraft.world.item.Item item = buildcraft.lib.misc.RegistryUtilBC.getValue(net.minecraft.core.registries.BuiltInRegistries.ITEM, id);
                                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                                    return new net.minecraft.world.item.ItemStack(item, NBTUtilBC.getInt(tag, "count", 1));
                                }
                            }
                        }
                        return net.minecraft.world.item.ItemStack.EMPTY;
                    })
                    .filter(stack -> !stack.isEmpty())
                    .collect(java.util.stream.Collectors.toList())
            );
            power = NBTUtilBC.getLong(nbt, "power", 0L);
        }

        public long getTarget() {
            return (long) (Math.sqrt(pos.distSqr(tile.getBuilderPos())) * 10 * MjAPI.MJ);
        }

        public void writePayload(PacketBufferBC buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeInt(items.size());
            items.forEach(item -> {
                CompoundTag tag = new CompoundTag();
                if (!item.isEmpty()) {
                    ItemStack.CODEC.encodeStart(NBTUtilBC.registryAwareOps(), item.copyWithCount(1))
                            .resultOrPartial()
                            .ifPresent(payload -> tag.put("stack", payload));
                }
                buffer.writeNbt(tag);
                buffer.writeInt(item.getCount());
            });
            buffer.writeLong(power);
        }

        public CompoundTag writeToNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.putLong("pos_x", pos.getX());
            nbt.putLong("pos_y", pos.getY());
            nbt.putLong("pos_z", pos.getZ());
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (net.minecraft.world.item.ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    net.minecraft.nbt.CompoundTag itemNbt = new net.minecraft.nbt.CompoundTag();
                    net.minecraft.resources.Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                    itemNbt.putString("id", itemId.toString());
                    itemNbt.putInt("count", stack.getCount());
                    list.add(itemNbt);
                }
            }
            nbt.put("items", list);
            nbt.putLong("power", power);
            return nbt;
        }
    }
}
