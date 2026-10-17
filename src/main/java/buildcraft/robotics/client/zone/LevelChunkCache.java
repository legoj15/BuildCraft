/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
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
 *   {@link #clear} drops all (logout, level unload). An entry nobody has looked at for
 *   {@link #EVICT_AFTER_UNUSED_TICKS} (a chunk panned out of view) is dropped too, so the cache cannot grow
 *   without bound while the view wanders.</li>
 *   <li><b>Time-based refresh, oldest first.</b> The client is never told that a single block changed, so an
 *   entry is due for a rebuild once it is {@link #REFRESH_AFTER_TICKS} old. At most {@link #REFRESHES_PER_TICK}
 *   are rebuilt per {@link #tick}, so a wide survey view cannot make one frame rebuild thousands of chunks. The
 *   budget is <em>not</em> spent in call order: the viewport scans the same rows in the same order every frame,
 *   so a first-come budget would rebuild the head of the scan forever and never reach its tail. Instead each
 *   {@code tick()} picks the oldest due entries among those looked at during the previous tick, and only those
 *   are rebuilt; a view of {@code n} chunks therefore refreshes every chunk within about
 *   {@code REFRESH_AFTER_TICKS + n / REFRESHES_PER_TICK} ticks. Budget the picks leave over goes, first come,
 *   to entries coming back into view (not looked at last tick), which no pick could have known about.</li>
 * </ul>
 *
 * <p>Time is counted in {@code tick()} calls rather than read from a clock, because {@link #get} is called per
 * terrain column and must not cost a clock read; the per-tick pick is one pass over the entries.
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
    /** Ticks (one minute) an entry may go unlooked-at before it is dropped: a chunk panned out of view. */
    public static final int EVICT_AFTER_UNUSED_TICKS = 1200;

    private static final class Entry<V> {
        V value;
        int builtAtTick;
        int lastUsedTick;
        /** Chosen by this tick's pick: rebuilt on its next look. */
        boolean picked;

        Entry(V value, int tick) {
            this.value = value;
            this.builtAtTick = tick;
            this.lastUsedTick = tick;
        }
    }

    private final Builder<L, V> builder;
    private final Map<Long, Entry<V>> entries = new HashMap<>();
    private WeakReference<L> owner = new WeakReference<>(null);
    private int tick;
    /** This tick's picks, oldest first; {@code pickCount} of them are live. */
    @SuppressWarnings("unchecked")
    private final Entry<V>[] picks = new Entry[REFRESHES_PER_TICK];
    private int pickCount;
    /** Budget left this tick for entries coming back into view. */
    private int spareThisTick = REFRESHES_PER_TICK;

    public LevelChunkCache(Builder<L, V> builder) {
        this.builder = builder;
    }

    private static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFF_FFFFL) | ((long) chunkZ << 32);
    }

    /**
     * The value for the given chunk of {@code level}: cached, rebuilt if this tick's refresh picked it, or built
     * now if new. {@code null} when the chunk is not loaded (not cached, so it is built as soon as it is).
     */
    @Nullable
    public V get(L level, int chunkX, int chunkZ) {
        if (!owner.refersTo(level)) {
            clear();
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
        boolean wasInView = entry.lastUsedTick >= tick - 1;
        entry.lastUsedTick = tick;
        if (tick - entry.builtAtTick < REFRESH_AFTER_TICKS) {
            return entry.value;
        }
        if (entry.picked) {
            entry.picked = false;
        } else if (!wasInView && spareThisTick > 0) {
            spareThisTick--;
        } else {
            return entry.value; // due, but waiting its turn behind older entries
        }
        V rebuilt = builder.build(level, chunkX, chunkZ);
        if (rebuilt == null) {
            entries.remove(k);
            return null;
        }
        entry.value = rebuilt;
        entry.builtAtTick = tick;
        return rebuilt;
    }

    /** Drops one chunk's entry if it belongs to {@code level} (a chunk that reloaded or unloaded). */
    public void invalidate(L level, int chunkX, int chunkZ) {
        if (owner.refersTo(level)) {
            entries.remove(key(chunkX, chunkZ));
        }
    }

    /**
     * Advances the refresh clock one tick: drops entries out of view for too long, and picks the oldest due
     * entries looked at during the tick just ended as this tick's rebuilds.
     */
    public void tick() {
        tick++;
        dropPicks();
        for (Iterator<Entry<V>> it = entries.values().iterator(); it.hasNext(); ) {
            Entry<V> e = it.next();
            if (tick - e.lastUsedTick > EVICT_AFTER_UNUSED_TICKS) {
                it.remove();
            } else if (e.lastUsedTick >= tick - 1 && tick - e.builtAtTick >= REFRESH_AFTER_TICKS) {
                offerPick(e);
            }
        }
        for (int i = 0; i < pickCount; i++) {
            picks[i].picked = true;
        }
        spareThisTick = REFRESHES_PER_TICK - pickCount;
    }

    /** Keeps {@code picks} as the oldest {@link #REFRESHES_PER_TICK} candidates seen, sorted oldest first. */
    private void offerPick(Entry<V> e) {
        int at = pickCount;
        while (at > 0 && picks[at - 1].builtAtTick > e.builtAtTick) {
            at--;
        }
        if (at >= REFRESHES_PER_TICK) {
            return;
        }
        int last = Math.min(pickCount, REFRESHES_PER_TICK - 1);
        System.arraycopy(picks, at, picks, at + 1, last - at);
        picks[at] = e;
        if (pickCount < REFRESHES_PER_TICK) {
            pickCount++;
        }
    }

    private void dropPicks() {
        for (int i = 0; i < pickCount; i++) {
            picks[i].picked = false;
            picks[i] = null;
        }
        pickCount = 0;
    }

    public void clear() {
        dropPicks();
        entries.clear();
        owner = new WeakReference<>(null);
    }

    /** Entries currently held. */
    public int size() {
        return entries.size();
    }
}
