/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
            BlockPos loggedLocal = new BlockPos(2, 3, 3);

            helper.setBlock(quarryLocal, BCBuildersBlocks.QUARRY.get());
            helper.setBlock(waterLocal, Blocks.WATER); // default state == source, viscosity 1000
            helper.setBlock(lavaLocal, Blocks.LAVA);   // default state == source, viscosity 6000
            helper.setBlock(solidLocal, Blocks.STONE);
            helper.setBlock(loggedLocal, Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));

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
            BlockPos loggedAbs = helper.absolutePos(loggedLocal);
            assertTrue(!quarry.canMoveThrough(loggedAbs),
                    "a waterlogged slab is a block, not water: the drill must mine it, not slide past it");
            // The other half of that contract: the box iterator skips any cell where canMoveThrough is true OR
            // canMine is false, and canMoveDownTo refuses every cell under an impassable one. So a waterlogged
            // slab the drill can't pass MUST be mineable, or the whole column beneath it is silently abandoned.
            assertTrue(quarry.canMine(loggedAbs),
                    "the quarry must be able to mine a waterlogged slab (else the column under it is abandoned)");
            assertTrue(!quarry.canMine(waterAbs) && !quarry.canMine(lavaAbs),
                    "the quarry must never 'mine' a bare fluid cell");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * A waterlogged block standing where the frame goes (a slab on the frame line) is a block, not water:
     * the frame pass must break it (drops routed like any other broken block) before the frame goes in,
     * never overwrite it with a frame. The regression: {@link TileQuarry#canIgnoreInFrameBox} classified the
     * cell by its fluid alone, so the slab was queued as "place frame here" and {@code TaskAddFrame.finish}
     * {@code setBlockAndUpdate(FRAME)} deleted it without drops. Drives the two frame-pass tasks directly
     * (synchronous, no tick timing): the frame task must refuse the occupied cell, the break task must
     * break the slab, leave its water, and send the slab to the chest beside the quarry.
     */
    public static void testFrameLineWaterloggedBlockIsBrokenNotOverwritten(GameTestHelper helper) {
        try {
            BlockPos quarryLocal = new BlockPos(2, 2, 2);
            BlockPos chestLocal = new BlockPos(3, 2, 2);
            BlockPos slabLocal = new BlockPos(2, 3, 2);

            helper.setBlock(quarryLocal, BCBuildersBlocks.QUARRY.get());
            helper.setBlock(chestLocal, Blocks.CHEST);
            helper.setBlock(slabLocal, Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));

            //? if >=1.21.10 {
            TileQuarry quarry = helper.getBlockEntity(quarryLocal, TileQuarry.class);
            //?} else {
            /*TileQuarry quarry = helper.getBlockEntity(quarryLocal);*/
            //?}
            assertTrue(quarry != null, "quarry block-entity must be present");
            BlockPos slabAbs = helper.absolutePos(slabLocal);

            assertTrue(quarry.canIgnoreInFrameBox(slabAbs),
                    "a waterlogged slab in the frame box is a block to break, not a fluid cell to frame over");

            TileQuarry.TaskAddFrame frame = quarry.new TaskAddFrame(slabAbs);
            frame.addPower(frame.getTarget());
            assertTrue(helper.getLevel().getBlockState(slabAbs).is(Blocks.OAK_SLAB),
                    "the frame task must not overwrite (delete) a waterlogged slab, got "
                            + helper.getLevel().getBlockState(slabAbs));

            TileQuarry.TaskBreakBlock breakTask = quarry.new TaskBreakBlock(slabAbs);
            assertTrue(breakTask.addPower(breakTask.getTarget()), "the break task must finish in one call");
            BlockState after = helper.getLevel().getBlockState(slabAbs);
            assertTrue(after.is(Blocks.WATER) && after.getFluidState().isSource(),
                    "breaking the waterlogged slab must leave its water source, got " + after);
            assertTrue(!quarry.canIgnoreInFrameBox(slabAbs),
                    "the leftover water is a fluid cell again: the frame goes in over it");

            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(chestLocal));
            assertTrue(chest != null, "chest block-entity must be present");
            boolean foundSlab = false;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(Items.OAK_SLAB)) {
                    foundSlab = true;
                    break;
                }
            }
            assertTrue(foundSlab, "the broken slab must be routed into the chest beside the quarry");

            // Don't leave a live water source flowing toward neighbouring arenas.
            helper.setBlock(slabLocal, Blocks.AIR);
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
