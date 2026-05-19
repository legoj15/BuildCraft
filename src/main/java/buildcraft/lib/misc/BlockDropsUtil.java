/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.api.items.FluidItemDrops;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Shared helpers for the inventory side of {@code playerWillDestroy}.
 * <p>
 * The block self-drop is handled by the loot table + {@code requiresCorrectToolForDrops}
 * (no drop with a bare hand, drops with a pickaxe). These helpers pop the <em>contents</em>
 * which should always be returned to the player regardless of tool — losing them feels
 * like a bug to anyone who's been on the receiving end of an accidental hand-break.
 * <p>
 * Ghost / template / filter slots (registered as {@code EnumAccess.PHANTOM}) are skipped
 * automatically by {@link TileBC_Neptune#addDrops(NonNullList, int)} because
 * {@link buildcraft.lib.tile.item.ItemHandlerManager} only collects non-phantom handlers
 * into its drop list. The raw-handler overloads below intentionally take only "real"
 * inventories — callers must not pass ghost slots in.
 */
public final class BlockDropsUtil {
    private BlockDropsUtil() {}

    /** Drop everything registered with the tile's ItemHandlerManager (skipping PHANTOM
     *  slots automatically) plus any extra fluid tanks as fragile fluid-shard items. */
    @SafeVarargs
    public static void dropTileContents(Level level, BlockPos pos, TileBC_Neptune tile,
            ResourceHandler<FluidResource>... fluidTanks) {
        if (level.isClientSide()) {
            return;
        }
        NonNullList<ItemStack> toDrop = NonNullList.create();
        tile.addDrops(toDrop, 0);
        if (fluidTanks != null && fluidTanks.length > 0) {
            FluidItemDrops.addFluidDrops(toDrop, fluidTanks);
        }
        for (ItemStack drop : toDrop) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    /** Drop each non-empty stack from raw item handlers — for tiles that don't use
     *  {@link buildcraft.lib.tile.item.ItemHandlerManager}. */
    public static void dropItems(Level level, BlockPos pos, ItemHandlerSimple... handlers) {
        if (level.isClientSide() || handlers == null) {
            return;
        }
        for (ItemHandlerSimple handler : handlers) {
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    Block.popResource(level, pos, stack);
                }
            }
        }
    }

    /** Pop a single ItemStack if non-empty. Convenience for tiles holding loose
     *  ItemStacks (e.g. the Stirling engine's fuel slot). */
    public static void dropStack(Level level, BlockPos pos, ItemStack stack) {
        if (!level.isClientSide() && stack != null && !stack.isEmpty()) {
            Block.popResource(level, pos, stack);
        }
    }

    /** Drop fluid tanks as fragile fluid-shard items. */
    @SafeVarargs
    public static void dropFluidShards(Level level, BlockPos pos,
            ResourceHandler<FluidResource>... tanks) {
        if (level.isClientSide() || tanks == null || tanks.length == 0) {
            return;
        }
        NonNullList<ItemStack> toDrop = NonNullList.create();
        FluidItemDrops.addFluidDrops(toDrop, tanks);
        for (ItemStack drop : toDrop) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
    }
}
