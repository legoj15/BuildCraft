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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.misc.FluidUtilBC;

/**
 * The tank block. Stacks vertically with other tanks to form a multi-block
 * fluid column. Has a narrower bounding box (2/16 to 14/16 on X/Z) and
 * uses cutout rendering for the glass-like appearance.
 * Ported from 1.12.2 BlockTank.
 */
public class BlockTank extends BaseEntityBlock implements ITankBlockConnector {
    public static final MapCodec<BlockTank> CODEC = simpleCodec(BlockTank::new);
    public static final BooleanProperty JOINED_BELOW = BooleanProperty.create("joined_below");

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public BlockTank(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(JOINED_BELOW, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(JOINED_BELOW);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileTank(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, BCFactoryBlockEntities.TANK.get(),
                (lvl, pos, st, tile) -> tile.serverTick());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // --- Block state from neighbors ---

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        updateJoinedBelow(level, pos, state);
        // Also update the block above (it may need to become joined_below=true)
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.getBlock() instanceof BlockTank) {
            updateJoinedBelow(level, above, aboveState);
        }
        // Balance tank fluids on placement
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileTank tank) {
                tank.balanceTankFluids();
            }
        }
    }

    private void updateJoinedBelow(Level level, BlockPos pos, BlockState state) {
        boolean isTankBelow = level.getBlockState(pos.below()).getBlock() instanceof ITankBlockConnector;
        if (state.getValue(JOINED_BELOW) != isTankBelow) {
            level.setBlock(pos, state.setValue(JOINED_BELOW, isTankBelow), Block.UPDATE_ALL);
        }
    }

    // --- Interaction ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileTank tank)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(tank, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileTank tank)) {
            return InteractionResult.PASS;
        }
        // Try bucket interaction first — use column-aware handler so buckets
        // fill/drain across the entire tank stack
        boolean didChange = FluidUtilBC.onTankActivated(player, pos, hand, tank.getColumnFluidHandler());
        if (didChange) {
            return InteractionResult.SUCCESS;
        }
        // If the player is holding a non-fluid item, open the GUI
        // (useWithoutItem is NOT called when useItemOn returns PASS in 1.21.11)
        if (!level.isClientSide()) {
            player.openMenu(tank, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
