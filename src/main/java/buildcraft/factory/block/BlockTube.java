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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.factory.tile.TileMiner;

/**
 * The tube block placed below a mining well / quarry as it drills down.
 * Unbreakable and invisible — its visuals are handled by the pump's laser
 * renderer (RenderPump). The block only provides collision.
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
    protected RenderShape getRenderShape(BlockState state) {
        // Invisible — the pump's BER renders the tube as a laser beam
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.item.ItemStack toolStack, boolean willHarvest,
            net.minecraft.world.level.material.FluidState fluid) {
        // Walk upwards to find if a TileMiner is above this tube column
        BlockPos checkPos = pos;
        while (level.getBlockState(checkPos = checkPos.above()).getBlock() == this) {
            // keep going up
        }
        if (level.getBlockEntity(checkPos) instanceof TileMiner) {
            // Don't allow breaking — the miner owns this tube
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
    }
}
