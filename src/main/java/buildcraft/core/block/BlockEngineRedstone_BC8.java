/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
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
import buildcraft.core.tile.TileEngineRedstone_BC8;
import buildcraft.lib.engine.BlockEngineBase_BC8;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.EntityUtil;

public class BlockEngineRedstone_BC8 extends BlockEngineBase_BC8 {
    public BlockEngineRedstone_BC8(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineRedstone_BC8(pos, state);
    }

    /**
     * Redstone engine has no GUI and never overheats. Wrench is the only meaningful
     * interaction:
     *   - rotate to next valid receiver if one exists (delegated to wrench.useOn via PASS,
     *     which grants the `wrenched` advancement and plays the slide sound)
     *   - if no alternate receiver, play the tripwire-armed sound and CONSUME, so the
     *     player gets audio feedback that the wrench fired but had nothing to do.
     * Crouch is irrelevant.
     */
    @Override
    //? if >=1.21.10 {
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
    //?} else {
    /*protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {*/
    //?}
        if (!EntityUtil.isWrench(stack)) {
            return BlockUtil.itemUsePass();
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEngineBase_BC8 engine && engine.hasAlternateReceiver()) {
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
        // swing on both sides: client side animates the swinger immediately, server side
        // broadcasts to observers. Server-only swing doesn't reliably animate the swinger's
        // own first-person hand — same reason ItemWrench_Neptune.wrenchUsed swings unconditionally.
        player.swing(hand);
        return BlockUtil.itemUseConsume();
    }
}
