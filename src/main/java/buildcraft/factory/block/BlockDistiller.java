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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.core.NonNullList;


import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.items.FluidItemDrops;
import buildcraft.api.tools.IToolWrench;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.lib.misc.FluidUtilBC;

/**
 * The Distiller block. Distills input fluids into gas (output up) and liquid
 * (output down) products using MJ power.
 * Ported from 1.12.2 BlockDistiller.
 */
public class BlockDistiller extends BaseEntityBlock implements ICustomRotationHandler {
    public static final MapCodec<BlockDistiller> CODEC = simpleCodec(BlockDistiller::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockDistiller(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.WEST));
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

    /**
     * Record the placing player as the owner so the GUI ownership ledger has a profile to show
     * and the Heating and Distilling advancement has someone to grant. Forwards to
     * {@link TileDistiller_BC8#onPlacedBy}, which handles the {@code Player} → {@code GameProfile}
     * conversion and syncs the change to tracking clients.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof TileDistiller_BC8 distiller) {
            distiller.onPlacedBy(placer);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileDistiller_BC8(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, BCFactoryBlockEntities.DISTILLER.get(),
                    (lvl, pos, st, tile) -> tile.clientTick());
        }
        return createTickerHelper(type, BCFactoryBlockEntities.DISTILLER.get(),
                (lvl, pos, st, tile) -> tile.serverTick());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Wrench priority (unified with engine/dynamo blocks):
        //   - crouch + wrench → open GUI (overrides rotation, so the player can sneak-tweak
        //     a working distiller without spinning it)
        //   - non-crouch + wrench → PASS so ItemWrench_Neptune.useOn dispatches rotation
        //     through ICustomRotationHandler + plays the slide sound + grants `wrenched`
        if (stack.getItem() instanceof IToolWrench) {
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide()) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof TileDistiller_BC8 distiller) {
                        player.openMenu(distiller, pos);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileDistiller_BC8 distiller)) {
            return InteractionResult.PASS;
        }
        // 1.12.2 behaviour: TileBC_Neptune.onActivated calls tankManager.onActivated,
        // which iterates the tanks. Each distiller tank is gated:
        // tankIn rejects external extracts and accepts only distillable inserts,
        // and the two output tanks reject external inserts. So the bucket "lands"
        // on whichever tank the held item is compatible with — a distillable bucket
        // empties into tankIn, an empty bucket fills from gasOut (then liquidOut).
        @SuppressWarnings("removal")
        boolean didChange = FluidUtilBC.onTankActivated(player, pos, hand, distiller.getTankIn());
        if (!didChange) {
            @SuppressWarnings("removal")
            boolean drainedGas = FluidUtilBC.onTankActivated(player, pos, hand, distiller.getTankGasOut());
            didChange = drainedGas;
        }
        if (!didChange) {
            @SuppressWarnings("removal")
            boolean drainedLiquid = FluidUtilBC.onTankActivated(player, pos, hand, distiller.getTankLiquidOut());
            didChange = drainedLiquid;
        }
        if (didChange) {
            return InteractionResult.SUCCESS;
        }
        // No fluid interaction — open the GUI
        if (!level.isClientSide()) {
            player.openMenu(distiller, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // --- ICustomRotationHandler ---

    /**
     * Called by the wrench's useOn() via CustomRotationHelper.attemptRotateBlock().
     * Rotates the horizontal facing clockwise (1.12.2 parity: IBlockWithFacing's
     * EnumFacing.rotateY(), which equals Direction.getClockWise() in 1.21+).
     */
    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Direction current = state.getValue(FACING);
        level.setBlock(pos, state.setValue(FACING, current.getClockWise()), Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileDistiller_BC8) {
                player.openMenu((TileDistiller_BC8) be, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // --- Block removal: drop fluid shards ---

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileDistiller_BC8 distiller) {
                NonNullList<ItemStack> toDrop = NonNullList.create();
                FluidItemDrops.addFluidDrops(toDrop, distiller.getTankIn());
                FluidItemDrops.addFluidDrops(toDrop, distiller.getTankGasOut());
                FluidItemDrops.addFluidDrops(toDrop, distiller.getTankLiquidOut());
                for (ItemStack drop : toDrop) {
                    Block.popResource(level, pos, drop);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
