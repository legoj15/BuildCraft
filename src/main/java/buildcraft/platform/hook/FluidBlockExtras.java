/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What BuildCraft adds to a fluid's {@code LiquidBlock}: its steam, its answers to fire and its chain detonation. The
 * block itself is the loader's own class ({@code FluidServices#liquidBlock}), because the {@code LiquidBlock}
 * constructors differ by loader (MinecraftForge deprecates the one that takes the fluid for one that takes a supplier,
 * which only it has) and the constructor a subclass calls cannot be chosen in common code. That class forwards every
 * override to this object, so the behaviour is written once; see {@link Host}.
 */
public interface FluidBlockExtras extends FireReactiveBlock {
    /**
     * The block's scheduled tick, run after vanilla's. A liquid block is ticked as a fluid, and as a block only for a
     * bubble column (26.1 and later), which a BuildCraft fluid never makes, so a call is the chain of a neighbouring
     * detonation.
     */
    void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random);

    /** The client's random display tick, run after vanilla's. */
    void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random);

    /**
     * A {@code LiquidBlock} carrying {@link FluidBlockExtras}: the loader's own subclass, so how it asks fire and lava
     * questions is the loader's concern, and everything else asks through this. The fire questions are answered by the
     * extras, which is where Fabric's mixins find them ({@link FireReactiveBlock}).
     */
    interface Host extends FireReactiveBlock {
        /** The behaviour this block was built with. */
        FluidBlockExtras extras();

        @Override
        default int fireBurnOdds(BlockState state) {
            return extras().fireBurnOdds(state);
        }

        @Override
        default int fireIgniteOdds(BlockState state) {
            return extras().fireIgniteOdds(state);
        }

        @Override
        default boolean lavaIgnites(BlockState state) {
            return extras().lavaIgnites(state);
        }

        @Override
        default boolean catchFire(BlockState state, Level level, BlockPos pos) {
            return extras().catchFire(state, level, pos);
        }
    }
}
