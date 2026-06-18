/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import buildcraft.builders.BCBuildersBlocks;

/**
 * Pins the Quarry's drill-descent fluid gate ({@link TileQuarry#canMoveThrough}) to 1.12.2 parity:
 * the drill passes through LOW-viscosity fluids (water, viscosity 1000) but is BLOCKED by
 * high-viscosity fluids (lava, viscosity 6000) — matching the Mining Well.
 *
 * <p>The regression: {@code canMoveThrough} had been simplified to {@code return fluid != null}
 * (any fluid passable), so the drill bored straight through a lava column to mine the block
 * beneath it, letting the lava cascade into the pit. 1.12.2 required {@code viscosity <= 1000},
 * so lava/oil columns stopped the descent. The fix restores that gate.
 */
public class TileQuarryFluidPassabilityTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testLavaBlocksDrillButWaterDoesNot(GameTestHelper helper) {
        try {
            BlockPos quarryLocal = new BlockPos(2, 4, 2);
            BlockPos airLocal = new BlockPos(2, 3, 2);
            BlockPos waterLocal = new BlockPos(3, 3, 2);
            BlockPos lavaLocal = new BlockPos(1, 3, 2);
            BlockPos solidLocal = new BlockPos(2, 2, 2);

            helper.setBlock(quarryLocal, BCBuildersBlocks.QUARRY.get());
            helper.setBlock(waterLocal, Blocks.WATER); // default state == source, viscosity 1000
            helper.setBlock(lavaLocal, Blocks.LAVA);   // default state == source, viscosity 6000
            helper.setBlock(solidLocal, Blocks.STONE);

            //? if >=1.21.10 {
            TileQuarry quarry = helper.getBlockEntity(quarryLocal, TileQuarry.class);
            //?} else {
            /*TileQuarry quarry = helper.getBlockEntity(quarryLocal);*/
            //?}
            assertTrue(quarry != null, "quarry block-entity must be present");

            BlockPos airAbs = helper.absolutePos(airLocal);
            BlockPos waterAbs = helper.absolutePos(waterLocal);
            BlockPos lavaAbs = helper.absolutePos(lavaLocal);
            BlockPos solidAbs = helper.absolutePos(solidLocal);

            assertTrue(quarry.canMoveThrough(airAbs),
                    "the drill must pass through air");
            assertTrue(quarry.canMoveThrough(waterAbs),
                    "the drill must pass through water (low viscosity)");
            assertTrue(!quarry.canMoveThrough(lavaAbs),
                    "the drill must be BLOCKED by lava (high viscosity) — was the bore-through-lava regression");
            assertTrue(!quarry.canMoveThrough(solidAbs),
                    "the drill must be blocked by a solid block");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
