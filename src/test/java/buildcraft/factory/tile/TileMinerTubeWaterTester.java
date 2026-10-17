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
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.mj.MjAPI;
import buildcraft.factory.BCFactoryBlocks;

/**
 * A miner's tube must not delete the water it is drilled through.
 *
 * <p>The regression: {@code TileMiner.updateLength} placed {@code TUBE.defaultBlockState()} into every cell of the
 * bore, and the tube was not waterloggable, so every water source in the column was overwritten; retracting
 * ({@code removeBlock}) then left air. A well drilled through a pond permanently deleted a column of it. With the
 * tube waterloggable the water lives on inside the tube, and {@code Level.removeBlock} (which swaps a block for its
 * fluid state) puts the source back when the tube retracts.
 *
 * <p>Column (local coordinates, x=2 z=2; every cell below the well is walled in with stone so the water cannot run
 * into a neighbouring arena):
 * <pre>
 *   y=6  mining well          y=3  water source
 *   y=5  air                  y=2  stone  (the target)
 *   y=4  water source         y=1  stone floor
 * </pre>
 * Everything is driven synchronously ({@code mine()} / {@code updateLength()} / {@code onRemove()} on a
 * freshly-placed well), so none of it depends on when the arena's chunks start ticking.
 */
public class TileMinerTubeWaterTester {

    private static final BlockPos WELL = new BlockPos(2, 6, 2);
    private static final BlockPos AIR_CELL = new BlockPos(2, 5, 2);
    private static final BlockPos UPPER_WATER = new BlockPos(2, 4, 2);
    private static final BlockPos LOWER_WATER = new BlockPos(2, 3, 2);
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static boolean isWaterSource(BlockState state) {
        return state.getFluidState().isSource() && state.getFluidState().getType() == Fluids.WATER;
    }

