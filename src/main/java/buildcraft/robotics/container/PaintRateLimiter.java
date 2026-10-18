/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.container;

/**
 * A token bucket on the game clock for one viewer's Zone Planner stroke messages. A stroke is one drag released (or one
 * click), so a person makes a few a second at the very most; a client that sends one every tick is broken or hostile,
 * and each accepted stroke makes the server rewrite the layer and push the result to every other open planner.
 *
 * <p>The bucket holds {@link #BURST} strokes (a backlog delivered at once by a lagging connection, or a quick run of
 * single-cell clicks) and refills one stroke per {@link #TICKS_PER_STROKE} ticks (ten a second). Credit is counted in
 * ticks so the arithmetic stays integral.
 */
public final class PaintRateLimiter {
    /** Strokes accepted back to back before the sustained rate applies. */
    public static final int BURST = 24;
    /** Game ticks of credit one stroke costs: the sustained rate is {@code 20 / TICKS_PER_STROKE} strokes a second. */
    public static final int TICKS_PER_STROKE = 2;

    private static final long CAPACITY = (long) BURST * TICKS_PER_STROKE;

    private long credit = CAPACITY;
    private boolean started;
    private long last;

    /**
     * True when the bucket is full, or would be by game time {@code now}: it then behaves exactly as a new one, so
     * whoever holds it can drop it. (A clock that has stepped back since the last stroke is not refilled until it
     * catches up.)
     */
    public boolean isRefilled(long now) {
        return !started || (now >= last && credit + (now - last) >= CAPACITY);
    }

    /**
     * Takes one stroke from the bucket at game time {@code now}.
     *
     * @return false if the viewer is sending faster than the limit and this stroke should be dropped.
     */
    public boolean tryAcquire(long now) {
        if (started && now > last) {
            credit = Math.min(CAPACITY, credit + (now - last));
        }
        started = true;
        last = now; // a clock that steps back (it should not) earns nothing, and refills from the new time
        if (credit >= TICKS_PER_STROKE) {
            credit -= TICKS_PER_STROKE;
            return true;
        }
        return false;
    }
}
