/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
//? if >=1.21.10 {
import net.minecraft.world.level.ScheduledTickAccess;
//?}
import net.minecraft.util.RandomSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraft.core.NonNullList;

import buildcraft.api.items.FluidItemDrops;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.tile.TileTank;
import net.neoforged.neoforge.capabilities.Capabilities;

import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;

/**
 * The tank block. Stacks vertically with other tanks to form a multi-block
 * fluid column. Has a narrower bounding box (2/16 to 14/16 on X/Z) and
 * uses cutout rendering for the glass-like appearance.
 * Ported from 1.12.2 BlockTank.
 */
@SuppressWarnings("this-escape")
public class BlockTank extends BlockBCTile_Neptune<TileTank> implements ITankBlockConnector {
    public static final MapCodec<BlockTank> CODEC = simpleCodec(BlockTank::new);
    public static final BooleanProperty JOINED_BELOW = BooleanProperty.create("joined_below");
    private static final Identifier ADVANCEMENT = Identifier.parse("buildcraftunofficial:fluid_storage");

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public BlockTank(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(JOINED_BELOW, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(JOINED_BELOW);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileTank(pos, state);
    }

    @Override
    protected BlockEntityType<?> getBlockEntityType() {
        return BCFactoryBlockEntities.TANK.get();
    }

    @Nullable
    @Override
    protected BlockEntityTicker<TileTank> getServerTicker() {
        return (lvl, pos, st, tile) -> tile.serverTick();
    }

    @Nullable
    @Override
    protected BlockEntityTicker<TileTank> getClientTicker() {
        return (lvl, pos, st, tile) -> tile.clientTick();
    }

    // Load-bearing: BaseEntityBlock defaults getRenderShape to INVISIBLE on the 1.21.1 node (the
    // override was dropped at 1.21.10). Every BlockBCTile_Neptune subclass must declare MODEL itself
    // or it compiles clean, looks right on 26.1.x, and renders nothing on 1.21.1.
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // Cull top/bottom glass faces between vertically adjacent tanks.
    // Port of 1.12.2's shouldSideBeRendered: side.getAxis() != Y || !(neighbor instanceof ITankBlockConnector)
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        if (side.getAxis() == Direction.Axis.Y && adjacentState.getBlock() instanceof ITankBlockConnector) {
            return true;
        }
        return super.skipRendering(state, adjacentState, side);
    }

    // --- Block state from neighbors ---

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean isTankBelow = level.getBlockState(pos.below()).getBlock() instanceof ITankBlockConnector;
        return this.defaultBlockState().setValue(JOINED_BELOW, isTankBelow);
    }

    @Override
    //? if >=1.21.10 {
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    //?} else {
    /*protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {*/
    //?}
        if (direction == Direction.DOWN) {
            boolean isTankBelow = neighborState.getBlock() instanceof ITankBlockConnector;
            return state.setValue(JOINED_BELOW, isTankBelow);
        }
        //? if >=1.21.10 {
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        //?} else {
        /*return super.updateShape(state, direction, neighborState, level, pos, neighborPos);*/
        //?}
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        // Balance tank fluids on placement
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileTank tank) {
                tank.balanceTankFluids();
            }
        }
    }

    // --- Interaction ---

    // useWithoutItem is inherited (BlockBCTileSupport.openTileMenu) — TileTank is an IBCMenuProvider,
    // so the inherited body is equivalent: PASS on a missing tile, SUCCESS otherwise, opened through
    // the one-arg openMenu so writeClientSideData still supplies the pos.

    @Override
    //? if >=1.21.10 {
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
    //?} else {
    /*protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {*/
    //?}
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileTank tank)) {
            return BlockUtil.itemUsePass();
        }
        // Try bucket interaction first — use column-aware handler so buckets
        // fill/drain across the entire tank stack
        boolean didChange = FluidUtilBC.onTankActivated(player, pos, hand, new buildcraft.factory.tile.TankColumnResourceHandler(tank));
        if (didChange) {
            if (!level.isClientSide()) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT);
            }
            return BlockUtil.itemUseSuccess();
        }
        // If the held item is a fluid container, the transfer had no valid result
        // (tank full/empty) — consume silently without opening the GUI, matching
        // 1.12.2 behavior where repeated right-clicking with a bucket did nothing.
        // Only open the GUI for non-fluid items (e.g. a wrench or empty hand fallback).
        //? if >=1.21.10 {
        boolean isFluidContainer = stack.getCapability(Capabilities.Fluid.ITEM, null) != null;
        //?} else {
        /*boolean isFluidContainer = stack.getCapability(Capabilities.FluidHandler.ITEM, null) != null;*/
        //?}
        if (!isFluidContainer && !level.isClientSide()) {
            player.openMenu(tank);
        }
        return BlockUtil.itemUseSuccess();
    }

    // --- Block removal: drop fluid shards ---

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileTank tank) {
                NonNullList<ItemStack> toDrop = NonNullList.create();
                FluidItemDrops.addFluidDrops(toDrop, tank.tank.getFluidStack(0));
                for (ItemStack drop : toDrop) {
                    Block.popResource(level, pos, drop);
                }
                tank.markDropsHandled();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // The <1.21.10 non-player-removal catch-all is inherited from BlockBCTile_Neptune
    // (>=1.21.10 uses TileTank#preRemoveSideEffects).
}
