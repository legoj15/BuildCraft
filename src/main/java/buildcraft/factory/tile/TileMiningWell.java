/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

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
                    // Iron pickaxe matches the recipe ingredient. Combined with the
                    // NEEDS_DIAMOND_TOOL gate in canBreak(), the well stops at obsidian /
                    // ancient debris / netherite-tier blocks rather than chewing through
                    // them yielding nothing — the loot context's match_tool condition
                    // would otherwise fail and the block would destroy without drops.
                    // breakBlockAndGetDropsWithXp captures the BlockEntity + BlockState
                    // BEFORE destroying so XP / container-drops are computed correctly.
                    BlockUtil.breakBlockAndGetDropsWithXp(
                        serverLevel,
                        currentPos,
                        new ItemStack(Items.IRON_PICKAXE),
                        getOwner()
                    ).ifPresent(result -> {
                        result.drops().forEach(stack ->
                            InventoryUtil.addToBestAcceptor(level, worldPosition, null, stack));
                        // Spawn XP at the well itself rather than the broken position. The orb
                        // is only ever created at worldPosition — no "spawn-then-teleport"
                        // tick-boundary window for duplication. popExperience uses
                        // ExperienceOrb.award which splits the integer XP into multiple
                        // appropriately-sized orbs and respects BLOCK_DROPS / restoringBlockSnapshots.
                        if (result.xp() > 0) {
                            getBlockState().getBlock().popExperience(serverLevel, worldPosition, result.xp());
                        }
                    });
                }
                nextPos();
            } else {
                if (!level.isEmptyBlock(currentPos)) {
                    level.destroyBlockProgress(currentPos.hashCode(), currentPos, (int) ((progress * 9) / target));
                }
            }
        } else if (currentPos != null && !canBreak()) {
            // Current target is no longer breakable (already mined, became fluid, etc.)
            // Reset progress and immediately look for the next target
            progress = 0;
            nextPos();
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

        // Iron-pickaxe tier limit. Anything with NEEDS_DIAMOND_TOOL (obsidian, ancient
        // debris, netherite-family blocks, crying obsidian, respawn anchor, etc.) is
        // refused — nextPos() then falls into its "impassable, stop" branch, mirroring
        // how the well stops at lava/oil. Without this the well would drill through
        // obsidian leaving no drops (the loot table's match_tool condition fails for
        // anything below diamond-tier), which is misleading to the player.
        BlockState state = level.getBlockState(currentPos);
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return false;
        }

        Fluid fluid = BlockUtil.getFluidWithFlowing(level, currentPos);
        if (fluid == null) {
            return true; // Not a fluid, can break
        }
        // Match 1.12.2: allow mining low-viscosity fluids (water, light fuel, etc.)
        // but block high-viscosity fluids (lava, oil, etc.)
        return fluid.getFluidType().getViscosity() <= 1000;
    }

    private void nextPos() {
        currentPos = worldPosition;
        while (true) {
            currentPos = currentPos.below();
            if (level.isOutsideBuildHeight(currentPos)) {
                break;
            }
            if (worldPosition.getY() - currentPos.getY() > BCCoreConfig.miningMaxDepth.get()) {
                break;
            }
            if (canBreak()) {
                // Honor third-party player-protection mods. If the owner is not allowed to
                // break this position, skip past it instead of stopping — claims are usually
                // small and there may be unclaimed terrain further down.
                if (level instanceof ServerLevel serverLevel
                        && !BlockUtil.canMachineBreak(serverLevel, currentPos, getOwner())) {
                    continue;
                }
                updateLength();
                return;
            }
            // Skip air, tubes, and low-viscosity fluids (water, light fuel, etc.)
            FluidState fluidState = level.getFluidState(currentPos);
            boolean isPassable = !fluidState.isEmpty()
                    && fluidState.getFluidType().getViscosity() <= 1000;
            if (level.isEmptyBlock(currentPos)
                    || level.getBlockState(currentPos).is(BCFactoryBlocks.TUBE.get())
                    || isPassable) {
                continue;
            } else {
                // Hit an impassable block (bedrock, lava, oil, etc.) — stop
                break;
            }
        }
        currentPos = null;
        updateLength();
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            // Only clear break progress overlay — do NOT remove tubes here.
            // Tubes are removed by onRemove() which is called only when the
            // mining well block itself is explicitly broken (not on chunk unload).
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
