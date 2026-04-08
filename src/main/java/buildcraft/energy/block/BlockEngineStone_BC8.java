/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.lib.engine.BlockEngineBase_BC8;

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
     * Handle items: non-wrench items or non-crouching wrench = open GUI.
     * Crouch+wrench is handled by base class (rotation).
     * 
     * In 1.12.2, the stirling engine had no tile onActivated override,
     * so non-wrench interaction always opened the GUI via the block class.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Let base class handle crouch+wrench for rotation
        if (stack.getItem() instanceof IToolWrench && player.isShiftKeyDown()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        // Do not open GUI when holding a pipe — let the pipe be placed (1.12.2 parity)
        if (stack.getItem() instanceof IItemPipe) {
            return InteractionResult.PASS;
        }
        // Everything else (including non-crouching wrench) opens GUI
        return openGui(state, level, pos, player);
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
}
