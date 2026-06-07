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
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.lib.engine.BlockEngineBase_BC8;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.BlockUtil;

public class BlockEngineCreative extends BlockEngineBase_BC8 {
    public BlockEngineCreative(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineCreative(pos, state);
    }

    /**
     * Creative engine has no GUI and never overheats. Wrench priority:
     *   1. Non-wrench → PASS.
     *   2. Crouch + wrench → rotate to next valid receiver (PASS so wrench.useOn handles
     *      rotation + slide sound + `wrenched`); tripwire-armed sound + CONSUME if no
     *      alternate receiver.
     *   3. Non-crouch + wrench → cycle output power, manually grant `wrenched`. Plays the
     *      tripwire sound (not the slide/piston sound, which is reserved for rotation).
     */
    @Override
    //? if >=1.21.10 {
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
    //?} else {
    /*protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {*/
    //?}
        if (!(stack.getItem() instanceof IToolWrench wrench)) {
            return BlockUtil.itemUsePass();
        }
        BlockEntity be = level.getBlockEntity(pos);

        if (player.isShiftKeyDown()) {
            if (be instanceof TileEngineBase_BC8 engine && engine.hasAlternateReceiver()) {
                return BlockUtil.itemUsePass();
            }
            if (!level.isClientSide()) {
                // Crouch-with-nothing-to-rotate-to: same soft-fail sound as the other engines.
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.3f);
            }
            player.swing(hand);
            return BlockUtil.itemUseConsume();
        }

        if (be instanceof TileEngineCreative creative) {
            creative.onWrenchInteract(player);
        }
        // wrenchUsed on both sides: server side awards `wrenched` (guarded internally by
        // ServerPlayer instanceof), both sides swing the arm. Matches ItemWrench_Neptune.useOn.
        wrench.wrenchUsed(player, hand, stack, hitResult);
        if (!level.isClientSide()) {
            // Cycle-power confirmation: lower-pitched lever click, distinct from the soft-fail.
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 0.7f);
        }
        return BlockUtil.itemUseConsume();
    }
}
