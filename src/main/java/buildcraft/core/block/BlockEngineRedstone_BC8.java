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

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tools.IToolWrench;
import buildcraft.core.tile.TileEngineRedstone_BC8;
import buildcraft.lib.engine.BlockEngineBase_BC8;
import buildcraft.lib.engine.TileEngineBase_BC8;

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
     * Redstone engine: normal wrench click rotates to next valid receiver.
     * In 1.12.2, redstone engine had no onActivated override, so wrench
     * interaction was handled entirely by ICustomRotationHandler.attemptRotation().
     * Both normal and crouch+wrench rotate (crouch handled by base class).
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof IToolWrench) {
            // Both normal and crouch wrench rotate to next valid receiver
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof TileEngineBase_BC8 engine) {
                    if (engine.attemptRotation()) {
                        level.setBlock(pos, state.setValue(
                                BuildCraftProperties.BLOCK_FACING_6, engine.getOrientation()), 3);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
