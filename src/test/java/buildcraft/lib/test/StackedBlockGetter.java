/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** A two-cell {@link BlockGetter}: one block at a position, whatever stands directly under it, and air
 *  everywhere else. Enough to drive the neighbour-reading crop rules (a stacking plant is "mature" only
 *  when it stands on its own kind) from a plain unit test, with no Level.
 *
 *  <p>The {@code LevelHeightAccessor} getter name cliffed at 1.21.10 ({@code getMinBuildHeight} →
 *  {@code getMinY}), so BOTH are declared here without {@code @Override}: on each node the abstract one is
 *  implemented and the other is simply an unused extra method. That keeps this shared test class free of
 *  Stonecutter directives. */
public final class StackedBlockGetter implements BlockGetter {

    private final BlockPos pos;
    private final BlockState here;
    private final BlockState below;

    public StackedBlockGetter(BlockPos pos, BlockState here, BlockState below) {
        this.pos = pos;
        this.here = here;
        this.below = below;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos at) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos at) {
        if (pos.equals(at)) {
            return here;
        }
        if (pos.below().equals(at)) {
            return below;
        }
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos at) {
        return getBlockState(at).getFluidState();
    }

    public int getHeight() {
        return 384;
    }

    public int getMinBuildHeight() {
        return -64;
    }

    public int getMinY() {
        return -64;
    }
}
