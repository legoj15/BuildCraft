/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.IWorldProperty;

/** The "dirt" world property: is the block at the position tillable dirt? Ported from 7.1.x
 *  {@code WorldPropertyIsDirt} ({@code BlockDirt || BlockGrass}); the modern set is the
 *  {@code minecraft:dirt} tag plus an explicit grass block — the 26.x tag covers dirt/coarse dirt/rooted
 *  dirt but not grass, while the 1.21.x tag already includes grass (and podzol/mycelium/moss/mud, a
 *  harmless superset). Registered from {@code BCCore.preInit} under the key {@code "dirt"}; the farmer
 *  board queries it through {@link #matches(BlockState)}. */
public class WorldPropertyIsDirt implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: block identity needs no level, so {@link #get} delegates here and the JUnit
     *  predicate sweeps can drive it without a {@link Level}. */
    public boolean matches(BlockState state) {
        return state.is(BlockTags.DIRT) || state.getBlock() == Blocks.GRASS_BLOCK;
    }

    @Override
    public void clear() {
    }
}
