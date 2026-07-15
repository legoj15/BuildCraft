/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.tile.TileReplacer;
import buildcraft.lib.block.BlockBCTile_Directional;

public class BlockReplacer extends BlockBCTile_Directional<TileReplacer> {
    public static final MapCodec<BlockReplacer> CODEC = simpleCodec(BlockReplacer::new);

    public BlockReplacer(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileReplacer(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCBuildersBlockEntities.REPLACER.get();
    }

    /** Drops the snapshot in/out and the two schematic match-pattern slots. None of the
     *  three are PHANTOM — the replacer consumes the input snapshot to produce the output
     *  one, and the schematics persist between sessions, so all should return to the player. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof TileReplacer replacer) {
            buildcraft.lib.misc.BlockDropsUtil.dropItems(level, pos,
                replacer.invSnapshot, replacer.invSchematicFrom, replacer.invSchematicTo);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
