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
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.engine.BlockEngineBase_BC8;
import buildcraft.lib.misc.FluidUtilBC;

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
     * Called when the player right-clicks with an item in hand.
     * Handles fluid container fill/drain before falling through to GUI opening.
     * Uses FluidUtilBC.onTankActivated which handles ALL fluid container types
     * (buckets, tanks, etc.) via NeoForge's FluidUtil.getFluidHandler — matching
     * the 1.12.2 behavior where super.onActivated() used FluidUtil.interactWithFluidHandler.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Wrenches: non-crouching wrench opens GUI (matches 1.12.2)
        // Crouch+wrench falls through to PASS -> Item.useOn() -> ICustomRotationHandler rotation
        if (!stack.isEmpty() && stack.getItem() instanceof IToolWrench) {
            if (!player.isShiftKeyDown()) {
                return openGui(state, level, pos, player);
            }
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        // Try fluid container interaction (buckets, tanks, etc.)
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEngineIron_BC8 engine) {
            if (FluidUtilBC.onTankActivated(player, pos, hand, engine.combinedFluidHandler)) {
                return InteractionResult.SUCCESS;
            }
        }

        // Not a fluid container — open GUI (same as useWithoutItem)
        // We can't return PASS here because that invokes the item's useOn,
        // not useWithoutItem, so the GUI would never open when holding items.
        return openGui(state, level, pos, player);
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
