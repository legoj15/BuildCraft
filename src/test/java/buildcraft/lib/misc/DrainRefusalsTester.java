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
 * memory ages out (a window that grows with the refused pool, never a clear-everything cap). Pure logic over an
 * explicit game-time argument (and, where staleness matters, an explicit state lookup), so no world is needed.
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
     * Size never evicts a live refusal: one refusal per tick (the pump's rate) over thousands of cells keeps every
     * one of them remembered until its own window ends, however many cells the pool has. (Forgetting live refusals
     * would have the pump re-queue and re-probe them on the next rebuild, a probe on nearly every tick.)
     */
    @Test
    public void aLargePoolIsNotForgottenEarly() {
        DrainRefusals refusals = new DrainRefusals();
        int cells = 5000; // thousands of cells, all still inside their windows when they are checked
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
        long measureFrom = 40L * cells; // well past the first expiries and their re-asks
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

    /**
     * The window stretches only for refusals that are still live. Once a big pool's refusals have all expired, a
     * single new refusal gets the plain {@link DrainRefusals#MEMORY_TICKS} window, not one sized for cells nobody
     * remembers any more.
     */
    @Test
    public void expiredRefusalsDoNotStretchANewWindow() {
        DrainRefusals refusals = new DrainRefusals();
        for (int i = 0; i < 5000; i++) {
            refusals.remember(new BlockPos(i, 64, 0), WATER, i);
        }
        long later = 1000L * DrainRefusals.MEMORY_TICKS; // past the expiry of every pool refusal
        refusals.remember(CELL, WATER, later);
        Assertions.assertTrue(refusals.isRefused(CELL, WATER, later + DrainRefusals.MEMORY_TICKS - 1),
                "precondition: the new refusal is remembered for the plain window");
        Assertions.assertFalse(refusals.isRefused(CELL, WATER, later + DrainRefusals.MEMORY_TICKS),
                "a lone live refusal must be asked again after the plain window, not one stretched by "
                        + "expired entries");
    }

    /**
     * Cells that have changed since they refused no longer count either: a pool that was emptied (or turned into
     * something else) by other means must not stretch the window of the next refusal, even though nothing looked
     * those cells up again. The state lookup is how the memory tells; a cell it cannot read stays counted.
     */
    @Test
    public void changedCellsDoNotStretchANewWindow() {
        DrainRefusals refusals = new DrainRefusals();
        for (int i = 0; i < 5000; i++) {
            refusals.remember(new BlockPos(i, 64, 0), WATER, i);
        }
        long now = 5000;
        refusals.remember(CELL, WATER, now, pos -> pos.equals(CELL) ? WATER : LAVA); // the pool is lava now
        Assertions.assertEquals(1, refusals.size(), "the changed cells' refusals are dropped");
        Assertions.assertFalse(refusals.isRefused(CELL, WATER, now + DrainRefusals.MEMORY_TICKS),
                "a lone live refusal gets the plain window, not one stretched by cells that have changed");

        DrainRefusals unreadable = new DrainRefusals();
        for (int i = 0; i < 5000; i++) {
            unreadable.remember(new BlockPos(i, 64, 0), WATER, i);
        }
        unreadable.remember(CELL, WATER, now, pos -> null); // e.g. every pool cell is in an unloaded chunk
        Assertions.assertEquals(5001, unreadable.size(), "a cell whose state cannot be read is kept");
        Assertions.assertTrue(unreadable.isRefused(CELL, WATER, now + DrainRefusals.MEMORY_TICKS),
                "and still counts toward the window, since it may well still be refusing");
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
