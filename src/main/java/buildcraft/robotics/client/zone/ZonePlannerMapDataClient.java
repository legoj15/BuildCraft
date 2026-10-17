/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import javax.annotation.Nullable;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Client-side cache of {@link ZonePlannerMapChunk}s. Built lazily from the client's own loaded chunks as the Zone
 * Planner's viewport (and the planner block's in-world screen) look at them; columns outside the loaded area
 * simply return {@code null} and render as gaps.
 *
 * <p>The lifetime rules live in {@link LevelChunkCache} (and its unit tests): the cache holds one level's chunks
 * at a time, so another dimension or world never sees them; it is {@link #clear() cleared} when the planner GUI
 * opens, on logout and on client level unload; a chunk is {@link #invalidateChunk dropped} when it reloads or
 * unloads; and, because the client is never told when a single block changes, an entry older than a few seconds
 * is rebuilt (a few per {@link #tick}, oldest first, so a wide survey view stays cheap and its far side still
 * refreshes); a chunk nobody has looked at for a minute is dropped. Everything here runs on the client
 * main thread; the events that drive it are wired in {@code BCRoboticsClient.GameBus}.
 */
public enum ZonePlannerMapDataClient {
    INSTANCE;

    private final LevelChunkCache<Level, ZonePlannerMapChunk> cache =
            new LevelChunkCache<>(ZonePlannerMapDataClient::build);

    /** Scans one chunk of {@code level}, or returns {@code null} if the client has not got it. */
    @Nullable
    private static ZonePlannerMapChunk build(Level level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        if (chunk instanceof EmptyLevelChunk) {
            return null;
        }
        return new ZonePlannerMapChunk(level, chunk, chunkX, chunkZ);
    }

    /**
     * The map data for the given chunk of {@code level}, building and caching it on first access. Returns
     * {@code null} if the chunk is not currently loaded on the client (the viewport renders those as empty).
     */
    @Nullable
    public ZonePlannerMapChunk getChunk(Level level, int chunkX, int chunkZ) {
        return cache.get(level, chunkX, chunkZ);
    }

    /** Drops one chunk (it was reloaded or unloaded); the next look rebuilds it from the client's chunk. */
    public void invalidateChunk(Level level, int chunkX, int chunkZ) {
        cache.invalidate(level, chunkX, chunkZ);
    }

    /** Advances the refresh clock; called once per client tick. */
    public void tick() {
        cache.tick();
    }

    /** Forgets everything: the planner GUI opening, logout, a client level unloading. */
    public void clear() {
        cache.clear();
    }
}
