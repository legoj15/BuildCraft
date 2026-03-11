/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;

import buildcraft.core.BCCoreConfig;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;

/**
 * Mining well tile entity. Drills straight down one block at a time,
 * breaking blocks and ejecting drops to adjacent inventories.
 * Ported from 1.12.2 TileMiningWell.
 */
public class TileMiningWell extends TileMiner {
    private boolean shouldCheck = true;
    private int recheckCooldown = 0;

    // Lazily created receiver to avoid caching issues
    private IMjReceiver mjReceiver;

    public TileMiningWell(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.MINING_WELL.get(), pos, state);
    }

    @Override
    protected void mine() {
        if (currentPos != null && canBreak()) {
            shouldCheck = true;
            long target = BlockUtil.computeBlockBreakPower(level, currentPos);
            progress += battery.extractPower(0, target - progress);
            if (progress >= target) {
                progress = 0;
                level.destroyBlockProgress(currentPos.hashCode(), currentPos, -1);
                if (level instanceof ServerLevel serverLevel) {
                    BlockUtil.breakBlockAndGetDrops(
                        serverLevel,
                        currentPos,
                        new ItemStack(Items.DIAMOND_PICKAXE),
                        getOwner()
                    ).ifPresent(stacks ->
                        stacks.forEach(stack -> InventoryUtil.addToBestAcceptor(level, worldPosition, null, stack))
                    );
                }
                nextPos();
            } else {
                if (!level.isEmptyBlock(currentPos)) {
                    level.destroyBlockProgress(currentPos.hashCode(), currentPos, (int) ((progress * 9) / target));
                }
            }
        } else if (shouldCheck || recheckCooldown <= 0) {
            nextPos();
            if (currentPos == null) {
                shouldCheck = false;
            }
            recheckCooldown = 256; // ~12.8 seconds at 20 tps
        } else {
            recheckCooldown--;
        }
    }

    private boolean canBreak() {
        if (level.isEmptyBlock(currentPos) || BlockUtil.isUnbreakableBlock(level, currentPos, getOwner())) {
            return false;
        }

        Fluid fluid = BlockUtil.getFluidWithFlowing(level, currentPos);
        // In 1.21 we don't have getViscosity directly; water has viscosity 1000.
        // Skip high-viscosity fluids (lava = 6000). Water (1000) is fine.
        // For simplicity: if it's a fluid, skip it (mining well shouldn't mine fluids)
        return fluid == null;
    }

    private void nextPos() {
        currentPos = worldPosition;
        while (true) {
            currentPos = currentPos.below();
            if (level.isOutsideBuildHeight(currentPos)) {
                break;
            }
            if (worldPosition.getY() - currentPos.getY() > BCCoreConfig.miningMaxDepth) {
                break;
            }
            if (canBreak()) {
                updateLength();
                return;
            } else if (!level.isEmptyBlock(currentPos) && !level.getBlockState(currentPos).is(BCFactoryBlocks.TUBE.get())) {
                break;
            }
        }
        currentPos = null;
        updateLength();
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            onRemove();
            if (currentPos != null) {
                level.destroyBlockProgress(currentPos.hashCode(), currentPos, -1);
            }
        }
        super.setRemoved();
    }

    @Override
    protected IMjReceiver createMjReceiver() {
        if (mjReceiver == null) {
            mjReceiver = new IMjReceiver() {
                @Override
                public long getPowerRequested() {
                    return battery.getCapacity() - battery.getStored();
                }

                @Override
                public long receivePower(long microJoules, boolean simulate) {
                    return battery.addPowerChecking(microJoules, simulate);
                }

                @Override
                public boolean canConnect(@Nonnull IMjConnector other) {
                    return true;
                }
            };
        }
        return mjReceiver;
    }
}
