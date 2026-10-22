/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;

import buildcraft.lib.misc.PositionMemory;

/**
 * "I could not reach that block" memory, the block twin of {@link UnreachableEntityCache}: a block search that starts
 * a path finder on a candidate and watches it fail (a crop under glass, a log in a sealed room) notes the candidate
 * here, and the next search leaves it alone instead of starting the same finder on it again. Without it the nearest
 * unreachable candidates are rediscovered, and re-flooded, by every search from the centre outwards, and a robot with
 * more of them than one search's round budget can pay for never gets past them to the reachable block behind.
 *
 * <p>Keyed by the packed {@link BlockPos} (a plain {@code long}), so a note is about the cell and a fresh position
 * object for it finds it; the entity cache's weak keys would drop a {@code BlockPos} the moment the caller let go of
 * it. The notes live in a {@link PositionMemory} with a fixed window ({@link #POLICY}), so notes end in the order
 * they were made, which is what lets the capacity drop the oldest. A note is only a hint for a while: a robot that moved, or a player who opened the glass, is a new question
 * once the window ends. Transient: never saved, so a reloaded robot simply tries once more. Server thread only.
 *
 * <p><b>A note is about the spot it was made from.</b> "Unreachable" is a fact about a flight from somewhere: the
 * block that is sealed off from where the robot searched may be an easy flight from where it stands after it has
 * moved (out of a pocket, across to the next field). Each note therefore remembers where the robot was when it was
 * made, and applies only while the robot is within {@link #NOTE_RADIUS} blocks of that spot; farther away it is
 * ignored, not erased, so a robot that comes back finds the note still there. The radius is wide enough that the
 * hovering, drifting and short hops of a robot working one patch are the same spot (the memory exists for exactly
 * that robot), and narrow enough that a robot sent to another job is not held back by a failure from the last one.
 * A finder that ran out of its expansion budget is noted the same as one that proved there was no path; the spot
 * bounds how long either can misinform.
 */
public final class UnreachableBlockCache {

    /** How long a note survives, in ticks: five minutes. Longer than a fruitless search (1000 rounds, a tick each)
     *  plus the sleep after it (1200 ticks), or the next search would start from nothing and the memory would only
     *  help a search that found something anyway. Short enough that opening the glass is noticed within a coffee. */
    public static final long EXPIRY_TICKS = 6000;

    /** The most notes kept; past it the oldest is dropped. A search starts at most a few hundred finders on
     *  unreachable candidates before its rounds run out, so this holds several searches' worth. */
    public static final int CAPACITY = 512;

    /** How far, in blocks, the robot may be from the spot a note was made at for the note to still apply: a
     *  straight-line distance, inclusive. */
    public static final int NOTE_RADIUS = 16;

    /** The memory's window and cap: {@link #EXPIRY_TICKS}, {@link #CAPACITY}. */
    public static final PositionMemory.Policy POLICY = PositionMemory.Policy.fixed(EXPIRY_TICKS, CAPACITY);

    private static final long NOTE_RADIUS_SQ = (long) NOTE_RADIUS * NOTE_RADIUS;

    private final LongSupplier clock;
    private final Supplier<BlockPos> origin;

    /** Packed position -> the packed block position the robot stood at when the note was made. */
    private final PositionMemory<Long> notes = new PositionMemory<>(POLICY);

    /**
     * @param clock the current game time in ticks (normally {@code level::getGameTime})
     * @param origin where the robot is now (normally {@code robot::blockPosition}): read when a note is made, to
     *               record the spot it was made from, and when one is asked about, to see whether the robot is
     *               still there
     */
    public UnreachableBlockCache(LongSupplier clock, Supplier<BlockPos> origin) {
        this.clock = clock;
        this.origin = origin;
    }

    /** Records that {@code pos} could not be reached from where the robot is now, starting (or restarting) its
     *  {@link #EXPIRY_TICKS} countdown. A position already noted from somewhere else is now noted from here, and a
     *  re-noted position becomes the newest note rather than staying the oldest. */
    public void unreachableDetected(BlockPos pos) {
        notes.put(pos.asLong(), origin.get().asLong(), clock.getAsLong(), null);
    }

    /** @return true while a note for {@code pos} is within {@link #EXPIRY_TICKS} and was made from within
     *          {@link #NOTE_RADIUS} blocks of where the robot is now; reading an expired one drops it */
    public boolean isKnownUnreachable(BlockPos pos) {
        Long madeFrom = notes.get(pos.asLong(), clock.getAsLong());
        return madeFrom != null && madeFromNear(madeFrom, origin.get());
    }

    private static boolean madeFromNear(long madeFrom, BlockPos now) {
        long dx = (long) BlockPos.getX(madeFrom) - now.getX();
        long dy = (long) BlockPos.getY(madeFrom) - now.getY();
        long dz = (long) BlockPos.getZ(madeFrom) - now.getZ();
        return dx * dx + dy * dy + dz * dz <= NOTE_RADIUS_SQ;
    }
}
