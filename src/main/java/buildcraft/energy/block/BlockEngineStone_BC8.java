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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.tools.IToolWrench;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.lib.engine.BlockEngineBase_BC8;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.SoundUtil;

public class BlockEngineStone_BC8 extends BlockEngineBase_BC8 {
    public BlockEngineStone_BC8(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineStone_BC8(pos, state);
    }

    /**
     * Wrench priority on the Stirling engine:
     *   1. Wrench + OVERHEAT → clear overheat, grant `to_much_power`, play slide sound, CONSUME.
     *   2. Pipe in hand → place it if it's an item pipe or a wooden kinesis pipe (the pipe types
     *      that connect to a stirling engine); otherwise — or if placement is obstructed — open GUI.
     *   3. Crouch → open GUI (overrides wrench).
     *   4. Wrench (non-crouch) → PASS if there's an alternate receiver (wrench.useOn will rotate,
     *      play the slide sound, and grant `wrenched`); otherwise play the tripwire-armed sound
     *      and CONSUME so the player knows the wrench fired but had nothing to rotate to.
     *   5. Default → open GUI.
     */
    @Override
    //? if >=1.21.10 {
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
    //?} else {
    /*protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {*/
    //?}
        boolean isWrench = EntityUtil.isWrench(stack);
        BlockEntity be = level.getBlockEntity(pos);
        TileEngineBase_BC8 engine = (be instanceof TileEngineBase_BC8 e) ? e : null;

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
                    pipe, stack, level, player, hand, hitResult, PipeApi.flowItems, PipeApi.flowPower);
            return BlockUtil.itemUseFrom(placed != null ? placed : openGui(state, level, pos, player));
        }

        if (player.isShiftKeyDown()) {
            return BlockUtil.itemUseFrom(openGui(state, level, pos, player));
        }

        if (isWrench) {
            if (engine != null && engine.hasAlternateReceiver()) {
                // BuildCraft's own wrench rotates via its useOn (ICustomRotationHandler); a foreign
                // tag-only wrench has no such hook, so drive the rotation block-side here.
                if (stack.getItem() instanceof IToolWrench) {
                    return BlockUtil.itemUsePass();
                }
                return BlockUtil.itemUseFrom(
                        BlockUtil.rotateByForeignWrench(level, pos, state, player, hand, hitResult.getDirection()));
            }
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.3f);
            }
            player.swing(hand);
            return BlockUtil.itemUseConsume();
        }

        return BlockUtil.itemUseFrom(openGui(state, level, pos, player));
    }

    /**
     * Empty hand right-click opens GUI.
     */
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
        if (be instanceof TileEngineStone_BC8 engine && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                new net.minecraft.world.SimpleMenuProvider(
                    (containerId, playerInv, p) -> new buildcraft.energy.container.ContainerEngineStone(containerId, playerInv, engine),
                    net.minecraft.network.chat.Component.translatable("tile.engineStone.name")
                ),
                buf -> buf.writeBlockPos(pos)
            );
        }
        return InteractionResult.SUCCESS;
    }

    /** Drops the fuel slot contents regardless of the tool used to break the block.
     *  fuelStack is a loose ItemStack, not held by ItemHandlerManager. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEngineStone_BC8 engine) {
            buildcraft.lib.misc.BlockDropsUtil.dropStack(level, pos, engine.getFuelStack());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
