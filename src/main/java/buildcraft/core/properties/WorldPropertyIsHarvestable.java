/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.IWorldProperty;
import buildcraft.api.crops.CropManager;

/** The "harvestable" world property: is the block at the position a crop the
 *  {@link CropManager} considers mature? Ported from 7.1.x {@code WorldPropertyIsHarvestable}. Registered
 *  from {@code BCCore.preInit} under the key {@code "harvestable"}; the harvester board queries it through
 *  {@link #matches(BlockState)}.
 *
 *  <p>Two entry points on purpose: {@link #get} keeps the full CropManager path with its block-below
 *  neighbours (a {@code BushBlock} such as cocoa/sugar cane is mature only when stacked on its own block),
 *  while the state-only {@link #matches} seam answers with an empty getter — the JUnit sweeps and the
 *  search-AI filter need no live level. */
public class WorldPropertyIsHarvestable implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return CropManager.isMature(world, world.getBlockState(pos), pos);
    }

    /** The state-only seam: the JUnit predicate sweeps drive it without a {@link Level}. */
    public boolean matches(BlockState state) {
        return CropManager.isMature(EmptyBlockGetter.INSTANCE, state, BlockPos.ZERO);
    }

    @Override
    public void clear() {
    }
}
