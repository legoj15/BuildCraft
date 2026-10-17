/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A per-chunk cache of values built from a level (the Zone Planner's terrain colours), with the lifetime rules a
 * client-side cache of world data needs. Pure and generic over the level type &mdash; no Minecraft types &mdash; so
 * the rules are unit-testable; {@link ZonePlannerMapDataClient} binds it to real levels and chunks.
 *
 * <ul>
 *   <li><b>One level at a time, by identity.</b> Entries belong to the level they were built from. Asking about a
 *   different level (another dimension, or the same dimension of another world: a client level is a new object
 *   each time) discards everything first, so chunk coordinates never alias across worlds. The level is held
 *   weakly, so an idle cache never keeps a closed world alive.</li>
 *   <li><b>Invalidation.</b> {@link #invalidate} drops one chunk (it was reloaded or unloaded);
 *   {@link #clear} drops all (logout, level unload).</li>
 *   <li><b>Time-based refresh.</b> The client is never told that a single block changed, so an entry is rebuilt
 *   once it is {@link #REFRESH_AFTER_TICKS} old &mdash; at most {@link #REFRESHES_PER_TICK} per {@link #tick},
 *   so a wide survey view cannot make one frame rebuild thousands of chunks; the rest are served stale until a
 *   later tick's budget reaches them. Time is counted in {@code tick()} calls rather than read from a clock,
 *   because {@link #get} is called per terrain column and must not cost a clock read.</li>
 * </ul>
 *
 * <p>Single-threaded: the client main thread only (render, GUI and the client events all run there).
 */
public final class LevelChunkCache<L, V> {

    /** Builds the value for one chunk of a level, or returns {@code null} when the chunk is not loaded. */
    @FunctionalInterface
    public interface Builder<L, V> {
        @Nullable
        V build(L level, int chunkX, int chunkZ);
    }

    /** Ticks (3 s at 20 TPS) an entry is served before it is due for a rebuild. */
    public static final int REFRESH_AFTER_TICKS = 60;
    /** Due entries rebuilt per tick; each rebuild is a full scan of the chunk's 256 columns. */
    public static final int REFRESHES_PER_TICK = 4;

    private static final class Entry<V> {
        V value;
        int builtAtTick;

        Entry(V value, int builtAtTick) {
            this.value = value;
            this.builtAtTick = builtAtTick;
        }
    }

    private final Builder<L, V> builder;
    private final Map<Long, Entry<V>> entries = new HashMap<>();
    private WeakReference<L> owner = new WeakReference<>(null);
    private int tick;
    private int refreshesThisTick;

    public LevelChunkCache(Builder<L, V> builder) {
        this.builder = builder;
    }

    private static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFF_FFFFL) | ((long) chunkZ << 32);
    }

    /**
     * The value for the given chunk of {@code level}: cached, rebuilt if it is due and the tick's budget allows,
     * or built now if new. {@code null} when the chunk is not loaded (not cached, so it is built as soon as it is).
     */
    @Nullable
    public V get(L level, int chunkX, int chunkZ) {
        if (owner.get() != level) {
            entries.clear();
            owner = new WeakReference<>(level);
        }
        long k = key(chunkX, chunkZ);
        Entry<V> entry = entries.get(k);
        if (entry == null) {
            V built = builder.build(level, chunkX, chunkZ);
            if (built != null) {
                entries.put(k, new Entry<>(built, tick));
            }
            return built;
        }
        if (tick - entry.builtAtTick >= REFRESH_AFTER_TICKS && refreshesThisTick < REFRESHES_PER_TICK) {
            refreshesThisTick++;
            V rebuilt = builder.build(level, chunkX, chunkZ);
            if (rebuilt == null) {
                entries.remove(k);
                return null;
            }
            entry.value = rebuilt;
            entry.builtAtTick = tick;
            return rebuilt;
        }
        return entry.value;
    }

    /** Drops one chunk's entry if it belongs to {@code level} (a chunk that reloaded or unloaded). */
    public void invalidate(Object level, int chunkX, int chunkZ) {
        if (owner.get() == level) {
            entries.remove(key(chunkX, chunkZ));
        }
    }

    /** Advances the refresh clock one tick and renews the rebuild budget. */
    public void tick() {
        tick++;
        refreshesThisTick = 0;
    }

    public void clear() {
        entries.clear();
        owner = new WeakReference<>(null);
    }

    /** Entries currently held. */
    public int size() {
        return entries.size();
    }
}
