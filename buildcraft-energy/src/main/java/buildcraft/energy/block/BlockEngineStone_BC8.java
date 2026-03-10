/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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
     * Empty hand right-click opens GUI.
     * Wrench interaction is handled by the base class:
     * - Crouch+wrench = rotate to next valid receiver
     * - Normal wrench = PASS (falls through, Minecraft tries useWithoutItem next)
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
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
