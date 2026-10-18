/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.function.LongSupplier;

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;

import net.minecraft.core.BlockPos;

/**
 * "I could not reach that block" memory, the block twin of {@link UnreachableEntityCache}: a block search that starts
 * a path finder on a candidate and watches it fail (a crop under glass, a log in a sealed room) notes the candidate
 * here, and the next search leaves it alone instead of starting the same finder on it again. Without it the nearest
 * unreachable candidates are rediscovered, and re-flooded, by every search from the centre outwards, and a robot with
 * more of them than one search's round budget can pay for never gets past them to the reachable block behind.
 *
 * <p>Keyed by the packed {@link BlockPos} (a plain {@code long}), so a note is about the cell and a fresh position
 * object for it finds it; the entity cache's weak keys would drop a {@code BlockPos} the moment the caller let go of
 * it. The window is fixed, so notes end in the order they were made, which is what lets the capacity drop the
 * oldest. A note is only a hint for a while: a robot that moved, or a player who opened the glass, is a new question
 * once the window ends. Transient: never saved, so a reloaded robot simply tries once more. Server thread only.
 */
public final class UnreachableBlockCache {

    /** How long a note survives, in ticks: five minutes. Longer than a fruitless search (1000 rounds, a tick each)
     *  plus the sleep after it (1200 ticks), or the next search would start from nothing and the memory would only
     *  help a search that found something anyway. Short enough that opening the glass is noticed within a coffee. */
    public static final long EXPIRY_TICKS = 6000;

    /** The most notes kept; past it the oldest is dropped. A search starts at most a few hundred finders on
     *  unreachable candidates before its rounds run out, so this holds several searches' worth. */
    public static final int CAPACITY = 512;

    private final LongSupplier clock;

    /** Packed position -> the absolute game tick its note expires ON, in the order the notes were made. */
    private final Long2LongLinkedOpenHashMap deadlines = new Long2LongLinkedOpenHashMap();

    /** @param clock the current game time in ticks (normally {@code level::getGameTime}) */
    public UnreachableBlockCache(LongSupplier clock) {
        this.clock = clock;
        deadlines.defaultReturnValue(Long.MIN_VALUE);
    }

    /** Records that {@code pos} could not be reached, starting (or restarting) its {@link #EXPIRY_TICKS} countdown. */
    public void unreachableDetected(BlockPos pos) {
        long key = pos.asLong();
        // Remove first so a re-noted position moves to the back of the order, the newest, not stays the oldest.
        deadlines.remove(key);
        deadlines.put(key, clock.getAsLong() + EXPIRY_TICKS);
        while (deadlines.size() > CAPACITY) {
            deadlines.removeFirstLong();
        }
    }

    /** @return true while a note for {@code pos} is within {@link #EXPIRY_TICKS}; reading an expired one drops it */
    public boolean isKnownUnreachable(BlockPos pos) {
        long key = pos.asLong();
        long deadline = deadlines.get(key);
        if (deadline == Long.MIN_VALUE) {
            return false;
        }
        if (clock.getAsLong() < deadline) {
            return true;
        }
        deadlines.remove(key);
        return false;
    }
}
