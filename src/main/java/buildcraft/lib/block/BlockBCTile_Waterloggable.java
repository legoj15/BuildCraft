/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
//? if >=1.21.10 {
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ScheduledTickAccess;
//?} else {
/*import net.minecraft.world.level.LevelAccessor;*/
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;

import buildcraft.lib.tile.AbstractBCBlockEntity;

/**
 * A {@link BlockBCTile_Neptune} that waterlogs ({@link BCWaterlogging}): for machines whose model does not
 * fill the cell, so that one set out underwater stands in the water instead of in an air pocket.
 *
 * <p>Subclasses that define their own state call {@code super.createBlockStateDefinition}, build their
 * default from {@code defaultBlockState()} — never {@code stateDefinition.any()}, whose boolean properties
 * come out {@code true}, i.e. waterlogged — and pass their own {@code getStateForPlacement} result through
 * {@link BCWaterlogging#placementState(BlockState, BlockPlaceContext)}. Neighbour-driven state goes in
 * {@link #updateNeighbourShape}, not {@code updateShape}, which this class owns to keep the water ticking.
 */
@SuppressWarnings("this-escape")
public abstract class BlockBCTile_Waterloggable<T extends AbstractBCBlockEntity> extends BlockBCTile_Neptune<T>
        implements SimpleWaterloggedBlock {

    protected BlockBCTile_Waterloggable(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BCWaterlogging.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BCWaterlogging.WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return BCWaterlogging.placementState(defaultBlockState(), context);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return BCWaterlogging.fluidState(state);
    }

    @Override
    //? if >=1.21.10 {
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        BCWaterlogging.tickContainedWater(state, scheduledTickAccess, level, pos);
        return updateNeighbourShape(state, level, pos, direction, neighborState);
    }
    //?} else {
    /*protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BCWaterlogging.tickContainedWater(state, level, pos);
        return updateNeighbourShape(state, level, pos, direction, neighborState);
    }*/
    //?}

    /** The version-neutral {@code updateShape}: {@code state} after the neighbour on {@code direction}
     *  changed to {@code neighborState}. The contained water has already been ticked. */
    protected BlockState updateNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
            BlockState neighborState) {
        return state;
    }
}
