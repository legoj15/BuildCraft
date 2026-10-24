/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.wire;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The wire resend pacing: chunk deliveries flag a player over many consecutive ticks (login, a teleport, walking along
 * a wire run), and each flag used to cost that player the whole structure again on the next tick. The throttle turns a
 * burst of flags into one send at once and one trailing send, so the last state always lands after the last flag. The
 * clock is passed in, so the tests drive it tick by tick.
 */
public class ResendThrottleTester {
    private static final int INTERVAL = 20;

    /** Flags {@code key} on each tick in {@code [from, to]} and collects it from {@link ResendThrottle#takeDue} on
     *  every tick; returns the ticks it was due on. */
    private static List<Long> dueTicks(ResendThrottle<String> throttle, String key, long from, long to,
                                       long flagUntil) {
        List<Long> due = new ArrayList<>();
        for (long now = from; now <= to; now++) {
            if (now <= flagUntil) {
                throttle.request(key);
            }
            if (throttle.takeDue(now).contains(key)) {
                due.add(now);
            }
        }
        return due;
    }

    @Test
    public void aFirstFlagIsDueOnTheSameTickWithNoWait() {
        ResendThrottle<String> throttle = new ResendThrottle<>(INTERVAL);
        throttle.request("a");
        Assertions.assertTrue(throttle.isPending("a"));
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(100));
        Assertions.assertFalse(throttle.isPending("a"), "taking it consumes the flag");
        Assertions.assertTrue(throttle.takeDue(101).isEmpty(), "nothing flagged, nothing due");
    }

    @Test
    public void aBurstOfFlagsGivesOneImmediateSendAndOneTrailingSend() {
        ResendThrottle<String> throttle = new ResendThrottle<>(INTERVAL);
        // A flag every tick for 60 ticks: chunks arriving the whole time.
        List<Long> due = dueTicks(throttle, "a", 0, 200, 59);

        Assertions.assertEquals(List.of(0L, 20L, 40L, 60L), due,
            "one send at once, then at most one per interval, then the trailing one after the last flag");
        Assertions.assertFalse(throttle.isPending("a"));
    }

    @Test
    public void aFlagInsideTheIntervalWaitsForTheTrailingSendAndIsNotLost() {
        ResendThrottle<String> throttle = new ResendThrottle<>(INTERVAL);
        throttle.request("a");
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(0));

        // The last chunk of the burst lands three ticks later.
        throttle.request("a");
        for (long now = 1; now < INTERVAL; now++) {
            Assertions.assertTrue(throttle.takeDue(now).isEmpty(), "still inside the interval at tick " + now);
            Assertions.assertTrue(throttle.isPending("a"), "the flag survives the wait at tick " + now);
        }
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(INTERVAL), "the trailing send lands once the interval is up");
        Assertions.assertTrue(throttle.takeDue(INTERVAL + 1).isEmpty());
    }

    @Test
    public void aFlagAfterAQuietSpellIsImmediateAgain() {
        ResendThrottle<String> throttle = new ResendThrottle<>(INTERVAL);
        throttle.request("a");
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(0));

        long later = 10 * INTERVAL;
        throttle.request("a");
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(later), "a long-past send does not delay a new one");
    }

    @Test
    public void playersAreThrottledIndependently() {
        ResendThrottle<String> throttle = new ResendThrottle<>(INTERVAL);
        throttle.request("a");
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(0));

        throttle.request("a");
        throttle.request("b");
        Assertions.assertEquals(Set.of("b"), throttle.takeDue(5), "b has never been sent; a is inside its interval");
        Assertions.assertTrue(throttle.isPending("a"));
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(INTERVAL));
    }

    @Test
    public void aSendMadeForAnotherReasonSatisfiesTheFlagAndStartsTheInterval() {
        ResendThrottle<String> throttle = new ResendThrottle<>(INTERVAL);
        throttle.request("a");
        // The full structure went out anyway this tick (a structure change): the flag has nothing left to add.
        throttle.markSent("a", 7);
        Assertions.assertFalse(throttle.isPending("a"));
        Assertions.assertTrue(throttle.takeDue(7).isEmpty());

        // And it counts as the last send, so a chunk landing right after waits for the interval.
        throttle.request("a");
        Assertions.assertTrue(throttle.takeDue(8).isEmpty());
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(7 + INTERVAL));
    }

    @Test
    public void forgettingAPlayerDropsItsFlagAndItsHistory() {
        ResendThrottle<String> throttle = new ResendThrottle<>(INTERVAL);
        throttle.request("a");
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(0));
        throttle.request("a");

        throttle.forget("a");
        Assertions.assertFalse(throttle.isPending("a"));
        Assertions.assertTrue(throttle.takeDue(1).isEmpty());

        throttle.request("a");
        Assertions.assertEquals(Set.of("a"), throttle.takeDue(2), "its history went with it: due at once, not after the old interval");
    }
}
