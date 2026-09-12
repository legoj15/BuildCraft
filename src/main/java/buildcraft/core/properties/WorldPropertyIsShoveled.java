/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.IWorldProperty;

/** The "shoveled" world property: is the block at the position something a shovel digs? Ported from
 *  7.1.x {@code WorldPropertyIsShoveled} ({@code BlockDirt || BlockSand || BlockClay || BlockGravel ||
 *  BlockFarmland || BlockGrass || BlockSnow} — snow there is the thin layer, not the snow block). The
 *  modern mapping keeps that set block-for-block, with the two era differences the farmer's "dirt"
 *  property already established: {@link BlockTags#DIRT} stands in for {@code BlockDirt} (on 26.x that
 *  tag additionally holds podzol/mycelium/moss/mud, a harmless superset of "shovel dirt"), and grass
 *  block stays an explicit entry. Dirt path is deliberately absent — 7.1.x had no such block and the
 *  property is 7.1.x's, not vanilla {@code mineable/shovel}'s (which would also drag in soul sand,
 *  concrete powder and snow block). Registered from {@code BCCore.preInit} under the key
 *  {@code "shoveled"}; the shovelman board queries it through {@link #matches(BlockState)}. */
public class WorldPropertyIsShoveled implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: block identity needs no level, so {@link #get} delegates here and the JUnit
     *  predicate sweeps can drive it without a live server's data load. */
    public boolean matches(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.SNOW);
    }

    @Override
    public void clear() {
    }
}
