/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.test.FarChunk;

/** The robot pathfinder against a REAL server level: asking about a cell whose chunk is not loaded must answer
 *  "not soft" without touching the chunk. {@code Level.getBlockState} on an unloaded chunk loads (or generates) it
 *  synchronously on the server thread, which a robot near the edge of view distance would do on every search and
 *  path that brushed the boundary. The far chunk is picked thousands of blocks from the arena and checked unloaded
 *  first; after the reads it must still be unloaded. A second chunk is merely <em>queued</em> to load (a ticket and a
 *  holder, not in memory), the case {@code Level.hasChunk} answers yes to. Synchronous: nothing is placed. */
public class UnloadedChunkSearchTester {

    /** Chunks east and south of the arena's, apart from every other test's far chunk (see {@link FarChunk}). */
    private static final int QUEUED_CHUNK_OFFSET = 1555;

    public static void searchNearAnUnloadedChunkDoesNotLoadIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos arena = helper.absolutePos(BlockPos.ZERO);
        // Towards the world origin, so the far chunk can never fall outside the world border.
        int sx = arena.getX() > 0 ? -1 : 1;
        int sz = arena.getZ() > 0 ? -1 : 1;
        BlockPos far = null;
        for (int k = 1; k <= 16 && far == null; k++) {
            BlockPos candidate = arena.offset(sx * 16 * 100 * k, 0, sz * 16 * 100 * k);
            if (!isLoaded(level, candidate)) {
                far = candidate;
            }
        }
        helper.assertTrue(far != null, "precondition: some chunk far from the arena is unloaded");

        searchLeavesTheChunkAlone(helper, far);
        helper.succeed();
    }

    /**
     * A chunk with a holder at the ticket level of a loaded one, not in memory yet: the robot's access must treat it
     * as unloaded. {@code Level.hasChunk} answers yes here, so a gate built on it let the first read of the chunk load
     * it on the server thread.
     */
    public static void searchNearAQueuedChunkDoesNotLoadIt(GameTestHelper helper) {
        FarChunk chunk = FarChunk.queue(helper, QUEUED_CHUNK_OFFSET);
        try {
            searchLeavesTheChunkAlone(helper, chunk.pos(8, 64, 8));
            helper.succeed();
        } finally {
            chunk.release();
        }
    }

    /** The robot pathfinder over {@code unloaded}, a cell whose chunk is not in memory: reads nothing, and the chunk
     *  is still out of memory afterwards. */
    private static void searchLeavesTheChunkAlone(GameTestHelper helper, BlockPos unloaded) {
        ServerLevel level = helper.getLevel();
        helper.assertFalse(isLoaded(level, unloaded), "precondition: the chunk is not in memory");

        SoftBlockAccess access = SoftBlockAccess.of(level);
        helper.assertFalse(access.isChunkLoaded(unloaded), "the access reports the chunk unloaded");
        helper.assertFalse(access.isSoft(unloaded), "a cell in an unloaded chunk is not somewhere a robot may fly");

        // A path whose every neighbour lies in the unloaded chunk: A* asks about all 26 of them.
        PathFinding path = new PathFinding(access, unloaded, unloaded.east(4), 0, 96f);
        for (int i = 0; i < 10 && !path.isDone(); i++) {
            path.iterate();
        }
        helper.assertTrue(path.isDone() && path.getResult().isEmpty(), "no path through an unloaded chunk");

        helper.assertFalse(isLoaded(level, unloaded),
                "asking the robot pathfinder about an unloaded chunk must not load it");
    }

    /** In memory ({@code getChunkNow}), the notion the production gate uses: {@code Level.hasChunk} only tests the
     *  ticket level, which a chunk queued to load already satisfies. */
    private static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return BlockUtil.isChunkPresent(level, pos);
    }
}
