/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import buildcraft.robotics.client.zone.LevelChunkCache;
import buildcraft.robotics.client.zone.ZonePlannerMapChunk;
import buildcraft.robotics.client.zone.ZonePlannerMapDataClient;

/**
 * Game tests for {@link ZonePlannerMapDataClient} against real levels and chunks. The cache is client code, but
 * everything it touches ({@code Level}, chunks, heightmaps, map colours) is common, so a server level exercises
 * the same build path the client's does; the unit tests for {@link LevelChunkCache} pin its rules in isolation,
 * these pin that the colour cache is actually wired through them. No test waits on a game tick: the cache's
 * clock is {@code tick()} calls, which the tests make themselves.
 */
public class ZonePlannerMapCacheTester {

    private static final BlockPos COLUMN = new BlockPos(2, 3, 2);

    /** A changed block shows up once the cached chunk is due for a refresh, instead of never. */
    public static void terrainChangesAppearAfterTheRefreshAge(GameTestHelper helper) {
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;
        data.clear();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(COLUMN);
        int cx = abs.getX() >> 4;
        int cz = abs.getZ() >> 4;
        int lx = abs.getX() & 15;
        int lz = abs.getZ() & 15;

        helper.setBlock(COLUMN, Blocks.GOLD_BLOCK);
        int gold = Blocks.GOLD_BLOCK.defaultBlockState().getMapColor(level, abs).col;
        int diamond = Blocks.DIAMOND_BLOCK.defaultBlockState().getMapColor(level, abs).col;
        helper.assertTrue(gold != diamond, "baseline: the two blocks have different map colours");

        ZonePlannerMapChunk first = data.getChunk(level, cx, cz);
        helper.assertTrue(first != null && first.getColour(lx, lz) == gold, "baseline: the gold block is mapped");

        helper.setBlock(COLUMN, Blocks.DIAMOND_BLOCK);
        helper.assertTrue(data.getChunk(level, cx, cz).getColour(lx, lz) == gold,
                "within the refresh age the cached chunk is served as built");

        for (int i = 0; i < LevelChunkCache.REFRESH_AFTER_TICKS; i++) {
            data.tick();
        }
        ZonePlannerMapChunk refreshed = data.getChunk(level, cx, cz);
        helper.assertTrue(refreshed != null && refreshed.getColour(lx, lz) == diamond,
                "after the refresh age the changed block is mapped");

        data.clear();
        helper.succeed();
    }

    /** An explicit invalidation (a chunk reload) drops the entry at once, and the next look sees the new terrain. */
    public static void invalidatingAChunkRebuildsItAtOnce(GameTestHelper helper) {
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;
        data.clear();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(COLUMN);
        int cx = abs.getX() >> 4;
        int cz = abs.getZ() >> 4;
        int lx = abs.getX() & 15;
        int lz = abs.getZ() & 15;

        helper.setBlock(COLUMN, Blocks.GOLD_BLOCK);
        int gold = Blocks.GOLD_BLOCK.defaultBlockState().getMapColor(level, abs).col;
        int diamond = Blocks.DIAMOND_BLOCK.defaultBlockState().getMapColor(level, abs).col;
        ZonePlannerMapChunk first = data.getChunk(level, cx, cz);
        helper.assertTrue(first != null && first.getColour(lx, lz) == gold, "baseline: the gold block is mapped");

        helper.setBlock(COLUMN, Blocks.DIAMOND_BLOCK);
        data.invalidateChunk(level, cx, cz);
        ZonePlannerMapChunk rebuilt = data.getChunk(level, cx, cz);
        helper.assertTrue(rebuilt != null && rebuilt.getColour(lx, lz) == diamond,
                "the next look after an invalidation maps the changed block");

        data.clear();
        helper.succeed();
    }

    /** Another level (dimension, or another world's same-named dimension) is never served this level's chunks
     *  just because the chunk coordinates match. */
    public static void chunksAreNotSharedAcrossLevels(GameTestHelper helper) {
        ZonePlannerMapDataClient data = ZonePlannerMapDataClient.INSTANCE;
        data.clear();
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(COLUMN);
        int cx = abs.getX() >> 4;
        int cz = abs.getZ() >> 4;

        helper.setBlock(COLUMN, Blocks.GOLD_BLOCK);
        ZonePlannerMapChunk here = data.getChunk(level, cx, cz);
        helper.assertTrue(here != null, "baseline: this level's chunk is mapped");

        Level other = null;
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            if (candidate != level) {
                other = candidate;
                break;
            }
        }
        helper.assertTrue(other != null, "the test server has a second level to ask about");

        ZonePlannerMapChunk there = data.getChunk(other, cx, cz);
        helper.assertTrue(there != here,
                "another level is not handed this level's cached chunk for the same coordinates");

        // And the cache has moved on to the other level: coming back rebuilds, it does not resurrect the old entry.
        ZonePlannerMapChunk back = data.getChunk(level, cx, cz);
        helper.assertTrue(back != null && back != here, "returning to the first level builds it afresh");

        data.clear();
        helper.succeed();
    }
}
