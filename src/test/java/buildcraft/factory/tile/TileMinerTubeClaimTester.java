/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import buildcraft.core.BCCoreConfig;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.test.ClaimProtectionMod;

/**
 * A miner's tube is laid through the cells between the machine and its target, and it must never take the place of
 * a block it was not allowed to break or one that is not its to replace.
 *
 * <p>The regression: the Mining Well skips a block its owner may not break (a claim) and drills on to the unclaimed
 * ground below, but {@code TileMiner.updateLength} then wrote a tube into <em>every</em> cell above the new target,
 * including the skipped one: the claimed block was replaced with no break event, no drops and its block entity
 * destroyed. A pump's tube had the same reach over a waterlogged block in its column (the downward probe walks
 * through any block holding a fluid), so a slab above an oil pocket was deleted too.
 *
 * <p>Everything is driven synchronously ({@code mine()} / {@code updateLength()} / {@code onRemove()} on freshly
 * placed machines), so none of it depends on when the arena's chunks start ticking.
 */
public class TileMinerTubeClaimTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static boolean isTube(ServerLevel level, GameTestHelper helper, BlockPos local) {
        return level.getBlockState(helper.absolutePos(local)).is(BCFactoryBlocks.TUBE.get());
    }

    /**
     * Column (local x=2 z=2): well y=7, air y=6, claimed stone y=5, air y=4, stone y=3 (the target), stone y=2.
     * The well asks about the stone at y=5, is refused, and targets y=3 -- the tube must run through the air cells
     * and leave the claimed block alone, extending, retracting and being removed.
     */
    public static void testWellTubeLeavesClaimedBlockAlone(GameTestHelper helper) {
        try {
            assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                    "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            ServerLevel level = helper.getLevel();
            BlockPos wellLocal = new BlockPos(2, 7, 2);
            BlockPos upperAir = new BlockPos(2, 6, 2);
            BlockPos claimed = new BlockPos(2, 5, 2);
            BlockPos lowerAir = new BlockPos(2, 4, 2);
            BlockPos target = new BlockPos(2, 3, 2);
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
            helper.setBlock(target, Blocks.STONE);
            helper.setBlock(claimed, Blocks.STONE);
            helper.setBlock(wellLocal, BCFactoryBlocks.MINING_WELL.get());
            //? if >=1.21.10 {
            TileMiningWell well = helper.getBlockEntity(wellLocal, TileMiningWell.class);
            //?} else {
            /*TileMiningWell well = helper.getBlockEntity(wellLocal);*/
            //?}
            assertTrue(well != null, "mining well block-entity must be present");

            try (ClaimProtectionMod claim = new ClaimProtectionMod(helper.absolutePos(claimed), true)) {
                well.mine(); // one downward scan: skips the claimed stone, targets the stone below it
                assertTrue(claim.asks() >= 1, "liveness: the well must have asked the claim about the stone");
                assertTrue(helper.absolutePos(target).equals(well.currentPos),
                        "the well must drill past the claimed block to the stone below it, got " + well.currentPos);

                assertTrue(level.getBlockState(helper.absolutePos(claimed)).is(Blocks.STONE),
                        "the tube replaced a block the well was refused -- the claimed stone is "
                                + level.getBlockState(helper.absolutePos(claimed)));
                assertTrue(isTube(level, helper, upperAir) && isTube(level, helper, lowerAir),
                        "the tube must still run through the air above and below the claimed block");

                well.currentPos = null; // nothing left to mine: the tube retracts
                well.updateLength();
                assertTrue(level.getBlockState(helper.absolutePos(claimed)).is(Blocks.STONE),
                        "retracting the tube must leave the claimed stone");
                assertTrue(!isTube(level, helper, upperAir) && !isTube(level, helper, lowerAir),
                        "retracting must remove the tube cells on both sides of the claimed block");

                well.scheduleRecheck();
                well.mine(); // drill back down, then break the well: the whole column goes, gap or not
                assertTrue(isTube(level, helper, lowerAir), "the well must lay its tube again");

                // A tube cell lost mid-column (an explosion, a creative player) is laid again the next time the
                // column grows, as the old rewrite-everything updateLength did: the delta-only extend started at
                // the old end and never looked back up.
                helper.setBlock(upperAir, Blocks.AIR);
                well.currentPos = helper.absolutePos(new BlockPos(2, 2, 2)); // one more block mined
                well.updateLength();
                assertTrue(isTube(level, helper, upperAir),
                        "a tube cell lost above the old end of the column must be laid again when the column grows");
                well.onRemove();
                assertTrue(!isTube(level, helper, upperAir) && !isTube(level, helper, lowerAir),
                        "breaking the well must remove the tube below the claimed block too, not stop at it");
                assertTrue(level.getBlockState(helper.absolutePos(claimed)).is(Blocks.STONE),
                        "breaking the well must leave the claimed stone");
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Column (local x=2 z=2): well y=7, claimed water source y=6 (walled in so it cannot run into a neighbouring
     * arena), air y=5, stone y=4 (the target), stone y=3. The well drills through water without breaking it, so it
     * never asks about the water; the tube must ask before it takes the cell, be refused, and leave the claimed water
     * where it is while still filling the air below.
     */
    public static void testWellTubeLeavesClaimedWaterAlone(GameTestHelper helper) {
        BlockPos claimed = new BlockPos(2, 6, 2);
        try {
            assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                    "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
            ServerLevel level = helper.getLevel();
            BlockPos wellLocal = new BlockPos(2, 7, 2);
            BlockPos lowerAir = new BlockPos(2, 5, 2);
            BlockPos target = new BlockPos(2, 4, 2);
            helper.setBlock(new BlockPos(1, 6, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 6, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(2, 6, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(2, 6, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
            helper.setBlock(target, Blocks.STONE);
            helper.setBlock(claimed, Blocks.WATER);
            helper.setBlock(wellLocal, BCFactoryBlocks.MINING_WELL.get());
            //? if >=1.21.10 {
            TileMiningWell well = helper.getBlockEntity(wellLocal, TileMiningWell.class);
            //?} else {
            /*TileMiningWell well = helper.getBlockEntity(wellLocal);*/
            //?}
            assertTrue(well != null, "mining well block-entity must be present");

            try (ClaimProtectionMod claim = new ClaimProtectionMod(helper.absolutePos(claimed), true)) {
                well.mine(); // one downward scan: drills through the water, targets the stone below the air
                assertTrue(helper.absolutePos(target).equals(well.currentPos),
                        "the well must drill through the water to the stone below it, got " + well.currentPos);
                assertTrue(claim.asks() >= 1, "the tube must ask the claim before it takes the water's cell");
                BlockState water = level.getBlockState(helper.absolutePos(claimed));
                assertTrue(water.is(Blocks.WATER) && water.getFluidState().isSource(),
                        "the tube replaced a claimed water source, which is now " + water);
                assertTrue(isTube(level, helper, lowerAir), "the tube must still run through the air below the water");

                well.onRemove();
                assertTrue(!isTube(level, helper, lowerAir),
                        "breaking the well must remove the tube below the claimed water too, not stop at it");
            }
            helper.setBlock(claimed, Blocks.AIR);
            helper.succeed();
        } catch (Throwable t) {
            helper.setBlock(claimed, Blocks.AIR);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Column (local x=2 z=2): pump y=6, air y=5, waterlogged oak slab y=4, oil source y=3, stone y=2; the slab and
     * oil cells are walled in so neither fluid can run into a neighbouring arena. The pump's probe walks through the
     * slab (it holds a water source) and finds the oil under it, so the tube runs past the slab's cell: it must put
     * a tube in the air and leave the slab, water and all, where it stands.
     */
    public static void testPumpTubeLeavesWaterloggedBlockInColumn(GameTestHelper helper) {
        BlockPos oilCell = new BlockPos(2, 3, 2);
        BlockPos slabCell = new BlockPos(2, 4, 2);
        try {
            ServerLevel level = helper.getLevel();
            for (int y = 3; y <= 4; y++) {
                helper.setBlock(new BlockPos(1, y, 2), Blocks.STONE);
                helper.setBlock(new BlockPos(3, y, 2), Blocks.STONE);
                helper.setBlock(new BlockPos(2, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
            helper.setBlock(oilCell, BCEnergyFluids.OIL_COOL.source().get().defaultFluidState().createLegacyBlock());
            BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
            helper.setBlock(slabCell, slab);
            BlockPos pumpLocal = new BlockPos(2, 6, 2);
            helper.setBlock(pumpLocal, BCFactoryBlocks.PUMP.get());
            TilePump pump = (TilePump) level.getBlockEntity(helper.absolutePos(pumpLocal));
            assertTrue(pump != null, "the pump must have a block entity");

            pump.serverTick(); // builds the queue (seed: the oil) and extends the tube down to it

            assertTrue(isTube(level, helper, new BlockPos(2, 5, 2)),
                    "the pump's tube must run through the air cell above the slab");
            BlockState slabNow = level.getBlockState(helper.absolutePos(slabCell));
            assertTrue(slabNow.is(Blocks.OAK_SLAB) && slabNow.getValue(BlockStateProperties.WATERLOGGED),
                    "the tube replaced a waterlogged block in the pump's column, which is now " + slabNow);
            helper.setBlock(slabCell, Blocks.AIR);
            helper.setBlock(oilCell, Blocks.AIR);
            helper.succeed();
        } catch (Throwable t) {
            helper.setBlock(slabCell, Blocks.AIR);
            helper.setBlock(oilCell, Blocks.AIR);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
