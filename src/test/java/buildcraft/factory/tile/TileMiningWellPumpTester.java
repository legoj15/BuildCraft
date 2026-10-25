/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.misc.BlockUtil;

/**
 * A Pump standing on a Mining Well drains the thick fluid the well stopped at, on the well's own power, and the well
 * digs on past it. Synchronous: the test drives both machines by hand, so no tick timing is involved, and the lava is
 * walled in so it cannot flow even if a run fails part-way.
 */
public class TileMiningWellPumpTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testPumpOnWellDrainsBlockingLava(GameTestHelper helper) {
        try {
            BlockPos pumpLocal = new BlockPos(2, 6, 2);
            BlockPos wellLocal = new BlockPos(2, 5, 2);
            BlockPos lavaLocal = new BlockPos(2, 3, 2); // one air cell under the well, then lava
            BlockPos stoneLocal = new BlockPos(2, 2, 2); // what the well should reach once the lava is gone

            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            helper.setBlock(stoneLocal, Blocks.STONE);
            for (BlockPos wall : new BlockPos[] { lavaLocal.north(), lavaLocal.south(), lavaLocal.east(),
                    lavaLocal.west() }) {
                helper.setBlock(wall, Blocks.STONE);
            }
            helper.setBlock(lavaLocal, Blocks.LAVA);
            helper.setBlock(wellLocal, BCFactoryBlocks.MINING_WELL.get());
            helper.setBlock(pumpLocal, BCFactoryBlocks.PUMP.get());

            //? if >=1.21.10 {
            TileMiningWell well = helper.getBlockEntity(wellLocal, TileMiningWell.class);
            TilePump pump = helper.getBlockEntity(pumpLocal, TilePump.class);
            //?} else {
            /*TileMiningWell well = helper.getBlockEntity(wellLocal);
            TilePump pump = helper.getBlockEntity(pumpLocal);*/
            //?}
            assertTrue(well != null && pump != null, "mining well and pump block-entities must be present");

            BlockPos lavaAbs = helper.absolutePos(lavaLocal);
            BlockPos stoneAbs = helper.absolutePos(stoneLocal);
            // The pump drains only where every chunk within the drain's reach is in memory, and the arena can sit by a
            // chunk border: load those chunks now (a plain getChunk holds them for this tick, all the test needs).
            int reach = BlockUtil.DRAIN_UPDATE_REACH;
            for (BlockPos corner : new BlockPos[] { lavaAbs.offset(-reach, 0, -reach), lavaAbs.offset(reach, 0, -reach),
                    lavaAbs.offset(-reach, 0, reach), lavaAbs.offset(reach, 0, reach) }) {
                helper.getLevel().getChunk(corner);
            }

            well.mine();
            assertTrue(well.currentPos == null, "precondition: the lava must stop the well; currentPos=" + well.currentPos);
            assertTrue(pump.getBattery().getStored() == 0, "precondition: the pump has no power of its own");

            // Only the well is powered: the pump must draw on the well's battery.
            for (int i = 0; i < 100 && !stoneAbs.equals(well.currentPos); i++) {
                well.getBattery().setStored(well.getBattery().getCapacity());
                pump.serverTick();
                well.mine();
            }

            assertTrue(!helper.getLevel().getFluidState(lavaAbs).is(Fluids.LAVA),
                    "the pump on the well must drain the lava blocking it");
            assertTrue(pump.getTank().getFluidStack(0).getFluid().isSame(Fluids.LAVA),
                    "the drained lava must land in the pump's tank");
            assertTrue(stoneAbs.equals(well.currentPos),
                    "once the lava is gone the well must target the stone below it. Expected " + stoneAbs + ", got "
                            + well.currentPos);
            assertTrue(pump.getWantedLength() == 0, "a pump on a well lays no tube of its own through the well");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
