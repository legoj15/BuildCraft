/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.tile.TileLaser;

/**
 * The Laser block — a directional block (all 6 faces) that provides MJ power
 * to ILaserTarget blocks (e.g. assembly tables) by projecting a visible beam.
 * Ported from 1.12.2 BlockLaser.
 */
@SuppressWarnings("this-escape")
public class BlockLaser extends BaseEntityBlock {
    public static final MapCodec<BlockLaser> CODEC = simpleCodec(BlockLaser::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    // Collision/outline matches the rendered model (assets/.../models/block/laser.json): a full-footprint
    // 4px mounting slab on the face opposite FACING, plus a centred 6x6 emitter tower projecting 9px toward
    // the beam. One shape per facing — the tower is symmetric on its cross-axes, so it just extends inward.
    private static final VoxelShape SHAPE_UP =
            Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(5, 4, 5, 11, 13, 11));
    private static final VoxelShape SHAPE_DOWN =
            Shapes.or(Block.box(0, 12, 0, 16, 16, 16), Block.box(5, 3, 5, 11, 12, 11));
    private static final VoxelShape SHAPE_NORTH =
            Shapes.or(Block.box(0, 0, 12, 16, 16, 16), Block.box(5, 5, 3, 11, 11, 12));
    private static final VoxelShape SHAPE_SOUTH =
            Shapes.or(Block.box(0, 0, 0, 16, 16, 4), Block.box(5, 5, 4, 11, 11, 13));
    private static final VoxelShape SHAPE_WEST =
            Shapes.or(Block.box(12, 0, 0, 16, 16, 16), Block.box(3, 5, 5, 12, 11, 11));
    private static final VoxelShape SHAPE_EAST =
            Shapes.or(Block.box(0, 0, 0, 4, 16, 16), Block.box(4, 5, 5, 13, 11, 11));

    public BlockLaser(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face opposite the direction the player is looking (laser points away from placement surface)
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileLaser(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, BCSiliconBlockEntities.LASER.get(),
                    (lvl, pos, st, tile) -> tile.clientTick());
        }
        return createTickerHelper(type, BCSiliconBlockEntities.LASER.get(),
                (lvl, pos, st, tile) -> tile.serverTick());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> SHAPE_DOWN;
            case UP -> SHAPE_UP;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }
}
