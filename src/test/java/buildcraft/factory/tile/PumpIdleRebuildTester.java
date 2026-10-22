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

import buildcraft.api.mj.MjAPI;
import buildcraft.core.BCCoreConfig;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.test.ClaimProtectionMod;

/**
 * A pump with nothing to pump must not re-walk its whole pool at a fixed cadence, yet must still notice work.
 *
 * <p>The regression: while its queue was empty the pump rebuilt the flood fill (up to {@code pumpMaxDistance}
 * blocks out, 512 at most) every 30 ticks. A big pool whose every cell refuses the pump (a claim) or is not
 * drainable is walked in full each time, which is the standing cost of an idle pump. The rebuilds now back off the
 * longer they keep finding nothing, up to {@link TilePump#MAX_IDLE_REBUILD_DELAY}, and start over the moment the
 * pump finds work, its column changes, or an adjacent block change turns out to have changed the column.
 *
 * <p>Layout (one arena cell): the pump at y=6 over a tube column of air, a two-cell pool at y=2 ringed with stone
 * (so the water cannot run into a neighbouring arena), nothing adjacent to the pump but the cell below it. The pump
 * is driven by calling its own {@code serverTick} with power, so no world tick is assumed; every loop counts the
 * pump's own ticks.
 */
public class PumpIdleRebuildTester {

    private static final BlockPos POOL_A = new BlockPos(2, 2, 2);
    private static final BlockPos POOL_B = new BlockPos(3, 2, 2);
    private static final BlockPos PUMP = new BlockPos(2, 6, 2);
    /** Beside the pump, clear of its tube column: changing it fires the pump block's neighbour update. */
    private static final BlockPos BESIDE_PUMP = new BlockPos(3, 6, 2);

