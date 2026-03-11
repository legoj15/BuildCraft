/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.factory.tile.TileMiner;

/**
 * The tube block placed below a mining well / quarry as it drills down.
 * Unbreakable and non-opaque. If a player tries to break it while a
 * TileMiner exists above, the break is prevented.
 */
public class BlockTube extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public static final MapCodec<BlockTube> CODEC = simpleCodec(BlockTube::new);

    public BlockTube(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Walk upwards to find if a TileMiner is above this tube column
        BlockPos checkPos = pos;
        while (level.getBlockState(checkPos = checkPos.above()).getBlock() == this) {
            // keep going up
        }
        if (level.getBlockEntity(checkPos) instanceof TileMiner) {
            // Don't allow breaking — the miner owns this tube
            // Return without calling super to skip the break
            return state;
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
