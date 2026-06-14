/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import buildcraft.factory.BCFactoryBlocks;

/**
 * Pins the Mining Well's fluid-handling contract (todos.md "finished mining well /
 * flowing water"): low-viscosity fluids like water must be <em>passable</em> — the
 * well drills its tube straight past them to mine the solids below — never a mine
 * <em>target</em>.
 *
 * <p>The regression: {@link TileMiningWell#canBreak} used to return {@code true} for
 * any fluid with viscosity ≤ 1000 (water is exactly 1000), and {@code nextPos()} tests
 * {@code canBreak()} <em>before</em> its passable-fluid skip — so a water block in the
 * bore became {@code currentPos} and the tube extended to it. {@code mine()} then broke
 * the water (a flat 1 MJ via {@code computeBlockBreakPower}), vanilla re-flowed it ~5
 * ticks later, and the well re-targeted it forever: a finished well standing in flowing
 * water shot its tube down, broke the water, retracted, and repeated instead of staying
 * finished. The fix makes {@code canBreak()} refuse all fluids (matching the Quarry's
 * {@code canMine()}), so {@code nextPos()} skips low-viscosity fluid and the well either
 * reaches the solid below or settles as complete.
 *
 * <p>Both tests drive {@link TileMiningWell#mine()} once on a freshly-placed well
 * (initial {@code currentPos == null}, {@code shouldCheck == true}), which runs a single
 * downward scan, then inspect the chosen target {@code currentPos} (a package-visible
 * {@code TileMiner} field). No power is needed — only the scan/targeting is under test.
 */
public class TileMiningWellFluidTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /**
     * Water sitting above a solid: the well must skip the water and target the solid
     * below it (drilling its tube past the water), never lock onto the water itself.
     * Under the old code {@code currentPos} would be the water block.
     */
    public static void testWellDrillsPastWaterToSolidBelow(GameTestHelper helper) {
        try {
            BlockPos wellLocal = new BlockPos(2, 4, 2);
            BlockPos waterLocal = new BlockPos(2, 3, 2); // directly below the well
            BlockPos stoneLocal = new BlockPos(2, 2, 2); // below the water
            BlockPos baseLocal = new BlockPos(2, 1, 2);  // floor under the target

            helper.setBlock(baseLocal, Blocks.STONE);
            helper.setBlock(stoneLocal, Blocks.STONE);
            helper.setBlock(waterLocal, Blocks.WATER); // default state == source
            helper.setBlock(wellLocal, BCFactoryBlocks.MINING_WELL.get());

            //? if >=1.21.10 {
            TileMiningWell well = helper.getBlockEntity(wellLocal, TileMiningWell.class);
            //?} else {
            /*TileMiningWell well = helper.getBlockEntity(wellLocal);*/
            //?}
            assertTrue(well != null, "mining well block-entity must be present");

            BlockPos waterAbs = helper.absolutePos(waterLocal);
            BlockPos stoneAbs = helper.absolutePos(stoneLocal);

            well.mine(); // single downward scan -> sets currentPos

            assertTrue(!waterAbs.equals(well.currentPos),
                    "well must NOT target the water block (was the oscillation bug); currentPos=" + well.currentPos);
            assertTrue(stoneAbs.equals(well.currentPos),
                    "well must drill past the water and target the solid below it. Expected " + stoneAbs
                            + ", got " + well.currentPos);

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Water standing on an unbreakable floor — the "already mined to the bottom, then
     * water flooded the shaft" case. The well must scan past the water, hit the
     * impassable floor, and settle as complete ({@code currentPos == null}), not latch
     * onto the water. This is the "stays finished" half of the bug — under the old code
     * the well targeted the water and {@code isComplete()} flickered false forever.
     * (Bedrock stands in for a finished bore floor; the void test arena always has a
     * solid floor below, so the column must end in something genuinely impassable.)
     */
    public static void testFinishedWellStaysCompleteOverWater(GameTestHelper helper) {
        try {
            BlockPos wellLocal = new BlockPos(2, 4, 2);
            BlockPos waterLocal = new BlockPos(2, 3, 2); // below the well
            BlockPos floorLocal = new BlockPos(2, 2, 2); // impassable bore floor under the water

            helper.setBlock(floorLocal, Blocks.BEDROCK);
            helper.setBlock(waterLocal, Blocks.WATER); // default state == source
            helper.setBlock(wellLocal, BCFactoryBlocks.MINING_WELL.get());

            //? if >=1.21.10 {
            TileMiningWell well = helper.getBlockEntity(wellLocal, TileMiningWell.class);
            //?} else {
            /*TileMiningWell well = helper.getBlockEntity(wellLocal);*/
            //?}
            assertTrue(well != null, "mining well block-entity must be present");

            BlockPos waterAbs = helper.absolutePos(waterLocal);

            well.mine(); // single downward scan -> should find nothing breakable

            assertTrue(!waterAbs.equals(well.currentPos),
                    "well must NOT target the water block; currentPos=" + well.currentPos);
            assertTrue(well.currentPos == null,
                    "well over a water column with no breakable solid must settle complete (currentPos==null), got "
                            + well.currentPos);
            assertTrue(well.isComplete(),
                    "isComplete() must report finished once the bore holds only passable fluid/air");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
