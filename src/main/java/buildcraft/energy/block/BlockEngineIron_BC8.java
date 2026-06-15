/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.tools.IToolWrench;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.engine.BlockEngineBase_BC8;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.misc.SoundUtil;

public class BlockEngineIron_BC8 extends BlockEngineBase_BC8 {
    public BlockEngineIron_BC8(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineIron_BC8(pos, state);
    }

    /**
     * Wrench priority on the Combustion engine:
     *   1. Wrench + OVERHEAT → clear overheat, grant `to_much_power`, play slide sound, CONSUME.
     *   2. Pipe in hand → place it if it's a fluid pipe or a wooden kinesis pipe (the pipe types
     *      that connect to a combustion engine); otherwise — or if placement is obstructed — open GUI.
     *   3. Crouch → open GUI (overrides wrench).
     *   4. Wrench (non-crouch) → PASS if there's an alternate receiver (wrench.useOn rotates,
     *      plays slide sound, grants `wrenched`); otherwise tripwire-armed sound + CONSUME.
     *   5. Non-wrench fluid container → fill/drain via FluidUtilBC (buckets, tanks).
     *   6. Default → open GUI.
     */
    @Override
    //? if >=1.21.10 {
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
    //?} else {
    /*protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {*/
    //?}
        boolean isWrench = !stack.isEmpty() && stack.getItem() instanceof IToolWrench;
        BlockEntity be = level.getBlockEntity(pos);
        TileEngineIron_BC8 engine = (be instanceof TileEngineIron_BC8 e) ? e : null;

        if (isWrench && engine != null && engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
            if (!level.isClientSide()) {
                engine.clearOverheat(player);
                SoundUtil.playSlideSound(level, pos, state, InteractionResult.SUCCESS);
            }
            player.swing(hand);
            return BlockUtil.itemUseConsume();
        }

        if (stack.getItem() instanceof IItemPipe pipe) {
            InteractionResult placed = EnginePipeInteraction.tryPlacePipe(
                    pipe, stack, level, player, hand, hitResult, PipeApi.flowFluids, PipeApi.flowPower);
            return BlockUtil.itemUseFrom(placed != null ? placed : openGui(state, level, pos, player));
        }

        if (player.isShiftKeyDown()) {
            return BlockUtil.itemUseFrom(openGui(state, level, pos, player));
        }

        if (isWrench) {
            if (engine != null && engine.hasAlternateReceiver()) {
                return BlockUtil.itemUsePass();
            }
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.3f);
            }
            player.swing(hand);
            return BlockUtil.itemUseConsume();
        }

        if (engine != null && FluidUtilBC.onTankActivated(player, pos, hand, engine.combinedFluidHandler)) {
            return BlockUtil.itemUseSuccess();
        }

        return BlockUtil.itemUseFrom(openGui(state, level, pos, player));
    }

    /**
     * Called when the player right-clicks with an empty hand.
     * Opens the GUI.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        return openGui(state, level, pos, player);
    }

    /**
     * Drop fragile fluid shards for all tank contents when the engine is broken.
     * Matches 1.12.2 behavior where TankManager contents were dropped as fluid shard items.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEngineIron_BC8 engine) {
                net.minecraft.core.NonNullList<ItemStack> drops = net.minecraft.core.NonNullList.create();
                buildcraft.api.items.FluidItemDrops.addFluidDrops(drops,
                        engine.tankFuel, engine.tankCoolant, engine.tankResidue);
                for (ItemStack drop : drops) {
                    Block.popResource(level, pos, drop);
                }
                engine.markDropsHandled();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Non-player removal catch-all for the pre-1.21.10 API; >=1.21.10 uses TileEngineIron_BC8#preRemoveSideEffects.
    //? if <1.21.10 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof buildcraft.energy.tile.TileEngineIron_BC8 tile) {
            tile.dropContentsOnRemoval(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }*/
    //?}

    /** Open the combustion engine GUI for the given player. */
    private InteractionResult openGui(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEngineIron_BC8 engine && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                new net.minecraft.world.SimpleMenuProvider(
                    (containerId, playerInv, p) -> new buildcraft.energy.container.ContainerEngineIron(containerId, playerInv, engine),
                    net.minecraft.network.chat.Component.translatable("tile.engineIron.name")
                ),
                buf -> buf.writeBlockPos(pos)
            );
        }
        return InteractionResult.SUCCESS;
    }
}
