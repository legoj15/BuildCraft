/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link UnreachableEntityCache}'s expiry rule (robotics Ph3).
 *
 * <p>7.1.x kept this as a {@code WeakHashMap<Entity, Long>} inline on the robot entity, so its only test
 * would have been a full game test with a live world. Pulling it out behind an injected clock is what makes
 * the rule — "a robot forgets it could not reach something after 1200 ticks, and forgetting actually frees
 * the entry" — checkable in pure JUnit. The clock here is an {@link AtomicLong} standing in for
 * {@code level::getGameTime}, driven forwards by hand.
 *
 * <p><b>On the keys.</b> The cache is generic over its key type precisely so this file does not need an
 * {@code Entity}. When it was written the {@code test} task could not class-load {@code Entity} at all
 * (no FML loader — see {@link buildcraft.FmlJunitEnvironmentTest}), and an earlier draft went as far as
 * allocating constructor-free instances through {@code sun.reflect.ReflectionFactory} to get around it —
 * a hack that bought nothing, because the cache never looks at a key beyond hashing it. The FML-JUnit
 * environment has since removed the class-loading limit, but the generic design stays on its merits:
 * plain {@code Object}s hash and compare by identity, which is exactly the "these are two different
 * things" property every assertion below needs.
 */
public class UnreachableEntityCacheTest {

    /** An arbitrary non-zero starting game time. Deliberately not 0: a robot in a real world meets the cache
     *  millions of ticks in, and an implementation that stored elapsed-since-zero rather than an absolute
     *  deadline would pass a zero-based test and fail in play. */
    private static final long START_TICK = 5_000_000L;

    private final AtomicLong clock = new AtomicLong(START_TICK);
    private final UnreachableEntityCache<Object> cache = new UnreachableEntityCache<>(clock::get);

    // ── The constant ────────────────────────────────────────────────────────

    @Test
    public void expiryWindowIs1200Ticks() {
        Assertions.assertEquals(1200L, UnreachableEntityCache.EXPIRY_TICKS,
                "7.1.x forgot an unreachable entity after 1200 ticks (one minute) — a robot that never "
                        + "forgets permanently blacklists anything it failed to path to once");
    }

    // ── The rule ────────────────────────────────────────────────────────────

    @Test
    public void anEntityNeverSeenIsReachable() {
        Assertions.assertFalse(cache.isKnownUnreachable(new Object()),
                "an entity the robot has never failed to reach must not be pre-blacklisted");
    }

    @Test
    public void aDetectedEntityIsImmediatelyUnreachable() {
        Object target = new Object();
        cache.unreachableDetected(target);

        Assertions.assertTrue(cache.isKnownUnreachable(target),
                "the note takes effect on the same tick it is made");
    }

    @Test
    public void theNoteHoldsForTheWholeWindow() {
        Object target = new Object();
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS - 1);
        Assertions.assertTrue(cache.isKnownUnreachable(target),
                "one tick short of the window the entity is still remembered as unreachable");
    }

    @Test
    public void theNoteExpiresExactlyOnTheWindowBoundary() {
        Object target = new Object();
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS);
        Assertions.assertFalse(cache.isKnownUnreachable(target),
                "the entity becomes reachable again after exactly EXPIRY_TICKS, not a tick either side");
    }

    @Test
    public void theNoteIsLongExpiredWellPastTheWindow() {
        Object target = new Object();
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS * 10);
        Assertions.assertFalse(cache.isKnownUnreachable(target), "and it stays reachable afterwards");
    }

    @Test
    public void reDetectingRestartsTheCountdown() {
        Object target = new Object();
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS - 1);
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS);
        Assertions.assertTrue(cache.isKnownUnreachable(target),
                "a second failure restarts the window rather than letting the original note run out — a "
                        + "robot that keeps failing keeps remembering");
    }

    @Test
    public void readingAnExpiredNoteDropsIt() {
        // The only externally visible proof that the expired entry is actually removed rather than left to
        // accumulate: after the expiring read, wind the clock BACK inside the original window. A cache that
        // still held the old deadline would answer "unreachable" again; one that dropped it answers
        // "reachable". Without the drop a long-lived robot's map grows for every entity it ever missed.
        Object target = new Object();
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS);
        Assertions.assertFalse(cache.isKnownUnreachable(target), "precondition: the note has expired");

        clock.set(START_TICK + 1);
        Assertions.assertFalse(cache.isKnownUnreachable(target),
                "the expiring read must have removed the entry, so rewinding the clock cannot resurrect it");
    }

    @Test
    public void notesAreIndependentPerEntity() {
        Object noted = new Object();
        Object other = new Object();
        cache.unreachableDetected(noted);

        Assertions.assertTrue(cache.isKnownUnreachable(noted));
        Assertions.assertFalse(cache.isKnownUnreachable(other),
                "one entity's note must not blacklist a different entity");
    }

    @Test
    public void twoEntitiesExpireOnTheirOwnSchedules() {
        Object early = new Object();
        Object late = new Object();

        cache.unreachableDetected(early);
        clock.set(START_TICK + 600);
        cache.unreachableDetected(late);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS);
        Assertions.assertFalse(cache.isKnownUnreachable(early), "the first note has run its full window");
        Assertions.assertTrue(cache.isKnownUnreachable(late), "the second one is only 600 ticks old");
    }

    // ── Weak keys ───────────────────────────────────────────────────────────

    @Test
    public void theCacheDoesNotKeepItsKeysAlive() {
        // A robot that pinned every entity it ever failed to reach would keep unloaded chunks' entities
        // resident for the lifetime of the robot. 7.1.x used a WeakHashMap for exactly this reason.
        Object target = new Object();
        cache.unreachableDetected(target);

        WeakReference<Object> ref = new WeakReference<>(target);
        target = null;

        for (int attempt = 0; attempt < 50 && ref.get() != null; attempt++) {
            System.gc();
            // Allocate to give the collector something to react to on a lightly loaded VM.
            byte[] pressure = new byte[1 << 20];
            Assertions.assertNotNull(pressure);
        }

        Assertions.assertNull(ref.get(),
                "the cache must hold its entity keys weakly — a strong reference here leaks every entity a "
                        + "robot has ever failed to path to");
    }
}
