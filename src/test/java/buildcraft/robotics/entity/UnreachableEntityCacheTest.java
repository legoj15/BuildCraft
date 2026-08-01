/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Entity;

/**
 * Pins {@link UnreachableEntityCache}'s expiry rule (robotics Ph3).
 *
 * <p>7.1.x kept this as a {@code WeakHashMap<Entity, Long>} inline on the robot entity, so its only test
 * would have been a full game test with a live world. Pulling it out behind an injected clock is what makes
 * the rule — "a robot forgets it could not reach something after 1200 ticks, and forgetting actually frees
 * the entry" — checkable in pure JUnit. The clock here is an {@link AtomicLong} standing in for
 * {@code level::getGameTime}, driven forwards by hand.
 *
 * <p><b>On the fake entities.</b> The cache is keyed on {@code Entity}, and {@code Entity}'s constructor
 * needs a live {@code Level} on every node from 26.x up ({@code level.getNextEntityId()}), so there is no
 * way to build one in a unit test. These fakes are therefore allocated without running any constructor and
 * then given an id through the public {@code setId}. That matters because {@code Entity.equals}/
 * {@code hashCode} are both id-based: two constructor-free instances would otherwise both report id 0 and
 * collapse into a single map key, quietly turning every multi-entity assertion below into a tautology.
 * Nothing else about the fakes is touched — the cache only ever uses them as keys.
 *
 * <p><b>Environment note.</b> Every assertion that needs a key currently fails before it reaches the
 * cache, because {@code Entity} cannot be class-loaded at all in this test tree: {@code Entity} extends
 * NeoForge's {@code AttachmentHolder}, whose static initialiser calls {@code FMLEnvironment.isProduction()}
 * and throws "There is no current FML Loader" under the plain {@code test} task. The expiry rule below is
 * still the contract to implement against; making it runnable is a build change, not a test change — see
 * the Ph3 hand-off notes. {@link #expiryWindowIs1200Ticks()} needs no key and runs today.
 */
public class UnreachableEntityCacheTest {

    /** An arbitrary non-zero starting game time. Deliberately not 0: a robot in a real world meets the cache
     *  millions of ticks in, and an implementation that stored elapsed-since-zero rather than an absolute
     *  deadline would pass a zero-based test and fail in play. */
    private static final long START_TICK = 5_000_000L;

    private final AtomicLong clock = new AtomicLong(START_TICK);
    private final UnreachableEntityCache cache = new UnreachableEntityCache(clock::get);

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
        Assertions.assertFalse(cache.isKnownUnreachable(fakeEntity(1)),
                "an entity the robot has never failed to reach must not be pre-blacklisted");
    }

    @Test
    public void aDetectedEntityIsImmediatelyUnreachable() {
        Entity target = fakeEntity(1);
        cache.unreachableDetected(target);

        Assertions.assertTrue(cache.isKnownUnreachable(target),
                "the note takes effect on the same tick it is made");
    }

    @Test
    public void theNoteHoldsForTheWholeWindow() {
        Entity target = fakeEntity(1);
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS - 1);
        Assertions.assertTrue(cache.isKnownUnreachable(target),
                "one tick short of the window the entity is still remembered as unreachable");
    }

    @Test
    public void theNoteExpiresExactlyOnTheWindowBoundary() {
        Entity target = fakeEntity(1);
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS);
        Assertions.assertFalse(cache.isKnownUnreachable(target),
                "the entity becomes reachable again after exactly EXPIRY_TICKS, not a tick either side");
    }

    @Test
    public void theNoteIsLongExpiredWellPastTheWindow() {
        Entity target = fakeEntity(1);
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS * 10);
        Assertions.assertFalse(cache.isKnownUnreachable(target), "and it stays reachable afterwards");
    }

    @Test
    public void reDetectingRestartsTheCountdown() {
        Entity target = fakeEntity(1);
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
        Entity target = fakeEntity(1);
        cache.unreachableDetected(target);

        clock.set(START_TICK + UnreachableEntityCache.EXPIRY_TICKS);
        Assertions.assertFalse(cache.isKnownUnreachable(target), "precondition: the note has expired");

        clock.set(START_TICK + 1);
        Assertions.assertFalse(cache.isKnownUnreachable(target),
                "the expiring read must have removed the entry, so rewinding the clock cannot resurrect it");
    }

    @Test
    public void notesAreIndependentPerEntity() {
        Entity noted = fakeEntity(1);
        Entity other = fakeEntity(2);
        cache.unreachableDetected(noted);

        Assertions.assertTrue(cache.isKnownUnreachable(noted));
        Assertions.assertFalse(cache.isKnownUnreachable(other),
                "one entity's note must not blacklist a different entity");
    }

    @Test
    public void twoEntitiesExpireOnTheirOwnSchedules() {
        Entity early = fakeEntity(1);
        Entity late = fakeEntity(2);

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
        // A robot that pins every entity it ever failed to reach would keep unloaded chunks' entities
        // resident for the lifetime of the robot. 7.1.x used a WeakHashMap for exactly this reason.
        Entity target = fakeEntity(1);
        cache.unreachableDetected(target);

        WeakReference<Entity> ref = new WeakReference<>(target);
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

    // ── Fake-entity plumbing ────────────────────────────────────────────────

    /** Allocates an {@link EntityRobot} without running any constructor and stamps it with {@code id}.
     *
     * <p>{@code EntityRobot} is used purely because it is the concrete {@code Entity} subclass this cache
     * actually serves; none of its state is initialised or read. The reflective factory is reached by name
     * so this file carries no {@code sun.*} import. */
    private static Entity fakeEntity(int id) {
        try {
            Class<?> factoryClass = Class.forName("sun.reflect.ReflectionFactory");
            Object factory = factoryClass.getMethod("getReflectionFactory").invoke(null);
            Constructor<?> allocator = (Constructor<?>) factoryClass
                    .getMethod("newConstructorForSerialization", Class.class, Constructor.class)
                    .invoke(factory, EntityRobot.class, Object.class.getDeclaredConstructor());
            allocator.setAccessible(true);
            Entity entity = (Entity) allocator.newInstance();
            // Non-zero on purpose: getId() throws on an unassigned id from 26.x onwards, and equals/hashCode
            // are id-based, so distinct ids are what keeps distinct fakes distinct map keys.
            entity.setId(id);
            return entity;
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new AssertionError("could not allocate a constructor-free Entity for the cache test — if "
                    + "this is a NoClassDefFoundError on Entity/AttachmentHolder the test tree has no FML "
                    + "loader, which is an environment problem rather than a cache problem", e);
        }
    }
}
