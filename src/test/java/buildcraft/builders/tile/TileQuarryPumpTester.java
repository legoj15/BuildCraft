/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.mj.MjAPI;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TilePump;
import buildcraft.lib.misc.BlockUtil;

/**
 * A Pump standing on a Quarry drains the thick fluid the quarry's sweep skipped, on the quarry's power once it has
 * finished, and the quarry goes back and digs the column the fluid was blocking.
 *
 * <p>Layout (local coordinates, one arena cell):
 * <pre>
 *   quarry (1,2,2), pump on it (1,3,2), chest (0,2,2) catches the drops
 *   frame box  (2,2,1)..(5,4,4)
 *   mining box (3..4, *, 2..3), floored with bedrock at y=0
 *   lava source (3,2,2) in the frame box's bottom layer (frame clearing passes fluid by), over stone (3,1,2) just
 *   below the frame box, where only the drill can reach it; the bedrock keeps the drill out of the world below
 * </pre>
 * Synchronous: the test drives both machines by hand inside one tick, so the lava never gets a fluid tick to spread.
 */
public class TileQuarryPumpTester {

    private static final BlockPos QUARRY = new BlockPos(1, 2, 2);
    private static final BlockPos PUMP = new BlockPos(1, 3, 2);
    private static final BlockPos CHEST = new BlockPos(0, 2, 2);
    private static final BlockPos FRAME_MIN = new BlockPos(2, 2, 1);
    private static final BlockPos FRAME_MAX = new BlockPos(5, 4, 4);
    private static final BlockPos LAVA = new BlockPos(3, 2, 2);
    private static final BlockPos STONE = new BlockPos(3, 1, 2);

    private static final long ENGINE_MJ_PER_TICK = 100 * MjAPI.MJ;

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testPumpOnQuarryDrainsSkippedLava(GameTestHelper helper) {
        TileQuarry quarry = null;
        try {
            for (int x = 3; x <= 4; x++) {
                for (int z = 2; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
                }
            }
            helper.setBlock(STONE, Blocks.STONE);
            helper.setBlock(LAVA, Blocks.LAVA);
            helper.setBlock(CHEST, Blocks.CHEST);
            helper.setBlock(QUARRY, BCBuildersBlocks.QUARRY.get());
            helper.setBlock(PUMP, BCFactoryBlocks.PUMP.get());

            //? if >=1.21.10 {
            quarry = helper.getBlockEntity(QUARRY, TileQuarry.class);
            TilePump pump = helper.getBlockEntity(PUMP, TilePump.class);
            //?} else {
            /*quarry = helper.getBlockEntity(QUARRY);
            TilePump pump = helper.getBlockEntity(PUMP);*/
            //?}
            assertTrue(quarry != null && pump != null, "quarry and pump block-entities must be present");

            BlockPos min = helper.absolutePos(FRAME_MIN);
            BlockPos max = helper.absolutePos(FRAME_MAX);
            BlockPos quarryAbs = helper.absolutePos(QUARRY);
            quarry.frameBox.reset();
            quarry.frameBox.setMin(min);
            quarry.frameBox.setMax(max);
            quarry.miningBox.reset();
            quarry.miningBox.setMin(new BlockPos(min.getX() + 1, quarryAbs.getY() - 1, min.getZ() + 1));
            quarry.miningBox.setMax(new BlockPos(max.getX() - 1, max.getY() - 1, max.getZ() - 1));
            quarry.updatePoses();

            BlockPos lavaAbs = helper.absolutePos(LAVA);
            BlockPos stoneAbs = helper.absolutePos(STONE);
            // The pump drains only where every chunk within the drain's reach is in memory, and the arena can sit by a
            // chunk border: load those chunks now (a plain getChunk holds them for this tick, all the test needs).
            int reach = BlockUtil.DRAIN_UPDATE_REACH;
            for (BlockPos corner : new BlockPos[] { lavaAbs.offset(-reach, 0, -reach), lavaAbs.offset(reach, 0, -reach),
                    lavaAbs.offset(-reach, 0, reach), lavaAbs.offset(reach, 0, reach) }) {
                helper.getLevel().getChunk(corner);
            }

            // Phase 1: the quarry builds its frame and sweeps the box, skipping the lava and the stone under it.
            for (int i = 0; i < 4000 && (quarry.drillPos == null || quarry.hasPendingWork()); i++) {
                quarry.getBattery().setStored(quarry.getBattery().getCapacity());
                quarry.tick();
            }
            assertTrue(quarry.drillPos != null && !quarry.hasPendingWork(),
                    "precondition: the quarry must finish its sweep (hasPendingWork=" + quarry.hasPendingWork() + ")");
            assertTrue(helper.getLevel().getBlockState(stoneAbs).is(Blocks.STONE),
                    "precondition: the lava must keep the drill off the stone under it");
            assertTrue(pump.getBattery().getStored() == 0, "precondition: the pump has no power of its own");

            // Phase 2: an engine feeds the quarry only what its receiver asks for, starting from an empty battery (a
            // finished quarry asks for nothing on its own). The pump has no power of its own: it must get the quarry
            // taking power again, drain the lava on it, and the quarry must go back for the stone.
            quarry.getBattery().setStored(0);
            for (int i = 0; i < 4000 && !helper.getLevel().getBlockState(stoneAbs).isAir(); i++) {
                long asked = quarry.getMjReceiver().getPowerRequested();
                quarry.getMjReceiver().receivePower(Math.min(asked, ENGINE_MJ_PER_TICK), false);
                pump.serverTick();
                quarry.tick();
            }

            assertTrue(!helper.getLevel().getFluidState(lavaAbs).is(Fluids.LAVA),
                    "the pump on the quarry must drain the lava its sweep skipped");
            assertTrue(pump.getTank().getFluidStack(0).getFluid().isSame(Fluids.LAVA),
                    "the drained lava must land in the pump's tank");
            assertTrue(helper.getLevel().getBlockState(stoneAbs).isAir(),
                    "once the lava is gone the quarry must go back and mine the stone under it");
            assertTrue(pump.getWantedLength() == 0, "a pump on a quarry lays no tube of its own");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            if (quarry != null) {
                quarry.currentTask = null;
                quarry.drillPos = null;
                quarry.updateRigs();
            }
            helper.setBlock(LAVA, Blocks.AIR);
            helper.setBlock(PUMP, Blocks.AIR);
            helper.setBlock(QUARRY, Blocks.AIR);
        }
    }
}
