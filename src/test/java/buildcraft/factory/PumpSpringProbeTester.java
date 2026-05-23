/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.tile.TilePump;
import buildcraft.factory.tile.TilePump.ColumnProbe;

/**
 * Coverage for {@link TilePump#probeDown} — the spring-aware downward probe that
 * lets a pump drill past ocean water to reach a submerged oil spring, and idle on
 * a dry spring instead of bricking on the water around it.
 * <p>
 * Pre-fix, the pump's descent stopped at the first fluid it met: water flooding a
 * depleted oil well's vertical shaft would lock the pump onto water and hide the
 * oil source on the spring beneath it. The probe now walks <em>through</em> fluid.
 */
public class PumpSpringProbeTester {

    /** Pump-tube reach passed to the probe; the test columns are only a few blocks tall. */
    private static final int MAX_DEPTH = 32;

    private static void assertPos(BlockPos expected, BlockPos actual, String what) {
        boolean eq = expected == null ? actual == null : expected.equals(actual);
        if (!eq) {
            throw new IllegalStateException(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static BlockState oilSource() {
        return BCEnergyFluids.OIL_COOL.source().get().defaultFluidState().createLegacyBlock();
    }

    /**
     * Water sitting on top of oil (which sits on a spring): the probe must see past
     * the water and report both the oil and the spring — not stop at the water.
     * This is the core submerged-spring fix.
     */
    public static void testOilBeneathWaterIsFound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pump = new BlockPos(1, 4, 1);
        BlockPos water = new BlockPos(1, 3, 1);
        BlockPos oil = new BlockPos(1, 2, 1);
        BlockPos spring = new BlockPos(1, 1, 1);

        helper.setBlock(water, Blocks.WATER);
        helper.setBlock(oil, oilSource());
        helper.setBlock(spring, BCCoreBlocks.SPRING_OIL.get());

        ColumnProbe probe = TilePump.probeDown(level, helper.absolutePos(pump), MAX_DEPTH);

        assertPos(helper.absolutePos(water), probe.firstFluid(), "firstFluid should be the topmost water");
        assertPos(helper.absolutePos(oil), probe.firstOil(), "firstOil should be the oil beneath the water");
        assertPos(helper.absolutePos(spring), probe.spring(), "spring should be found beneath the oil");
        helper.succeed();
    }

    /**
     * A spring flooded with water but with no oil regenerated yet: the probe must
     * report the spring and NO oil, so the pump idles and waits instead of locking
     * onto (and bricking on) the surrounding water.
     */
    public static void testDrySpringUnderWaterReportsNoOil(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pump = new BlockPos(1, 4, 1);
        BlockPos water = new BlockPos(1, 3, 1);
        BlockPos spring = new BlockPos(1, 1, 1);

        helper.setBlock(water, Blocks.WATER);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.WATER);
        helper.setBlock(spring, BCCoreBlocks.SPRING_OIL.get());

        ColumnProbe probe = TilePump.probeDown(level, helper.absolutePos(pump), MAX_DEPTH);

        assertPos(helper.absolutePos(water), probe.firstFluid(), "firstFluid should be the topmost water");
        assertPos(null, probe.firstOil(), "firstOil should be absent — the spring has not regenerated");
        assertPos(helper.absolutePos(spring), probe.spring(), "spring should still be found beneath the water");
        helper.succeed();
    }

    /**
     * Plain water with no oil or spring below: the probe reports only the water, so
     * an ordinary pump-over-water keeps its historical behaviour. Regression guard
     * for the descent rewrite.
     */
    public static void testPlainWaterColumnUnaffected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pump = new BlockPos(1, 4, 1);
        BlockPos water = new BlockPos(1, 3, 1);

        helper.setBlock(water, Blocks.WATER);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);

        ColumnProbe probe = TilePump.probeDown(level, helper.absolutePos(pump), MAX_DEPTH);

        assertPos(helper.absolutePos(water), probe.firstFluid(), "firstFluid should be the topmost water");
        assertPos(null, probe.firstOil(), "firstOil should be absent");
        assertPos(null, probe.spring(), "spring should be absent");
        helper.succeed();
    }

    /**
     * A solid block blocks the probe: it cannot tunnel through stone, so oil and a
     * spring buried below an obstruction are not reported — correct, since the pump
     * tube is straight and could not reach them either.
     */
    public static void testSolidObstructionStopsProbe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pump = new BlockPos(1, 4, 1);

        helper.setBlock(new BlockPos(1, 3, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 2, 1), oilSource());
        helper.setBlock(new BlockPos(1, 1, 1), BCCoreBlocks.SPRING_OIL.get());

        ColumnProbe probe = TilePump.probeDown(level, helper.absolutePos(pump), MAX_DEPTH);

        assertPos(null, probe.firstFluid(), "firstFluid should be absent — stone blocks the probe");
        assertPos(null, probe.firstOil(), "firstOil should be absent — stone blocks the probe");
        assertPos(null, probe.spring(), "spring should be absent — stone blocks the probe");
        helper.succeed();
    }
}
