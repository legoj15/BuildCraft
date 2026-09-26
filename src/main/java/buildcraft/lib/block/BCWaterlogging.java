/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
//? if >=1.21.10 {
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
//?} else {
/*import net.minecraft.world.level.LevelAccessor;*/
//?}
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * The shared body of every BuildCraft {@code SimpleWaterloggedBlock}.
 *
 * <p><b>Why a block needs this.</b> Vanilla flowing water — and an emptied water bucket — destroys any
 * block that is not "solid", a legacy heuristic on the static collision shape (bounding box average edge
 * length at least ~0.73, or a full block tall; a {@code dynamicShape} block is never solid). Implementing
 * {@code SimpleWaterloggedBlock} makes the block a {@code LiquidBlockContainer}, so water takes the
 * coexist branch instead: a water source waterlogs the block, flowing water is held back, and lava or oil
 * (which {@code canPlaceLiquid} refuses) are held back too. The {@code bc_blocks_not_washed_away_by_water}
 * game test sweeps the block registry for any BuildCraft block that still is not.
 *
 * <p><b>The pattern</b> (see {@code BlockPipeHolder} / {@code BlockMarkerBase}): implement
 * {@code SimpleWaterloggedBlock}; add {@link #WATERLOGGED} in {@code createBlockStateDefinition} and
 * default it to false; route {@code getStateForPlacement} through {@link #placementState},
 * {@code getFluidState} through {@link #fluidState}, and call {@link #tickContainedWater} from
 * {@code updateShape}. Blockstate JSONs need no change as long as their variant keys don't list every
 * property — a {@code ""} key or a partial key like {@code "facing=up"} matches both waterlogged values.
 * A block with a hand-built model swap keyed by one exact state must swap every state (see
 * {@code BCTransportClient#onModifyBakingResult}).
 */
public final class BCWaterlogging {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private BCWaterlogging() {}

    /** {@code state} with {@link #WATERLOGGED} set when it is being placed into a water source, so placing
     *  the block underwater keeps the water instead of deleting it. */
    public static BlockState placementState(BlockState state, BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return state.setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    /** The {@code getFluidState} body: a still water source while waterlogged, otherwise no fluid. */
    public static FluidState fluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    /** The {@code updateShape} hook: keep the contained water flowing and levelling when a neighbour
     *  changes (the standard {@code SimpleWaterloggedBlock} tick). */
    //? if >=1.21.10 {
    public static void tickContainedWater(BlockState state, ScheduledTickAccess ticks, LevelReader level, BlockPos pos) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
    }
    //?} else {
    /*public static void tickContainedWater(BlockState state, LevelAccessor level, BlockPos pos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
    }*/
    //?}
}
