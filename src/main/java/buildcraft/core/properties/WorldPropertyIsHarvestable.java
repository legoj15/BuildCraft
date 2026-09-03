/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
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
 *  <p>Three entry points on purpose. {@link #get} and {@link #matches(BlockGetter, BlockPos)} keep the
 *  full CropManager path with its block-below neighbours — a stacking plant (cactus, sugar cane, cocoa) is
 *  mature only when it stands on its own kind, so the neighbour read is load-bearing and anything that
 *  drops it silently reports "never ripe" for those crops. The state-only {@link #matches(BlockState)} seam
 *  answers with an EMPTY getter, which is fine for crops whose ripeness is a function of their own state
 *  (wheat's age, nether wart's age) and is what the JUnit sweeps use; it must NOT be the search filter. */
public class WorldPropertyIsHarvestable implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world, pos);
    }

    /** The neighbour-aware seam: the crop AND whatever it is standing on. */
    public boolean matches(BlockGetter access, BlockPos pos) {
        return CropManager.isMature(access, access.getBlockState(pos), pos);
    }

    /** The state-only seam: the JUnit predicate sweeps drive it without a {@link Level}. Blind to the
     *  block below, so stacking crops never match through it. */
    public boolean matches(BlockState state) {
        return CropManager.isMature(EmptyBlockGetter.INSTANCE, state, BlockPos.ZERO);
    }

    @Override
    public void clear() {
    }
}
