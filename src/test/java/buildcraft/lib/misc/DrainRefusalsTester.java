/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The refusal memory pumps (and the pump robot board) keep so a cell something refused to give up is not queued
 * again on every rebuild: remembered while the cell's state is unchanged, forgotten when it changes, when the
 * memory ages out, and when it outgrows its bound. Pure logic over an explicit game-time argument, so no world is
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

    @Test
    public void theMemoryIsBounded() {
        DrainRefusals refusals = new DrainRefusals();
        for (int i = 0; i < DrainRefusals.MAX_ENTRIES + 10; i++) {
            refusals.remember(new BlockPos(i, 64, 0), WATER, 0);
            Assertions.assertTrue(refusals.size() <= DrainRefusals.MAX_ENTRIES,
                    "size " + refusals.size() + " exceeds the bound after " + (i + 1) + " refusals");
        }
        Assertions.assertTrue(refusals.size() > 0, "overflowing drops old entries, it does not stop remembering");
        Assertions.assertTrue(refusals.isRefused(new BlockPos(DrainRefusals.MAX_ENTRIES + 9, 64, 0), WATER, 0),
                "the newest refusal survives an overflow");
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
