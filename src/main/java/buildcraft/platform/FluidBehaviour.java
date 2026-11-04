/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * BuildCraft's own gameplay on top of a registered fluid pair: the loader's flowing-fluid classes call these hooks from
 * their {@code tick}, {@code getSpread} and {@code canBeReplacedWith} overrides, so the logic stays in common code and
 * both loaders run the same rules. One behaviour serves the source and the flowing form; tell them apart by the fluid
 * state ({@link FluidState#isSource()}). The level is a server level whenever the loader ticks or spreads a fluid.
 */
public interface FluidBehaviour {
    /** A behaviour that adds nothing to the loader's plain flowing fluid. */
    FluidBehaviour NONE = new FluidBehaviour() {};

    /**
     * Runs first in every tick of the fluid at {@code pos}, before the vanilla flow update.
     *
     * @return true to end the tick there (the fluid is gone, for instance consumed by a blast)
     */
    default boolean beforeTick(FluidFlow flow, Level level, BlockPos pos, FluidState state) {
        return false;
    }

    /** Runs after the vanilla flow update of the fluid at {@code pos}. */
    default void afterTick(FluidFlow flow, Level level, BlockPos pos, FluidState state) {}

    /**
     * Adjusts where the fluid at {@code pos} spreads to: {@code spread} is vanilla's choice, mutable, and what is left
     * in it after this call is what the fluid spreads into.
     */
    default void adjustSpread(FluidFlow flow, Level level, BlockPos pos, BlockState state,
            Map<Direction, FluidState> spread) {}

    /**
     * Whether {@code state} (this fluid) refuses to be replaced by {@code by} flowing in from {@code direction}. False
     * leaves the decision to the loader's default (vanilla water's rule: only from above, and never the same fluid).
     */
    default boolean refusesReplacement(FluidState state, BlockGetter level, BlockPos pos, Fluid by,
            Direction direction) {
        return false;
    }
}
