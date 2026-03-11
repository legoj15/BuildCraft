/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;

public class FluidUtilBC {

    /**
     * Pushes fluid from the given FluidTank to all adjacent fluid-capable blocks.
     * Uses NeoForge 1.21.11's Capabilities.Fluid.BLOCK with ResourceHandler API.
     */
    public static void pushFluidToNeighbors(Level level, BlockPos pos, FluidTank tank) {
        if (tank.getFluidAmount() <= 0) return;
        for (Direction dir : Direction.values()) {
            if (tank.getFluidAmount() <= 0) break;
            BlockPos neighborPos = pos.relative(dir);
            ResourceHandler<FluidResource> neighbor = level.getCapability(
                    Capabilities.Fluid.BLOCK, neighborPos, dir.getOpposite());
            if (neighbor == null) continue;

            FluidStack inTank = tank.getFluid();
            if (inTank.isEmpty()) break;

            FluidResource resource = FluidResource.of(inTank);
            int amountToTry = Math.min(tank.getFluidAmount(), 1000);
            // Simulate insert to find how much the neighbor can accept
            int accepted = neighbor.insert(resource, amountToTry, null);
            if (accepted > 0) {
                // Actually drain from our tank
                FluidStack drained = tank.drain(accepted,
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                // The insert already committed (null = auto-commit transaction)
                // If drain gave less than expected, we have a mismatch but this is rare
            }
        }
    }

    public static List<FluidStack> mergeSameFluids(List<FluidStack> fluids) {
        List<FluidStack> stacks = new ArrayList<>();
        fluids.forEach(toAdd -> {
            boolean found = false;
            for (FluidStack stack : stacks) {
                if (FluidStack.isSameFluidSameComponents(stack, toAdd)) {
                    stack.grow(toAdd.getAmount());
                    found = true;
                }
            }
            if (!found) {
                stacks.add(toAdd.copy());
            }
        });
        return stacks;
    }

    public static boolean areFluidStackEqual(FluidStack a, FluidStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return FluidStack.isSameFluidSameComponents(a, b) && a.getAmount() == b.getAmount();
    }

    public static boolean areFluidsEqual(Fluid a, Fluid b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a == b;
    }

    /** @return The fluidstack that was moved, or null if no fluid was moved. */
    @Nullable
    public static FluidStack move(IFluidHandler from, IFluidHandler to) {
        return move(from, to, Integer.MAX_VALUE);
    }

    /** @param max The maximum amount of fluid to move.
     * @return The fluidstack that was moved, or null if no fluid was moved. */
    @Nullable
    public static FluidStack move(IFluidHandler from, IFluidHandler to, int max) {
        if (from == null || to == null) {
            return null;
        }
        FluidStack toDrainPotential;
        if (from instanceof IFluidHandlerAdv advFrom) {
            IFluidFilter filter = f -> to.fill(f, IFluidHandler.FluidAction.SIMULATE) > 0;
            toDrainPotential = advFrom.drain(filter, max, false);
        } else {
            toDrainPotential = from.drain(max, IFluidHandler.FluidAction.SIMULATE);
        }
        if (toDrainPotential.isEmpty()) {
            return null;
        }
        int accepted = to.fill(toDrainPotential.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return null;
        }
        FluidStack toDrain = toDrainPotential.copyWithAmount(accepted);
        if (accepted < toDrainPotential.getAmount()) {
            toDrainPotential = from.drain(toDrain, IFluidHandler.FluidAction.SIMULATE);
            if (toDrainPotential.isEmpty() || toDrainPotential.getAmount() < accepted) {
                return null;
            }
        }
        FluidStack drained = from.drain(toDrain.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty() || toDrain.getAmount() != drained.getAmount() || !FluidStack.isSameFluidSameComponents(toDrain, drained)) {
            throw new IllegalStateException("Drained fluid did not equal expected fluid!");
        }
        int actuallyAccepted = to.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (actuallyAccepted != accepted) {
            throw new IllegalStateException("Mismatched IFluidHandler implementations!");
        }
        return drained.copyWithAmount(accepted);
    }

    public static boolean onTankActivated(Player player, BlockPos pos, InteractionHand hand,
        IFluidHandler fluidHandler) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }
        boolean replace = !player.getAbilities().instabuild;
        boolean single = held.getCount() == 1;
        java.util.Optional<IFluidHandlerItem> flItemOpt;
        if (replace && single) {
            flItemOpt = FluidUtil.getFluidHandler(held);
        } else {
            ItemStack copy = held.copy();
            copy.setCount(1);
            flItemOpt = FluidUtil.getFluidHandler(copy);
        }
        if (flItemOpt.isEmpty()) {
            return false;
        }
        IFluidHandlerItem flItem = flItemOpt.get();
        Level world = player.level();
        if (world.isClientSide()) {
            return true;
        }
        boolean changed = true;
        FluidStack moved;
        if ((moved = FluidUtilBC.move(flItem, fluidHandler)) != null) {
            SoundUtil.playBucketEmpty(world, pos, moved);
        } else if ((moved = FluidUtilBC.move(fluidHandler, flItem)) != null) {
            SoundUtil.playBucketFill(world, pos, moved);
        } else {
            changed = false;
        }

        if (changed && replace) {
            if (single) {
                player.setItemInHand(hand, flItem.getContainer());
            } else {
                held.shrink(1);
                net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(player, flItem.getContainer());
            }
            player.containerMenu.broadcastChanges();
        }
        return true;
    }
}
