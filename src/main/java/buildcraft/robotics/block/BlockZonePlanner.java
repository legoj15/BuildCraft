/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.tile.TileZonePlanner;

/**
 * The Zone Planner block — allows players to define zone areas on a map
 * and write them to Map Location items using paintbrushes.
 * Ported from 1.12.2 BlockZonePlanner.
 */
@SuppressWarnings("this-escape")
public class BlockZonePlanner extends BlockBCTile_Neptune<TileZonePlanner> {
    public static final MapCodec<BlockZonePlanner> CODEC = simpleCodec(BlockZonePlanner::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockZonePlanner(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileZonePlanner(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCRoboticsBlockEntities.ZONE_PLANNER.get();
    }

    /** Server-only ticker; the client ticker stays null (the base's default), matching the old body. */
    @Nullable
    @Override
    protected BlockEntityTicker<TileZonePlanner> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.serverTick();
    }

    // Load-bearing: BaseEntityBlock defaults getRenderShape to INVISIBLE on the 1.21.1 node (the
    // override was dropped at 1.21.10). Every BlockBCTile_Neptune subclass must declare MODEL itself
    // or it compiles clean, looks right on 26.1.x, and renders nothing on 1.21.1.
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // useWithoutItem is inherited (BlockBCTileSupport.openTileMenu). The one delta from the old body:
    // a missing/wrong block entity now returns PASS instead of SUCCESS — unreachable in normal play,
    // and PASS is the more correct value (it lets item use fall through instead of swallowing it).

    /** Drops the 16-slot paintbrush bank and the 6 in/out slots. All hold real items —
     *  the planner has no ghost/template slots; zone data is stored in level data, not on
     *  the items in these slots. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileZonePlanner planner) {
            buildcraft.lib.misc.BlockDropsUtil.dropItems(level, pos,
                planner.invPaintbrushes,
                planner.invInputPaintbrush, planner.invInputMapLocation, planner.invInputResult,
                planner.invOutputPaintbrush, planner.invOutputMapLocation, planner.invOutputResult);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
