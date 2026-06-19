/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.enums.EnumOptionalSnapshotType;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.tile.TileBuilder;

@SuppressWarnings("this-escape")
public class BlockBuilder extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BlockBuilder> CODEC = simpleCodec(BlockBuilder::new);
    /** Drives the front-face "door" submodel: NONE = closed empty door, TEMPLATE / BLUEPRINT =
     *  matching door variant. TileBuilder pushes this whenever its loaded snapshot changes. */
    public static final EnumProperty<EnumOptionalSnapshotType> SNAPSHOT_TYPE =
        EnumProperty.create("snapshot_type", EnumOptionalSnapshotType.class);

    public BlockBuilder(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(SNAPSHOT_TYPE, EnumOptionalSnapshotType.NONE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SNAPSHOT_TYPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(SNAPSHOT_TYPE, EnumOptionalSnapshotType.NONE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileBuilder(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        if (blockEntityType != BCBuildersBlockEntities.BUILDER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TileBuilder builder) {
                builder.tick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity tile = level.getBlockEntity(pos);
            if (tile instanceof TileBuilder builder) {
                player.openMenu(builder, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof TileBuilder builder) {
            builder.onPlacedBy(placer, stack);
        }
    }

    /**
     * Drops the tile entity's contents (snapshot item, the 27-slot resource grid, and any
     * fluid held in the 4 tanks as fluid-shard items) when the block is broken. The block
     * itself drops via its loot table (loot_table/block/builder.json); this override only
     * handles the <em>contents</em>, matching the 1.12.2 behaviour where
     * {@code BlockBCTile_Neptune.breakBlock} delegated to the tile's own {@code onRemove}.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileBuilder builder) {
                ItemStack snapshot = builder.getSnapshot();
                if (!snapshot.isEmpty()) {
                    Block.popResource(level, pos, snapshot);
                }
                for (int i = 0; i < TileBuilder.RESOURCE_SLOTS; i++) {
                    ItemStack stack = builder.getResource(i);
                    if (!stack.isEmpty()) {
                        Block.popResource(level, pos, stack);
                    }
                }
                net.minecraft.core.NonNullList<ItemStack> fluidDrops = net.minecraft.core.NonNullList.create();
                buildcraft.api.items.FluidItemDrops.addFluidDrops(fluidDrops,
                    builder.getTank(0).getFluidStack(0), builder.getTank(1).getFluidStack(0),
                    builder.getTank(2).getFluidStack(0), builder.getTank(3).getFluidStack(0));
                for (ItemStack drop : fluidDrops) {
                    Block.popResource(level, pos, drop);
                }
                builder.markDropsHandled();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Non-player removal catch-all for the pre-1.21.10 API; >=1.21.10 uses TileBC_Neptune#preRemoveSideEffects.
    //? if <1.21.10 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof buildcraft.lib.tile.TileBC_Neptune tile) {
            tile.dropContentsOnRemoval(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }*/
    //?}
}
