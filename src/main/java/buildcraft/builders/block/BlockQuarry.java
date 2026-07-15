/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.lib.block.BlockBCTile_Directional;
import buildcraft.lib.misc.AdvancementUtil;

public class BlockQuarry extends BlockBCTile_Directional<TileQuarry> {
    public static final MapCodec<BlockQuarry> CODEC = simpleCodec(BlockQuarry::new);
    private static final Identifier ADVANCEMENT = Identifier.parse("buildcraftunofficial:shaping_the_world");

    public BlockQuarry(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuarry(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCBuildersBlockEntities.QUARRY.get();
    }

    @Override
    protected BlockEntityTicker<TileQuarry> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    @Override
    protected BlockEntityTicker<TileQuarry> getClientTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player) {
            AdvancementUtil.unlockAdvancement(player, ADVANCEMENT);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof TileQuarry quarry) {
            for (BlockPos blockPos : quarry.framePoses) {
                if (level.getBlockState(blockPos).is(BCBuildersBlocks.FRAME.get())) {
                    level.removeBlock(blockPos, false);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
