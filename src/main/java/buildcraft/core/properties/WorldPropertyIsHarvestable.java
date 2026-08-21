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

/** The "harvestable" world property: is the block at the position a crop the
 *  {@link buildcraft.api.crops.CropManager} considers mature? Ported from 7.1.x
 *  {@code WorldPropertyIsHarvestable}. Registered from {@code BCCore.preInit} under the key
 *  {@code "harvestable"}; the harvester board queries it through {@link #matches(BlockState)}.
 *
 *  <p>Red-baseline skeleton: {@link #matches} returns false until the foundations commit lands the real
 *  implementation (the state-only crop-maturity check; {@link #get} keeps the full CropManager path with
 *  its block-below neighbours).</p> */
public class WorldPropertyIsHarvestable implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: the JUnit predicate sweeps drive it without a {@link Level}. */
    public boolean matches(BlockState state) {
        // Red-baseline skeleton — real implementation (CropManager maturity) in the foundations commit.
        return false;
    }

    @Override
    public void clear() {
    }
}
