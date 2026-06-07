/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import java.util.Locale;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
//? if >=1.21.10 {
import net.minecraft.world.level.redstone.Orientation;
//?}
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.items.FluidItemDrops;
import buildcraft.api.tools.IToolWrench;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;

/**
 * The Heat Exchanger block. Forms horizontal multi-block structures (3-5 blocks)
 * that transfer heat between two fluid streams.
 * Ported from 1.12.2 BlockHeatExchange.
 */
@SuppressWarnings("this-escape")
public class BlockHeatExchange extends BaseEntityBlock implements ICustomRotationHandler {
    public static final MapCodec<BlockHeatExchange> CODEC = simpleCodec(BlockHeatExchange::new);

    public enum EnumExchangePart implements StringRepresentable {
        START,
        MIDDLE,
        END;

        private final String lowerCaseName = name().toLowerCase(Locale.ROOT);

        @Override
        public String getSerializedName() {
            return lowerCaseName;
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<EnumExchangePart> PART = EnumProperty.create("part", EnumExchangePart.class);
    public static final BooleanProperty CONNECTED_LEFT = BooleanProperty.create("connected_left");
    public static final BooleanProperty CONNECTED_RIGHT = BooleanProperty.create("connected_right");

    public BlockHeatExchange(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, EnumExchangePart.MIDDLE)
                .setValue(CONNECTED_LEFT, false)
                .setValue(CONNECTED_RIGHT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, CONNECTED_LEFT, CONNECTED_RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState state = this.defaultBlockState().setValue(FACING, facing);
        return updateConnections(state, context.getLevel(), context.getClickedPos(), facing);
    }

    @Override
    //? if >=1.21.10 {
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource randomSource) {
    //?} else {
    /*protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {*/
    //?}
        // Only care about horizontal neighbors
        if (direction.getAxis().isVertical()) {
            return state;
        }
        Direction facing = state.getValue(FACING);
        return updateConnections(state, level, pos, facing);
    }

    private BlockState updateConnections(BlockState state, LevelReader level, BlockPos pos, Direction facing) {
        // Left = counter-clockwise, Right = clockwise
        boolean connectLeft = doesNeighbourConnect(level, pos, facing, facing.getCounterClockWise());
        boolean connectRight = doesNeighbourConnect(level, pos, facing, facing.getClockWise());
        return state
                .setValue(CONNECTED_LEFT, connectLeft)
                .setValue(CONNECTED_RIGHT, connectRight);
    }

    private static boolean doesNeighbourConnect(LevelReader level, BlockPos pos, Direction thisFacing,
            Direction dir) {
        BlockState neighbour = level.getBlockState(pos.relative(dir));
        if (neighbour.getBlock() instanceof BlockHeatExchange) {
            return neighbour.getValue(FACING) == thisFacing;
        }
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileHeatExchange(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, BCFactoryBlockEntities.HEAT_EXCHANGE.get(),
                    (lvl, pos, st, tile) -> tile.clientTick());
        }
        return createTickerHelper(type, BCFactoryBlockEntities.HEAT_EXCHANGE.get(),
                (lvl, pos, st, tile) -> tile.serverTick());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    //? if >=1.21.10 {
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
    //?} else {
    /*protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {*/
    //?}
        // Wrench priority (unified with engine/distiller/dynamo blocks):
        //   - crouch + wrench → open GUI (so the player can sneak-tweak a running
        //     chain without rotating it)
        //   - non-crouch + wrench → PASS so ItemWrench_Neptune.useOn dispatches
        //     rotation through ICustomRotationHandler#attemptRotation, which
        //     calls TileHeatExchange.rotate() (single block: 90°; chain: 180°
        //     swap of start/end). Matches 1.12.2's Block#rotateBlock wiring.
        if (stack.getItem() instanceof IToolWrench) {
            if (player.isShiftKeyDown()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof TileHeatExchange exchange) {
                    return BlockUtil.itemUseFrom(openExchangeMenu(level, exchange, player));
                }
                return BlockUtil.itemUsePass();
            }
            return BlockUtil.itemUsePass();
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileHeatExchange exchange)) {
            return BlockUtil.itemUsePass();
        }
        TileHeatExchange.ExchangeSection section = exchange.getSection();
        if (section != null) {
            // Try bucket/fluid container interaction with section tanks
            @SuppressWarnings("removal")
            boolean didChange = FluidUtilBC.onTankActivated(player, pos, hand, section.tankInput);
            if (!didChange) {
                @SuppressWarnings("removal")
                boolean didChangeOutput = FluidUtilBC.onTankActivated(player, pos, hand, section.tankOutput);
                didChange = didChangeOutput;
            }
            if (didChange) {
                return BlockUtil.itemUseSuccess();
            }
        }
        return BlockUtil.itemUseFrom(openExchangeMenu(level, exchange, player));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileHeatExchange exchange)) {
            return InteractionResult.PASS;
        }
        return openExchangeMenu(level, exchange, player);
    }

    // --- ICustomRotationHandler ---

    /**
     * Wrench rotation entry point. Delegates to {@link TileHeatExchange#rotate()},
     * which rotates a single exchanger 90° or swaps the start/end of a multi-block
     * chain 180° (the chain-rotation case also plays the slide sound at the
     * wrenched position).
     */
    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileHeatExchange exchange && exchange.rotate()) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult openExchangeMenu(Level level, TileHeatExchange exchange, Player player) {
        TileHeatExchange start = exchange.findStart();
        if (start == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(start, start.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // --- Block removal: drop fluid shards ---

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileHeatExchange exchange) {
                TileHeatExchange.ExchangeSection section = exchange.getSection();
                if (section != null) {
                    NonNullList<ItemStack> toDrop = NonNullList.create();
                    FluidItemDrops.addFluidDrops(toDrop, section.tankInput);
                    FluidItemDrops.addFluidDrops(toDrop, section.tankOutput);
                    for (ItemStack drop : toDrop) {
                        Block.popResource(level, pos, drop);
                    }
                }
                // Drop bucket / fluid-shard slot contents from the START tile.
                for (int i = 0; i < exchange.containerSlots.size(); i++) {
                    ItemStack slotStack = exchange.containerSlots.getStackInSlot(i);
                    if (!slotStack.isEmpty()) {
                        Block.popResource(level, pos, slotStack);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    //? if >=1.21.10 {
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            @Nullable Orientation orientation, boolean movedByPiston) {
    //?} else {
    /*protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {*/
    //?}
        //? if >=1.21.10 {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        //?} else {
        /*super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);*/
        //?}
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileHeatExchange exchange) {
            exchange.markCheckNeighbours();
        }
    }
}
