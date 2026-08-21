/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.IWorldProperty;

/** The "replaceable" world property: is the position air or otherwise replaceable? Ported from 7.1.x
 *  {@code WorldPropertyIsReplaceable} (null / air / {@code isReplaceable}); the modern equivalent is
 *  {@code state.isAir() || state.is(minecraft:replaceable)} — the tag is vanilla's own umbrella of
 *  plant-through blocks (air, water, grass, vines, snow, …). Like the upstream, this returns TRUE for air
 *  — the planter board NEGATES it (a seed wants a solid cell, not a hole). Registered from
 *  {@code BCCore.preInit} under the key {@code "replaceable"}; the planter board queries it through
 *  {@link #matches(BlockState)}. */
public class WorldPropertyIsReplaceable implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: air/replaceable needs no level, so {@link #get} delegates here and the JUnit
     *  predicate sweeps can drive it without a {@link Level}. */
    public boolean matches(BlockState state) {
        return state.isAir() || state.is(BlockTags.REPLACEABLE);
    }

    @Override
    public void clear() {
    }
}
