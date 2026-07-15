/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.tile.AbstractBCBlockEntity;

/**
 * Shared bodies for the two BuildCraft block bases — {@link BlockBCTile_Neptune} (a
 * {@code BaseEntityBlock}) and {@link BlockBCTile_Directional} (a {@code HorizontalDirectionalBlock}).
 * Java single inheritance forces the pair to have different superclasses, so the common skeleton
 * (owner placement, standard menu-open, the non-player-removal drop catch-all, and ticker matching)
 * lives here as static helpers that both bases delegate to, keeping them thin and identical in surface.
 */
final class BlockBCTileSupport {
    private BlockBCTileSupport() {}

    /**
     * Records the placing player as the tile's owner (server-side only — owner attribution is server
     * state; {@code onPlacedBy} only acts for a {@code Player} anyway). Unifies the 17 hand-copied
     * block-side forwards (some client-guarded, some not) onto one server-guarded call.
     */
    static void placeOwner(Level level, BlockPos pos, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AbstractBCBlockEntity tile) {
            tile.onPlacedBy(placer, stack);
        }
    }

    /**
     * Standard menu-open: if the tile is an {@link IBCMenuProvider}, open it (server-side) and report
     * SUCCESS on both sides; otherwise PASS (a block with no GUI keeps vanilla's default behaviour).
     * Opening via the one-arg {@code openMenu(tile)} lets {@code writeClientSideData} supply the pos.
     */
    static InteractionResult openTileMenu(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof IBCMenuProvider provider) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(provider);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * The {@code <1.21.10} non-player-removal drop catch-all body (spill contents while the block
     * entity is still alive). Safe to call for every tile: {@link AbstractBCBlockEntity#dropContentsOnRemoval}
     * is a no-op unless the tile opts in (so it is byte-identical for non-spilling tiles).
     */
    static void dropOnRemove(BlockState state, Level level, BlockPos pos, BlockState newState) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AbstractBCBlockEntity tile) {
            tile.dropContentsOnRemoval(level, pos);
        }
    }

    /**
     * The standardised {@code getTicker} body (the {@code createTickerHelper} pattern, but usable by the
     * directional base too since that isn't a {@code BaseEntityBlock}). Returns null unless {@code type}
     * matches the block's own BE type, then the per-side ticker (or null when the tile doesn't tick on
     * that side).
     */
    @SuppressWarnings("unchecked")
    @Nullable
    static <U extends BlockEntity, V extends BlockEntity> BlockEntityTicker<U> ticker(
            Level level, BlockEntityType<U> type, @Nullable BlockEntityType<?> expected,
            @Nullable BlockEntityTicker<V> serverTicker, @Nullable BlockEntityTicker<V> clientTicker) {
        if (expected == null || type != expected) {
            return null;
        }
        return (BlockEntityTicker<U>) (level.isClientSide() ? clientTicker : serverTicker);
    }
}
