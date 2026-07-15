/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.lib.block.BlockBCTile_Directional;

public class BlockElectronicLibrary extends BlockBCTile_Directional<TileElectronicLibrary> {
    public static final MapCodec<BlockElectronicLibrary> CODEC = simpleCodec(BlockElectronicLibrary::new);

    public BlockElectronicLibrary(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileElectronicLibrary(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCBuildersBlockEntities.LIBRARY.get();
    }

    @Override
    protected BlockEntityTicker<TileElectronicLibrary> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    @Override
    protected BlockEntityTicker<TileElectronicLibrary> getClientTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    /** Drops the download in/out and upload in/out slots — 4 real inventories registered
     *  with ItemHandlerManager. The selected snapshot key lives in level data, not in any
     *  slot, so there's nothing else to recover. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileElectronicLibrary library) {
            buildcraft.lib.misc.BlockDropsUtil.dropTileContents(level, pos, library);
            library.markDropsHandled();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
