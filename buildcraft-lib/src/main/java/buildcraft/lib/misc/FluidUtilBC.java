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
import net.neoforged.neoforge.transfer.transaction.Transaction;

import net.minecraft.world.level.material.Fluids;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;

public class FluidUtilBC {

    /**
     * Returns whether the given fluid should be rendered with translucent
     * blending (e.g. vanilla water) rather than opaque cutout.
     *
     * <p>In 1.12.2, each BC fluid had its own opaque procedurally-generated
     * texture. In 1.21.11, all BC energy fluids reuse vanilla
     * {@code water_still / water_flow} textures tinted with a colour, but
     * those textures have semi-transparent pixels. Without this check,
     * oil, fuel, etc. would all inherit water's translucency.
     *
     * <p>The heuristic is simple: only vanilla water (still or flowing)
     * is expected to be translucent. Every other fluid — including BC
     * fluids that happen to reuse water's texture as a tint base — uses
     * cutout rendering so the texture alpha is clamped to binary.
     */
    public static boolean shouldRenderTranslucent(Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    /** @see #shouldRenderTranslucent(Fluid) */
    public static boolean shouldRenderTranslucent(FluidStack stack) {
        return !stack.isEmpty() && shouldRenderTranslucent(stack.getFluid());
    }

    /**
     * Pushes fluid from the given FluidTank to all adjacent fluid-capable blocks.
     * Uses NeoForge 1.21.11's Capabilities.Fluid.BLOCK with ResourceHandler API.
     */
    @SuppressWarnings("removal")
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

            try (Transaction tx = Transaction.openRoot()) {
                int accepted = neighbor.insert(resource, amountToTry, tx);
                if (accepted > 0) {
                    FluidStack drained = tank.drain(accepted,
                            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    if (!drained.isEmpty() && drained.getAmount() > 0) {
                        tx.commit();
                    }
                }
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

    /** @return A debug string representation of the given fluid stack, or "empty" if empty. */
    public static String getDebugString(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return stack.getAmount() + " mB " + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
    }

    /** @return A debug string matching the 1.12.2 Tank format: "amount / capacity mB of fluidName". */
    public static String getDebugString(FluidTank tank) {
        FluidStack f = tank.getFluid();
        String name = f.isEmpty() ? "n/a" : net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.getFluid()).toString();
        return (f.isEmpty() ? 0 : f.getAmount()) + " / " + tank.getCapacity() + " mB of " + name;
    }

    /**
     * Returns whether the given fluid is gaseous (lighter than air).
     * In NeoForge 1.21.11 this checks {@code FluidType.isLighterThanAir()},
     * which returns true when density ≤ 0 — equivalent to the old
     * {@code Fluid.isGaseous()} from Forge 1.12.2.
     */
    public static boolean isGaseous(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().getFluidType().isLighterThanAir();
    }

    /** Overload for bare {@link Fluid} instances. */
    public static boolean isGaseous(Fluid fluid) {
        return fluid != null && fluid.getFluidType().isLighterThanAir();
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
