/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.HashMap;
import java.util.Map;

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
 * <li>{@link #MEMORY_TICKS} passing, so a claim that was lifted while the cell stayed the same is noticed within
 *     a few minutes instead of never.</li>
 * </ul>
 *
 * <p>The memory is bounded ({@link #MAX_ENTRIES}; overflowing it forgets everything, which costs one re-ask per
 * cell) and deliberately transient: it belongs to the machine instance and is never saved. Server thread only. The
 * game-time-taking overloads are the real API and keep the logic testable without a world; the {@link Level}
 * overloads read the state and the clock from the world.
 */
public final class DrainRefusals {

    /** How long a refusal is remembered while the cell stays unchanged: five minutes of game time. */
    public static final long MEMORY_TICKS = 6000;

    /** The most refused cells one machine keeps before it forgets them all. */
    public static final int MAX_ENTRIES = 2048;

    private record Refusal(BlockState state, long expiresAt) {
    }

    private final Map<BlockPos, Refusal> refused = new HashMap<>();

    /** Records that {@code pos}, holding {@code state}, refused at {@code gameTime}. */
    public void remember(BlockPos pos, BlockState state, long gameTime) {
        if (refused.size() >= MAX_ENTRIES && !refused.containsKey(pos)) {
            refused.clear();
        }
        refused.put(pos.immutable(), new Refusal(state, gameTime + MEMORY_TICKS));
    }

    /**
     * Whether {@code pos} should be treated as refusing: it refused before, it still holds the same state, and the
     * memory has not expired. A stale entry (changed or expired) is dropped on the way out.
     */
    public boolean isRefused(BlockPos pos, BlockState current, long gameTime) {
        Refusal refusal = refused.get(pos);
        if (refusal == null) {
            return false;
        }
        if (!refusal.state().equals(current) || gameTime >= refusal.expiresAt()) {
            refused.remove(pos);
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
    }
}
