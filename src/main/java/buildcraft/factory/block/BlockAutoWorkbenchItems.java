/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.factory.tile.TileAutoWorkbenchItems;

public class BlockAutoWorkbenchItems extends BaseEntityBlock {
    public static final MapCodec<BlockAutoWorkbenchItems> CODEC =
            simpleCodec(BlockAutoWorkbenchItems::new);

    public BlockAutoWorkbenchItems(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileAutoWorkbenchItems(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
                (lvl, pos, st, tile) -> tile.serverTick());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @org.jetbrains.annotations.Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileAutoWorkbenchItems workbench) {
                workbench.onPlacedBy(placer, stack);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileAutoWorkbenchItems workbench && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                new SimpleMenuProvider(
                    (containerId, playerInv, p) -> new ContainerAutoCraftItems(containerId, playerInv, workbench),
                    Component.translatable("tile.autoWorkbenchBlock.name")
                ),
                buf -> buf.writeBlockPos(pos)
            );
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Drops the materials grid and the result slot. The blueprint and material-filter slots
     *  are registered as PHANTOM in TileAutoWorkbenchBase — they never held real items, so
     *  ItemHandlerManager.addDrops correctly skips them. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileAutoWorkbenchItems workbench) {
            buildcraft.lib.misc.BlockDropsUtil.dropTileContents(level, pos, workbench);
            workbench.markDropsHandled();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Non-player removal catch-all for the pre-1.21.10 API (explosion, piston, /setblock, mod tools):
    // spill contents while the BlockEntity is alive in onRemove. On >=1.21.10 this is handled centrally
    // by TileBC_Neptune#preRemoveSideEffects. The player path set the guard in playerWillDestroy.
    //? if <1.21.10 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof buildcraft.lib.tile.TileBC_Neptune tile) {
            tile.dropContentsOnRemoval(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }*/
    //?}
}
