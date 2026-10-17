/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.ArrayDeque;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The refusal memory pumps (and the pump robot board) keep so a cell something refused to give up is not queued
 * again on every rebuild: remembered while the cell's state is unchanged, forgotten when it changes or when the
 * memory ages out (a window that grows with the refused pool, never a clear-everything cap). Pure logic over an explicit game-time argument, so no world is
 * needed.
 */
public class DrainRefusalsTester {

    private static final BlockPos CELL = new BlockPos(3, 64, -5);
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    @Test
    public void unknownCellIsNotRefused() {
        DrainRefusals refusals = new DrainRefusals();
        Assertions.assertFalse(refusals.isRefused(CELL, WATER, 0), "nothing was remembered yet");
    }

    @Test
    public void remembersARefusalWhileTheStateIsUnchanged() {
        DrainRefusals refusals = new DrainRefusals();
        refusals.remember(CELL, WATER, 100);
        Assertions.assertTrue(refusals.isRefused(CELL, WATER, 100), "refused straight away");
        Assertions.assertTrue(refusals.isRefused(CELL, WATER, 101), "and on every later look");
        Assertions.assertTrue(refusals.isRefused(CELL.immutable(), WATER, 150),
                "a different BlockPos instance for the same cell is the same cell");
        Assertions.assertFalse(refusals.isRefused(CELL.above(), WATER, 100), "only the refused cell");
    }

    @Test
    public void aChangedCellIsForgotten() {
        DrainRefusals refusals = new DrainRefusals();
        refusals.remember(CELL, WATER, 100);
        Assertions.assertEquals(1, refusals.size(), "precondition: the refusal was recorded");
        Assertions.assertFalse(refusals.isRefused(CELL, LAVA, 101), "the cell is something else now");
        Assertions.assertEquals(0, refusals.size(), "and the stale entry is dropped, not just ignored");
        Assertions.assertFalse(refusals.isRefused(CELL, WATER, 102),
                "the refusal was about the old state: a cell seen changed does not stay refused when it reverts");
    }

    @Test
    public void aRefusalAgesOut() {
        DrainRefusals refusals = new DrainRefusals();
        refusals.remember(CELL, WATER, 100);
        long lastRefused = 100 + DrainRefusals.MEMORY_TICKS - 1;
        Assertions.assertTrue(refusals.isRefused(CELL, WATER, lastRefused), "still remembered just before it expires");
        Assertions.assertFalse(refusals.isRefused(CELL, WATER, lastRefused + 1),
                "asked again once the memory expires, so a claim that was lifted is noticed");
        Assertions.assertEquals(0, refusals.size(), "an expired entry is dropped");
    }

    @Test
    public void rememberingAgainRestartsTheClock() {
        DrainRefusals refusals = new DrainRefusals();
        refusals.remember(CELL, WATER, 100);
        refusals.remember(CELL, WATER, 5000);
        Assertions.assertTrue(refusals.isRefused(CELL, WATER, 100 + DrainRefusals.MEMORY_TICKS + 10),
                "the second refusal, not the first, sets the expiry");
        Assertions.assertEquals(1, refusals.size(), "one entry per cell");
    }

    /**
     * A claimed pool bigger than any fixed cap must not be forgotten wholesale: one refusal per tick (the pump's
     * rate) over thousands of cells keeps every one of them remembered. A clear-everything cap made the pump re-queue
     * and re-probe the whole pool on the next rebuild, i.e. a probe on nearly every tick, forever.
     */
    @Test
    public void aLargePoolIsNotForgottenEarly() {
        DrainRefusals refusals = new DrainRefusals();
        int cells = 5000; // past the old 2048 clear-all cap, inside the five-minute window at one refusal per tick
        for (int i = 0; i < cells; i++) {
            refusals.remember(new BlockPos(i, 64, 0), WATER, i);
        }
        for (int i = 0; i < cells; i++) {
            Assertions.assertTrue(refusals.isRefused(new BlockPos(i, 64, 0), WATER, cells),
                    "cell " + i + " of a " + cells + "-cell refused pool was forgotten before its memory expired");
        }
    }

    /**
     * The steady state of a fully refused pool larger than {@link DrainRefusals#MEMORY_TICKS} cells: the pump
     * probes the next cell of its queue once per tick and rebuilds an empty queue every 30 ticks. A fixed
     * five-minute window would then re-ask on (almost) every tick indefinitely; the window grows with the pool, so
     * re-asking averages about one probe per {@link DrainRefusals#RECHECK_SPACING_TICKS}.
     */
    @Test
    public void aFullyRefusedPoolIsReaskedSparingly() {
        DrainRefusals refusals = new DrainRefusals();
        int cells = (int) DrainRefusals.MEMORY_TICKS + 2000;
        BlockPos[] pool = new BlockPos[cells];
        for (int i = 0; i < cells; i++) {
            pool[i] = new BlockPos(i % 100, 64, i / 100);
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        long measureFrom = 40L * cells; // well past the first sweep and its first re-asks
        long measureTo = measureFrom + 20L * cells;
        int probes = 0;
        for (long tick = 0; tick < measureTo; tick++) {
            if (queue.isEmpty() && tick % 30 == 0) {
                for (BlockPos cell : pool) {
                    if (!refusals.isRefused(cell, WATER, tick)) {
                        queue.add(cell);
                    }
                }
            }
            BlockPos next = queue.poll();
            if (next != null && !refusals.isRefused(next, WATER, tick)) {
                refusals.remember(next, WATER, tick); // the claim still says no
                if (tick >= measureFrom) {
                    probes++;
                }
            }
        }
        long measured = measureTo - measureFrom;
        long allowed = measured * 5 / (4 * DrainRefusals.RECHECK_SPACING_TICKS);
        Assertions.assertTrue(probes <= allowed, probes + " probes over " + measured
                + " ticks re-asking a fully refused " + cells + "-cell pool; allowed " + allowed);
        Assertions.assertTrue(probes > 0, "a refused pool is still re-asked eventually, so a lifted claim is noticed");
    }

    /** Expired refusals for cells nobody looks at again are swept, so the memory tracks what is live. */
    @Test
    public void expiredRefusalsDoNotPileUp() {
        DrainRefusals refusals = new DrainRefusals();
        for (int i = 0; i < 1000; i++) {
            refusals.remember(new BlockPos(i, 64, 0), WATER, 0);
        }
        long later = 100L * DrainRefusals.MEMORY_TICKS; // every first-batch refusal has long expired
        for (int i = 0; i < 1000; i++) {
            refusals.remember(new BlockPos(i, 80, 0), WATER, later);
        }
        Assertions.assertTrue(refusals.size() < 2000,
                "size " + refusals.size() + ": expired refusals were kept instead of swept");
        Assertions.assertTrue(refusals.isRefused(new BlockPos(999, 80, 0), WATER, later), "live refusals stay");
    }

    @Test
    public void clearForgetsEverything() {
        DrainRefusals refusals = new DrainRefusals();
        refusals.remember(CELL, WATER, 0);
        refusals.clear();
        Assertions.assertFalse(refusals.isRefused(CELL, WATER, 0));
        Assertions.assertEquals(0, refusals.size());
    }
}
