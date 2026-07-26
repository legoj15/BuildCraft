/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.lib.tile.AbstractBCBlockEntity;

/**
 * The shared base for BuildCraft {@code BaseEntityBlock}-rooted machine blocks. Absorbs the six-method
 * skeleton every GUI machine used to hand-copy: owner placement ({@link #setPlacedBy}), the standard
 * menu-open ({@link #useWithoutItem}), ticker hookup ({@link #getTicker}, standardised on the
 * {@code createTickerHelper} match), and the {@code <1.21.10} non-player-removal drop catch-all
 * ({@code onRemove}). Subclasses provide only what genuinely varies — {@link #newBlockEntity},
 * {@link #getBlockEntityType}, the per-side tickers, {@code codec}, and their bespoke
 * {@code playerWillDestroy} drops — and override any absorbed method whose behaviour differs.
 *
 * <p>The facing-flavoured sibling {@link BlockBCTile_Directional} is a thin parallel of this class
 * rooted on {@code HorizontalDirectionalBlock} (Java single inheritance forces the pair); both delegate
 * their shared bodies to {@link BlockBCTileSupport}.
 *
 * <p><b>Every subclass must declare its own {@code getRenderShape -> RenderShape.MODEL}.</b> This base
 * does not supply it, and {@code BaseEntityBlock} defaults it to {@code INVISIBLE} on the 1.21.1 node
 * only (the override was dropped at 1.21.10) — omitting it compiles clean and looks correct on every
 * other node while rendering nothing on that one. Enforced by
 * {@code buildcraft.lib.block.BlockRenderShapeTester}, a game test that walks the live block registry.
 *
 * @param <T> the tile type this block hosts.
 */
public abstract class BlockBCTile_Neptune<T extends AbstractBCBlockEntity> extends BaseEntityBlock {

    protected BlockBCTile_Neptune(Properties properties) {
        super(properties);
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
