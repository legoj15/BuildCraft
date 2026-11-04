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
 */
public class ChunkTicketSharingTester {

    public static void releaseKeepsAnotherOwnersForcedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Far from every arena: the framework places none here, so forcing and unforcing it disturbs no test.
        BlockPos owner = helper.absolutePos(BlockPos.ZERO).offset(16 * 64, 0, 16 * 64);
        int chunkX = owner.getX() >> 4;
        int chunkZ = owner.getZ() >> 4;
        long key = BlockUtil.chunkKey(chunkX, chunkZ);
        boolean wasForced = forced(level).contains(key);
        try {
            level.setChunkForced(chunkX, chunkZ, true); // the other owner (/forceload)
            Platform.chunkTickets().forceChunk(level, owner, chunkX, chunkZ, true);
            Platform.chunkTickets().forceChunk(level, owner, chunkX, chunkZ, false);
            helper.assertTrue(forced(level).contains(key),
                "BuildCraft's release must leave a chunk another owner forced still forced");
        } finally {
            if (!wasForced) {
                level.setChunkForced(chunkX, chunkZ, false);
            }
        }
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
