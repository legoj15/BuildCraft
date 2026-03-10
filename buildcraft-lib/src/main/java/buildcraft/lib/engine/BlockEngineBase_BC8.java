/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.context.BlockPlaceContext;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tools.IToolWrench;

/**
 * Abstract base block for all BuildCraft engine blocks.
 * Provides directional facing, wrench rotation, and block entity ticker hookup.
 */
public abstract class BlockEngineBase_BC8 extends Block implements EntityBlock {

    // Non-full-cube shape to prevent neighbor face culling
    private static final VoxelShape ENGINE_SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public BlockEngineBase_BC8(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(defaultBlockState()
            .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BuildCraftProperties.BLOCK_FACING_6);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING_6, ctx.getClickedFace());
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
        return state.setValue(BuildCraftProperties.BLOCK_FACING_6,
            rot.rotate(state.getValue(BuildCraftProperties.BLOCK_FACING_6)));
    }

    // --- Shape / Occlusion ---

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ENGINE_SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    // --- Block Entity ---

    @Nullable
    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof TileEngineBase_BC8 engine) {
                    TileEngineBase_BC8.serverTick(lvl, pos, st, engine);
                }
            };
        } else {
            return (lvl, pos, st, be) -> {
                if (be instanceof TileEngineBase_BC8 engine) {
                    engine.clientTick();
                }
            };
        }
    }

    // --- Interaction ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        // Wrench rotation - check if player is holding a wrench
        ItemStack heldItem = player.getMainHandItem();
        if (!heldItem.isEmpty() && heldItem.getItem() instanceof IToolWrench) {
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof TileEngineBase_BC8 engine) {
                    engine.rotateOrientation();
                    // Sync the blockstate facing to the tile orientation
                    level.setBlock(pos, state.setValue(BuildCraftProperties.BLOCK_FACING_6, engine.getOrientation()), 3);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // --- Neighbor changes ---

    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block blockIn,
            BlockPos fromPos, boolean isMoving) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEngineBase_BC8 engine) {
            engine.onNeighborUpdate();
        }
    }
}
