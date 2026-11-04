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

import net.minecraft.world.level.material.Fluids;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.lib.transfer.FluidStorageUtil;
import buildcraft.platform.Platform;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.TransferAccess;

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
    public static boolean shouldRenderTranslucent(BCFluidStack stack) {
        return !stack.isEmpty() && shouldRenderTranslucent(stack.getFluid());
    }

    /** Pushes up to a bucket of the tank's first slot into each adjacent fluid storage, in direction order. */
    public static void pushFluidToNeighbors(Level level, BlockPos pos, BCFluidTank tank) {
        if (tank.getAmountMb(0) <= 0) return;
        for (Direction dir : Direction.values()) {
            if (tank.getAmountMb(0) <= 0) break;
            BCFluidStorage neighbor = TransferAccess.findFluid(level, pos.relative(dir), dir.getOpposite());
            if (neighbor == null) continue;
            if (tank.isTankEmpty(0)) break;
            int amountToTry = Math.min(tank.getAmountMb(0), 1000);
            FluidStorageUtil.move(tank, neighbor, r -> true, amountToTry, null);
        }
    }

    public static List<BCFluidStack> mergeSameFluids(List<BCFluidStack> fluids) {
        List<BCFluidStack> stacks = new ArrayList<>();
        fluids.forEach(toAdd -> {
            boolean found = false;
            for (BCFluidStack stack : stacks) {
                if (BCFluidStack.isSameFluidSameComponents(stack, toAdd)) {
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

    public static boolean areFluidStackEqual(BCFluidStack a, BCFluidStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return BCFluidStack.isSameFluidSameComponents(a, b) && a.getAmount() == b.getAmount();
    }

    public static boolean areFluidsEqual(Fluid a, Fluid b) {
        if (a == null || b == null) {
            return a == b;
        }
        // Mojang split source/flowing into separate Fluid instances in 1.13, so
        // strict `==` returns false between Fluids.WATER and Fluids.FLOWING_WATER.
        // Compare by kind (NeoForge: the FluidType), which the two variants of the
        // same base fluid share (e.g. NeoForgeMod.WATER_TYPE for both water variants).
        return a == b || Platform.fluids().isSameKind(a, b);
    }

    /**
     * Moves fluid between two storages.
     * @return The fluidstack that was moved, or null if no fluid was moved.
     */
    @Nullable
    public static BCFluidStack move(BCFluidStorage from, BCFluidStorage to) {
        return move(from, to, Integer.MAX_VALUE);
    }

    /**
     * Moves a maximum amount of fluid between two storages.
     * @param max The maximum amount of fluid to move.
     * @return The fluidstack that was moved, or null if no fluid was moved.
     */
    @Nullable
    public static BCFluidStack move(BCFluidStorage from, BCFluidStorage to, int max) {
        return FluidStorageUtil.moveAndReport(from, to, max);
    }

    /** @return A debug string representation of the given fluid stack, or "empty" if empty. */
    public static String getDebugString(BCFluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return stack.getAmount() + " mB " + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
    }

    /**
     * Returns whether the given fluid is gaseous (lighter than air).
     * On NeoForge this is {@code FluidType.isLighterThanAir()},
     * which returns true when density ≤ 0 — equivalent to the old
     * {@code Fluid.isGaseous()} from Forge 1.12.2.
     */
    public static boolean isGaseous(BCFluidStack fluid) {
        return !fluid.isEmpty() && Platform.fluids().isLighterThanAir(fluid.getFluid());
    }

    /** Overload for bare {@link Fluid} instances. */
    public static boolean isGaseous(Fluid fluid) {
        return fluid != null && Platform.fluids().isLighterThanAir(fluid);
    }

    /**
     * A right click on a tank with a held item: fills the item from the tank, else empties it into the tank,
     * swapping the held item and playing the bucket sound. Returns whether anything happened (true on the client).
     */
    public static boolean onTankActivated(Player player, BlockPos pos, InteractionHand hand, BCFluidStorage fluidHandler) {
        return Platform.transfer().interactWithHeldContainer(player, hand, pos, fluidHandler);
    }

    // ---- Item fluid containers -------------------------------------------------------------------------------
    // The single seam every BuildCraft call site uses to read, fill or empty a fluid container item, so the call
    // sites stay loader-free: the platform layer does the item swap the loader's way (NeoForge 1.21.10+: an ItemAccess;
    // 1.21.1: the classic FluidUtil; Fabric: a ContainerItemContext). FluidContainerHelperTest pins the behaviour.

    /**
     * The fluid held by ONE item of the given stack (the stack's count is ignored), or {@link BCFluidStack#EMPTY} for
     * an empty container, a non-container or the empty stack. Never modifies {@code stack}.
     */
    public static BCFluidStack getFluidContained(ItemStack stack) {
        return stack.isEmpty() ? BCFluidStack.EMPTY : Platform.transfer().getFluidContained(stack);
    }

    /** Whether the item exposes an item fluid handler at all — empty containers (e.g. an empty bucket) included. */
    public static boolean isFluidContainer(ItemStack stack) {
        return !stack.isEmpty() && Platform.transfer().isFluidContainer(stack);
    }

    /**
     * Empties the single fluid container sitting in {@code slots[slot]} into {@code tank}, swapping it in place for
     * whatever the container becomes (an empty bucket, or nothing for a consumable one). A stack of more than one
     * container is left alone — there is nowhere to put the emptied item, and swapping the whole stack for one
     * result would delete the rest.
     *
     * @return true if any fluid moved.
     */
    public static boolean drainContainerSlot(ItemHandlerSimple slots, int slot, BCFluidStorage tank) {
        if (slots.getStackInSlot(slot).getCount() != 1) {
            return false; // an empty slot, or a stack with nowhere to put the swapped container
        }
        return Platform.transfer().drainContainerSlot(slots, slot, result -> slots.setStackInSlot(slot, result), tank);
    }

    /**
     * Fills the single fluid container sitting in {@code slots[slot]} from {@code tank}, swapping it in place for the
     * filled item. A stack of more than one container is left alone, as in {@link #drainContainerSlot}.
     *
     * @return true if any fluid moved.
     */
    public static boolean fillContainerSlot(ItemHandlerSimple slots, int slot, BCFluidStorage tank) {
        if (slots.getStackInSlot(slot).getCount() != 1) {
            return false; // an empty slot, or a stack with nowhere to put the swapped container
        }
        return Platform.transfer().fillContainerSlot(slots, slot, result -> slots.setStackInSlot(slot, result), tank);
    }

    public static ItemStack getFilledBucket(BCFluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (fluidStack.getComponents().isEmpty()) {
            if (fluidStack.is(Fluids.WATER)) {
                return new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
            } else if (fluidStack.is(Fluids.LAVA)) {
                return new ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);
            }
        }
        return Platform.fluids().filledBucket(fluidStack);
    }
}
