/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.lib.block.BlockBCTile_Neptune;

public class BlockAutoWorkbenchItems extends BlockBCTile_Neptune<TileAutoWorkbenchItems> {
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

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get();
    }

    @Override
    protected BlockEntityTicker<TileAutoWorkbenchItems> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.serverTick();
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
}
