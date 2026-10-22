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
 * A test-only waterloggable block whose survival test depends only on whether it is waterlogged, standing in for a
 * modded block. Registered by {@link TestOnlyBlocks} twice:
 * <ul>
 * <li>{@code dry_fragile} ({@code aliveWhenWaterlogged = true}): a water plant that lives only while it holds
 * water. Vanilla's {@code SimpleWaterloggedBlock.pickupBlock} leaves it dry (its destroy check reads the state as it
 * was before the pickup, which survives), in a state it cannot hold.</li>
 * <li>{@code wet_fragile} ({@code aliveWhenWaterlogged = false}): a block whose waterlogged state fails its survival
 * test. That is exactly the case {@code pickupBlock} destroys (with drops) after drying it.</li>
 * </ul>
 * No vanilla waterloggable block fails either test while it stands supported, so this is the only way to reach both
 * cases from {@code BlockUtil.drainBlock}.
 */
@SuppressWarnings("this-escape")
public final class WaterSensitiveBlock extends Block implements SimpleWaterloggedBlock {

    private final boolean aliveWhenWaterlogged;

    public WaterSensitiveBlock(BlockBehaviour.Properties properties, boolean aliveWhenWaterlogged) {
        super(properties);
        this.aliveWhenWaterlogged = aliveWhenWaterlogged;
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

    /** Alive only while waterlogged ({@code dry_fragile}), or only while dry ({@code wet_fragile}). */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(BlockStateProperties.WATERLOGGED) == aliveWhenWaterlogged;
    }
}
