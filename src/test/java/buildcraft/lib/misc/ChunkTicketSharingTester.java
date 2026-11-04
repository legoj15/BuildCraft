/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

import buildcraft.platform.Platform;

/**
 * A chunk BuildCraft loads and then lets go of stays forced when someone else forced it too (/forceload, another mod,
 * the game-test framework under each test's structure). The Fabric chunk tickets used to be vanilla's shared forced
 * set, so BuildCraft's release unforced the chunk for everyone; a game-test structure chunk unforced that way never
 * became entity-ticking and its test never started.
 *
 * <p>The other owner is the framework itself: it forced this test's own structure chunk before the test began, and
 * only unforces it when the batch ends. An earlier version forced a fresh chunk 64 chunks away instead, and
 * {@code setChunkForced} loads the chunk it forces on the server thread, with the 27x27 chunks its ticket reaches
 * queued for generation ahead of it: under load that blocked the whole server for up to 109 s, which the Fabric
 * run's wall-clock chunk-wait watchdog counted against every test of the batch still waiting for its chunks (#114).
 */
public class ChunkTicketSharingTester {

    public static void releaseKeepsAnotherOwnersForcedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos owner = helper.absolutePos(BlockPos.ZERO);
        int chunkX = owner.getX() >> 4;
        int chunkZ = owner.getZ() >> 4;
        long key = BlockUtil.chunkKey(chunkX, chunkZ);
        helper.assertTrue(forced(level).contains(key),
            "precondition: the framework forced this test's structure chunk");
        Platform.chunkTickets().forceChunk(level, owner, chunkX, chunkZ, true);
        Platform.chunkTickets().forceChunk(level, owner, chunkX, chunkZ, false);
        helper.assertTrue(forced(level).contains(key),
            "BuildCraft's release must leave a chunk another owner forced still forced");
        helper.succeed();
    }

    private static it.unimi.dsi.fastutil.longs.LongSet forced(ServerLevel level) {
        //? if >=1.21.10 {
        return level.getForceLoadedChunks();
        //?} else {
        /*return level.getForcedChunks();*/
        //?}
    }
}
