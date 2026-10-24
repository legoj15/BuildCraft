/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * The client's book of snapshot requests it has sent and not yet had answered, so a preview that is redrawn every
 * frame asks the server once rather than sixty times a second. A request nobody answers expires after a wait and
 * may then be sent again: the server refuses a hash the player cannot show an item for (a storage-mod terminal lists
 * items that are in no menu slot) and the reply can be lost, and without expiry that blueprint's preview would stay
 * dead for the whole session even after the item reached the player's inventory. The wait doubles with every resend,
 * up to a cap, because an expired request may only be slow, not lost: the server answers every request it accepts,
 * so a short fixed wait would draw a second full copy of a large blueprint, and a third, while the first is still in
 * transit. Pure (the clock is injected), so the decision is unit-testable without any client class.
 */
final class SnapshotRequestTracker {
    /** The first wait: long enough that a hovered tooltip does not spam the server, short enough that moving the
     *  item into the inventory brings the preview back within a few seconds. */
    static final long DEFAULT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    /** The longest the wait between resends grows to: still a prompt return of the preview, but no more than a few
     *  requests a minute for a hash the server keeps refusing. */
    static final long DEFAULT_MAX_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(20);

    /** What to do about a hash the client has no snapshot for. */
    enum Ask {
        /** A request for it is out and has not yet timed out: send nothing. */
        WAIT,
        /** Not asked for since it was last answered (or ever): send. */
        FIRST,
        /** Asked for, but unanswered for its full wait: send again. */
        RESEND
    }

    /** One outstanding request: when it was last sent, and how long to wait for an answer before sending it again. */
    private record Pending(long sentAt, long waitNanos) {}

    private final Map<Snapshot.Key, Pending> pending = new HashMap<>();
    private final LongSupplier nanoClock;
    private final long timeoutNanos;
    private final long maxTimeoutNanos;

    SnapshotRequestTracker(LongSupplier nanoClock, long timeoutNanos, long maxTimeoutNanos) {
        this.nanoClock = nanoClock;
        this.timeoutNanos = timeoutNanos;
        this.maxTimeoutNanos = Math.max(timeoutNanos, maxTimeoutNanos);
    }

    /** Whether a request for {@code key} should be sent now, and if so whether it repeats an unanswered one. Any
     *  answer but {@link Ask#WAIT} records the send and starts the next wait, double the last up to the cap. */
    Ask ask(Snapshot.Key key) {
        long now = nanoClock.getAsLong();
        Pending last = pending.get(key);
        // Subtraction, not a comparison of the two readings, so a wrapped nanoTime still measures the gap.
        if (last != null && now - last.sentAt() < last.waitNanos()) {
            return Ask.WAIT;
        }
        long wait = last == null ? timeoutNanos
                : last.waitNanos() > maxTimeoutNanos / 2 ? maxTimeoutNanos : last.waitNanos() * 2;
        pending.put(key, new Pending(now, wait));
        return last == null ? Ask.FIRST : Ask.RESEND;
    }

    /** Records that the snapshot for {@code key} arrived; returns whether a request for it was outstanding. The next
     *  ask for the same hash starts over at the first wait. */
    boolean received(Snapshot.Key key) {
        return pending.remove(key) != null;
    }
}
