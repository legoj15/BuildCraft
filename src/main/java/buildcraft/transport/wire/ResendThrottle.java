/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.wire;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Paces a "send this key the whole state again" request that is raised far more often than the state is worth
 * sending. A player's chunks are delivered over many ticks (login, a teleport, walking along a wire run), and every
 * delivery asks for the player's whole wire structure again; sending it on each of those ticks costs a full payload
 * per tick for as long as the delivery lasts.
 * <p>
 * A key flagged with {@link #request} is due at once if it has not been sent in the last {@code minIntervalTicks},
 * and otherwise stays flagged until that interval is up. So a burst gives one send straight away and one trailing
 * send that carries everything flagged since: the last state sent always comes after the last request, which a plain
 * "at most once per interval" drop would not guarantee. The clock is the caller's own tick count, passed in, so this
 * has no game dependency and is driven tick by tick in tests.
 * <p>
 * A quiet tick (nothing flagged) costs a map probe at most; sends older than the interval are forgotten in a sweep
 * that runs once per interval, so a key that left (a logged-out player) is not held for long.
 *
 * @param <K> what is being sent to (a player)
 */
final class ResendThrottle<K> {
    private final int minIntervalTicks;
    /** Keys that asked for a send and have not had one since. */
    private final Set<K> pending = new HashSet<>();
    /** The clock value of each key's last send, kept only while it still delays a new one. */
    private final Map<K, Long> lastSent = new HashMap<>();
    private long lastSweep;

    ResendThrottle(int minIntervalTicks) {
        if (minIntervalTicks < 0) {
            throw new IllegalArgumentException("minIntervalTicks must not be negative: " + minIntervalTicks);
        }
        this.minIntervalTicks = minIntervalTicks;
    }

    /** Ask for {@code key} to be sent the whole state; it is returned by {@link #takeDue} once the pacing allows. */
    void request(K key) {
        pending.add(key);
    }

    /** Whether {@code key} has asked for a send that has not happened yet. */
    boolean isPending(K key) {
        return pending.contains(key);
    }

    /**
     * The flagged keys whose wait is over at {@code now}. They leave the flagged set and count as sent at {@code now};
     * the caller sends them. Keys still inside their interval stay flagged for a later call.
     */
    Set<K> takeDue(long now) {
        sweep(now);
        if (pending.isEmpty()) {
            return Set.of();
        }
        Set<K> due = null;
        for (Iterator<K> it = pending.iterator(); it.hasNext(); ) {
            K key = it.next();
            Long sent = lastSent.get(key);
            if (sent == null || now - sent >= minIntervalTicks) {
                if (due == null) {
                    due = new HashSet<>();
                }
                due.add(key);
                lastSent.put(key, now);
                it.remove();
            }
        }
        return due == null ? Set.of() : due;
    }

    /**
     * {@code key} was sent the whole state at {@code now} for some other reason: whatever it had asked for is
     * satisfied, and the interval before its next paced send starts from here.
     */
    void markSent(K key, long now) {
        pending.remove(key);
        lastSent.put(key, now);
    }

    /** Drop everything held about {@code key}: its pending request and its send history. */
    void forget(K key) {
        pending.remove(key);
        lastSent.remove(key);
    }

    /** Forget sends the interval has outlived; they no longer delay anything. Once per interval, so it is amortized. */
    private void sweep(long now) {
        if (lastSent.isEmpty() || now - lastSweep < minIntervalTicks) {
            return;
        }
        lastSent.values().removeIf(sent -> now - sent >= minIntervalTicks);
        lastSweep = now;
    }
}
