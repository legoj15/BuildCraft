/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * The {@link DrainRefusals} overloads that read the world ({@code remember(Level, BlockPos)} and
 * {@code isRefused(Level, BlockPos)}) promise not to load chunks: a cell in an unloaded chunk is not read, so it is
 * neither remembered nor reported as refused. Needs a real level, hence a game test rather than a unit test.
 */
public class DrainRefusalsLevelTester {

    /** Two million blocks out: nothing in a game-test server ever loads it. */
    private static final BlockPos UNLOADED = new BlockPos(2_000_000, 64, 2_000_000);

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    public static void levelOverloadsNeverLoadAChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(!level.isLoaded(UNLOADED), "precondition: the far cell's chunk is not loaded");
        DrainRefusals refusals = new DrainRefusals();

        // A cell that cannot be read cannot have refused: nothing is remembered, and the chunk stays unloaded.
        refusals.remember(level, UNLOADED);
        check(refusals.size() == 0, "an unloaded cell is not remembered, " + refusals.size() + " entries");
        check(!level.isLoaded(UNLOADED), "remember(Level, BlockPos) must not load the chunk it was asked about");

        // An entry recorded while the cell was loaded, whose chunk is unloaded by the time it is asked about: not
        // reported as refused (the caller re-evaluates once the cell is loaded), but kept, and still not loaded.
        refusals.remember(UNLOADED, Blocks.WATER.defaultBlockState(), level.getGameTime());
        check(!refusals.isRefused(level, UNLOADED), "an unloaded cell is not reported as refused");
        check(!level.isLoaded(UNLOADED), "isRefused(Level, BlockPos) must not load the chunk it was asked about");
        check(refusals.size() == 1, "the entry for the unloaded cell is kept, " + refusals.size() + " entries");

        helper.succeed();
    }

    /** The guard must not blunt the real thing: a loaded cell is remembered, reported, and forgotten on change. */
    public static void levelOverloadsStillWorkOnLoadedCells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos cell = helper.absolutePos(rel);
        helper.setBlock(rel, Blocks.WATER);
        check(level.isLoaded(cell), "precondition: the arena cell is loaded");
        DrainRefusals refusals = new DrainRefusals();

        refusals.remember(level, cell);
        check(refusals.size() == 1, "a loaded cell is remembered, " + refusals.size() + " entries");
        check(refusals.isRefused(level, cell), "and reported as refused while it is unchanged");

        helper.setBlock(rel, Blocks.LAVA);
        check(!refusals.isRefused(level, cell), "a changed cell is no longer refused");
        check(refusals.size() == 0, "and its stale entry is dropped");

        helper.setBlock(rel, Blocks.AIR);
        helper.succeed();
    }
}
