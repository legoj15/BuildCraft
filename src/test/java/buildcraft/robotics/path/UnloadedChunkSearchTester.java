/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** The robot pathfinder against a REAL server level: asking about a cell whose chunk is not loaded must answer
 *  "not soft" without touching the chunk. {@code Level.getBlockState} on an unloaded chunk loads (or generates) it
 *  synchronously on the server thread, which a robot near the edge of view distance would do on every search and
 *  path that brushed the boundary. The far chunk is picked thousands of blocks from the arena and checked unloaded
 *  first; after the reads it must still be unloaded. Synchronous: nothing is placed. */
public class UnloadedChunkSearchTester {

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

        SoftBlockAccess access = SoftBlockAccess.of(level);
        helper.assertFalse(access.isChunkLoaded(far), "the access reports the far chunk unloaded");
        helper.assertFalse(access.isSoft(far), "a cell in an unloaded chunk is not somewhere a robot may fly");

        // A path whose every neighbour lies in the unloaded chunk: A* asks about all 26 of them.
        PathFinding path = new PathFinding(access, far, far.east(4), 0, 96f);
        for (int i = 0; i < 10 && !path.isDone(); i++) {
            path.iterate();
        }
        helper.assertTrue(path.isDone() && path.getResult().isEmpty(), "no path through an unloaded chunk");

        helper.assertFalse(isLoaded(level, far),
                "asking the robot pathfinder about an unloaded chunk must not load it");
        helper.succeed();
    }

    private static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
