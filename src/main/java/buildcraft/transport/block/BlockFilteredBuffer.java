/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.tile.TileFilteredBuffer;

/**
 * The Filtered Buffer block — a storage block with 9 filter slots and 9 inventory slots.
 * Items can only be inserted into a slot if the corresponding filter slot contains a matching item.
 * Ported from 1.12.2 BlockFilteredBuffer.
 */
public class BlockFilteredBuffer extends BlockBCTile_Neptune<TileFilteredBuffer> {
    public static final MapCodec<BlockFilteredBuffer> CODEC = simpleCodec(BlockFilteredBuffer::new);

    public BlockFilteredBuffer(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFilteredBuffer(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCTransportBlockEntities.FILTERED_BUFFER.get();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Push the post-placement NBT (now carrying the owner) to clients immediately. No-re-mesh
        // push: owner is GUI-only data, the placement itself already drew the block.
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileFilteredBuffer buffer) {
            buffer.markForGuiUpdate();
        }
    }

    /** Drops the 9-slot main inventory regardless of the tool used to break the block.
     *  ItemHandlerManager.addDrops skips the 9-slot filter (registered as PHANTOM), so the
     *  template/filter items are not duplicated — they were never consumed and shouldn't
     *  drop. The block-self drop is handled by loot_table/blocks/filtered_buffer.json
     *  gated by requiresCorrectToolForDrops. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileFilteredBuffer buffer) {
            buildcraft.lib.misc.BlockDropsUtil.dropTileContents(level, pos, buffer);
            buffer.markDropsHandled();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
