/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.lib.tile.AbstractBCBlockEntity;

/**
 * The facing-flavoured sibling of {@link BlockBCTile_Neptune}, rooted on
 * {@code HorizontalDirectionalBlock} (Java single inheritance forces the pair to have different
 * superclasses). It adds the standard horizontal {@code FACING} property + placement rotation and
 * otherwise carries the same thin skeleton — owner placement, standard menu-open, ticker hookup and the
 * {@code <1.21.10} drop catch-all — all delegated to {@link BlockBCTileSupport} so the two bases stay
 * identical in surface. Subclasses with extra blockstate (e.g. the Builder's snapshot-type) override
 * {@link #createBlockStateDefinition}/{@link #getStateForPlacement} to add it.
 *
 * @param <T> the tile type this block hosts.
 */
@SuppressWarnings("this-escape")
public abstract class BlockBCTile_Directional<T extends AbstractBCBlockEntity> extends HorizontalDirectionalBlock implements EntityBlock {

    protected BlockBCTile_Directional(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // --- Block entity ---

    @Nullable
    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    /** The registered {@link BlockEntityType} of this block's tile, used to match the ticker. */
    protected abstract BlockEntityType<?> getBlockEntityType();

    /** The server-side ticker, or null if the tile does not tick server-side. */
    @Nullable
    protected BlockEntityTicker<T> getServerTicker() {
        return null;
    }

    /** The client-side ticker, or null if the tile does not tick client-side. */
    @Nullable
    protected BlockEntityTicker<T> getClientTicker() {
        return null;
    }

    @Nullable
    @Override
    public <U extends BlockEntity> BlockEntityTicker<U> getTicker(Level level, BlockState state, BlockEntityType<U> type) {
        return BlockBCTileSupport.ticker(level, type, getBlockEntityType(), getServerTicker(), getClientTicker());
    }

    // --- Placement owner attribution ---

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockBCTileSupport.placeOwner(level, pos, placer, stack);
    }

    // --- Menu open ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return BlockBCTileSupport.openTileMenu(level, pos, player);
    }

    // --- Non-player removal drop catch-all (pre-1.21.10 API; >=1.21.10 uses TileBC_Neptune#preRemoveSideEffects) ---
    //? if <1.21.10 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockBCTileSupport.dropOnRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }*/
    //?}
}
