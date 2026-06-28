/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Client-side cache of {@link ZonePlannerMapChunk}s, keyed by packed chunk coordinate. Built lazily
 * from the client's own loaded chunks as the Zone Planner's viewport pans over them; columns outside
 * the loaded area simply return {@code null} and render as gaps.
 *
 * <p>The cache is {@link #clear() cleared} when the planner GUI opens (and is safe to clear on
 * dimension change), so it never serves stale terrain from a previous world. A built chunk is kept for
 * the life of the open GUI &mdash; terrain rarely changes mid-edit, and a one-time scan per chunk keeps
 * the per-frame render allocation-free.
 */
public enum ZonePlannerMapDataClient {
    INSTANCE;

    private final Map<Long, ZonePlannerMapChunk> cache = new HashMap<>();

    private static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFF_FFFFL) | ((long) chunkZ << 32);
    }

    /**
     * The map data for the given chunk, building and caching it on first access. Returns {@code null}
     * if the chunk is not currently loaded on the client (the viewport renders those as empty).
     */
    @Nullable
    public ZonePlannerMapChunk getChunk(Level level, int chunkX, int chunkZ) {
        long k = key(chunkX, chunkZ);
        ZonePlannerMapChunk existing = cache.get(k);
        if (existing != null) {
            return existing;
        }
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        if (chunk instanceof EmptyLevelChunk) {
            return null;
        }
        ZonePlannerMapChunk built = new ZonePlannerMapChunk(level, chunk, chunkX, chunkZ);
        cache.put(k, built);
        return built;
    }

    public void clear() {
        cache.clear();
    }
}
