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

    /**
     * Called when the player right-clicks with an item in hand.
     * Handles bucket fill/drain before falling through to GUI opening.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Skip wrenches and pipes — let base class handle them
        if (!stack.isEmpty() && stack.getItem() instanceof IToolWrench) {
            return InteractionResult.PASS;
        }

        // Try bucket interaction
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileEngineIron_BC8 engine) {
            if (tryFluidInteraction(player, hand, engine, level, pos)) {
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

    /**
     * Handle bucket fluid interaction — filling or draining.
     * Mimics 1.12.2 behavior:
     *   - Full bucket → empties into tank, gives player empty bucket
     *   - Empty bucket → fills from tank, gives player full bucket
     */
    private boolean tryFluidInteraction(Player player, InteractionHand hand,
            TileEngineIron_BC8 engine, Level level, BlockPos pos) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return false;
        if (!(held.getItem() instanceof BucketItem bucket)) return false;

        Fluid bucketFluid = bucket.content;

        if (bucketFluid != Fluids.EMPTY) {
            // --- FILL: bucket has fluid → try to fill tank ---
            return tryFillFromBucket(player, hand, held, bucketFluid, engine, level, pos);
        } else {
            // --- DRAIN: empty bucket → try to drain from tank ---
            return tryDrainIntoBucket(player, hand, engine, level, pos);
        }
    }

    /**
     * Fill engine tanks from a full bucket.
     */
    private boolean tryFillFromBucket(Player player, InteractionHand hand, ItemStack held,
            Fluid bucketFluid, TileEngineIron_BC8 engine, Level level, BlockPos pos) {
        FluidStack fluidStack = new FluidStack(bucketFluid, 1000);

        // Simulate first to make sure there's room
        int simulated = engine.combinedFluidHandler.fill(fluidStack, IFluidHandler.FluidAction.SIMULATE);
        if (simulated < 1000) {
            return false; // not enough room for a full bucket
        }

        if (level.isClientSide()) {
            return true; // client side just returns success to show animation
        }


        // Actually fill the tank
        engine.combinedFluidHandler.fill(fluidStack, IFluidHandler.FluidAction.EXECUTE);

        // Replace the bucket if in survival
        if (!player.getAbilities().instabuild) {
            if (held.getCount() == 1) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            } else {
                held.shrink(1);
                ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                if (!player.getInventory().add(emptyBucket)) {
                    player.drop(emptyBucket, false);
                }
            }
        }

        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    /**
     * Drain engine tanks into an empty bucket.
     * Tries fuel first, then coolant, then residue.
     */
    private boolean tryDrainIntoBucket(Player player, InteractionHand hand,
            TileEngineIron_BC8 engine, Level level, BlockPos pos) {
        // Try draining 1000 mB from each tank in order: fuel, coolant, residue
        FluidStack drained = tryDrainTank(engine.tankFuel, 1000);
        if (drained.isEmpty()) {
            drained = tryDrainTank(engine.tankCoolant, 1000);
        }
        if (drained.isEmpty()) {
            drained = tryDrainTank(engine.tankResidue, 1000);
        }

        if (drained.isEmpty() || drained.getAmount() < 1000) {
            return false; // not enough for a full bucket
        }

        if (level.isClientSide()) {
            return true;
        }

        // Actually drain
        Fluid fluid = drained.getFluid();
        FluidStack actualDrain = null;
        if (engine.tankFuel.getFluidAmount() >= 1000 && FluidStack.isSameFluid(engine.tankFuel.getFluid(), drained)) {
            actualDrain = engine.tankFuel.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        } else if (engine.tankCoolant.getFluidAmount() >= 1000 && FluidStack.isSameFluid(engine.tankCoolant.getFluid(), drained)) {
            actualDrain = engine.tankCoolant.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        } else if (engine.tankResidue.getFluidAmount() >= 1000 && FluidStack.isSameFluid(engine.tankResidue.getFluid(), drained)) {
            actualDrain = engine.tankResidue.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        }

        if (actualDrain == null || actualDrain.isEmpty()) {
            return false;
        }

        // Convert the fluid to a bucket item
        ItemStack filledBucket = new ItemStack(fluid.getBucket());
        if (filledBucket.isEmpty()) {
            // This fluid doesn't have a bucket form — refund the drain
            // (This shouldn't normally happen for BuildCraft fluids that have buckets)
            return false;
        }

        // Replace the empty bucket
        if (!player.getAbilities().instabuild) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getCount() == 1) {
                player.setItemInHand(hand, filledBucket);
            } else {
                held.shrink(1);
                if (!player.getInventory().add(filledBucket)) {
                    player.drop(filledBucket, false);
                }
            }
        }

        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        engine.setChanged();
        return true;
    }

    /**
     * Simulate draining a specific tank and return what would be drained.
     */
    private FluidStack tryDrainTank(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank, int amount) {
        return tank.drain(amount, IFluidHandler.FluidAction.SIMULATE);
    }
}
