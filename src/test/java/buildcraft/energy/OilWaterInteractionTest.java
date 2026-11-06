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

import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.platform.FluidFlow;

public class OilWaterInteractionTest {

    /** Interior of {@link #buildBasin}: a 3x3 of cells at x/z 1..3. */
    private static final BlockPos CENTRE = new BlockPos(2, 1, 2);

    /** The work budget of the oil-over-water spread poll, counted from the cell ticking (the poll's own budget adds
     *  {@code EntityArenaUtil.CELL_READY_TICKS}, read as such by {@code GameTestPollBudgetTester}; the manifest's
     *  {@code max_ticks} stays above the sum). It keeps the headroom the old 400-tick watchdog gave the spread. */
    private static final int OIL_SPREAD_WORK_TICKS = 390;

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
        // A fluid's spread runs as a scheduled tick, and that runs only in a chunk that is ticking. The framework
        // forces only the structure's chunk, and the 6x7 cell crosses a chunk border in about 57% of runs, so the
        // basin's far half would sit in a neighbour that starts ticking later: force the cell, and start the poll's
        // work budget only once it ticks (buildBasin stays pure for the synchronous getSpread test below).
        EntityArenaUtil.forceLoadCell(helper);
        buildBasin(helper);
        fillPool(helper, Blocks.WATER);
        BlockPos oilPos = CENTRE.above();
        helper.setBlock(oilPos, BCEnergyFluids.OIL_COOL.block().get());

        BlockPos spreadPos = oilPos.east();
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + OIL_SPREAD_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, OIL_SPREAD_WORK_TICKS,
                        () -> helper.getBlockState(CENTRE).is(Blocks.WATER)
                                && helper.getBlockState(spreadPos).is(BCEnergyFluids.OIL_COOL.block().get()),
                        () -> "the water beneath the oil is "
                                + (helper.getBlockState(CENTRE).is(Blocks.WATER) ? "still there" : "gone")
                                + ", the cell east of the oil holds " + helper.getBlockState(spreadPos)),
                helper::succeed,
                "the oil never spread sideways over the water");
    }

    /** The sideways spread over water never targets a neighbour that already holds the same oil: with an oil
     *  source on the surface and an oil neighbour (source, then flowing) to its east, only the three free sides
     *  get a spread entry. Pins the behaviour the "layer cake" guard in {@code BCEnergyFluids.OilBehaviour.adjustSpread} was
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

        FluidFlow source = (FluidFlow) BCEnergyFluids.OIL_COOL.source().get();
        FluidFlow flowing = (FluidFlow) BCEnergyFluids.OIL_COOL.flowing().get();

        // Neighbour is an oil source.
        helper.setBlock(neighbourPos, BCEnergyFluids.OIL_COOL.block().get());
        BlockState state = level.getBlockState(abs);
        assertSpreadSides("over water, source fluid, source neighbour", free, source.spreadTargets(level, abs, state));
        assertSpreadSides("over water, flowing fluid, source neighbour", free, flowing.spreadTargets(level, abs, state));

        // Neighbour is flowing oil.
        level.setBlock(neighbourAbs, flowing.fluid().getFlowing(3, false).createLegacyBlock(), 3);
        //? if >=1.21.10 {
        Set<Direction> overFlowing = free;
        //?} else {
        /*// 1.21.1's vanilla getSpread still pushes into a lower flowing neighbour on its own, before BuildCraft's
        // addition runs (measured identical with and without the same-fluid guard), so east is expected there.
        Set<Direction> overFlowing = EnumSet.of(Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.EAST);*/
        //?}
        assertSpreadSides("over water, source fluid, flowing neighbour", overFlowing, source.spreadTargets(level, abs, state));
        assertSpreadSides("over water, flowing fluid, flowing neighbour", overFlowing, flowing.spreadTargets(level, abs, state));

        helper.succeed();
    }

    /** A light oil SOURCE resting on its own fluid keeps the forced sideways spread the old {@code BCCustomSourceFluid}
     *  had (issue #98 restored it after the platform layer folded both forms into one rule): it tops up every open
     *  neighbour, not only the side vanilla's slope search picks. Only the FLOWING form leaves a fluid that rests on
     *  its own family to vanilla's choice (the "layer cake" guard of {@code OilBehaviour.adjustSpread}), so the two
     *  forms differ here, and this pins both halves.
     *
     *  <p>The rig is a stone basin with one crude-oil source (y=1) under the tested source (y=2) and a one-deep pit
     *  east of it: the pit is a hole, which vanilla's spread alone answers with just EAST, so the forced three sides
     *  (north, south, west) are exactly what tells the source form from the flowing form. */
    public static void testOilSourceOverOwnFluidKeepsForcedSpread(GameTestHelper helper) {
        buildBasin(helper);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.AIR);
        helper.setBlock(CENTRE, BCEnergyFluids.OIL_COOL.block().get());
        BlockPos oilPos = CENTRE.above();
        helper.setBlock(oilPos, BCEnergyFluids.OIL_COOL.block().get());

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(oilPos);
        BlockState state = level.getBlockState(abs);
        FluidFlow source = (FluidFlow) BCEnergyFluids.OIL_COOL.source().get();
        FluidFlow flowing = (FluidFlow) BCEnergyFluids.OIL_COOL.flowing().get();

        assertSpreadSides("source fluid over its own fluid, pit to the east",
                EnumSet.of(Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST),
                source.spreadTargets(level, abs, state));
        assertSpreadSides("flowing fluid over its own fluid, pit to the east", EnumSet.of(Direction.EAST),
                flowing.spreadTargets(level, abs, state));

        helper.succeed();
    }

    private static void assertSpreadSides(String what, Set<Direction> expected, Map<Direction, FluidState> spread) {
        if (!spread.keySet().equals(expected)) {
            throw new IllegalStateException("Oil spread (" + what + ") must target exactly " + expected
                    + " but targeted " + spread.keySet());
        }
        for (FluidState target : spread.values()) {
            if (target.isEmpty() || !target.getType().isSame(BCEnergyFluids.OIL_COOL.source().get())) {
                throw new IllegalStateException("Oil spread (" + what + ") produced a non-oil target: " + target);
            }
        }
    }
}
