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

/** The "fluidSource" world property: is the block at the position a source fluid block? Ported from 7.1.x
 *  {@code WorldPropertyIsFluidSource} (liquid/fluid block with metadata 0 = the source form); the modern
 *  equivalent is a {@code FluidState} that is {@link net.minecraft.world.level.material.FluidState#isSource
 *  a source} (standing water/lava, not a flowing/falling form). Registered from {@code BCCore.preInit}
 *  under the key {@code "fluidSource"}; the pump board queries it through {@link #matches(BlockState)}. */
public class WorldPropertyIsFluidSource implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: source-ness is a {@code FluidState} property, so {@link #get} delegates here
     *  and the JUnit predicate sweeps can drive it without a {@link Level}. */
    public boolean matches(BlockState state) {
        return !state.getFluidState().isEmpty() && state.getFluidState().isSource();
    }

    @Override
    public void clear() {
    }
}
