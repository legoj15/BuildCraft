/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class OilWaterInteractionTest {

    /** Interior of {@link #buildBasin}: a 3x3 of cells at x/z 1..3. */
    private static final BlockPos CENTRE = new BlockPos(2, 1, 2);

    /** A stone basin — floor at y=0 over x/z 0..4 and a two-high wall ring around the 3x3 interior — so no fluid
     *  poured into it can leave this test's 6x7 arena cell. The old open layout let the water source run 7
     *  blocks out over the arena floor, flooding the neighbouring tests' arenas. */
    static void buildBasin(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean rim = x == 0 || x == 4 || z == 0 || z == 4;
                helper.setBlock(new BlockPos(x, 1, z), rim ? Blocks.STONE : Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE : Blocks.AIR);
            }
        }
    }

    /** Fills the basin's bottom layer (y=1) with {@code fluid} sources — a still pool that cannot flow. */
    static void fillPool(GameTestHelper helper, Block fluid) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), fluid);
            }
        }
    }

    /** A light (non-dense) oil poured onto water floats: it leaves the water beneath it alone and spreads
     *  sideways across the surface, rather than stalling as a one-block puddle. This is the only coverage of
     *  that spread path, which every non-dense BC fluid shares (crude oil and the refined fuels alike). */
    public static void testOilOverWater(GameTestHelper helper) {
        buildBasin(helper);
        fillPool(helper, Blocks.WATER);
        BlockPos oilPos = CENTRE.above();
        helper.setBlock(oilPos, BCEnergyFluids.OIL_COOL.block().get());

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WATER, CENTRE);
            helper.assertBlockPresent(BCEnergyFluids.OIL_COOL.block().get(), oilPos.east());
        });
    }

    /** The sideways spread over water never targets a neighbour that already holds the same oil: with an oil
     *  source on the surface and an oil neighbour (source, then flowing) to its east, only the three free sides
     *  get a spread entry. Pins the behaviour the "layer cake" guard in {@code BCEnergyFluids.getSpread} was
     *  added for, independent of whether that guard stays (the target fluid's own replace check refuses it).
     *  The one exception is 1.21.1's vanilla spread, which already pushes into a lower flowing neighbour itself. */
    public static void testOilSpreadSkipsSameFluidNeighbour(GameTestHelper helper) {
        buildBasin(helper);
        fillPool(helper, Blocks.WATER);
        BlockPos oilPos = CENTRE.above();
        BlockPos neighbourPos = oilPos.east();
        helper.setBlock(oilPos, BCEnergyFluids.OIL_COOL.block().get());

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(oilPos);
        BlockPos neighbourAbs = helper.absolutePos(neighbourPos);
        Set<Direction> free = EnumSet.of(Direction.WEST, Direction.NORTH, Direction.SOUTH);

        BCEnergyFluids.BCCustomSourceFluid source =
                (BCEnergyFluids.BCCustomSourceFluid) BCEnergyFluids.OIL_COOL.source().get();
        BCEnergyFluids.BCCustomFlowingFluid flowing =
                (BCEnergyFluids.BCCustomFlowingFluid) BCEnergyFluids.OIL_COOL.flowing().get();

        // Neighbour is an oil source.
        helper.setBlock(neighbourPos, BCEnergyFluids.OIL_COOL.block().get());
        BlockState state = level.getBlockState(abs);
        assertSpreadSides("source fluid, source neighbour", free, source.getSpread(level, abs, state));
        assertSpreadSides("flowing fluid, source neighbour", free, flowing.getSpread(level, abs, state));

        // Neighbour is flowing oil.
        level.setBlock(neighbourAbs, flowing.getFlowing(3, false).createLegacyBlock(), 3);
        //? if >=1.21.10 {
        Set<Direction> overFlowing = free;
        //?} else {
        /*// 1.21.1's vanilla getSpread still pushes into a lower flowing neighbour on its own, before BuildCraft's
        // addition runs (measured identical with and without the same-fluid guard), so east is expected there.
        Set<Direction> overFlowing = EnumSet.of(Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.EAST);*/
        //?}
        assertSpreadSides("source fluid, flowing neighbour", overFlowing, source.getSpread(level, abs, state));
        assertSpreadSides("flowing fluid, flowing neighbour", overFlowing, flowing.getSpread(level, abs, state));

        helper.succeed();
    }

    private static void assertSpreadSides(String what, Set<Direction> expected, Map<Direction, FluidState> spread) {
        if (!spread.keySet().equals(expected)) {
            throw new IllegalStateException("Oil spread over water (" + what + ") must target exactly " + expected
                    + " but targeted " + spread.keySet());
        }
        for (FluidState target : spread.values()) {
            if (target.isEmpty() || !target.getType().isSame(BCEnergyFluids.OIL_COOL.source().get())) {
                throw new IllegalStateException("Oil spread over water (" + what + ") produced a non-oil target: " + target);
            }
        }
    }
}
