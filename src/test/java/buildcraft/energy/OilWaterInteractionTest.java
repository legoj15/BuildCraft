/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
}
