/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.mj.MjAPI;

public class BlockUtil {
    /** Mining multiplier, set by BCCoreConfig on init. Default 1.0. */
    public static double miningMultiplier = 1.0;

    /** Returns the fluid associated with a block if it is a fluid block, or null otherwise. */
    @Nullable
    public static Fluid getFluidWithFlowing(Block block) {
        if (block instanceof LiquidBlock liquidBlock) {
            Fluid fluid = liquidBlock.fluid;
            if (fluid != null && fluid != Fluids.EMPTY) {
                return fluid;
            }
        }
        return null;
    }

    /** Returns the fluid at a world position, including flowing fluids, or null if none. */
    @Nullable
    public static Fluid getFluidWithFlowing(Level world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        if (!fluidState.isEmpty()) {
            return fluidState.getType();
        }
        return getFluidWithFlowing(world.getBlockState(pos).getBlock());
    }

    /**
     * Returns the fluid at a world position only if it is a full source block.
     * Returns null for flowing fluid or non-fluid blocks.
     */
    @Nullable
    public static Fluid getFluid(Level world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        if (!fluidState.isEmpty() && fluidState.isSource()) {
            return fluidState.getType();
        }
        return null;
    }

    /**
     * Drains a fluid source block from the world.
     *
     * @param world    the level
     * @param pos      the position to drain
     * @param doDrain  if true, actually removes the block; if false, simulates only
     * @return a FluidStack of 1000mB if a source block was present, or null
     */
    @Nullable
    public static FluidStack drainBlock(Level world, BlockPos pos, boolean doDrain) {
        FluidState fluidState = world.getFluidState(pos);
        if (fluidState.isEmpty() || !fluidState.isSource()) {
            return null;
        }
        Fluid fluid = fluidState.getType();
        if (doDrain) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return new FluidStack(fluid, 1000);
    }

    /** Creates a comparator that falls back to coordinate comparison when the parent reports equality,
     * ensuring a total ordering for use with sorted streams. */
    public static Comparator<BlockPos> uniqueBlockPosComparator(Comparator<BlockPos> parent) {
        return (a, b) -> {
            int parentValue = parent.compare(a, b);
            if (parentValue != 0) {
                return parentValue;
            } else if (a.getX() != b.getX()) {
                return Integer.compare(a.getX(), b.getX());
            } else if (a.getY() != b.getY()) {
                return Integer.compare(a.getY(), b.getY());
            } else if (a.getZ() != b.getZ()) {
                return Integer.compare(a.getZ(), b.getZ());
            } else {
                return 0;
            }
        };
    }

    /** Returns true if the block at the given position is unbreakable (hardness < 0). */
    public static boolean isUnbreakableBlock(Level world, BlockPos pos, GameProfile owner) {
        BlockState state = world.getBlockState(pos);
        return state.getDestroySpeed(world, pos) < 0;
    }

    /** Computes the MJ power required to break a block, based on its hardness. */
    public static long computeBlockBreakPower(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        float hardness = state.getDestroySpeed(world, pos);
        return (long) Math.floor(16 * MjAPI.MJ * ((hardness + 1) * 2) * miningMultiplier);
    }

    /**
     * Breaks a block in the world and returns its drops, or empty if the block could not be broken.
     *
     * @param world  the server world
     * @param pos    the position of the block
     * @param tool   the tool to use for breaking (affects drop calculation)
     * @param owner  the player profile responsible for the break
     * @return an Optional containing the list of dropped items, or empty if the break failed
     */
    public static Optional<List<ItemStack>> breakBlockAndGetDrops(
            ServerLevel world, BlockPos pos, @Nonnull ItemStack tool, GameProfile owner) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return Optional.of(List.of());
        }
        if (state.getDestroySpeed(world, pos) < 0) {
            return Optional.empty();
        }

        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, world, pos, world.getBlockEntity(pos)));

        // Remove the block
        world.removeBlock(pos, false);

        return Optional.of(drops);
    }
}
