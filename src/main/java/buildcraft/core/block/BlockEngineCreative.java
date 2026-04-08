/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
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
     * 1.12.2 parity:
     * - Crouch + wrench: rotate to next valid receiver (delegate to base class)
     * - Normal wrench: cycle output power (creative engine specific)
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof IToolWrench) {
            if (player.isShiftKeyDown()) {
                // Crouch + wrench = rotate to next valid receiver (base class handles this)
                return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
            }
            // Normal wrench = cycle output power
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof TileEngineCreative creative) {
                    creative.onWrenchInteract(player);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
