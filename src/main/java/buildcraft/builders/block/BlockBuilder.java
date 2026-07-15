/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import buildcraft.api.enums.EnumOptionalSnapshotType;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.lib.block.BlockBCTile_Directional;

public class BlockBuilder extends BlockBCTile_Directional<TileBuilder> {
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

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCBuildersBlockEntities.BUILDER.get();
    }

    @Override
    protected BlockEntityTicker<TileBuilder> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
    }

    @Override
    protected BlockEntityTicker<TileBuilder> getClientTicker() {
        return (lvl, pos, st, tile) -> tile.tick();
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
}
