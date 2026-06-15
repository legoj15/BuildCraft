/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.tile.TileMiner;
import buildcraft.factory.tile.TilePump;

/**
 * The pump block. Unlike the mining well, the pump has no directional facing —
 * it simply pumps fluids from below.
 * Ported from 1.12.2 BlockPump.
 */
public class BlockPump extends BaseEntityBlock {
    public static final MapCodec<BlockPump> CODEC =
            simpleCodec(BlockPump::new);

    public BlockPump(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePump(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, BCFactoryBlockEntities.PUMP.get(),
                    (lvl, pos, st, tile) -> tile.clientTick());
        }
        return createTickerHelper(type, BCFactoryBlockEntities.PUMP.get(),
                (lvl, pos, st, tile) -> tile.serverTick());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TilePump pump) {
                pump.onPlacedBy(placer, stack);
            }
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

    // Non-player removal catch-all for the pre-1.21.10 API (explosion, piston, /setblock, mod tools):
    // spill the pump's tank while the BlockEntity is alive in onRemove. On >=1.21.10 this is handled
    // centrally by TileBC_Neptune#preRemoveSideEffects. The player path set the guard in playerWillDestroy.
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
