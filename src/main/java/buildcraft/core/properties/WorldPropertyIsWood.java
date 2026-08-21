/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.IWorldProperty;

/** The "wood" world property: is the block at the position a log? Ported from 7.1.x
 *  {@code WorldPropertyIsWood} (ore-dict {@code logWood}); the modern equivalent of the log umbrella is the
 *  {@code minecraft:logs} tag. Registered from {@code BCCore.preInit} under the key {@code "wood"}; the
 *  lumberjack board queries it through {@link #matches(BlockState)}.
 *
 *  <p>Red-baseline skeleton: {@link #matches} returns false until the foundations commit lands the real
 *  implementation.</p> */
public class WorldPropertyIsWood implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: the full check needs no level, so {@link #get} delegates here and the JUnit
     *  predicate sweeps can drive it without a {@link Level}. */
    public boolean matches(BlockState state) {
        // Red-baseline skeleton — real implementation (minecraft:logs tag) in the foundations commit.
        return false;
    }

    @Override
    public void clear() {
    }
}
