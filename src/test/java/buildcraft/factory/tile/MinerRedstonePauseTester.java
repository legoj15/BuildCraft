/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import static buildcraft.lib.test.GameTestUtil.check;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.mj.MjAPI;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.misc.BlockUtil;

/**
 * A redstone signal beside a Pump or a Mining Well pauses it in place, as their guide pages say: no work, no power
 * spent, progress kept; taking the signal away lets it carry on. The Quarry has its own copy of this in
 * {@code TileQuarryRedstonePauseTester}.
 *
 * <p>Two paths set the pause, so each machine is run both ways: the signal arrives after the machine has already
 * ticked (the block's neighbour update sets it), and the machine is placed beside a signal already there (nothing
 * tells it, so its first tick reads the world: the flag is not saved, so a reloaded machine does the same).
 *
 * <p>Synchronous: each test drives the machine's own {@code serverTick} by hand with a full battery, so no world tick
 * is assumed and the signal's effect is read off the world. The powered stretch is long against the few ticks the
 * machine needs to finish its job when it is not paused, so a machine that works while powered fails by name.
 */
public class MinerRedstonePauseTester {

    /** Every pause is held this many of the machine's own ticks, against the one or two its job takes. */
    private static final int PAUSED_TICKS = 100;
    /** The ticks a resumed machine gets to finish its job. */
    private static final int RESUME_TICKS = 60;

    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    private static final BlockPos MACHINE = new BlockPos(2, 5, 2);
    private static final BlockPos SIGNAL_SIDE = new BlockPos(1, 5, 2);

    // Mining Well: the glass target sits two blocks under the well over a bedrock floor, so the well stops there.
    private static final BlockPos GLASS = new BlockPos(2, 3, 2);

    // Pump: a single water source in a stone cup under the pump's column, a tank beside the pump to push into.
    private static final BlockPos POOL = new BlockPos(2, 2, 2);
    private static final BlockPos TANK_SIDE = new BlockPos(3, 5, 2);

    private static <T extends BlockEntity> T tile(GameTestHelper helper, BlockPos rel, Class<T> type) {
        BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        check(type.isInstance(be), "expected a " + type.getSimpleName() + " at " + rel + ", found " + be);
        return type.cast(be);
    }

    // --- Mining Well ---

    /** The signal arrives while the well is partway through breaking the glass; the break must wait for it to go. */
    public static void wellPausesWhenSignalArrivesAndResumes(GameTestHelper helper) {
        runWell(helper, false);
    }

    /** A well placed beside a signal never starts. */
    public static void wellPlacedBesideSignalStartsPaused(GameTestHelper helper) {
        runWell(helper, true);
    }

