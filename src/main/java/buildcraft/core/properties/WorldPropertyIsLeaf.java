/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.core.properties;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.IWorldProperty;

/** The "leaves" world property: is the block at the position a tree leaf? Ported from 7.1.x
 *  {@code WorldPropertyIsLeaf}, whose ore-dictionary {@code treeLeaves} membership is exactly the modern
 *  {@link BlockTags#LEAVES} tag (both are "every leaf block, vanilla and modded, that a tree is made
 *  of"). Registered from {@code BCCore.preInit} under the key {@code "leaves"}; the leave-cutter board
 *  queries it through {@link #matches(BlockState)}. */
public class WorldPropertyIsLeaf implements IWorldProperty {
    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: tag membership needs no level, so {@link #get} delegates here and the JUnit
     *  predicate sweeps can drive it without a live server's data load. */
    public boolean matches(BlockState state) {
        return state.is(BlockTags.LEAVES);
    }

    @Override
    public void clear() {
    }
}
