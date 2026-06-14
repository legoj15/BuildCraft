/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.tile;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.IAreaProvider;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.chunkload.ChunkLoaderManager;
import buildcraft.lib.chunkload.IChunkLoadingTile;
import buildcraft.lib.debug.IAdvDebugTarget;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.BoundingBoxUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.AxisOrder;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.misc.data.BoxIterator;
import buildcraft.lib.misc.data.EnumAxisOrder;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC_Neptune;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersEntities;
import buildcraft.builders.entity.EntityQuarryRig;
import buildcraft.builders.BCBuildersConfig;
import buildcraft.builders.BCBuildersEventDist;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeConnection;
import buildcraft.core.marker.VolumeSubCache;
import buildcraft.core.tile.TileMarkerVolume;

@SuppressWarnings("this-escape")
public class TileQuarry extends TileBC_Neptune implements IDebuggable, IChunkLoadingTile, IAdvDebugTarget {
    public static final boolean DEBUG_QUARRY = BCDebugging.shouldDebugLog("builders.quarry");
    private static final long MAX_POWER_PER_TICK = 512 * MjAPI.MJ;
    private static final Identifier DIGGY_DIGGY_HOLE = Identifier.parse("buildcraftunofficial:diggy_diggy_hole");

    private final MjBattery battery = new MjBattery(24000 * MjAPI.MJ);
    // A finished quarry has nothing to spend power on, so its receiver stops requesting MJ once
    // work runs out (see hasPendingWork) instead of topping the 24k buffer off forever — which
    // only overheats the feeding engines and is voided when the quarry is torn down. It resumes
    // the instant real work is queued again.
    private final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery) {
        @Override
        public long getPowerRequested() {
            return hasPendingWork() ? super.getPowerRequested() : 0;
        }
    };
    public final Box frameBox = new Box();
    final Box miningBox = new Box();
    private BoxIterator boxIterator;
    public final List<BlockPos> framePoses = new ArrayList<>();
    private int frameBoxPosesCount = 0;
    private final LinkedList<BlockPos> toCheck = new LinkedList<>();
    private final Set<BlockPos> firstCheckedPoses = new HashSet<>();
    private boolean firstChecked = false;
    private final Set<BlockPos> frameBreakBlockPoses = new TreeSet<>(
        BlockUtil.uniqueBlockPosComparator(Comparator.comparingDouble(p -> getBlockPos().distSqr(p)))
    );
    private final Set<BlockPos> framePlaceFramePoses = new HashSet<>();
    public Task currentTask = null;
    public Vec3 drillPos;
    public Vec3 clientDrillPos;
    public Vec3 prevClientDrillPos;
    private long debugPowerRate = 0;
    private double blockPercentSoFar;
    private double moveDistanceSoFar;
    private boolean advancementGranted = false;
    // Last game-time tick on which this quarry was running in unrestricted-power mode
    // (battery > capacity/2, unlocking MAX_POWER_PER_TICK) AND actively mining (a non-
    // frame-placement task was queued). Read by BCBuildersEventDist.findOwnersToAward
    // to pair same-owner quarries for the destroying_the_world advancement. Package-
    // private so tests in this package can stamp it directly without an accessor.
    long lastFullSpeedTick = Long.MIN_VALUE;

    private List<AABB> collisionBoxes = ImmutableList.of();
    private Vec3 collisionDrillPos;

    /** The collision rig entities. Each moving arm (2 horizontal beams + 1 vertical column) is split
     *  into section-aligned segment entities so a player anywhere along it lands in a section the
     *  collision query actually scans — a single long entity is only found near its own position. */
    private final List<EntityQuarryRig> rigs = new ArrayList<>();

    public TileQuarry(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.QUARRY.get(), pos, state);
    }

    /** Returns the MjBatteryReceiver for capability registration. */
    public MjBatteryReceiver getMjReceiver() {
        return mjReceiver;
    }

    /** Last game-time tick on which this quarry was at full power and actively mining.
     *  See {@link #lastFullSpeedTick} for the exact stamp condition. */
    public long getLastFullSpeedTick() {
        return lastFullSpeedTick;
    }

    /** Returns the internal MjBattery for capability registration. */
    public MjBattery getBattery() {
        return battery;
    }

    @Nonnull
    private BoxIterator createBoxIterator() {
        long x = getBlockPos().getX();
        long y = getBlockPos().getY();
        long z = getBlockPos().getZ();
        long seed = ((x & 0xFFFF) << 0) | ((y & 0xFFFF) << 16) | ((z & 0xFFFF) << 32);

        Random rand = new Random(seed);
        EnumAxisOrder axisOrder = rand.nextBoolean() ? EnumAxisOrder.XZY : EnumAxisOrder.ZXY;
        AxisOrder.Inversion inv = AxisOrder.Inversion.getFor(rand.nextBoolean(), rand.nextBoolean(), false);
        return new BoxIterator(miningBox, AxisOrder.getFor(axisOrder, inv), true);
    }

    /** Gets the current positions where frame blocks should be placed, in order. */
    private List<BlockPos> getFramePositions() {
        Set<BlockPos> visitedSet = new HashSet<>();
        List<BlockPos> framePositions = new ArrayList<>();
        List<BlockPos> openSet = new ArrayList<>();
        List<BlockPos> nextOpenSet = new ArrayList<>();

        openSet.add(getBlockPos());
        Direction[] order = Direction.values();
        List<Direction> orderAsList = Arrays.asList(order);

        int maxIterationCount = frameBox.getBlocksOnEdgeCount();
        int iterationCount = 0;
        do {
            for (BlockPos p : openSet) {
                Collections.shuffle(orderAsList);
                for (Direction face : order) {
                    BlockPos next = p.relative(face);
                    if (frameBox.isOnEdge(next) && visitedSet.add(next)) {
                        nextOpenSet.add(next);
                        framePositions.add(next);
                    }
                }
            }
            openSet.clear();
            List<BlockPos> t = openSet;
            openSet = nextOpenSet;
            nextOpenSet = t;
            Collections.shuffle(openSet);

            if (openSet.size() > 8 * 3) {
                String msg = "OpenSet got too big!";
                msg += "\n  Position = " + worldPosition;
                msg += "\n  Frame Box = " + frameBox;
                msg += "\n  Iteration Count = " + iterationCount;
                msg += "\n  OpenSet = " + openSet.stream().map(Object::toString).collect(
                    Collectors.joining("\n  ", "[", "]")
                );
                throw new IllegalStateException(msg);
            }

            iterationCount++;
            if (iterationCount >= maxIterationCount) {
                String msg = "Failed to generate a correct list of frame positions! Was the frame box wrong?";
                msg += "\n  Position = " + worldPosition;
                msg += "\n  Frame Box = " + frameBox;
                msg += "\n  Iteration Count = " + iterationCount;
                msg += "\n  OpenSet = " + openSet.stream().map(Object::toString).collect(
                    Collectors.joining("\n  ", "[", "]")
                );
                throw new IllegalStateException(msg);
            }
        } while (!openSet.isEmpty());

        if (framePositions.isEmpty()) {
            String msg = "Failed to generate a correct list of frame positions! Was the frame box wrong?";
            msg += "\n  Position = " + worldPosition;
            msg += "\n  Frame Box = " + frameBox;
            throw new IllegalStateException(msg);
        }

        return framePositions;
    }

    private boolean shouldBeFrame(BlockPos p) {
        return frameBox.isOnEdge(p);
    }

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Records the placer as owner — load-bearing for both quarry advancements:
        // "Diggy diggy hole" gates on getOwner() != null, and "Destroying the world"
        // can't pair quarries without an owner UUID.
        super.onPlacedBy(placer, stack);
        Direction facing = level.getBlockState(worldPosition).getValue(HorizontalDirectionalBlock.FACING);
        BlockPos areaPos = worldPosition.relative(facing.getOpposite());
        BlockEntity tile = level.getBlockEntity(areaPos);
        BlockPos min = null, max = null;
        if (tile instanceof IAreaProvider provider) {
            min = provider.min();
            max = provider.max();
            int dx = max.getX() - min.getX();
            int dz = max.getZ() - min.getZ();
            if (dx < 3 || dz < 3) {
                min = null;
                max = null;
            } else {
                provider.removeFromWorld();
            }
        }
        if (min == null || max == null) {
            min = null;
            max = null;
            VolumeSubCache cache = VolumeCache.INSTANCE.getSubCache(getLevel());
            for (BlockPos markerPos : cache.getAllMarkers()) {
                TileMarkerVolume marker = (TileMarkerVolume) cache.getMarker(markerPos);
                if (marker == null) {
                    continue;
                }
                VolumeConnection connection = marker.getCurrentConnection();
                if (connection == null) {
                    continue;
                }
                Box volBox = connection.getBox();
                Box box2 = new Box();
                box2.initialize(volBox);
                if (!box2.isInitialized()) {
                    continue;
                }
                if (worldPosition.getY() != box2.min().getY()) {
                    continue;
                }
                if (box2.contains(worldPosition)) {
                    continue;
                }
                if (!box2.contains(areaPos)) {
                    continue;
                }
                if (box2.size().getX() < 3 || box2.size().getZ() < 3) {
                    continue;
                }
                box2.expand(1);
                box2.setMin(box2.min().above());
                if (box2.isOnEdge(worldPosition)) {
                    min = volBox.min();
                    max = volBox.max();
                    marker.removeFromWorld();
                    break;
                }
            }
        }
        if (min == null || max == null) {
            miningBox.reset();
            frameBox.reset();
            switch (facing.getOpposite()) {
                case DOWN:
                case UP:
                default:
                case EAST: // +X
                    min = worldPosition.offset(1, 0, -5);
                    max = worldPosition.offset(11, 4, 5);
                    break;
                case WEST: // -X
                    min = worldPosition.offset(-11, 0, -5);
                    max = worldPosition.offset(-1, 4, 5);
                    break;
                case SOUTH: // +Z
                    min = worldPosition.offset(-5, 0, 1);
                    max = worldPosition.offset(5, 4, 11);
                    break;
                case NORTH: // -Z
                    min = worldPosition.offset(-5, 0, -11);
                    max = worldPosition.offset(5, 4, -1);
                    break;
            }
        }
        if (max.getY() - min.getY() < BCBuildersConfig.quarryFrameMinHeight.get()) {
            max = new BlockPos(max.getX(), min.getY() + BCBuildersConfig.quarryFrameMinHeight.get(), max.getZ());
        }
        if (level.isOutsideBuildHeight(max)) {
            int dist = max.getY() - min.getY();
            min = min.below(dist);
            max = max.below(dist);
        }
        frameBox.reset();
        frameBox.setMin(min);
        frameBox.setMax(max);
        miningBox.reset();
        int minY = computeMiningMinY();
        miningBox.setMin(new BlockPos(min.getX() + 1, minY, min.getZ() + 1));
        miningBox.setMax(new BlockPos(max.getX() - 1, max.getY() - 1, max.getZ() - 1));
        updatePoses();
        // Sync to client so beams render
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * The mining box's floor Y, derived from {@link BCCoreConfig#miningMaxDepth}: dig at most
     * {@code miningMaxDepth} blocks below the quarry block (matching the mining well / pump).
     * Clamped to world floor when build height is shallower than the config allows.
     */
    private int computeMiningMinY() {
        int minY = worldPosition.getY() - BCCoreConfig.miningMaxDepth.get();
        if (level != null && level.isOutsideBuildHeight(minY)) {
            minY = level.getMinY();
        }
        return minY;
    }

    private boolean canMine(BlockPos blockPos) {
        if (level.getBlockState(blockPos).getDestroySpeed(level, blockPos) < 0) {
            return false;
        }
        Fluid fluid = BlockUtil.getFluidWithFlowing(level, blockPos);
        if (fluid != null) {
            return false;
        }
        // Respects player-protection mods via BCCoreConfig.minePlayerProtected.
        // The box iterator's advance loop already skips positions where canMine() is false,
        // and the in-flight task at finish() returns true (task complete, no retry) — so a
        // protected position is naturally bypassed without stalling the quarry.
        if (level instanceof ServerLevel serverLevel
                && !BlockUtil.canMachineBreak(serverLevel, blockPos, getOwner())) {
            return false;
        }
        return true;
    }

    private boolean canMoveThrough(BlockPos blockPos) {
        if (level.getBlockState(blockPos).isAir()) {
            return true;
        }
        Fluid fluid = BlockUtil.getFluidWithFlowing(level, blockPos);
        return fluid != null;
    }

    private boolean canMoveDownTo(BlockPos blockPos) {
        for (int y = miningBox.max().getY(); y > blockPos.getY(); y--) {
            if (!canMoveThrough(VecUtil.replaceValue(blockPos, Axis.Y, y))) {
                return false;
            }
        }
        return true;
    }

    private boolean canIgnoreInFrameBox(BlockPos blockPos) {
        return !level.getBlockState(blockPos).isAir() && BlockUtil.getFluidWithFlowing(level, blockPos) == null;
    }

    private void check(BlockPos blockPos) {
        frameBreakBlockPoses.remove(blockPos);
        framePlaceFramePoses.remove(blockPos);
        if (shouldBeFrame(blockPos)) {
            if (!level.getBlockState(blockPos).is(BCBuildersBlocks.FRAME.get())) {
                if (canIgnoreInFrameBox(blockPos)) {
                    frameBreakBlockPoses.add(blockPos);
                } else {
                    framePlaceFramePoses.add(blockPos);
                }
            }
        } else {
            if (canIgnoreInFrameBox(blockPos)) {
                frameBreakBlockPoses.add(blockPos);
            }
        }
        if (!firstChecked) {
            firstCheckedPoses.add(blockPos);
            if (firstCheckedPoses.size() >= frameBoxPosesCount) {
                firstChecked = true;
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            updatePoses();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        BCBuildersEventDist.INSTANCE.invalidateQuarry(this);
        if (level != null && !level.isClientSide()) {
            ChunkLoaderManager.releaseChunksFor(this);
            discardRigs();
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        BCBuildersEventDist.INSTANCE.validateQuarry(this);
    }

    @Nullable
    @Override
    public LoadType getLoadType() {
        return LoadType.HARD;
    }

    @Nullable
    @Override
    public Set<ChunkPos> getChunksToLoad() {
        if (!miningBox.isInitialized()) {
            return null;
        }
        Set<ChunkPos> chunkPoses = new HashSet<>();
        ChunkPos minChunkPos = buildcraft.lib.misc.PositionUtil.chunkContaining(frameBox.min());
        ChunkPos maxChunkPos = buildcraft.lib.misc.PositionUtil.chunkContaining(frameBox.max());
        int minX = buildcraft.lib.misc.PositionUtil.chunkX(minChunkPos);
        int maxX = buildcraft.lib.misc.PositionUtil.chunkX(maxChunkPos);
        int minZ = buildcraft.lib.misc.PositionUtil.chunkZ(minChunkPos);
        int maxZ = buildcraft.lib.misc.PositionUtil.chunkZ(maxChunkPos);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunkPoses.add(new ChunkPos(x, z));
            }
        }
        return chunkPoses;
    }

    @Override
    public Component getAdvDebugMessage() {
        return Component.translatable("chat.debugger.quarry");
    }

    private void updatePoses() {
        framePoses.clear();
        frameBoxPosesCount = 0;
        toCheck.clear();
        firstCheckedPoses.clear();
        firstChecked = false;
        frameBreakBlockPoses.clear();
        framePlaceFramePoses.clear();
        BlockState state = level.getBlockState(worldPosition);
        if (state.is(BCBuildersBlocks.QUARRY.get()) && frameBox.isInitialized()) {
            List<BlockPos> blocksInArea = frameBox.getBlocksInArea();
            blocksInArea.sort(BlockUtil.uniqueBlockPosComparator(Comparator.comparingDouble(worldPosition::distSqr)));
            frameBoxPosesCount = blocksInArea.size();
            toCheck.addAll(blocksInArea);
            framePoses.addAll(getFramePositions());
            ChunkLoaderManager.loadChunksForTile(this);
        }
    }

    public boolean hasPower() {
        return battery.getStored() > 0;
    }

    public boolean isMining() {
        return currentTask != null;
    }

    /**
     * True while the quarry still has something to spend power on: an in-flight task, queued
     * frame break/place work, or unmined cells left in the box. A completed quarry returns false,
     * which gates {@link #getMjReceiver()} so it stops pulling power (see the receiver's
     * {@code getPowerRequested} override) rather than holding its battery topped off after the job.
     */
    boolean hasPendingWork() {
        return currentTask != null
            || !frameBreakBlockPoses.isEmpty()
            || !framePlaceFramePoses.isEmpty()
            || (boxIterator != null && boxIterator.hasNext());
    }

    /** Called once per tick by the BlockEntityTicker. */
    public void tick() {
        if (level == null) return;

        if (drillPos == null) {
            collisionBoxes = ImmutableList.of();
            collisionDrillPos = null;
        }

        if (level.isClientSide()) {
            prevClientDrillPos = clientDrillPos;
            clientDrillPos = drillPos;
            if (currentTask != null) {
                currentTask.clientTick();
            }
            return;
        }

        if (!frameBox.isInitialized() || !miningBox.isInitialized()) {
            return;
        }

        // Re-apply miningMaxDepth so config changes take effect on already-placed quarries
        // (saved mining box was sized under whatever config was in effect when it was placed).
        // Mining-box top stays anchored to the frame interior; only the floor depends on the
        // config, so reshape just the min Y and reset the iterator if it shifted.
        int desiredMinY = computeMiningMinY();
        if (miningBox.min().getY() != desiredMinY) {
            BlockPos oldMin = miningBox.min();
            miningBox.setMin(new BlockPos(oldMin.getX(), desiredMinY, oldMin.getZ()));
            boxIterator = null;
        }

        if (!toCheck.isEmpty()) {
            for (int i = 0; i < (firstChecked ? 10 : 500); i++) {
                BlockPos blockPos = toCheck.pollFirst();
                check(blockPos);
                toCheck.addLast(blockPos);
            }
        }

        if (!firstChecked) {
            return;
        }

        // Capture the unrestricted-power gate at tick start — the power_loop below drains
        // up to MAX_POWER_PER_TICK from the battery, which can cross the half-capacity
        // threshold downward and falsely look "not at full speed" by the time we stamp.
        boolean atFullSpeedThisTick = battery.getStored() > battery.getCapacity() / 2;
        long max;
        if (atFullSpeedThisTick) {
            max = MAX_POWER_PER_TICK;
        } else {
            long roundedUp = battery.getStored() + MjAPI.MJ / 2;
            if (roundedUp > Long.MAX_VALUE / MAX_POWER_PER_TICK) {
                max = BigInteger.valueOf(roundedUp).multiply(BigInteger.valueOf(MAX_POWER_PER_TICK))
                    .divide(BigInteger.valueOf(battery.getCapacity() / 2)).longValue();
            } else {
                max = MAX_POWER_PER_TICK * roundedUp / (battery.getCapacity() / 2);
            }
            max = MathUtil.clamp(max, 0, MAX_POWER_PER_TICK);
        }
        debugPowerRate = max;
        blockPercentSoFar = 0;
        moveDistanceSoFar = 0;

        int maxTasks = Math.max(1, (int) (max * BCBuildersConfig.quarryMaxTasksPerTick.get() / MAX_POWER_PER_TICK));
        boolean sendUpdate = false;
        power_loop: for (int i = 0; i < maxTasks; i++) {

            if (currentTask != null) {
                long needed = currentTask.getRequiredPowerThisTick();
                long added;
                final int mult = BCBuildersConfig.quarryTaskPowerDivisor.get();
                if (mult > 0) {
                    long nNeeded = needed * (mult + i) / mult;
                    long leftover = (needed * (mult + i)) % mult;
                    long power = battery.extractPower(0, Math.min(max, nNeeded));
                    max -= power;
                    added = power * mult / (mult + i);
                    if (leftover > 0) {
                        added++;
                    }
                } else {
                    added = battery.extractPower(0, Math.min(max, needed));
                    max -= added;
                }
                if (currentTask.addPower(added)) {
                    currentTask = null;
                    // Sync the final drill position the task just committed (TaskMoveDrill.finish sets
                    // drillPos = to). Without this a task that COMPLETES on this tick — common at high
                    // power, where a whole 1-block move finishes in a single tick — never pushes its final
                    // drillPos to the client: the tile only synced while the task was still running, so
                    // clientDrillPos (and the rendered rig) lags a block or two behind the rig's collision
                    // entity (which syncs every tick), leaving a gap in a boom arm's collision that
                    // persists while the drill is stopped. Re-sync so the visual matches the collision.
                    sendUpdate = true;
                } else {
                    sendUpdate = true;
                    break;
                }
            }

            if (!frameBreakBlockPoses.isEmpty()) {
                BlockPos blockPos = frameBreakBlockPoses.iterator().next();
                if (canMine(blockPos)) {
                    drillPos = null;
                    currentTask = new TaskBreakBlock(blockPos);
                    sendUpdate = true;
                }
                check(blockPos);
                continue power_loop;
            }

            if (!framePlaceFramePoses.isEmpty()) {
                for (BlockPos blockPos : framePoses) {
                    if (!framePlaceFramePoses.contains(blockPos)) {
                        continue;
                    }
                    check(blockPos);
                    if (!framePlaceFramePoses.contains(blockPos)) {
                        continue;
                    }
                    drillPos = null;
                    currentTask = new TaskAddFrame(blockPos);
                    sendUpdate = true;
                    continue power_loop;
                }
            }

            if (boxIterator == null || drillPos == null) {
                boxIterator = createBoxIterator();
                while (
                    canMoveThrough(boxIterator.getCurrent()) || !canMine(boxIterator.getCurrent())
                    || !canMoveDownTo(boxIterator.getCurrent())
                ) {
                    if (boxIterator.advance() == null) {
                        break;
                    }
                }
                // Only seed the drill at the mining-box corner when it has no position yet (initial
                // setup after the frame is built). The boxIterator isn't persisted, so it is ALWAYS
                // recreated on load — but drillPos IS persisted (saveAdditional/readData), and on a world
                // reload it arrives here non-null. Recomputing it then would discard the saved mid-mining
                // position and snap the drill (and its collision rig entity) to the corner, while the
                // client's rendered rig stays at the restored position until the next move syncs — the
                // whole-block "collision desynced from the rig after reload" offset that persisted until
                // the quarry was broken and replaced. Guarding on null preserves the restored drillPos so
                // the rig entity and the rendered rig agree.
                if (drillPos == null) {
                    drillPos = Vec3.atLowerCornerOf(miningBox.closestInsideTo(worldPosition));
                }
            }

            if (boxIterator != null && boxIterator.hasNext()) {
                while (
                    canMoveThrough(boxIterator.getCurrent()) || !canMine(boxIterator.getCurrent())
                    || !canMoveDownTo(boxIterator.getCurrent())
                ) {
                    if (boxIterator.advance() == null) {
                        break;
                    }
                }

                if (boxIterator.hasNext()) {
                    boolean found = false;

                    Vec3 targetVec = Vec3.atLowerCornerOf(boxIterator.getCurrent());
                    if (drillPos.distanceToSqr(targetVec) >= 1) {
                        currentTask = new TaskMoveDrill(drillPos, targetVec);
                        found = true;
                    } else if (canMine(boxIterator.getCurrent())) {
                        currentTask = new TaskBreakBlock(boxIterator.getCurrent());
                        found = true;
                    }

                    if (found) {
                        sendUpdate = true;
                    }
                }
            }
        }
        debugPowerRate -= max;

        // Stamp for the destroying_the_world per-owner pairing scan. Frame placement isn't
        // "destroying"; everything else (TaskBreakBlock, TaskMoveDrill toward a target) is.
        if (atFullSpeedThisTick && currentTask != null && !(currentTask instanceof TaskAddFrame)) {
            lastFullSpeedTick = level.getGameTime();
        }

        // Grant "Diggy diggy hole" advancement when quarry finishes mining with frame >= 64x64
        if (!advancementGranted && boxIterator != null && !boxIterator.hasNext()
                && frameBreakBlockPoses.isEmpty() && framePlaceFramePoses.isEmpty()
                && frameBox.isInitialized()) {
            int sizeX = frameBox.max().getX() - frameBox.min().getX() + 1;
            int sizeZ = frameBox.max().getZ() - frameBox.min().getZ() + 1;
            if (sizeX >= 64 && sizeZ >= 64 && getOwner() != null) {
                // Only latch advancementGranted when the award actually reached the player.
                // unlockAdvancement returns false if the owner is offline; latching anyway
                // would silently drop the award and never retry on subsequent ticks.
                if (AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), level, DIGGY_DIGGY_HOLE)) {
                    advancementGranted = true;
                    setChanged();
                }
            }
        }

        if (sendUpdate) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                MessageUtil.sendUpdateToTrackingPlayers(this);
            }
        }

        updateRigs();
    }

    private void updateRigs() {
        if (level == null || level.isClientSide()) return;

        if (drillPos == null || !frameBox.isInitialized()) {
            discardRigs();
            return;
        }

        List<AABB> boxes = getCollisionBoxes();
        if (boxes.size() != 3) {
            discardRigs();
            return;
        }

        // Split each arm along its long axis into 16-block, section-aligned segments. MC files every
        // entity in the single entity-storage section at its POSITION and a collision query only scans
        // sections within ~a couple of blocks of the query (EntitySectionStorage.forEachAccessibleNon-
        // EmptySection); one long entity is therefore only found near its own centre, so a player out at
        // a beam's end — or partway down a deep column — falls through despite the box being there.
        // Per-section segments keep a collidable piece in whichever section the player is in.
        List<AABB> beamSegments = new ArrayList<>();
        QuarryRigGeometry.splitBySection(beamSegments, boxes.get(0), Axis.Z); // X-axis beam runs along Z
        QuarryRigGeometry.splitBySection(beamSegments, boxes.get(1), Axis.X); // Z-axis beam runs along X
        List<AABB> columnSegments = new ArrayList<>();
        QuarryRigGeometry.splitBySection(columnSegments, boxes.get(2), Axis.Y); // vertical column runs along Y

        boolean isDrillMoving = (currentTask instanceof TaskMoveDrill);
        int total = beamSegments.size() + columnSegments.size();

        // Resize the rig list to match the segment count, discarding any now-surplus entities (the
        // column's segment count grows as the drill descends and shrinks if the box is rebuilt smaller).
        while (rigs.size() > total) {
            EntityQuarryRig surplus = rigs.remove(rigs.size() - 1);
            if (surplus != null && !surplus.isRemoved()) {
                surplus.discard();
            }
        }
        while (rigs.size() < total) {
            rigs.add(null);
        }

        for (int i = 0; i < total; i++) {
            boolean isColumn = i >= beamSegments.size();
            AABB box = isColumn ? columnSegments.get(i - beamSegments.size()) : beamSegments.get(i);
            EntityQuarryRig rig = rigs.get(i);
            if (rig == null || rig.isRemoved()) {
                rig = new EntityQuarryRig(BCBuildersEntities.QUARRY_RIG.get(), level);
                level.addFreshEntity(rig);
                rigs.set(i, rig);
            }
            rig.setRiggingBox(box);
            // Only the column phases (passes through the player) while the drill moves, so the moving
            // mast doesn't shove anyone; the beams stay solid to be walked on.
            rig.setPhasing(isColumn && isDrillMoving);
        }
    }

    private void discardRigs() {
        for (EntityQuarryRig rig : rigs) {
            if (rig != null && !rig.isRemoved()) {
                rig.discard();
            }
        }
        rigs.clear();
    }

    /** Returns collision boxes for the quarry's moving drill rig, allowing players to walk on it.
     *  Produces 3 AABBs: 2 horizontal crossbeams (X-axis and Z-axis at the top) and 1 vertical column. */
    public List<AABB> getCollisionBoxes() {
        if (drillPos != null && drillPos != collisionDrillPos && frameBox.isInitialized()) {
            Vec3 fMax = VecUtil.convertCenter(frameBox.max());
            Vec3 fMin = VecUtil.replaceValue(VecUtil.convertCenter(frameBox.min()), Direction.Axis.Y, fMax.y);
            collisionBoxes = ImmutableList.of(
                // X-axis beam (runs along Z)
                BoundingBoxUtil.makeFrom(
                    VecUtil.replaceValue(fMin, Direction.Axis.X, drillPos.x + 0.5),
                    VecUtil.replaceValue(fMax, Direction.Axis.X, drillPos.x + 0.5),
                    0.25
                ),
                // Z-axis beam (runs along X)
                BoundingBoxUtil.makeFrom(
                    VecUtil.replaceValue(fMin, Direction.Axis.Z, drillPos.z + 0.5),
                    VecUtil.replaceValue(fMax, Direction.Axis.Z, drillPos.z + 0.5),
                    0.25
                ),
                // Vertical column from drill to top
                BoundingBoxUtil.makeFrom(
                    drillPos.add(0.5, 0, 0.5),
                    VecUtil.replaceValue(drillPos, Direction.Axis.Y, fMax.y).add(0.5, 0, 0.5),
                    0.25
                )
            );
            collisionDrillPos = drillPos;
        }
        return collisionBoxes;
    }

    // NBT

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        // Mining box — write coordinates directly (Box.writeToNBT uses nested tags that don't work with flat ValueOutput)
        output.putBoolean("box_init", miningBox.isInitialized());
        if (miningBox.isInitialized()) {
            output.putInt("box_minX", miningBox.min().getX());
            output.putInt("box_minY", miningBox.min().getY());
            output.putInt("box_minZ", miningBox.min().getZ());
            output.putInt("box_maxX", miningBox.max().getX());
            output.putInt("box_maxY", miningBox.max().getY());
            output.putInt("box_maxZ", miningBox.max().getZ());
        }

        // Frame box — write coordinates directly
        output.putBoolean("frame_init", frameBox.isInitialized());
        if (frameBox.isInitialized()) {
            output.putInt("frame_minX", frameBox.min().getX());
            output.putInt("frame_minY", frameBox.min().getY());
            output.putInt("frame_minZ", frameBox.min().getZ());
            output.putInt("frame_maxX", frameBox.max().getX());
            output.putInt("frame_maxY", frameBox.max().getY());
            output.putInt("frame_maxZ", frameBox.max().getZ());
        }

        if (boxIterator != null) {
            // Store boxIterator as a sub-compound tag via CompoundTag
            // We store the compound as individual keys for ValueOutput compat
            CompoundTag iterTag = boxIterator.writeToNbt();
            output.putBoolean("hasBoxIterator", true);
            // Store serialized iterator nbt
            output.putString("boxIterator_data", iterTag.toString());
        } else {
            output.putBoolean("hasBoxIterator", false);
        }

        output.putLong("battery_mj", battery.getStored());

        if (currentTask != null) {
            int taskId = -1;
            for (EnumTaskType type : EnumTaskType.values()) {
                if (type.clazz == currentTask.getClass()) {
                    taskId = type.ordinal();
                    break;
                }
            }
            output.putByte("currentTaskId", (byte) taskId);
            CompoundTag taskTag = currentTask.serializeNBT();
            output.putLong("task_power", NBTUtilBC.getLong(taskTag, "power", 0L));
            if (currentTask instanceof TaskBreakBlock tb) {
                output.putInt("task_breakX", tb.breakPos.getX());
                output.putInt("task_breakY", tb.breakPos.getY());
                output.putInt("task_breakZ", tb.breakPos.getZ());
            } else if (currentTask instanceof TaskAddFrame tf) {
                output.putInt("task_frameX", tf.framePos.getX());
                output.putInt("task_frameY", tf.framePos.getY());
                output.putInt("task_frameZ", tf.framePos.getZ());
            } else if (currentTask instanceof TaskMoveDrill tm) {
                output.putDouble("task_fromX", tm.from.x);
                output.putDouble("task_fromY", tm.from.y);
                output.putDouble("task_fromZ", tm.from.z);
                output.putDouble("task_toX", tm.to.x);
                output.putDouble("task_toY", tm.to.y);
                output.putDouble("task_toZ", tm.to.z);
            }
        } else {
            output.putByte("currentTaskId", (byte) -1);
        }

        if (drillPos != null) {
            output.putDouble("drillX", drillPos.x);
            output.putDouble("drillY", drillPos.y);
            output.putDouble("drillZ", drillPos.z);
            output.putBoolean("hasDrill", true);
        } else {
            output.putBoolean("hasDrill", false);
        }
        output.putBoolean("firstChecked", firstChecked);
        output.putBoolean("advancementGranted", advancementGranted);
    }

    // Network sync — sends all data to client for rendering
    // In 1.21.11, getUpdateTag(HolderLookup.Provider) is called by
    // ClientboundBlockEntityDataPacket.create(this) to serialize data for the client.
    // We delegate to saveCustomOnly() which calls saveAdditional(ValueOutput),
    // ensuring all frame/mining box data is included in the network packet.

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);

        // Mining box
        if (input.getBooleanOr("box_init", false)) {
            miningBox.reset();
            miningBox.setMin(new BlockPos(
                input.getIntOr("box_minX", 0),
                input.getIntOr("box_minY", 0),
                input.getIntOr("box_minZ", 0)));
            miningBox.setMax(new BlockPos(
                input.getIntOr("box_maxX", 0),
                input.getIntOr("box_maxY", 0),
                input.getIntOr("box_maxZ", 0)));
        }

        // Frame box
        if (input.getBooleanOr("frame_init", false)) {
            frameBox.reset();
            frameBox.setMin(new BlockPos(
                input.getIntOr("frame_minX", 0),
                input.getIntOr("frame_minY", 0),
                input.getIntOr("frame_minZ", 0)));
            frameBox.setMax(new BlockPos(
                input.getIntOr("frame_maxX", 0),
                input.getIntOr("frame_maxY", 0),
                input.getIntOr("frame_maxZ", 0)));
        }

        // boxIterator — skipping for now, will be recreated on next tick
        boxIterator = null;

        // Battery
        long stored = input.getLongOr("battery_mj", 0L);
        CompoundTag mjTag = new CompoundTag();
        mjTag.putLong("stored", stored);
        battery.deserializeNBT(mjTag);

        // Task
        int taskId = input.getByteOr("currentTaskId", (byte) -1);
        if (taskId >= 0 && taskId < EnumTaskType.values().length) {
            currentTask = EnumTaskType.values()[taskId].supplier.apply(this);
            currentTask.power = input.getLongOr("task_power", 0L);
            // Initialize client interpolation from the synced power to avoid yOffset jumps
            currentTask.clientPower = currentTask.power;
            currentTask.prevClientPower = currentTask.power;
            if (currentTask instanceof TaskBreakBlock tb) {
                tb.breakPos = new BlockPos(
                    input.getIntOr("task_breakX", 0),
                    input.getIntOr("task_breakY", 0),
                    input.getIntOr("task_breakZ", 0));
            } else if (currentTask instanceof TaskAddFrame tf) {
                tf.framePos = new BlockPos(
                    input.getIntOr("task_frameX", 0),
                    input.getIntOr("task_frameY", 0),
                    input.getIntOr("task_frameZ", 0));
            } else if (currentTask instanceof TaskMoveDrill tm) {
                tm.from = new Vec3(
                    input.getDoubleOr("task_fromX", 0),
                    input.getDoubleOr("task_fromY", 0),
                    input.getDoubleOr("task_fromZ", 0));
                tm.to = new Vec3(
                    input.getDoubleOr("task_toX", 0),
                    input.getDoubleOr("task_toY", 0),
                    input.getDoubleOr("task_toZ", 0));
            }
        } else {
            currentTask = null;
        }

        // Drill position
        if (input.getBooleanOr("hasDrill", false)) {
            drillPos = new Vec3(
                input.getDoubleOr("drillX", 0),
                input.getDoubleOr("drillY", 0),
                input.getDoubleOr("drillZ", 0));
        } else {
            drillPos = null;
        }
        firstChecked = input.getBooleanOr("firstChecked", false);
        advancementGranted = input.getBooleanOr("advancementGranted", false);

        if (drillPos != null && drillPos.distanceToSqr(Vec3.atLowerCornerOf(getBlockPos())) > 1024 * 1024) {
            drillPos = null;
        }

        // Validation
        boolean isValid = false;
        if (frameBox.isInitialized() && miningBox.isInitialized()) {
            isValid = true;
            Direction validFace = null;
            for (Direction face : Direction.values()) {
                if (face.getAxis() == Axis.Y) continue;
                if (frameBox.isOnEdge(getBlockPos().relative(face))) {
                    validFace = face;
                    break;
                }
            }
            if (validFace == null) {
                isValid = false;
            } else {
                int fx0 = frameBox.min().getX();
                int fz0 = frameBox.min().getZ();
                int fx1 = frameBox.max().getX();
                int fy1 = frameBox.max().getY();
                int fz1 = frameBox.max().getZ();

                int mx0 = miningBox.min().getX();
                int mz0 = miningBox.min().getZ();
                int mx1 = miningBox.max().getX();
                int my1 = miningBox.max().getY();
                int mz1 = miningBox.max().getZ();

                isValid = fx0 + 1 == mx0
                    && fx1 - 1 == mx1
                    && fz0 + 1 == mz0
                    && fz1 - 1 == mz1
                    && fy1 - 1 == my1;
            }
        }
        if (!isValid) {
            frameBox.reset();
            miningBox.reset();
            drillPos = null;
        }
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("rate = " + LocaleUtil.localizeMjFlow(debugPowerRate));
        left.add("frameBox");
        left.add(" - min = " + frameBox.min());
        left.add(" - max = " + frameBox.max());
        left.add("miningBox:");
        left.add(" - min = " + miningBox.min());
        left.add(" - max = " + miningBox.max());
        left.add("firstCheckedPoses = " + firstCheckedPoses.size());
        left.add("frameBoxPosesCount = " + frameBoxPosesCount);
        left.add("firstChecked = " + firstChecked);
        BoxIterator iter = boxIterator;
        left.add("current = " + (iter == null ? "null" : iter.getCurrent()));
        Task task = currentTask;
        if (task != null) {
            left.add("task:");
            left.add(" - class = " + task.getClass().getName());
            left.add(" - power = " + LocaleUtil.localizeMj(task.power));
            left.add(" - target = " + LocaleUtil.localizeMj(task.getTarget()));
        } else {
            left.add("task = null");
        }
        left.add("drill = " + drillPos);
    }

    // Task types

    private enum EnumTaskType {
        BREAK_BLOCK(TaskBreakBlock.class, quarry -> quarry.new TaskBreakBlock()),
        ADD_FRAME(TaskAddFrame.class, quarry -> quarry.new TaskAddFrame()),
        MOVE_DRILL(TaskMoveDrill.class, quarry -> quarry.new TaskMoveDrill());

        public final Class<? extends Task> clazz;
        public final Function<TileQuarry, Task> supplier;

        EnumTaskType(Class<? extends Task> clazz, Function<TileQuarry, Task> supplier) {
            this.clazz = clazz;
            this.supplier = supplier;
        }
    }

    public abstract class Task {
        public long power;
        public long clientPower;
        public long prevClientPower;

        CompoundTag serializeNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.putLong("power", power);
            return nbt;
        }

        void clientTick() {
            prevClientPower = clientPower;
            clientPower = power;
        }

        public abstract long getTarget();

        public long getRequiredPowerThisTick() {
            return Math.max(0, getTarget() - power);
        }

        protected abstract boolean onReceivePower(long added, long target);

        protected abstract boolean finish(long added, long target);

        final boolean addPower(long microJoules) {
            power += microJoules;
            long target = getTarget();
            if (power >= target) {
                if (!finish(microJoules, target)) {
                    battery.addPower(Math.min(power, battery.getCapacity() - battery.getStored()), false);
                }
                return true;
            } else {
                return onReceivePower(microJoules, target);
            }
        }
    }

    public class TaskBreakBlock extends Task {
        public BlockPos breakPos = BlockPos.ZERO;

        TaskBreakBlock() {}

        TaskBreakBlock(BlockPos pos) {
            this.breakPos = pos;
        }

        @Override
        public long getTarget() {
            return BlockUtil.computeBlockBreakPower(level, breakPos);
        }

        @Override
        public long getRequiredPowerThisTick() {
            long target = getTarget();
            long req = Math.max(0, target - power);
            double rate = BCBuildersConfig.quarryMaxBlockMineRate.get();
            if (rate < 0.1) {
                return req;
            }
            rate /= 20; // seconds -> ticks
            rate -= blockPercentSoFar;
            if (rate <= 0) {
                return 0;
            }
            return Math.min(req, (long) (target * rate));
        }

        @Override
        protected boolean onReceivePower(long added, long target) {
            blockPercentSoFar += added / (double) target;
            if (!level.getBlockState(breakPos).isAir()) {
                level.destroyBlockProgress(breakPos.hashCode(), breakPos, (int) (power * 9 / getTarget()));
                return false;
            } else {
                return true;
            }
        }

        @Override
        protected boolean finish(long added, long target) {
            blockPercentSoFar += added / (double) target;
            if (!canMine(breakPos)) {
                return true;
            }
            level.destroyBlockProgress(breakPos.hashCode(), breakPos, -1);
            if (level instanceof ServerLevel serverLevel) {
                // Diamond-pickaxe tier — quarry chews through obsidian, ancient debris, the lot.
                // Destruction-laser blocks (above the mining surface) and mining-laser blocks
                // (the proper mining column) share this code path; per the user's spec, both
                // tiers route drops "as if mined by the rig itself," i.e. through the same
                // addToBestAcceptor pipeline the quarry already uses for its harvest.
                Optional<BlockUtil.BreakResult> result = BlockUtil.breakBlockAndGetDropsWithXp(
                    serverLevel, breakPos, new ItemStack(Items.DIAMOND_PICKAXE), getOwner()
                );
                if (result.isPresent()) {
                    result.get().drops().forEach(stack ->
                        InventoryUtil.addToBestAcceptor(level, worldPosition, null, stack));
                    // XP at the Quarry block itself rather than the broken position — matches
                    // the Mining Well / Builder / Filler convention so players collect XP at the
                    // machine they placed. Block.popExperience splits the integer into multiple
                    // orbs at one location and respects BLOCK_DROPS / restoringBlockSnapshots.
                    int xp = result.get().xp();
                    if (xp > 0) {
                        getBlockState().getBlock().popExperience(serverLevel, worldPosition, xp);
                    }
                }
                check(breakPos);
                return result.isPresent();
            }
            return false;
        }
    }

    public class TaskAddFrame extends Task {
        public BlockPos framePos = BlockPos.ZERO;

        TaskAddFrame() {}

        TaskAddFrame(BlockPos framePos) {
            this.framePos = framePos;
        }

        @Override
        public long getTarget() {
            return 24 * MjAPI.MJ;
        }

        @Override
        protected boolean onReceivePower(long added, long target) {
            return canIgnoreInFrameBox(framePos);
        }

        @Override
        protected boolean finish(long added, long target) {
            if (canIgnoreInFrameBox(framePos)) {
                return false;
            }
            level.setBlockAndUpdate(framePos, BCBuildersBlocks.FRAME.get().defaultBlockState());
            return true;
        }
    }

    public class TaskMoveDrill extends Task {
        public Vec3 from = Vec3.ZERO;
        public Vec3 to = Vec3.ZERO;

        TaskMoveDrill() {}

        TaskMoveDrill(Vec3 from, Vec3 to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public long getTarget() {
            return (long) (from.distanceTo(to) * 20 * MjAPI.MJ);
        }

        @Override
        public long getRequiredPowerThisTick() {
            long req = Math.max(0, getTarget() - power);

            double max = BCBuildersConfig.quarryMaxFrameMoveSpeed.get();
            if (max < 0.1) {
                return req;
            }
            max /= 20;
            max -= moveDistanceSoFar;
            if (max <= 0) {
                return 0;
            }
            return Math.min(req, (long) (max * 20 * MjAPI.MJ));
        }

        @Override
        protected boolean onReceivePower(long added, long target) {
            moveDistanceSoFar += added / (double) MjAPI.MJ;
            drillPos = from.scale(1 - power / (double) target).add(to.scale(power / (double) target));
            return false;
        }

        @Override
        protected boolean finish(long added, long target) {
            moveDistanceSoFar += added / (double) MjAPI.MJ;
            drillPos = to;
            return true;
        }
    }
}
