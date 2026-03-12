/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;

import buildcraft.core.BCCoreConfig;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.mj.MjRedstoneBatteryReceiver;

/**
 * Pump tile entity. Searches downward for fluids, then uses BFS flood-fill to
 * find connected source blocks and drains them using MJ power.
 * Ported from 1.12.2 TilePump.
 */
public class TilePump extends TileMiner {

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

    private final FluidTank tank = new FluidTank(16 * 1000); // 16 buckets
    private boolean queueBuilt = false;
    private final Map<BlockPos, FluidPath> paths = new HashMap<>();
    private BlockPos fluidConnection;
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private boolean isInfiniteWaterSource;
    private int rebuildDelay = 0;

    /** The position just below the bottom of the pump tube. */
    private BlockPos targetPos;

    public TilePump(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.PUMP.get(), pos, state);
    }

    @Override
    protected IMjReceiver createMjReceiver() {
        return new MjRedstoneBatteryReceiver(battery);
    }

    public FluidTank getTank() {
        return tank;
    }

    // --- Queue Building (BFS flood-fill) ---

    private void buildQueue() {
        queue.clear();
        paths.clear();
        Fluid queueFluid = null;
        isInfiniteWaterSource = false;
        Set<BlockPos> checked = new HashSet<>();
        List<BlockPos> nextPosesToCheck = new ArrayList<>();

        for (targetPos = worldPosition.below(); !level.isOutsideBuildHeight(targetPos); targetPos = targetPos.below()) {
            if (worldPosition.getY() - targetPos.getY() > BCCoreConfig.miningMaxDepth) {
                break;
            }
            if (BlockUtil.getFluidWithFlowing(level, targetPos) != null) {
                queueFluid = BlockUtil.getFluidWithFlowing(level, targetPos);
                nextPosesToCheck.add(targetPos);
                paths.put(targetPos, new FluidPath(targetPos, null));
                checked.add(targetPos);
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

    private void buildQueue0(Fluid queueFluid, List<BlockPos> nextPosesToCheck, Set<BlockPos> checked) {
        // In 1.21 we don't have Fluid.isGaseous() directly. For now, assume normal search.
        // TODO: add gaseous fluid detection if needed
        Direction[] directions = SEARCH_NORMAL;
        boolean isWater = !BCCoreConfig.pumpsConsumeWater
                && FluidUtilBC.areFluidsEqual(queueFluid, Fluids.WATER);
        final int maxLengthSquared = BCCoreConfig.pumpMaxDistance * BCCoreConfig.pumpMaxDistance;

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
                    boolean isNew = checked.add(offsetPos);
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
    }

    private boolean canDrain(BlockPos blockPos) {
        Fluid fluid = BlockUtil.getFluid(level, blockPos);
        if (tank.isEmpty()) {
            return fluid != null;
        }
        return FluidUtilBC.areFluidsEqual(fluid, tank.getFluid().getFluid());
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
        if (queue.isEmpty()) {
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
        if (tank.getFluidAmount() > tank.getCapacity() / 2) {
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

                tank.fill(drain, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                progress = 0;

                isInfiniteWaterSource &= !BCCoreConfig.pumpsConsumeWater;
                if (isInfiniteWaterSource) {
                    isInfiniteWaterSource = FluidUtilBC.areFluidsEqual(drain.getFluid(), Fluids.WATER);
                }

                if (!isInfiniteWaterSource) {
                    BlockUtil.drainBlock(level, currentPos, true);
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
        // Oil spring pos would be saved here once oil springs are ported
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Oil spring pos would be loaded here once oil springs are ported
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
