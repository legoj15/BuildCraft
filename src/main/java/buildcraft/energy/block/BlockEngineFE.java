/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.tools.IToolWrench;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.energy.BCEnergyConfig;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.lib.engine.BlockEngineBase_BC8;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.BlockUtil;

public class BlockEngineFE extends BlockEngineBase_BC8 {
    public BlockEngineFE(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineFE(pos, state);
    }

    /**
     * FE engine never overheats (heat is capped). Wrench priority:
     *   1. Pipe in hand → place it if it's an FE pipe or a wooden kinesis pipe (the pipe types
     *      that connect to an FE engine); otherwise — or if placement is obstructed — open GUI.
     *   2. Crouch → open GUI (overrides wrench; unified with Stone/Iron/Dynamo).
     *   3. Wrench (non-crouch) → PASS if there's an alternate receiver (rotate + sound +
     *      `wrenched` advancement); otherwise tripwire-armed sound + CONSUME.
     *   4. Default → open GUI.
     */
    @Override
    //? if >=1.21.10 {
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
    //?} else {
    /*protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {*/
    //?}
        if (stack.getItem() instanceof IItemPipe pipe) {
            InteractionResult placed = EnginePipeInteraction.tryPlacePipe(
                    pipe, stack, level, player, hand, hitResult, PipeApi.flowRf, PipeApi.flowPower);
            return BlockUtil.itemUseFrom(placed != null ? placed : openGui(state, level, pos, player));
        }

        if (player.isShiftKeyDown()) {
            return BlockUtil.itemUseFrom(openGui(state, level, pos, player));
        }

        if (stack.getItem() instanceof IToolWrench) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEngineBase_BC8 engine && engine.hasAlternateReceiver()) {
                return BlockUtil.itemUsePass();
            }
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.3f);
            }
            player.swing(hand);
            return BlockUtil.itemUseConsume();
        }

        return BlockUtil.itemUseFrom(openGui(state, level, pos, player));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        return openGui(state, level, pos, player);
    }

    private InteractionResult openGui(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEngineFE engine && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                new net.minecraft.world.SimpleMenuProvider(
                    (containerId, playerInv, p) -> new buildcraft.energy.container.ContainerEngineFE(containerId, playerInv, engine),
                    Component.translatable(BCEnergyConfig.rfFeKey(state.getBlock().getDescriptionId()))
                ),
                buf -> buf.writeBlockPos(pos)
            );
        }
        return InteractionResult.SUCCESS;
    }

    /** Drops the upgrade slots regardless of the tool used to break the block. The upgrades
     *  inventory is a raw ItemHandlerSimple, not registered with ItemHandlerManager. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEngineFE engine) {
            buildcraft.lib.misc.BlockDropsUtil.dropItems(level, pos, engine.upgrades);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