    /** Stone ring around the column cells y=2..4 and a floor, so the water cannot spread. */
    private static void buildPond(GameTestHelper helper) {
        for (int y = 2; y <= 5; y++) {
            helper.setBlock(new BlockPos(1, y, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(3, y, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(2, y, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(LOWER_WATER, Blocks.WATER); // default state == source
        helper.setBlock(UPPER_WATER, Blocks.WATER);
        helper.setBlock(WELL, BCFactoryBlocks.MINING_WELL.get());
    }

    private static TileMiningWell well(GameTestHelper helper) {
        //? if >=1.21.10 {
        TileMiningWell well = helper.getBlockEntity(WELL, TileMiningWell.class);
        //?} else {
        /*TileMiningWell well = helper.getBlockEntity(WELL);*/
        //?}
        assertTrue(well != null, "mining well block-entity must be present");
        return well;
    }

    /** Leave no live water behind: the ring/floor are harmless, the sources are not. */
    private static void drain(GameTestHelper helper) {
        helper.setBlock(UPPER_WATER, Blocks.AIR);
        helper.setBlock(LOWER_WATER, Blocks.AIR);
    }

    /**
     * Drilling through a pond: the tube cells in the water are waterlogged (still a water source), the one in air
     * is dry, and re-scanning the bore neither mines the waterlogged tube nor rebuilds the column.
     */
    public static void testTubeKeepsWaterInBore(GameTestHelper helper) {
        try {
            buildPond(helper);
            TileMiningWell well = well(helper);
            BlockPos targetAbs = helper.absolutePos(TARGET);

            well.mine(); // one downward scan: targets the stone and extends the tube down to it
            assertTrue(targetAbs.equals(well.currentPos),
                    "the well must target the stone under the pond, got " + well.currentPos);

            BlockState air = helper.getLevel().getBlockState(helper.absolutePos(AIR_CELL));
            BlockState upper = helper.getLevel().getBlockState(helper.absolutePos(UPPER_WATER));
            BlockState lower = helper.getLevel().getBlockState(helper.absolutePos(LOWER_WATER));
            assertTrue(air.is(BCFactoryBlocks.TUBE.get()), "the tube must extend through the air cell, got " + air);
            assertTrue(air.getFluidState().isEmpty(), "the tube in the air cell must hold no water, got " + air);
            assertTrue(upper.is(BCFactoryBlocks.TUBE.get()) && isWaterSource(upper),
                    "the tube drilled through the upper water source must stay waterlogged -- the water was"
                            + " overwritten, got " + upper);
            assertTrue(lower.is(BCFactoryBlocks.TUBE.get()) && isWaterSource(lower),
                    "the tube drilled through the lower water source must stay waterlogged -- the water was"
                            + " overwritten, got " + lower);
            assertTrue(upper.getValue(BlockStateProperties.WATERLOGGED) && lower.getValue(BlockStateProperties.WATERLOGGED),
                    "the water must live in the tube's WATERLOGGED property");

            // A fresh scan of the finished bore: the waterlogged tube is neither a target (it is unbreakable, and
            // its water is not mined) nor a reason to rebuild the column.
            well.currentPos = null;
            well.scheduleRecheck();
            well.mine();
            assertTrue(targetAbs.equals(well.currentPos),
                    "re-scanning the bore must target the stone again, not the waterlogged tube, got " + well.currentPos);
            assertTrue(isWaterSource(helper.getLevel().getBlockState(helper.absolutePos(UPPER_WATER))),
                    "re-scanning must leave the bore's water in place");

            well.onRemove();
            drain(helper);
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Retracting restores the water: the tube retracting by length ({@code updateLength} with no target) and the
     * well being broken ({@code onRemove}) both leave a water source where a waterlogged tube stood, and plain air
     * where a dry one did.
     */
    public static void testRetractRestoresWater(GameTestHelper helper) {
        try {
            buildPond(helper);
            TileMiningWell well = well(helper);

            well.mine();
            well.currentPos = null; // nothing left to mine: the tube retracts all the way
            well.updateLength();
            assertWaterRestored(helper, "retracting the tube");

            // And again, this time ending with the well itself being broken.
            well.scheduleRecheck();
            well.mine();
            assertTrue(helper.getLevel().getBlockState(helper.absolutePos(UPPER_WATER)).is(BCFactoryBlocks.TUBE.get()),
                    "the well must drill its tube back down through the restored water");
            well.onRemove();
            assertWaterRestored(helper, "breaking the well");

            drain(helper);
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static void assertWaterRestored(GameTestHelper helper, String how) {
        BlockState air = helper.getLevel().getBlockState(helper.absolutePos(AIR_CELL));
        BlockState upper = helper.getLevel().getBlockState(helper.absolutePos(UPPER_WATER));
        BlockState lower = helper.getLevel().getBlockState(helper.absolutePos(LOWER_WATER));
        assertTrue(air.isAir(), how + " must leave air where the dry tube stood, got " + air);
        assertTrue(upper.is(Blocks.WATER) && isWaterSource(upper),
                how + " must restore the upper water source -- the pond lost its water, got " + upper);
        assertTrue(lower.is(Blocks.WATER) && isWaterSource(lower),
                how + " must restore the lower water source -- the pond lost its water, got " + lower);
    }

    /**
     * A pump's own tube is not the fluid it pumps: the downward probe walks through tubes whether or not they hold
     * water, so a waterlogged tube above a pond is never the pump's first fluid (it would retarget its own tube).
     */
    public static void testPumpProbeIgnoresWaterloggedTube(GameTestHelper helper) {
        try {
            BlockPos pumpLocal = new BlockPos(2, 6, 2);
            BlockState tube = BCFactoryBlocks.TUBE.get().defaultBlockState();
            BlockState wetTube = tube.setValue(BlockStateProperties.WATERLOGGED, true);
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(new BlockPos(1, y, 2), Blocks.STONE);
                helper.setBlock(new BlockPos(3, y, 2), Blocks.STONE);
                helper.setBlock(new BlockPos(2, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(2, 2, 2), Blocks.WATER);
            helper.setBlock(new BlockPos(2, 3, 2), wetTube);
            helper.setBlock(new BlockPos(2, 4, 2), wetTube);
            helper.setBlock(new BlockPos(2, 5, 2), tube);

            TilePump.ColumnProbe probe = TilePump.probeDown(helper.getLevel(), helper.absolutePos(pumpLocal), 64);
            BlockPos expected = helper.absolutePos(new BlockPos(2, 2, 2));
            assertTrue(expected.equals(probe.firstFluid()),
                    "the pump must target the pond under its tube, not the water inside its own tube; expected "
                            + expected + ", got " + probe.firstFluid());
            assertTrue(probe.firstOil() == null && probe.spring() == null, "no oil or spring in this column");

            helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
            helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
            helper.setBlock(new BlockPos(2, 4, 2), Blocks.AIR);
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    // ---------- The pump's flood fill must treat its own tube as transparent ----------

    private static final BlockPos PUMP = new BlockPos(2, 6, 2);
    private static final BlockPos SEED = new BlockPos(2, 2, 2);
    private static final BlockPos WET_TUBE_LOW = new BlockPos(2, 3, 2);
    private static final BlockPos WET_TUBE_HIGH = new BlockPos(2, 4, 2);
    private static final BlockPos DRY_TUBE = new BlockPos(2, 5, 2);
    private static final BlockPos POOL_LOW = new BlockPos(3, 2, 2);
    /** Beside the low wet tube cell: the pool water touches the tube's water. */
    private static final BlockPos POOL_HIGH = new BlockPos(3, 3, 2);

    private static boolean isWaterlogged(BlockState state) {
        return state.is(BCFactoryBlocks.TUBE.get()) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    /**
     * A pump's own waterlogged tube is not part of the pool it drains. The tube column passes through water that
     * touches a three-cell pool (the cell under the tube, plus two beside it); the flood fill used to queue the
     * tube's water as pool cells -- it is a water source -- and the pump un-waterlogged its own tube bucket by bucket.
     *
     * <p>Column (local coordinates; the pump's first {@code mine()} rebuilds the tube column itself, which keeps
     * the water in the wet cells waterlogged):
     * <pre>
     *   y=6  pump                    y=3  wet tube  | pool (x=3)
     *   y=5  dry tube                y=2  pool seed | pool (x=3)
     *   y=4  wet tube                y=1  stone floor
     * </pre>
     * Driven synchronously and gated on the observed pool: the pump is powered and ticked until all three pool cells
     * are empty, and before every tick both wet tube cells must still be waterlogged tubes. (When the last bucket
     * goes the pump retracts its tube, which turns the wet cells back into plain water -- so the end state is checked
     * as "the water is still there", and exactly three buckets, the pool's, were pumped.)
     */
    public static void testPumpNeverDrainsOwnWaterloggedTube(GameTestHelper helper) {
        try {
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(new BlockPos(1, y, 2), Blocks.STONE);
                helper.setBlock(new BlockPos(2, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
                helper.setBlock(new BlockPos(3, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(3, y, 3), Blocks.STONE);
                helper.setBlock(new BlockPos(4, y, 2), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(3, 4, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 5, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
            helper.setBlock(SEED, Blocks.WATER);
            helper.setBlock(POOL_LOW, Blocks.WATER);
            helper.setBlock(POOL_HIGH, Blocks.WATER);
            BlockState tube = BCFactoryBlocks.TUBE.get().defaultBlockState();
            BlockState wetTube = tube.setValue(BlockStateProperties.WATERLOGGED, true);
            helper.setBlock(WET_TUBE_LOW, wetTube);
            helper.setBlock(WET_TUBE_HIGH, wetTube);
            helper.setBlock(DRY_TUBE, tube);
            helper.setBlock(PUMP, BCFactoryBlocks.PUMP.get());

            ServerLevel level = helper.getLevel();
            TilePump pump = (TilePump) level.getBlockEntity(helper.absolutePos(PUMP));
            assertTrue(pump != null, "the pump must have a block entity");
            BlockPos[] pool = { SEED, POOL_LOW, POOL_HIGH };
            BlockPos[] wetCells = { WET_TUBE_LOW, WET_TUBE_HIGH };

            int ticks = 0;
            while (poolHoldsWater(level, helper, pool)) {
                assertTrue(ticks++ < 200, "the pump never drained the pool, tank = " + pump.getTank().getAmountMb(0)
                        + " mB");
                for (BlockPos wet : wetCells) {
                    BlockState state = level.getBlockState(helper.absolutePos(wet));
                    assertTrue(isWaterlogged(state), "the pump drained its own tube: " + wet + " is " + state
                            + " after " + (ticks - 1) + " ticks and " + pump.getTank().getAmountMb(0) + " mB pumped");
                }
                pump.getBattery().addPowerChecking(10 * MjAPI.MJ, false);
                pump.serverTick();
            }

            assertTrue(pump.getTank().getAmountMb(0) == 3000,
                    "exactly the pool's three buckets must be pumped, got " + pump.getTank().getAmountMb(0) + " mB");
            for (BlockPos wet : wetCells) {
                BlockState state = level.getBlockState(helper.absolutePos(wet));
                assertTrue(state.getFluidState().isSource() && state.getFluidState().getType() == Fluids.WATER,
                        "the tube's water must still be there once the pool is drained, " + wet + " is " + state);
            }

            // Leave no live water behind. The pump itself stays: breaking it would spill its tank as shard items.
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(new BlockPos(2, y, 2), Blocks.AIR);
            }
            helper.setBlock(POOL_LOW, Blocks.AIR);
            helper.setBlock(POOL_HIGH, Blocks.AIR);
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static boolean poolHoldsWater(ServerLevel level, GameTestHelper helper, BlockPos[] pool) {
        for (BlockPos cell : pool) {
            if (!level.getFluidState(helper.absolutePos(cell)).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
