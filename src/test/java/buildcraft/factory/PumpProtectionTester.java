/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.mj.MjAPI;
import buildcraft.core.BCCoreConfig;
import buildcraft.factory.tile.TilePump;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.RefusingSourceBlock;
import buildcraft.lib.test.TestOnlyBlocks;

/**
 * Pumps ask before they drain, and do not keep asking a cell that said no.
 *
 * <p>Two refusals reach a pump. A claim/protection mod can cancel the break probe for a cell
 * ({@link buildcraft.lib.misc.BlockUtil#canMachineBreak}), and a modded {@code BucketPickup} can report a source yet
 * refuse {@code pickupBlock} ({@link RefusingSourceBlock}; no vanilla block does). Either way the pump must leave the
 * cell alone, take the rest of the pool, and not queue the same cell again on every 30-tick rebuild while it is
 * unchanged.
 *
 * <p>Layout (one arena cell; the pump sits directly above A, so A is the tube's seed cell):
 * <pre>
 *   y=3     P          P = pump
 *   y=2   # A  B  #    A, B = fluid cells, # = stone wall (plus walls on z=0 and z=2)
 *   y=1   # #  #  #    stone floor
 *        x=0 1  2  3
 * </pre>
 * The pump is driven by calling its own {@code serverTick}, so no fluid flows mid-test and no world tick is
 * assumed; the loops are the pump's own tick count, not a wait on the world clock.
 */
public class PumpProtectionTester {

    private static final BlockPos CELL_A = new BlockPos(1, 2, 1);
    private static final BlockPos CELL_B = new BlockPos(2, 2, 1);

    /** Long enough for the pump to drain what it can and then sit through a dozen idle rebuilds (one per 30 ticks). */
    private static final int PUMP_TICKS = 400;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static void requireProtectionChecksOn() {
        check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
    }

