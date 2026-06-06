/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import java.util.Map;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
//? if >=1.21.10 {
import net.minecraft.world.level.ScheduledTickAccess;
//?}
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.capabilities.Capabilities;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.tile.TileChute;

/**
 * The Chute block — an enhanced hopper that picks up items and inserts them
 * into adjacent inventories. Can face all 6 directions.
 * Ported from 1.12.2 BlockChute.
 */
@SuppressWarnings("this-escape")
public class BlockChute extends BaseEntityBlock {
    public static final MapCodec<BlockChute> CODEC = simpleCodec(BlockChute::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final Map<Direction, Property<Boolean>> CONNECTED_MAP = BuildCraftProperties.CONNECTED_MAP;

    public BlockChute(Properties properties) {
        super(properties);
        BlockState defaultState = this.stateDefinition.any().setValue(FACING, Direction.DOWN);
        for (Property<Boolean> prop : CONNECTED_MAP.values()) {
            defaultState = defaultState.setValue(prop, false);
        }
        this.registerDefaultState(defaultState);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        CONNECTED_MAP.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace());
        return computeAllConnections(context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    //? if >=1.21.10 {
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    //?} else {
    /*protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {*/
    //?}
        Property<Boolean> prop = CONNECTED_MAP.get(direction);
        if (prop != null) {
            return state.setValue(prop, shouldConnect(level, pos, state, direction));
        }
        return state;
    }

    private static BlockState computeAllConnections(LevelReader level, BlockPos pos, BlockState state) {
        BlockState updated = state;
        for (Map.Entry<Direction, Property<Boolean>> entry : CONNECTED_MAP.entrySet()) {
            updated = updated.setValue(entry.getValue(), shouldConnect(level, pos, state, entry.getKey()));
        }
        return updated;
    }

    /** True when a stub should be drawn toward {@code direction}: the side is not the
     * intake (FACING), and the neighbour either exposes a vanilla item handler or is a
     * BC item pipe that accepts injection. */
    private static boolean shouldConnect(LevelReader level, BlockPos pos, BlockState state, Direction direction) {
        if (direction == state.getValue(FACING)) {
            return false;
        }
        BlockPos neighborPos = pos.relative(direction);
        Direction toNeighbourFace = direction.getOpposite();

        if (level instanceof Level realLevel) {
            //? if >=1.21.10 {
            if (realLevel.getCapability(Capabilities.Item.BLOCK, neighborPos, toNeighbourFace) != null) {
            //?} else {
            /*if (realLevel.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, toNeighbourFace) != null) {*/
            //?}
                return true;
            }
        }

        BlockEntity tile = level.getBlockEntity(neighborPos);
        if (tile instanceof IPipeHolder holder) {
            var pipe = holder.getPipe();
            if (pipe != null) {
                PipeFlow flow = pipe.getFlow();
                if (flow != null && flow.getCapability(PipeApi.CAP_INJECTABLE, toNeighbourFace) instanceof IInjectable) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileChute(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, BCFactoryBlockEntities.CHUTE.get(),
                (lvl, pos, st, tile) -> tile.serverTick());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileChute chute) {
                chute.onPlacedBy(placer, stack);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileChute) {
                player.openMenu((TileChute) be, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Drops the 4-slot internal inventory regardless of the tool used to break the
     *  block. The block-self drop is gated by the loot table + requiresCorrectToolForDrops,
     *  so an empty hand still returns the items the player had stored without giving back
     *  the machine. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileChute chute) {
            buildcraft.lib.misc.BlockDropsUtil.dropTileContents(level, pos, chute);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
