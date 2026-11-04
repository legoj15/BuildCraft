/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

/**
 * The vanilla flow steps of a registered BuildCraft fluid that {@link FlowingFluid} keeps protected, opened for its
 * {@link FluidBehaviour}. Every registered BuildCraft fluid (source and flowing form, on either loader) implements it, so
 * a {@code Fluid} from the registrar can be cast to it. The {@code Level} parameters must be server levels (vanilla's
 * flow code takes {@code ServerLevel} from 1.21.10).
 */
public interface FluidFlow {
    /** The fluid itself. */
    FlowingFluid fluid();

    /** How much the level drops per block flowed ({@code FlowingFluid#getDropOff}). */
    int dropOffPerBlock(LevelReader level);

    /** Where the fluid at {@code pos} spreads to, after its behaviour adjusted it ({@code FlowingFluid#getSpread}). */
    Map<Direction, FluidState> spreadTargets(Level level, BlockPos pos, BlockState state);

    /** Spreads into {@code pos} as {@code target} ({@code FlowingFluid#spreadTo}). */
    void spreadInto(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target);

    /** The fluid state that would form at {@code pos} ({@code FlowingFluid#getNewLiquid}). */
    FluidState newLiquidAt(Level level, BlockPos pos, BlockState state);
}
