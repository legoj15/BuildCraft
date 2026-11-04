/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block whose answers to fire depend on more than vanilla's fixed flammability table: NeoForge's
 * {@code getFlammability}, {@code getFireSpreadSpeed}, {@code ignitedByLava} and {@code onCaughtFire}.
 */
public interface FireReactiveBlock {
    /** How likely fire next to this block destroys it (vanilla's burn odds; NeoForge's flammability). */
    int fireBurnOdds(BlockState state);

    /** How likely fire spreads onto this block (vanilla's ignite odds; NeoForge's fire spread speed). */
    int fireIgniteOdds(BlockState state);

    /** Whether lava's random tick may set fire above this block. */
    boolean lavaIgnites(BlockState state);

    /** Fire just caught this block; true when it reacted (NeoForge's {@code onCaughtFire} answer). */
    boolean catchFire(BlockState state, Level level, BlockPos pos);
}
