/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.redstone.Orientation;
//?}

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.tile.TileMiner;
import buildcraft.factory.tile.TilePump;
import buildcraft.lib.block.BlockBCTile_Neptune;

/**
 * The pump block. Unlike the mining well, the pump has no directional facing —
 * it simply pumps fluids from below.
 * Ported from 1.12.2 BlockPump.
 */
public class BlockPump extends BlockBCTile_Neptune<TilePump> {
    //? if <26.3 {
    /*public static final MapCodec<BlockPump> CODEC =
            simpleCodec(BlockPump::new);
    *///?}

    public BlockPump(Properties properties) {
        super(properties);
    }

    //? if <26.3 {
    /*@Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    *///?}

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePump(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCFactoryBlockEntities.PUMP.get();
    }

    @Override
    protected BlockEntityTicker<TilePump> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.serverTick();
    }

    @Override
    protected BlockEntityTicker<TilePump> getClientTicker() {
        return (lvl, pos, st, tile) -> tile.clientTick();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    //? if >=1.21.10 {
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            @Nullable Orientation orientation, boolean movedByPiston) {
    //?} else {
    /*protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {*/
    //?}
        //? if >=1.21.10 {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        //?} else {
        /*super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);*/
        //?}
        // An idle pump backs its flood-fill rebuilds off; a block changing beside it makes it re-check its column
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TilePump pump) {
            pump.wake(); // (TilePump#wake)
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.player.Player player) {
        // Clean up tubes below when the pump is explicitly broken, then drop the
        // pump's internal fluid tank as fragile fluid-shard items so accumulated
        // pumped fluid isn't lost on break.
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TilePump pump) {
                pump.onRemove();
                buildcraft.lib.misc.BlockDropsUtil.dropFluidShards(level, pos, pump.getTank());
                pump.markDropsHandled();
            } else if (be instanceof TileMiner miner) {
                miner.onRemove();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
