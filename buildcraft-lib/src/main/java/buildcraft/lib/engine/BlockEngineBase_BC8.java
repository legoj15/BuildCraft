/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
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
        return Shapes.block();
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        // BER handles all in-world rendering; prevent block model from rendering (eliminates z-fighting)
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
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

    /**
     * Handle wrench interactions on the base engine.
     * 
     * 1.12.2 parity:
     * - Crouch + wrench: attemptRotation() — rotate to next valid MJ receiver
     * - Normal wrench: PASS — let subclasses handle (creative=output, stone/iron/redstone=subclass behavior)
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof IToolWrench) {
            if (player.isShiftKeyDown()) {
                // Crouch + wrench = rotate to next valid receiver (all engine types)
                if (!level.isClientSide()) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof TileEngineBase_BC8 engine) {
                        if (engine.attemptRotation()) {
                            level.setBlock(pos, state.setValue(
                                    BuildCraftProperties.BLOCK_FACING_6, engine.getOrientation()), 3);
                        }
                    }
                }
                return InteractionResult.SUCCESS;
            }
            // Non-crouching wrench: PASS to let subclasses handle
            // (redstone engine will fall through to attemptRotation below,
            //  creative engine overrides to cycle output,
            //  stone/iron engines let it pass to open GUI)
            return InteractionResult.PASS;
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
