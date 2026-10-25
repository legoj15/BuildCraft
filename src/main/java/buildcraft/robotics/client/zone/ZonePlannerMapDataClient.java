/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Client-side cache of {@link ZonePlannerMapChunk}s. Built lazily from the client's own loaded chunks as the Zone
 * Planner's viewport (and the planner block's in-world screen) look at them. Where the client has no chunk, the
 * chunks the server sent from its far map when the planner screen opened ({@link #acceptFarChunk}) stand in; a chunk
 * neither has returns {@code null} and renders as a gap. The far chunks are kept until the next {@link #clear}.
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

    /** Chunks the server sent from its far map ({@code ZoneSurveyMap}), drawn wherever the client has no chunk of its
     *  own; they belong to {@link #farLevel} and are bounded by {@link #farBounds} (null while there are none). */
    private final Map<Long, ZonePlannerMapChunk> far = new HashMap<>();
    private WeakReference<Level> farLevel = new WeakReference<>(null);
    @Nullable
    private int[] farBounds;
    /** The client's own chunk square for {@link #farLevel}, as of the last {@link #tick} (null when unknown). */
    @Nullable
    private int[] ownBounds;

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
        boolean useFar = !far.isEmpty() && farLevel.refersTo(level);
        // Most of a survey view's chunks lie past the client's own square: those go straight to the far map, without
        // a miss through the client cache first (the terrain is walked every frame, several lookups per chunk).
        if (useFar && ownBounds != null && (chunkX < ownBounds[0] || chunkZ < ownBounds[1]
                || chunkX > ownBounds[2] || chunkZ > ownBounds[3])) {
            return far.get((chunkX & 0xFFFF_FFFFL) | ((long) chunkZ << 32));
        }
        ZonePlannerMapChunk own = cache.get(level, chunkX, chunkZ);
        if (own != null || !useFar) {
            return own;
        }
        return far.get((chunkX & 0xFFFF_FFFFL) | ((long) chunkZ << 32));
    }

    /** Stores one chunk of the server's far map for {@code level}, dropping any held for another level. */
    public void acceptFarChunk(Level level, int chunkX, int chunkZ, short[] heights, byte[] colourIds) {
        if (!farLevel.refersTo(level)) {
            clearFar();
            farLevel = new WeakReference<>(level);
        }
        far.put((chunkX & 0xFFFF_FFFFL) | ((long) chunkZ << 32), new ZonePlannerMapChunk(heights, colourIds));
        if (farBounds == null) {
            farBounds = new int[] {chunkX, chunkZ, chunkX, chunkZ};
        } else {
            farBounds[0] = Math.min(farBounds[0], chunkX);
            farBounds[1] = Math.min(farBounds[1], chunkZ);
            farBounds[2] = Math.max(farBounds[2], chunkX);
            farBounds[3] = Math.max(farBounds[3], chunkZ);
        }
    }

    /**
     * The chunks of {@code level} this source can have map data for, as {@code {minChunkX, minChunkZ, maxChunkX,
     * maxChunkZ}} (all inclusive), or {@code null} when it cannot say. For a client level that is the square of chunks
     * the client can hold ({@link #getChunk} answers {@code null} beyond it), so a caller walking a wide window can
     * stop at its edge instead of asking about every chunk past it. A source that can map further out than the
     * client's own chunks (a server-sampled far map) widens this answer; callers take it as the limit and never assume
     * the loaded area is the whole of it.
     */
    @Nullable
    public int[] mappedChunkBounds(Level level) {
        int[] own = level.isClientSide() ? ClientLoadedChunks.bounds(level) : null;
        if (farBounds == null || !farLevel.refersTo(level)) {
            return own;
        }
        if (own == null) {
            return farBounds.clone();
        }
        return new int[] {Math.min(own[0], farBounds[0]), Math.min(own[1], farBounds[1]),
                Math.max(own[2], farBounds[2]), Math.max(own[3], farBounds[3])};
    }

    /** Drops one chunk (it was reloaded or unloaded); the next look rebuilds it from the client's chunk. */
    public void invalidateChunk(Level level, int chunkX, int chunkZ) {
        cache.invalidate(level, chunkX, chunkZ);
    }

    /** Advances the refresh clock; called once per client tick. */
    public void tick() {
        cache.tick();
        Level level = farLevel.get();
        ownBounds = level != null && level.isClientSide() ? ClientLoadedChunks.bounds(level) : null;
    }

    /** Forgets everything: the planner GUI opening, logout, a client level unloading. */
    public void clear() {
        cache.clear();
        clearFar();
    }

    private void clearFar() {
        far.clear();
        farBounds = null;
        ownBounds = null;
        farLevel = new WeakReference<>(null);
    }
}
