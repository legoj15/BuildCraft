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

/** The "ore" world property: is the block at the position an ore a pickaxe of at most {@code harvestLevel}
 *  can harvest? Ported from 7.1.x {@code WorldPropertyIsOre} (ore-dict {@code ore*} names + the block's
 *  pickaxe tier); the modern equivalent of the ore umbrella is the eight {@code minecraft:*_ores} block
 *  tags, and the pickaxe tier is the smallest pickaxe tier whose item reports
 *  {@code isCorrectToolForDrops} for the state (unbreakable-with-no-tier states never match). Registered
 *  from {@code BCCore.preInit} under the keys {@code "ore@hardness=0"} .. {@code "ore@hardness=3"} (one
 *  instance per tier, as 7.1.x did); the miner board queries
 *  {@code "ore@hardness=" + min(3, heldPickaxeTier)} through {@link #matches(BlockState)}.
 *
 *  <p>Red-baseline skeleton: {@link #matches} returns false until the foundations commit lands the real
 *  implementation.</p> */
public class WorldPropertyIsOre implements IWorldProperty {

    /** The pickaxe tier this instance accepts (0 = wooden .. 3 = netherite-capped; 4 is clamped by the
     *  miner board, so four registered instances cover every held tool). */
    private final int harvestLevel;

    public WorldPropertyIsOre(int harvestLevel) {
        this.harvestLevel = harvestLevel;
    }

    public int getHarvestLevel() {
        return harvestLevel;
    }

    @Override
    public boolean get(Level world, BlockPos pos) {
        return matches(world.getBlockState(pos));
    }

    /** The state-only seam: tags + the pickaxe tier ladder both need no level, so {@link #get} delegates
     *  here and the JUnit predicate sweeps can drive it without a {@link Level}. */
    public boolean matches(BlockState state) {
        // Red-baseline skeleton — real implementation (ore tags + pickaxe tier ladder) in the
        // foundations commit.
        return false;
    }

    @Override
    public void clear() {
    }
}
