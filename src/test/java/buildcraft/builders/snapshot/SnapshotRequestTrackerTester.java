/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.builders.snapshot.SnapshotRequestTracker.Ask;
import buildcraft.lib.misc.HashUtil;

/**
 * The client asks the server for a snapshot once per hover frame at most, but the server may refuse or lose the
 * request (a storage-mod terminal shows items that are in no menu slot; the reply can also simply never arrive), and
 * a request marked pending forever would leave that blueprint's preview dead for the whole session. These pin the
 * decision with an injected clock: no resend while a request is fresh, a resend once it has gone unanswered for its
 * wait, a wait that doubles up to a cap (a slow reply is not a lost one, and every request the server accepts draws a
 * full copy of the blueprint), and an arrived snapshot clearing the entry.
 */
public class SnapshotRequestTrackerTester {
    private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(5);
    private static final long MAX_TIMEOUT = TimeUnit.SECONDS.toNanos(20);

    private long now;
    private final SnapshotRequestTracker tracker = new SnapshotRequestTracker(() -> now, TIMEOUT, MAX_TIMEOUT);

    private static Snapshot.Key key(int fill) {
        byte[] hash = new byte[HashUtil.HASH_BYTES];
        java.util.Arrays.fill(hash, (byte) fill);
        return new Snapshot.Key(new Snapshot.Key(), hash);
    }

    @Test
    public void aFreshRequestIsNotResentWhilePending() {
        Assertions.assertEquals(Ask.FIRST, tracker.ask(key(1)), "the first look sends the request");
        now += TIMEOUT / 2;
        Assertions.assertEquals(Ask.WAIT, tracker.ask(key(1)), "a preview redrawn every frame must not flood the server");
        now += TIMEOUT / 2 - 1;
        Assertions.assertEquals(Ask.WAIT, tracker.ask(key(1)), "still pending one tick before the timeout");
    }

    @Test
    public void anUnansweredRequestIsResentAfterAWaitThatDoublesToACap() {
        Assertions.assertEquals(Ask.FIRST, tracker.ask(key(1)));
        now += TIMEOUT;
        Assertions.assertEquals(Ask.RESEND, tracker.ask(key(1)),
            "a refused or lost request must not leave the preview dead for the whole session");
        Assertions.assertEquals(Ask.WAIT, tracker.ask(key(1)), "the resend is pending again");
        now += TIMEOUT;
        Assertions.assertEquals(Ask.WAIT, tracker.ask(key(1)),
            "a reply slower than the first wait must not draw a second full copy of the blueprint at once");
        now += TIMEOUT;
        Assertions.assertEquals(Ask.RESEND, tracker.ask(key(1)), "the second wait is twice the first");
        now += 4 * TIMEOUT;
        Assertions.assertEquals(Ask.RESEND, tracker.ask(key(1)), "the third is twice again, which is the cap");
        now += MAX_TIMEOUT - 1;
        Assertions.assertEquals(Ask.WAIT, tracker.ask(key(1)));
        now += 1;
        Assertions.assertEquals(Ask.RESEND, tracker.ask(key(1)),
            "the wait stops growing at the cap, so a hash the server refused still comes back within it");
    }

    @Test
    public void aReceivedSnapshotClearsThePending() {
        Assertions.assertEquals(Ask.FIRST, tracker.ask(key(1)));
        now += TIMEOUT;
        Assertions.assertEquals(Ask.RESEND, tracker.ask(key(1)));
        Assertions.assertTrue(tracker.received(key(1)), "the request was outstanding");
        Assertions.assertFalse(tracker.received(key(1)), "a second arrival has nothing left to clear");
        Assertions.assertEquals(Ask.FIRST, tracker.ask(key(1)), "a cleared hash is a first request again, at once");
        now += TIMEOUT;
        Assertions.assertEquals(Ask.RESEND, tracker.ask(key(1)), "and its wait starts over at the short one");
    }

    @Test
    public void hashesAreTrackedIndependently() {
        Assertions.assertEquals(Ask.FIRST, tracker.ask(key(1)));
        now += TIMEOUT - 1;
        Assertions.assertEquals(Ask.FIRST, tracker.ask(key(2)), "another hash is its own request");
        now += 1;
        Assertions.assertEquals(Ask.RESEND, tracker.ask(key(1)), "the first has expired");
        Assertions.assertEquals(Ask.WAIT, tracker.ask(key(2)), "the second, sent later, has not");
    }
}
