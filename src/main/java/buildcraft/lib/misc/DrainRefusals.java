/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a fluid-draining machine remembers about cells that refused to be drained, so it does not ask the same cell
 * again on every rebuild of its work list.
 *
 * <p>A cell refuses when a claim/protection mod cancels the machine's break probe, or when a modded
 * {@code BucketPickup} reports a source yet refuses {@code pickupBlock}. Neither says anything that changes by
 * itself, and re-asking costs a probe event (or, for a refusing pickup, a whole cycle of the machine's power), so
 * the refusal is kept for as long as the cell's {@link BlockState} is unchanged. Two things end it sooner:
 * <ul>
 * <li>the cell turning into something else ({@link #isRefused} drops the entry the next time it looks);</li>
 * <li>its window passing, so a claim that was lifted while the cell stayed the same is noticed eventually instead
 *     of never. The window is {@link #MEMORY_TICKS} (five minutes) for a small refused pool and grows with the
 *     pool, {@link #RECHECK_SPACING_TICKS} per remembered cell, so re-asking a fully refused pool of any size
 *     averages about one probe per {@link #RECHECK_SPACING_TICKS} rather than one per tick.</li>
 * </ul>
 *
 * <p>There is deliberately no entry cap: forgetting live refusals early is exactly what makes a machine thrash (the
 * next rebuild re-queues the forgotten cells and the machine probes one per tick, forever). The map only ever holds
 * cells this machine reached and was refused, so it is bounded by the machine's own reach (a pump's flood fill
 * already keeps a path entry for every one of those cells). Only live refusals stretch the window: once the pool is
 * past the size where the window is the plain five minutes, every new refusal first drops the entries that have
 * expired, and those whose cell has changed (read through a {@link StateLookup}; a cell it cannot read is kept), so
 * a pool that was drained or changed by other means does not inflate the next window. The memory is transient: it
 * belongs to the machine instance and is never saved. Server thread only. The game-time-taking overloads are the
 * real API and keep the logic testable without a world; the {@link Level} overloads read the state and the clock
 * from the world, without loading chunks.
 */
public final class DrainRefusals {

    /** The shortest time a refusal is remembered while the cell stays unchanged: five minutes of game time. */
    public static final long MEMORY_TICKS = 6000;

    /** How far each remembered refusal stretches the window of the next one, so a fully refused pool is re-asked
     *  about once per this many ticks on average, however large it is. */
    public static final long RECHECK_SPACING_TICKS = 20;

    /** Up to this many other live refusals the window is the plain {@link #MEMORY_TICKS}, so counting them exactly
     *  (and pruning the stale ones to do it) only starts above it. Also bounds the stale entries kept below it. */
    private static final int PLAIN_WINDOW_POOL = (int) (MEMORY_TICKS / RECHECK_SPACING_TICKS);

    /** A cell's current state, for telling which remembered refusals are stale. */
    @FunctionalInterface
    public interface StateLookup {
        /** The state at {@code pos} now, or null when it cannot be read cheaply (an unloaded chunk): such a refusal
         *  is kept, since it may well still hold. */
        @Nullable
        BlockState stateAt(BlockPos pos);
    }

    private record Refusal(BlockState state, long expiresAt) {
    }

    /** Keyed on {@link BlockPos#asLong()}: a large claimed pool can mean thousands of entries. */
    private final Long2ObjectOpenHashMap<Refusal> refused = new Long2ObjectOpenHashMap<>();

    /** Records that {@code pos}, holding {@code state}, refused at {@code gameTime}. Only expiry tells a stale entry
     *  here; prefer {@link #remember(BlockPos, BlockState, long, StateLookup)} where the world can be read. */
    public void remember(BlockPos pos, BlockState state, long gameTime) {
        remember(pos, state, gameTime, null);
    }

    /**
     * Records that {@code pos}, holding {@code state}, refused at {@code gameTime}. Its window is
     * {@code max(MEMORY_TICKS, RECHECK_SPACING_TICKS per other live refusal)}, where an entry is live while it has not
     * expired and {@code lookup} (when given) does not report its cell as changed.
     */
    public void remember(BlockPos pos, BlockState state, long gameTime, @Nullable StateLookup lookup) {
        long key = pos.asLong();
        refused.remove(key);
        if (refused.size() > PLAIN_WINDOW_POOL) {
            pruneStale(gameTime, lookup);
        }
        long window = Math.max(MEMORY_TICKS, refused.size() * RECHECK_SPACING_TICKS);
        refused.put(key, new Refusal(state, gameTime + window));
    }

    /** Drops every entry that has expired, or whose cell {@code lookup} reports as holding another state. */
    private void pruneStale(long gameTime, @Nullable StateLookup lookup) {
        ObjectIterator<Long2ObjectMap.Entry<Refusal>> it = refused.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Refusal> entry = it.next();
            Refusal refusal = entry.getValue();
            if (gameTime >= refusal.expiresAt()) {
                it.remove();
            } else if (lookup != null) {
                BlockState now = lookup.stateAt(BlockPos.of(entry.getLongKey()));
                if (now != null && !now.equals(refusal.state())) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Whether {@code pos} should be treated as refusing: it refused before, it still holds the same state, and the
     * memory has not expired. A stale entry (changed or expired) is dropped on the way out.
     */
    public boolean isRefused(BlockPos pos, BlockState current, long gameTime) {
        long key = pos.asLong();
        Refusal refusal = refused.get(key);
        if (refusal == null) {
            return false;
        }
        if (!refusal.state().equals(current) || gameTime >= refusal.expiresAt()) {
            refused.remove(key);
            return false;
        }
        return true;
    }

    /** {@link #remember(BlockPos, BlockState, long, StateLookup)} for the cell's current state and the world's clock;
     *  remembered cells in unloaded chunks are not read (that would load them), only aged out. */
    public void remember(Level level, BlockPos pos) {
        remember(pos, level.getBlockState(pos), level.getGameTime(),
                cell -> level.isLoaded(cell) ? level.getBlockState(cell) : null);
    }

    /** {@link #isRefused(BlockPos, BlockState, long)} against the world's current state and clock. */
    public boolean isRefused(Level level, BlockPos pos) {
        return !refused.isEmpty() && isRefused(pos, level.getBlockState(pos), level.getGameTime());
    }

    /** How many cells are currently remembered. */
    public int size() {
        return refused.size();
    }

    /** Forgets every refusal. */
    public void clear() {
        refused.clear();
    }
}
