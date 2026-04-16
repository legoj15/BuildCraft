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

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;

import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreConfig;
import buildcraft.core.tile.ITileOilSpring;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.mj.MjRedstoneBatteryReceiver;
import buildcraft.api.tiles.IDebuggable;

/**
 * Pump tile entity. Searches downward for fluids, then uses BFS flood-fill to
 * find connected source blocks and drains them using MJ power.
 * Ported from 1.12.2 TilePump.
 */
public class TilePump extends TileMiner implements IDebuggable {

    private static final Identifier ADVANCEMENT_DRAIN_ANY
        = Identifier.parse("buildcraftunofficial:draining_the_world");
    private static final Identifier ADVANCEMENT_DRAIN_OIL
        = Identifier.parse("buildcraftunofficial:oil_platform");

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

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 16 * 1000); // 16 buckets
    private boolean queueBuilt = false;
    private final Map<BlockPos, FluidPath> paths = new HashMap<>();
    private BlockPos fluidConnection;
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private boolean isInfiniteWaterSource;
    private int rebuildDelay = 0;

    /** The position just below the bottom of the pump tube. */
    private BlockPos targetPos;

    @Nullable
    private BlockPos oilSpringPos;

    public TilePump(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.PUMP.get(), pos, state);
    }

    @Override
    protected IMjReceiver createMjReceiver() {
        return new MjRedstoneBatteryReceiver(battery);
    }

    public FluidStacksResourceHandler getTank() {
        return tank;
    }

    // --- Queue Building (BFS flood-fill) ---

    private void buildQueue() {
        queue.clear();
        paths.clear();
        Fluid queueFluid = null;
        isInfiniteWaterSource = false;
        LongSet checked = new LongOpenHashSet();
        List<BlockPos> nextPosesToCheck = new ArrayList<>();

        for (targetPos = worldPosition.below(); !level.isOutsideBuildHeight(targetPos); targetPos = targetPos.below()) {
            if (worldPosition.getY() - targetPos.getY() > BCCoreConfig.miningMaxDepth.get()) {
                break;
            }
            if (BlockUtil.getFluidWithFlowing(level, targetPos) != null) {
                queueFluid = BlockUtil.getFluidWithFlowing(level, targetPos);
                nextPosesToCheck.add(targetPos);
                paths.put(targetPos, new FluidPath(targetPos, null));
                checked.add(targetPos.asLong());
                if (BlockUtil.getFluid(level, targetPos) != null) {
                    queue.add(targetPos);
                }
                fluidConnection = targetPos;
                break;
            }
            if (!level.isEmptyBlock(targetPos) && !level.getBlockState(targetPos).is(BCFactoryBlocks.TUBE.get())) {
                break;
            }
        }
        if (nextPosesToCheck.isEmpty() || queueFluid == null) {
            return;
        }

        buildQueue0(queueFluid, nextPosesToCheck, checked);
    }

    /** Returns true if the fluid is crude oil (any heat variant). */
    private static boolean isOil(Fluid fluid) {
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        // Covers "buildcraftunofficial:oil", "buildcraftunofficial:oil_heat_1", "buildcraftunofficial:oil_heat_2"
        return id.getNamespace().equals("buildcraftunofficial")
            && (id.getPath().equals("oil") || id.getPath().startsWith("oil_heat_"));
    }

    private void buildQueue0(Fluid queueFluid, List<BlockPos> nextPosesToCheck, LongSet checked) {
        Direction[] directions = FluidUtilBC.isGaseous(queueFluid) ? SEARCH_GASEOUS : SEARCH_NORMAL;
        boolean isWater = !BCCoreConfig.pumpsConsumeWater.get()
                && FluidUtilBC.areFluidsEqual(queueFluid, Fluids.WATER);
        final int maxLengthSquared = BCCoreConfig.pumpMaxDistance.get() * BCCoreConfig.pumpMaxDistance.get();

        outer:
        while (!nextPosesToCheck.isEmpty()) {
            List<BlockPos> nextPosesToCheckCopy = new ArrayList<>(nextPosesToCheck);
            nextPosesToCheck.clear();
            for (BlockPos posToCheck : nextPosesToCheckCopy) {
                int count = 0;
                for (Direction side : directions) {
                    BlockPos offsetPos = posToCheck.relative(side);
                    if (offsetPos.distSqr(targetPos) > maxLengthSquared) {
                        continue;
                    }
                    boolean isNew = checked.add(offsetPos.asLong());
                    if (isNew) {
                        Fluid fluidAt = BlockUtil.getFluidWithFlowing(level, offsetPos);
                        boolean eq = FluidUtilBC.areFluidsEqual(fluidAt, queueFluid);
                        if (eq) {
                            FluidPath oldPath = paths.get(posToCheck);
                            FluidPath path = new FluidPath(offsetPos, oldPath);
                            paths.put(offsetPos, path);
                            if (BlockUtil.getFluid(level, offsetPos) != null) {
                                queue.add(offsetPos);
                            }
                            nextPosesToCheck.add(offsetPos);
                            count++;
                        }
                    } else {
                        // We've already tested this block: it *must* be a valid water source
                        count++;
                    }
                }
                if (isWater) {
                    if (count >= 2) {
                        BlockState below = level.getBlockState(posToCheck.below());
                        Fluid fluidBelow = BlockUtil.getFluidWithFlowing(level, posToCheck.below());
                        if (FluidUtilBC.areFluidsEqual(fluidBelow, Fluids.WATER) || below.isSolid()) {
                            isInfiniteWaterSource = true;
                            break outer;
                        }
                    }
                }
            }
        }

        // Oil spring search — matches 1.12.2 logic
        if (isOil(queueFluid)) {
            List<BlockPos> springPositions = new ArrayList<>();
            BlockPos center = VecUtil.replaceValue(worldPosition, Axis.Y, 0);
            for (BlockPos spring : BlockPos.betweenClosed(center.offset(-10, 0, -10), center.offset(10, 0, 10))) {
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

    private boolean canDrain(BlockPos blockPos) {
        Fluid fluid = BlockUtil.getFluid(level, blockPos);
        if (tank.getAmountAsInt(0) == 0) {
            return fluid != null;
        }
        return FluidUtilBC.areFluidsEqual(fluid, tank.getResource(0).getFluid());
    }

    private void nextPos() {
        while (!queue.isEmpty()) {
            currentPos = queue.removeLast();
            if (canDrain(currentPos)) {
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
        if (queue.isEmpty() && currentPos == null) {
            return null;
        }
        return targetPos;
    }

    // --- Ticking ---

    @Override
    public void serverTick() {
        if (!queueBuilt) {
            buildQueue();
            queueBuilt = true;
        }

        super.serverTick();

        FluidUtilBC.pushFluidToNeighbors(level, worldPosition, tank);
    }

    @Override
    protected void mine() {
        if (tank.getAmountAsInt(0) > tank.getCapacityAsInt(0, FluidResource.EMPTY) / 2) {
            return;
        }

        if (rebuildDelay > 0) {
            rebuildDelay--;
        }

        long target = 10 * MjAPI.MJ;
        if (currentPos != null && paths.containsKey(currentPos)) {
            progress += battery.extractPower(0, target - progress);
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

                try (Transaction tx = Transaction.openRoot()) {
                    tank.insert(0, FluidResource.of(drain), drain.getAmount(), tx);
                    tx.commit();
                }
                progress = 0;

                if (getOwner() != null) {
                    AdvancementUtil.unlockAdvancement(getOwner().id(), level, ADVANCEMENT_DRAIN_ANY);
                }

                isInfiniteWaterSource &= !BCCoreConfig.pumpsConsumeWater.get();
                if (isInfiniteWaterSource) {
                    isInfiniteWaterSource = FluidUtilBC.areFluidsEqual(drain.getFluid(), Fluids.WATER);
                }

                if (!isInfiniteWaterSource) {
                    BlockUtil.drainBlock(level, currentPos, true);
                    if (isOil(drain.getFluid())) {
                        if (getOwner() != null) {
                            AdvancementUtil.unlockAdvancement(getOwner().id(), level, ADVANCEMENT_DRAIN_OIL);
                        }
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

            if (rebuildDelay > 0) {
                return;
            }
            rebuildDelay = 30;
        } else {
            if (currentPos == null && rebuildDelay > 0) {
                return;
            }
            rebuildDelay = 30;
        }
        buildQueue();
        nextPos();
    }

    @Nullable
    private BlockPos getFirstInvalidPointOnPath(BlockPos from) {
        FluidPath path = paths.get(from);
        if (path == null) {
            return from;
        }
        do {
            if (BlockUtil.getFluidWithFlowing(level, path.thisPos) == null) {
                return path.thisPos;
            }
        } while ((path = path.parent) != null);
        return null;
    }

    // --- Save / Load ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output);
        if (oilSpringPos != null) {
            output.putBoolean("hasOilSpring", true);
            output.putInt("oilSpringX", oilSpringPos.getX());
            output.putInt("oilSpringY", oilSpringPos.getY());
            output.putInt("oilSpringZ", oilSpringPos.getZ());
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input);
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
        left.add("fluid = " + FluidUtilBC.getDebugString(tank.getResource(0).toStack(tank.getAmountAsInt(0))));
        left.add("queue size = " + queue.size());
        left.add("infinite = " + isInfiniteWaterSource);
    }

    @Override
    public void getClientDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("current = " + currentPos);
        left.add("wantedLength = " + wantedLength);
        left.add("currentLength = " + currentLength);
        left.add("isComplete = " + isComplete());
        left.add("progress = " + MjAPI.formatMj((long) progress));
        left.add("fluid = " + FluidUtilBC.getDebugString(tank.getResource(0).toStack(tank.getAmountAsInt(0))));
        left.add("queue size = " + queue.size());
        left.add("infinite = " + isInfiniteWaterSource);
    }

    // --- Cleanup ---

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
