/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.BCEnergyBlocks;

/**
 * Engines do not carry a redstone signal through themselves, as in 1.7.10 and 1.12.2 (neither a normal cube there).
 * A lever on an engine's back switches the engine on; when the engine conducted, the same lever also powered the
 * machine the engine drives and paused it (a Quarry, a Mining Well or a Pump stops under a signal).
 */
public class EngineRedstoneConductionTest {

    @Test
    public void enginesDoNotConductRedstone() {
        List<Block> engines = List.of(BCCoreBlocks.ENGINE_REDSTONE.get(), BCCoreBlocks.ENGINE_CREATIVE.get(),
            BCEnergyBlocks.ENGINE_STONE.get(), BCEnergyBlocks.ENGINE_IRON.get(), BCEnergyBlocks.ENGINE_FE.get(),
            BCEnergyBlocks.DYNAMO_MJ.get());
        for (Block engine : engines) {
            for (Direction facing : Direction.values()) {
                BlockState state = engine.defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING_6, facing);
                Assertions.assertFalse(state.isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    engine + " facing " + facing + " conducts redstone");
            }
        }
    }
}
