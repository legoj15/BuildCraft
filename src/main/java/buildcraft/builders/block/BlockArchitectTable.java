/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.lib.block.BlockBCTile_Directional;

public class BlockArchitectTable extends BlockBCTile_Directional<TileArchitectTable> {
    public static final MapCodec<BlockArchitectTable> CODEC = simpleCodec(BlockArchitectTable::new);

    public BlockArchitectTable(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileArchitectTable(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCBuildersBlockEntities.ARCHITECT.get();
    }

    @Override
    protected BlockEntityTicker<TileArchitectTable> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    @Override
    protected BlockEntityTicker<TileArchitectTable> getClientTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    /** Drops whatever is left in the snapshot in/out slots when the block is broken so a used
     *  blueprint the player "forgot" in the output doesn't evaporate. The block itself drops
     *  via loot_table/block/architect.json — this override only handles the tile contents. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileArchitectTable architect) {
                ItemStack in = architect.getSnapshotIn();
                if (!in.isEmpty()) {
                    Block.popResource(level, pos, in);
                }
                ItemStack out = architect.getSnapshotOut();
                if (!out.isEmpty()) {
                    Block.popResource(level, pos, out);
                }
                architect.markDropsHandled();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
