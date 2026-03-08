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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import buildcraft.api.tools.IToolWrench;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.engine.BlockEngineBase_BC8;

public class BlockEngineIron_BC8 extends BlockEngineBase_BC8 {
    public BlockEngineIron_BC8(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineIron_BC8(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        // Check wrench first (handled by base class)
        ItemStack heldItem = player.getMainHandItem();
        if (!heldItem.isEmpty() && heldItem.getItem() instanceof IToolWrench) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }

        // Try bucket interaction with main hand
        if (!heldItem.isEmpty()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEngineIron_BC8 engine) {
                if (tryBucketFill(player, heldItem, engine, level, pos)) {
                    return InteractionResult.SUCCESS;
                }
            }
        }

        // Open GUI
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

    /**
     * Attempt to fill the engine's fuel or coolant tank from a bucket.
     * Mimics 1.12.2 behavior: right-click with a fluid bucket fills the
     * appropriate tank and replaces the bucket with an empty one.
     */
    private boolean tryBucketFill(Player player, ItemStack held, TileEngineIron_BC8 engine,
            Level level, BlockPos pos) {
        if (!(held.getItem() instanceof BucketItem bucket)) {
            return false;
        }

        // Get the fluid from the bucket
        Fluid bucketFluid = bucket.content;
        if (bucketFluid == Fluids.EMPTY) {
            return false; // empty bucket — skip
        }

        // Create a FluidStack with 1 bucket worth (1000 mB)
        FluidStack fluidStack = new FluidStack(bucketFluid, 1000);

        // Try filling fuel tank first, then coolant
        int filled = engine.combinedFluidHandler.fill(fluidStack, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) {
            return false; // not enough room for a full bucket
        }

        if (!level.isClientSide()) {
            engine.combinedFluidHandler.fill(fluidStack, IFluidHandler.FluidAction.EXECUTE);

            // Replace bucket with empty bucket in survival
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            }

            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        return true;
    }
}
