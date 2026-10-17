/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

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
 * already keeps a path entry for every one of those cells); expired entries are swept as it grows. The memory is
 * transient: it belongs to the machine instance and is never saved. Server thread only. The game-time-taking
 * overloads are the real API and keep the logic testable without a world; the {@link Level} overloads read the
 * state and the clock from the world.
 */
public final class DrainRefusals {

    /** The shortest time a refusal is remembered while the cell stays unchanged: five minutes of game time. */
    public static final long MEMORY_TICKS = 6000;

    /** How far each remembered refusal stretches the window of the next one, so a fully refused pool is re-asked
     *  about once per this many ticks on average, however large it is. */
    public static final long RECHECK_SPACING_TICKS = 20;

    /** The size below which expired entries are not swept; above it, a sweep runs each time the map doubles. */
    private static final int MIN_SWEEP_SIZE = 256;

    private record Refusal(BlockState state, long expiresAt) {
    }

    /** Keyed on {@link BlockPos#asLong()}: a large claimed pool can mean thousands of entries. */
    private final Long2ObjectOpenHashMap<Refusal> refused = new Long2ObjectOpenHashMap<>();

    private int sweepAt = MIN_SWEEP_SIZE;

    /** Records that {@code pos}, holding {@code state}, refused at {@code gameTime}. */
    public void remember(BlockPos pos, BlockState state, long gameTime) {
        long key = pos.asLong();
        if (refused.size() >= sweepAt && !refused.containsKey(key)) {
            refused.values().removeIf(refusal -> gameTime >= refusal.expiresAt());
            sweepAt = Math.max(MIN_SWEEP_SIZE, refused.size() * 2);
        }
        long others = refused.size() - (refused.containsKey(key) ? 1 : 0);
        long window = Math.max(MEMORY_TICKS, others * RECHECK_SPACING_TICKS);
        refused.put(key, new Refusal(state, gameTime + window));
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

    /** {@link #remember(BlockPos, BlockState, long)} for the cell's current state and the world's clock. */
    public void remember(Level level, BlockPos pos) {
        remember(pos, level.getBlockState(pos), level.getGameTime());
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
        sweepAt = MIN_SWEEP_SIZE;
    }
}
