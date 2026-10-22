/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * A test-only stand-in for a modded water plant: a waterloggable block that lives only while it holds water. Vanilla's
 * {@code SimpleWaterloggedBlock.pickupBlock} dries such a block and, for one whose survival test fails, destroys it
 * (with drops); no vanilla waterloggable block fails that test once dry, so this is the only way to reach the case
 * from {@code BlockUtil.drainBlock}. Registered by {@link TestOnlyBlocks}.
 */
@SuppressWarnings("this-escape")
public final class DryFragileBlock extends Block implements SimpleWaterloggedBlock {

    public DryFragileBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.WATERLOGGED);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED)
                ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    /** Alive only while waterlogged. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(BlockStateProperties.WATERLOGGED);
    }
}