    /** What a pump rebuilding every 30 ticks costs over the idle stretch; a back-off must come in far under it. */
    private static final int IDLE_TICKS = 1500;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static TilePump buildPump(GameTestHelper helper) {
        check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
        for (int x = 2; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 3), Blocks.STONE);
        }
        helper.setBlock(PUMP, BCFactoryBlocks.PUMP.get());
        TilePump pump = (TilePump) helper.getLevel().getBlockEntity(helper.absolutePos(PUMP));
        check(pump != null, "the pump must have a block entity");
        return pump;
    }

    private static void runPump(TilePump pump, int ticks) {
        for (int i = 0; i < ticks; i++) {
            pump.getBattery().addPowerChecking(10 * MjAPI.MJ, false);
            pump.serverTick();
        }
    }

    /** Runs the pump until it has pumped a bucket; returns the ticks it took, or -1 if {@code limit} ran out. */
    private static int ticksToFirstBucket(TilePump pump, int limit) {
        for (int i = 1; i <= limit; i++) {
            runPump(pump, 1);
            if (pump.getTank().getAmountMb(0) >= 1000) {
                return i;
            }
        }
        return -1;
    }

    private static void drainTheRig(GameTestHelper helper) {
        helper.setBlock(POOL_A, Blocks.AIR);
        helper.setBlock(POOL_B, Blocks.AIR);
        helper.setBlock(BESIDE_PUMP, Blocks.AIR);
    }

    /**
     * Every cell of the pool refuses the pump (a claim covers both). The pump takes nothing, and in a long idle
     * stretch it walks the pool a handful of times, not once per 30 ticks.
     */
    public static void idleRebuildsBackOffOverARefusedPool(GameTestHelper helper) {
        try {
            TilePump pump = buildPump(helper);
            helper.setBlock(POOL_A, Blocks.WATER);
            helper.setBlock(POOL_B, Blocks.WATER);
            ServerLevel level = helper.getLevel();
            try (ClaimProtectionMod atA = new ClaimProtectionMod(helper.absolutePos(POOL_A), true);
                    ClaimProtectionMod atB = new ClaimProtectionMod(helper.absolutePos(POOL_B), true)) {
                runPump(pump, IDLE_TICKS);

                check(atA.asks() + atB.asks() >= 1, "liveness: the pump must have asked the claim about the pool");
                check(pump.getTank().getAmountMb(0) == 0,
                        "precondition: a fully refused pool gives the pump nothing, got "
                                + pump.getTank().getAmountMb(0) + " mB");
                check(level.getFluidState(helper.absolutePos(POOL_A)).isSource()
                        && level.getFluidState(helper.absolutePos(POOL_B)).isSource(),
                        "precondition: the refused cells are still water");
                int fixedCadence = IDLE_TICKS / TilePump.REBUILD_DELAY;
                check(pump.rebuildCount <= 15,
                        "an idle pump over a refused pool must back its rebuilds off, but it rebuilt "
                                + pump.rebuildCount + " times in " + IDLE_TICKS + " ticks (a fixed "
                                + TilePump.REBUILD_DELAY + "-tick cadence would be " + fixedCadence + ")");
            }
            drainTheRig(helper);
            helper.succeed();
        } catch (Throwable t) {
            drainTheRig(helper);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * A pool that appears in the pump's column while the pump sits fully backed off, in a cell that is not next to the
     * pump (no neighbour update wakes it), is still found at the cadence the back-off replaced: the idle pump looks
     * down its column every {@link TilePump#REBUILD_DELAY} ticks and rebuilds as soon as it changed, instead of
     * waiting out the rest of a back-off of up to {@link TilePump#MAX_IDLE_REBUILD_DELAY} ticks.
     */
    public static void idlePumpFindsAColumnPoolPromptly(GameTestHelper helper) {
        try {
            TilePump pump = buildPump(helper);
            runPump(pump, IDLE_TICKS); // an empty basin: every rebuild finds nothing, so the back-off is at its cap
            check(pump.getTank().getAmountMb(0) == 0, "precondition: nothing to pump yet");
            // Pool the water just after a rebuild, so the next scheduled one is the whole capped wait away.
            int seen = pump.rebuildCount;
            for (int i = 0; i < TilePump.MAX_IDLE_REBUILD_DELAY + 50 && pump.rebuildCount == seen; i++) {
                runPump(pump, 1);
            }
            check(pump.rebuildCount > seen, "precondition: an idle pump must rebuild again within the longest back-off");

            helper.setBlock(POOL_A, Blocks.WATER); // y=2, four blocks under the pump: no neighbour update reaches it
            int limit = 2 * TilePump.REBUILD_DELAY + 20;
            int ticks = ticksToFirstBucket(pump, limit);
            check(ticks > 0, "a backed-off pump took more than " + limit + " ticks to find a pool that appeared in its"
                    + " column (the back-off waits up to " + TilePump.MAX_IDLE_REBUILD_DELAY + ")");
            drainTheRig(helper);
            helper.succeed();
        } catch (Throwable t) {
            drainTheRig(helper);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * A block changing next to a backed-off pump wakes it: the pool is found within a few ticks instead of waiting
     * out the rest of the back-off.
     */
    public static void idlePumpWakesWhenAnAdjacentBlockChanges(GameTestHelper helper) {
        try {
            TilePump pump = buildPump(helper);
            runPump(pump, IDLE_TICKS);
            check(pump.getTank().getAmountMb(0) == 0, "precondition: nothing to pump yet");
            // Pool the water just after a rebuild, so the next one is a whole delay away and only a wake can help.
            int seen = pump.rebuildCount;
            for (int i = 0; i < TilePump.MAX_IDLE_REBUILD_DELAY + 50 && pump.rebuildCount == seen; i++) {
                runPump(pump, 1);
            }
            check(pump.rebuildCount > seen, "precondition: an idle pump must rebuild again within the longest back-off");
            helper.setBlock(POOL_A, Blocks.WATER);
            runPump(pump, 5);
            check(pump.getTank().getAmountMb(0) == 0, "precondition: the pool is not found before anything wakes the pump");

            helper.setBlock(BESIDE_PUMP, Blocks.STONE); // fires the pump block's neighbour update
            int ticks = ticksToFirstBucket(pump, 10);
            check(ticks > 0, "an adjacent block change must wake the backed-off pump, but it took more than 10 ticks");
            drainTheRig(helper);
            helper.succeed();
        } catch (Throwable t) {
            drainTheRig(helper);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
