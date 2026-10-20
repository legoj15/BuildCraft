/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
//? if >=1.21.10 {
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
//?} else {
/*import net.minecraft.world.level.LevelAccessor;*/
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.factory.tile.TileMiner;
import buildcraft.lib.block.BCWaterlogging;

/**
 * The tube block placed below a mining well / quarry as it drills down.
 * Unbreakable and invisible — its visuals are handled by the pump's laser
 * renderer (RenderPump). The block only provides collision.
 *
 * <p>Waterloggable, so a bore drilled through a pond keeps the pond's water inside the tube instead of
 * overwriting it, and a retracting tube leaves the water source back (see {@link #stateFor}). Shared pattern:
 * {@link BCWaterlogging}.
 */
@SuppressWarnings("this-escape")
public class BlockTube extends Block implements SimpleWaterloggedBlock {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    //? if <26.3 {
    public static final MapCodec<BlockTube> CODEC = simpleCodec(BlockTube::new);
    //?}

    public BlockTube(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BCWaterlogging.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BCWaterlogging.WATERLOGGED);
    }

    /** The tube state to put into the cell at {@code pos}: waterlogged when that cell currently holds a water
     *  source, so the miner replacing the water with a tube does not delete it. */
    public BlockState stateFor(BlockGetter level, BlockPos pos) {
        return BCWaterlogging.placementState(defaultBlockState(), level, pos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return BCWaterlogging.fluidState(state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return BCWaterlogging.placementState(defaultBlockState(), context);
    }

    // Keep the contained water flowing/levelling when neighbours change (standard SimpleWaterloggedBlock pattern).
    @Override
    //? if >=1.21.10 {
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            net.minecraft.util.RandomSource random) {
        BCWaterlogging.tickContainedWater(state, ticks, level, pos);
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }
    //?} else {
    /*protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
            LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BCWaterlogging.tickContainedWater(state, level, pos);
        return super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
    }*/
    //?}

    //? if <26.3 {
    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Invisible — the pump's BER renders the tube as a laser beam
        return RenderShape.INVISIBLE;
    }

    @Override
    //? if >=1.21.10 {
    protected boolean propagatesSkylightDown(BlockState state) {
    //?} else {
    /*protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {*/
    //?}
        return true;
    }

    @Override
    //? if >=1.21.10 {
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.item.ItemStack toolStack, boolean willHarvest,
            net.minecraft.world.level.material.FluidState fluid) {
    //?} else {
    /*public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {*/
    //?}
        // Walk upwards to find if a TileMiner is above this tube column
        BlockPos checkPos = pos;
        while (level.getBlockState(checkPos = checkPos.above()).getBlock() == this) {
            // keep going up
        }
        if (level.getBlockEntity(checkPos) instanceof TileMiner) {
            // Don't allow breaking — the miner owns this tube
            return false;
        }
        //? if >=1.21.10 {
        return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
        //?} else {
        /*return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);*/
        //?}
    }
}