    /** Walls and floor around the two fluid cells, and the pump above A. The cells themselves are left to the test. */
    private static TilePump buildPumpedChannel(GameTestHelper helper) {
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
        for (int x = 1; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, 2, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 2), Blocks.STONE);
        }
        BlockPos pumpRel = CELL_A.above();
        helper.setBlock(pumpRel, BCFactoryBlocks.PUMP.get());
        TilePump pump = (TilePump) helper.getLevel().getBlockEntity(helper.absolutePos(pumpRel));
        check(pump != null, "the pump must have a block entity");
        return pump;
    }

    private static void runPump(TilePump pump, int ticks) {
        for (int i = 0; i < ticks; i++) {
            pump.getBattery().addPowerChecking(10 * MjAPI.MJ, false);
            pump.serverTick();
        }
    }

    private static boolean isSource(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).isSource();
    }

    /**
     * A claim covers the cell under the pump. The pump asks (once), is refused, drains the unclaimed cell beside it,
     * and leaves the claimed one alone — however many rebuilds go by.
     */
    public static void refusedCellIsNotDrainedOrRetried(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        TilePump pump = buildPumpedChannel(helper);
        helper.setBlock(CELL_A, Blocks.WATER);
        helper.setBlock(CELL_B, Blocks.WATER);
        BlockPos a = helper.absolutePos(CELL_A);
        BlockPos b = helper.absolutePos(CELL_B);

        try (ClaimProtectionMod claim = new ClaimProtectionMod(a, true);
                ClaimProtectionMod open = new ClaimProtectionMod(b, false)) {
            runPump(pump, PUMP_TICKS);

            check(isSource(level, a), "a refused pump must leave the claimed cell alone");
            check(!isSource(level, b), "the unclaimed cell beside it is still drained");
            check(pump.getTank().getAmountMb(0) == 1000,
                    "exactly the one unclaimed bucket is pumped, got " + pump.getTank().getAmountMb(0));
            check(claim.asks() == 1, "a refused cell is asked about once, then remembered while it is unchanged; asked "
                    + claim.asks() + " times in " + PUMP_TICKS + " pump ticks");
            check(open.asks() == 1, "an allowed cell is asked about once per bucket, asked " + open.asks());
            Player asker = claim.askedBy.get(0);
            check(asker instanceof FakePlayer, "the pump asks as a fake player");
            check(GameProfileUtil.getId(FakePlayerUtil.BUILDCRAFT_PROFILE).equals(asker.getUUID()),
                    "an unowned pump asks as the [BuildCraft] identity");
        }

        helper.setBlock(CELL_A, Blocks.AIR);
        helper.succeed();
    }

    /** Nothing refuses: the pump asks about each cell it drains, once, and takes every source. */
    public static void unprotectedSourcesAreStillDrained(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        TilePump pump = buildPumpedChannel(helper);
        helper.setBlock(CELL_A, Blocks.WATER);
        helper.setBlock(CELL_B, Blocks.WATER);
        BlockPos a = helper.absolutePos(CELL_A);
        BlockPos b = helper.absolutePos(CELL_B);

        try (ClaimProtectionMod atA = new ClaimProtectionMod(a, false);
                ClaimProtectionMod atB = new ClaimProtectionMod(b, false)) {
            runPump(pump, PUMP_TICKS);

            check(!isSource(level, a) && !isSource(level, b), "an unprotected pool is drained completely");
            check(pump.getTank().getAmountMb(0) == 2000,
                    "both buckets are pumped, got " + pump.getTank().getAmountMb(0));
            check(atA.asks() == 1 && atB.asks() == 1,
                    "each drained cell is asked about once: A " + atA.asks() + ", B " + atB.asks());
            check(GameProfileUtil.getId(FakePlayerUtil.BUILDCRAFT_PROFILE).equals(atA.askedBy.get(0).getUUID()),
                    "an unowned pump asks as the [BuildCraft] identity");
        }
        helper.succeed();
    }

    /**
     * The pump asks about the cell it is working on, not every cell it could reach: the whole pool is queued when the
     * pump (re)builds, but the cell it has not started on has not been asked about by the time the first bucket is
     * credited. A pool inside a big claim would otherwise spam every break listener with one probe per cell.
     */
    public static void asksOnlyAboutTheCellItIsWorkingOn(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        TilePump pump = buildPumpedChannel(helper);
        helper.setBlock(CELL_A, Blocks.WATER);
        helper.setBlock(CELL_B, Blocks.WATER);
        BlockPos a = helper.absolutePos(CELL_A);
        BlockPos b = helper.absolutePos(CELL_B);

        try (ClaimProtectionMod atA = new ClaimProtectionMod(a, false);
                ClaimProtectionMod atB = new ClaimProtectionMod(b, false)) {
            int ticks = 0;
            while (isSource(level, a) && isSource(level, b) && ticks < 50) {
                runPump(pump, 1);
                ticks++;
            }
            check(!(isSource(level, a) && isSource(level, b)), "precondition: the pump drained its first cell");
            check(pump.getTank().getAmountMb(0) == 1000, "precondition: exactly one bucket so far, got "
                    + pump.getTank().getAmountMb(0));
            check(atA.asks() + atB.asks() == 1,
                    "only the cell being drained is asked about; asked A " + atA.asks() + ", B " + atB.asks());
        }
        helper.setBlock(CELL_A, Blocks.AIR);
        helper.setBlock(CELL_B, Blocks.AIR);
        helper.succeed();
    }

    /**
     * A permit covers one bucket, not one cell forever. The pump is cleared to drain A but runs out of power
     * mid-bucket; a player scoops A first, so the pump abandons the bucket and moves on. The owner then claims A and
     * the water comes back. When a later rebuild queues A again the pump must ask afresh: the old permit was for a
     * bucket that was never credited, and reusing it would drain from inside the new claim without asking.
     */
    public static void abandonedPermitIsNotReused(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        TilePump pump = buildPumpedChannel(helper);
        helper.setBlock(CELL_A, Blocks.WATER);
        helper.setBlock(CELL_B, Blocks.STONE); // a one-cell pool: A is the only cell the pump can ever choose
        BlockPos a = helper.absolutePos(CELL_A);

        // Power-starved: a trickle that lets the pump pick A and be cleared for it, but never finish the bucket.
        try (ClaimProtectionMod open = new ClaimProtectionMod(a, false)) {
            for (int i = 0; i < 4 && open.asks() == 0; i++) {
                pump.getBattery().addPowerChecking(MjAPI.MJ, false);
                pump.serverTick();
            }
            check(open.asks() == 1, "precondition: the pump was cleared for A, asked " + open.asks() + " times");
            check(pump.getTank().getAmountMb(0) == 0, "precondition: the bucket from A is still unfinished");
        }

        // A player scoops A mid-bucket: the pump's bucket is abandoned and it moves off the cell.
        helper.setBlock(CELL_A, Blocks.AIR);
        for (int i = 0; i < PUMP_TICKS && !pump.isComplete(); i++) {
            runPump(pump, 1);
        }
        check(pump.isComplete(), "precondition: with A scooped, the pump has nothing left to work on");

        // The owner claims A, then the water comes back; the next rebuild queues A again.
        try (ClaimProtectionMod claim = new ClaimProtectionMod(a, true)) {
            helper.setBlock(CELL_A, Blocks.WATER);
            runPump(pump, PUMP_TICKS);

            check(claim.asks() >= 1, "a re-queued cell is asked about again, not waved through on an abandoned permit");
            check(isSource(level, a), "the claimed cell is not drained");
            check(pump.getTank().getAmountMb(0) == 0,
                    "nothing is pumped from inside the claim, got " + pump.getTank().getAmountMb(0));
        }

        helper.setBlock(CELL_A, Blocks.AIR);
        helper.succeed();
    }

    /**
     * A permit is checked again at the moment the fluid is taken, not only when the bucket starts. A pump with no
     * power picks A and is cleared for it while A is unclaimed; the owner then claims A, and only after that does the
     * pump get power. Finishing the bucket must not drain A: the claim exists when the world changes, and the pump
     * has to see it then, not ride a permit it was given before the claim.
     */
    public static void permitIsRecheckedBeforeTheDrain(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        TilePump pump = buildPumpedChannel(helper);
        helper.setBlock(CELL_A, Blocks.WATER);
        helper.setBlock(CELL_B, Blocks.STONE); // a one-cell pool: A is the only cell the pump can ever choose
        BlockPos a = helper.absolutePos(CELL_A);

        // No power at all: the pump picks A and is cleared for it, but cannot start the bucket.
        try (ClaimProtectionMod open = new ClaimProtectionMod(a, false)) {
            for (int i = 0; i < 4 && open.asks() == 0; i++) {
                pump.serverTick();
            }
            check(open.asks() == 1, "precondition: the pump was cleared for A, asked " + open.asks() + " times");
            check(pump.getTank().getAmountMb(0) == 0, "precondition: nothing pumped without power");
        }

        // The owner claims A while the pump waits; then the power arrives.
        try (ClaimProtectionMod claim = new ClaimProtectionMod(a, true)) {
            runPump(pump, PUMP_TICKS);

            check(claim.asks() >= 1, "the pump asks again before it takes the fluid, asked " + claim.asks());
            check(isSource(level, a), "a cell claimed after its permit was given is not drained");
            check(pump.getTank().getAmountMb(0) == 0,
                    "nothing is pumped from inside the claim, got " + pump.getTank().getAmountMb(0));
        }

        helper.setBlock(CELL_A, Blocks.AIR);
        helper.succeed();
    }

    /**
     * A refusal at the drain spends the bucket's power like any other refusal. The pump is cleared for a cell while
     * unpowered, the owner claims that cell, and exactly one bucket of power arrives: the drain-time recheck refuses
     * the cell and the pump moves on. The next cell then has to be paid for in full — with no further power it must
     * stay untouched, and it is drained only once another bucket of power has been received. (Carrying the refused
     * cell's charge over made the next bucket free.)
     */
    public static void drainTimeRefusalSpendsTheBucketsPower(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        TilePump pump = buildPumpedChannel(helper);
        helper.setBlock(CELL_A, Blocks.WATER);
        helper.setBlock(CELL_B, Blocks.WATER);
        BlockPos a = helper.absolutePos(CELL_A);
        BlockPos b = helper.absolutePos(CELL_B);

        // No power: the pump picks a cell and is cleared for it, but cannot start the bucket. Which of the two it
        // picks first is the pump's business, so watch both and read the answer off the asks.
        BlockPos refusedAbs;
        BlockPos otherAbs;
        try (ClaimProtectionMod atA = new ClaimProtectionMod(a, false);
                ClaimProtectionMod atB = new ClaimProtectionMod(b, false)) {
            for (int i = 0; i < 4 && atA.asks() + atB.asks() == 0; i++) {
                pump.serverTick();
            }
            check(atA.asks() + atB.asks() == 1,
                    "precondition: the pump was cleared for exactly one cell, asked A " + atA.asks() + ", B " + atB.asks());
            refusedAbs = atA.asks() == 1 ? a : b;
            otherAbs = atA.asks() == 1 ? b : a;
            check(pump.getTank().getAmountMb(0) == 0, "precondition: nothing pumped without power");
        }

        try (ClaimProtectionMod claim = new ClaimProtectionMod(refusedAbs, true);
                ClaimProtectionMod open = new ClaimProtectionMod(otherAbs, false)) {
            // Exactly one bucket of power arrives, after the claim: the drain-time recheck refuses the cell.
            pump.getBattery().addPowerChecking(10 * MjAPI.MJ, false);
            pump.serverTick();
            check(claim.asks() == 1, "precondition: the charged bucket was refused at the drain, asked "
                    + claim.asks() + " times");
            check(isSource(level, refusedAbs), "precondition: the refused cell is untouched");
            check(pump.getTank().getAmountMb(0) == 0, "precondition: nothing pumped yet");

            // No more power: the next cell cannot be drained, however long the pump sits on it (a rebuild included).
            for (int i = 0; i < 45; i++) {
                pump.serverTick();
            }
            check(isSource(level, otherAbs), "the next cell is not drained on the refused cell's charge");
            check(pump.getTank().getAmountMb(0) == 0,
                    "no bucket is credited without power for it, got " + pump.getTank().getAmountMb(0));

            // A full bucket of power: now the next cell is drained.
            runPump(pump, PUMP_TICKS);
            check(!isSource(level, otherAbs), "the next cell is drained once its own bucket of power arrives");
            check(isSource(level, refusedAbs), "the refused cell is still left alone");
            check(pump.getTank().getAmountMb(0) == 1000,
                    "exactly the one unclaimed bucket is pumped, got " + pump.getTank().getAmountMb(0));
            check(open.asks() >= 1, "the cell that was paid for is asked about before it is drained, asked "
                    + open.asks());
        }

        helper.setBlock(CELL_A, Blocks.AIR);
        helper.setBlock(CELL_B, Blocks.AIR);
        helper.succeed();
    }

    /**
     * An abandoned bucket spends its power too. The pump is cleared for the only cell of a one-cell pool while
     * unpowered; a player scoops that cell on the very tick a full bucket of power arrives, so the bucket is charged
     * but there is nothing to take and the pump abandons it. The water then comes back and a rebuild queues the cell
     * again. With no new power the pump must leave it alone, however long it waits (the abandoned charge carried over
     * made the cell free); it is drained only once a bucket of power has been received for it.
     */
    public static void abandonedBucketSpendsItsPower(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        TilePump pump = buildPumpedChannel(helper);
        helper.setBlock(CELL_A, Blocks.WATER);
        helper.setBlock(CELL_B, Blocks.STONE); // a one-cell pool: A is the only cell the pump can ever choose
        BlockPos a = helper.absolutePos(CELL_A);

        // No power: the pump picks A and is cleared for it, but cannot start the bucket.
        try (ClaimProtectionMod open = new ClaimProtectionMod(a, false)) {
            for (int i = 0; i < 4 && open.asks() == 0; i++) {
                pump.serverTick();
            }
            check(open.asks() == 1, "precondition: the pump was cleared for A, asked " + open.asks() + " times");
            check(pump.getTank().getAmountMb(0) == 0, "precondition: nothing pumped without power");
        }

        // A player scoops A on the tick a full bucket of power arrives: the bucket is charged, the cell is empty.
        helper.setBlock(CELL_A, Blocks.AIR);
        pump.getBattery().addPowerChecking(10 * MjAPI.MJ, false);
        pump.serverTick();
        check(pump.getTank().getAmountMb(0) == 0, "precondition: nothing is pumped from a scooped cell");

        // The water comes back, and no power with it. A rebuild queues A again within the 30-tick delay; the pump
        // starts a bucket on it (the start check asks) but has nothing to finish it with.
        helper.setBlock(CELL_A, Blocks.WATER);
        try (ClaimProtectionMod requeued = new ClaimProtectionMod(a, false)) {
            for (int i = 0; i < 90; i++) {
                pump.serverTick();
            }
            check(requeued.asks() >= 1, "precondition: the rebuild queued A again and the pump started a bucket on it,"
                    + " asked " + requeued.asks() + " times");
            check(isSource(level, a), "the re-queued cell is not drained on the abandoned bucket's charge");
            check(pump.getTank().getAmountMb(0) == 0,
                    "no bucket is credited without power for it, got " + pump.getTank().getAmountMb(0));

            // A bucket of power received for the cell: now it is drained.
            runPump(pump, PUMP_TICKS);
            check(!isSource(level, a), "the cell is drained once its own bucket of power arrives");
            check(pump.getTank().getAmountMb(0) == 1000,
                    "exactly the one bucket is pumped, got " + pump.getTank().getAmountMb(0));
        }

        helper.setBlock(CELL_A, Blocks.AIR);
        helper.succeed();
    }

    /**
     * A modded block that reports a water source but refuses its bucket pickup. The pump tries it, is refused, takes
     * the plain water beside it, and does not try the refusing cell again while it is unchanged. Before the pump
     * remembered refusals it re-queued the cell on every rebuild, and every retry spent a full cycle of power.
     */
    public static void refusedPickupIsNotRetried(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        TilePump pump = buildPumpedChannel(helper);
        helper.setBlock(CELL_A, TestOnlyBlocks.refusingSource());
        helper.setBlock(CELL_B, Blocks.WATER);
        BlockPos a = helper.absolutePos(CELL_A);
        BlockPos b = helper.absolutePos(CELL_B);
        RefusingSourceBlock.resetAttempts(a);
        check(level.getFluidState(a).isSource(), "precondition: the refusing block holds a water source");

        runPump(pump, PUMP_TICKS);

        check(level.getBlockState(a).getBlock() instanceof RefusingSourceBlock, "the refusing block is untouched");
        check(!isSource(level, b), "the plain water beside it is still drained");
        check(pump.getTank().getAmountMb(0) == 1000,
                "only the plain bucket is pumped, got " + pump.getTank().getAmountMb(0));
        check(RefusingSourceBlock.attemptsAt(a) == 1,
                "a refusing cell is tried once, then remembered while it is unchanged; tried "
                        + RefusingSourceBlock.attemptsAt(a) + " times in " + PUMP_TICKS + " pump ticks");

        helper.setBlock(CELL_A, Blocks.AIR);
        helper.succeed();
    }
}