    private static void runWell(GameTestHelper helper, boolean signalFirst) {
        TileMiningWell well = null;
        try {
            helper.setBlock(FLOOR, Blocks.BEDROCK);
            helper.setBlock(GLASS, Blocks.GLASS); // no drops: nothing for the test to catch or clean up
            if (signalFirst) {
                helper.setBlock(SIGNAL_SIDE, Blocks.REDSTONE_BLOCK);
            }
            helper.setBlock(MACHINE, BCFactoryBlocks.MINING_WELL.get());
            well = tile(helper, MACHINE, TileMiningWell.class);
            BlockPos glassAbs = helper.absolutePos(GLASS);

            if (!signalFirst) {
                // The first tick scans down and picks the glass; the second spends a trickle of power on it.
                well.serverTick();
                well.getBattery().setStored(MjAPI.MJ);
                well.serverTick();
                check(well.progress > 0, "precondition: the unpaused well must have started on the glass, progress="
                        + well.progress);
                check(helper.getLevel().getBlockState(glassAbs).is(Blocks.GLASS),
                        "precondition: a trickle of power must not break the glass yet");
                helper.setBlock(SIGNAL_SIDE, Blocks.REDSTONE_BLOCK);
            }
            int progress = well.progress;
            // The well lays its tube straight under itself as soon as it picks a target: a paused one lays none.
            BlockPos belowAbs = helper.absolutePos(MACHINE.below());
            BlockState below = helper.getLevel().getBlockState(belowAbs);

            long full = well.getBattery().getCapacity();
            well.getBattery().setStored(full);
            for (int i = 0; i < PAUSED_TICKS; i++) {
                well.serverTick();
            }
            check(helper.getLevel().getBlockState(glassAbs).is(Blocks.GLASS),
                    "a mining well beside a redstone signal broke its target anyway");
            check(helper.getLevel().getBlockState(belowAbs) == below,
                    "a paused mining well changed the cell under it from " + below + " to "
                            + helper.getLevel().getBlockState(belowAbs));
            check(well.getBattery().getStored() == full,
                    "a paused mining well spent power: " + well.getBattery().getStored() + " of " + full + " left");
            check(well.progress == progress,
                    "a paused mining well changed its break progress from " + progress + " to " + well.progress);

            helper.setBlock(SIGNAL_SIDE, Blocks.AIR);
            for (int i = 0; i < RESUME_TICKS && helper.getLevel().getBlockState(glassAbs).is(Blocks.GLASS); i++) {
                well.serverTick();
            }
            check(helper.getLevel().getBlockState(glassAbs).isAir(),
                    "the mining well did not carry on once the redstone signal went, within " + RESUME_TICKS + " ticks");
            cleanUpWell(helper, well);
            helper.succeed();
        } catch (Throwable t) {
            cleanUpWell(helper, well);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static void cleanUpWell(GameTestHelper helper, TileMiningWell well) {
        if (well != null) {
            well.onRemove(); // the tube it laid
        }
        helper.setBlock(SIGNAL_SIDE, Blocks.AIR);
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.setBlock(GLASS, Blocks.AIR);
        helper.setBlock(FLOOR, Blocks.AIR);
    }

    // --- Pump ---

    /** The signal arrives after the pump has found its water; the pump must leave the water until it goes. */
    public static void pumpPausesWhenSignalArrivesAndResumes(GameTestHelper helper) {
        runPump(helper, false);
    }

    /** A pump placed beside a signal never pumps. */
    public static void pumpPlacedBesideSignalStartsPaused(GameTestHelper helper) {
        runPump(helper, true);
    }

    private static void runPump(GameTestHelper helper, boolean signalFirst) {
        TilePump pump = null;
        TileTank tank = null;
        try {
            helper.setBlock(FLOOR, Blocks.STONE);
            for (BlockPos wall : new BlockPos[] { POOL.west(), POOL.east(), POOL.north(), POOL.south() }) {
                helper.setBlock(wall, Blocks.STONE);
            }
            helper.setBlock(POOL, Blocks.WATER);
            helper.setBlock(TANK_SIDE, BCFactoryBlocks.TANK.get());
            if (signalFirst) {
                helper.setBlock(SIGNAL_SIDE, Blocks.REDSTONE_BLOCK);
            }
            helper.setBlock(MACHINE, BCFactoryBlocks.PUMP.get());
            pump = tile(helper, MACHINE, TilePump.class);
            tank = tile(helper, TANK_SIDE, TileTank.class);
            BlockPos poolAbs = helper.absolutePos(POOL);

            // The pump drains only where every chunk within the drain's reach is in memory, and the arena can sit by
            // a chunk border: load those chunks now (a plain getChunk holds them for this tick, all the test needs).
            int reach = BlockUtil.DRAIN_UPDATE_REACH;
            for (BlockPos corner : new BlockPos[] { poolAbs.offset(-reach, 0, -reach), poolAbs.offset(reach, 0, -reach),
                    poolAbs.offset(-reach, 0, reach), poolAbs.offset(reach, 0, reach) }) {
                helper.getLevel().getChunk(corner);
            }

            if (!signalFirst) {
                // With no power the pump still builds its queue and lines up the pool cell, so it is mid-job.
                pump.serverTick();
                check(helper.getLevel().getFluidState(poolAbs).isSource(),
                        "precondition: an unpowered pump must not have drained the pool");
                helper.setBlock(SIGNAL_SIDE, Blocks.REDSTONE_BLOCK);
            }

            // Fluid the pump already holds still goes onward to a tank beside it while the pump is paused.
            pump.getTank().setFluidStack(0, new FluidStack(Fluids.WATER, 1000));
            // The pump lays its tube straight under itself as soon as it picks a cell: a paused one lays none.
            BlockPos belowAbs = helper.absolutePos(MACHINE.below());
            BlockState below = helper.getLevel().getBlockState(belowAbs);
            long full = pump.getBattery().getCapacity();
            pump.getBattery().setStored(full);
            for (int i = 0; i < PAUSED_TICKS; i++) {
                pump.serverTick();
            }
            check(helper.getLevel().getFluidState(poolAbs).isSource(),
                    "a pump beside a redstone signal drained its pool anyway");
            check(helper.getLevel().getBlockState(belowAbs) == below,
                    "a paused pump changed the cell under it from " + below + " to "
                            + helper.getLevel().getBlockState(belowAbs));
            check(pump.getBattery().getStored() == full,
                    "a paused pump spent power: " + pump.getBattery().getStored() + " of " + full + " left");
            check(pump.getTank().getAmountMb(0) == 0 && tank.tank.getAmountMb(0) == 1000,
                    "a paused pump must still push the fluid it holds into a tank beside it: pump holds "
                            + pump.getTank().getAmountMb(0) + " mB, tank " + tank.tank.getAmountMb(0) + " mB");

            helper.setBlock(SIGNAL_SIDE, Blocks.AIR);
            for (int i = 0; i < RESUME_TICKS && helper.getLevel().getFluidState(poolAbs).isSource(); i++) {
                pump.serverTick();
            }
            check(!helper.getLevel().getFluidState(poolAbs).isSource(),
                    "the pump did not carry on once the redstone signal went, within " + RESUME_TICKS + " ticks");
            cleanUpPump(helper, pump, tank);
            helper.succeed();
        } catch (Throwable t) {
            cleanUpPump(helper, pump, tank);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static void cleanUpPump(GameTestHelper helper, TilePump pump, TileTank tank) {
        // Empty both tanks before the blocks go: a tank broken with fluid in it spills fluid shards.
        if (pump != null) {
            pump.getTank().setFluidStack(0, FluidStack.EMPTY);
            pump.onRemove();
        }
        if (tank != null) {
            tank.tank.setFluidStack(0, FluidStack.EMPTY);
        }
        helper.setBlock(SIGNAL_SIDE, Blocks.AIR);
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.setBlock(TANK_SIDE, Blocks.AIR);
        helper.setBlock(POOL, Blocks.AIR);
        for (BlockPos wall : new BlockPos[] { POOL.west(), POOL.east(), POOL.north(), POOL.south() }) {
            helper.setBlock(wall, Blocks.AIR);
        }
        helper.setBlock(FLOOR, Blocks.AIR);
    }
}
