/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.tile.TileFiller;
import buildcraft.lib.block.BlockBCTile_Directional;

public class BlockFiller extends BlockBCTile_Directional<TileFiller> {
    public static final MapCodec<BlockFiller> CODEC = simpleCodec(BlockFiller::new);

    public BlockFiller(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFiller(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCBuildersBlockEntities.FILLER.get();
    }

    @Override
    protected BlockEntityTicker<TileFiller> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    @Override
    protected BlockEntityTicker<TileFiller> getClientTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    /** Right-click only opens the GUI once the filler has a marker box to fill. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof TileFiller filler && !filler.hasBox()) {
            return InteractionResult.PASS;
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    /** Drops the resource grid contents regardless of the tool used to break the block. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof TileFiller filler) {
            buildcraft.lib.misc.BlockDropsUtil.dropTileContents(level, pos, filler);
            filler.markDropsHandled();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
