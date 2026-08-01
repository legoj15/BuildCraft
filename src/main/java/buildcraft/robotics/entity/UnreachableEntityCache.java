/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/**
 * Short-lived "I could not reach that" memory, extracted out of the robot entity so it can be unit-tested
 * without a {@code Level}.
 *
 * <p>7.1.x kept this inline as a {@code WeakHashMap<Entity, Long>} on {@code EntityRobot}, expiring each note
 * 1200 ticks after it was made. Keeping it a plain object with an injected clock means the expiry rule is
 * testable in pure JUnit; the weak keys mean an entity that unloads takes its note with it.
 *
 * <p><b>Generic over its key, deliberately.</b> Nothing about "remember this thing for a minute, weakly" needs
 * to know what an {@code Entity} is — and typing it against {@code Entity} would drag the whole
 * {@code AttachmentHolder} -> {@code FMLEnvironment} static-init chain into any test that so much as
 * constructs one, which is exactly the wall the Ph3 test pass hit. {@code EntityRobot} instantiates it as
 * {@code UnreachableEntityCache<Entity>}; the unit tests key it on plain {@code Object}s.
 *
 * <p>Keys are compared with their own {@code equals}/{@code hashCode}, which is what {@code WeakHashMap} does
 * and what 7.1.x relied on: vanilla {@code Entity} compares by network id, so a reloaded entity is the same
 * key, while a plain {@code Object} compares by identity. Not thread-safe — a robot's pathing runs on the
 * server thread.
 *
 * @param <K> The key type. Held weakly, so the cache never keeps an unloaded entity resident.
 */
public class UnreachableEntityCache<K> {

    /** How long a note survives, in ticks. Matches 7.1.x. */
    public static final long EXPIRY_TICKS = 1200;

    private final LongSupplier clock;

    /** Key -> the absolute game tick the note expires ON (the note is dead at exactly this tick, alive one
     *  tick earlier). Absolute rather than elapsed-since-creation so nothing has to be swept per tick. */
    private final Map<K, Long> deadlines = new WeakHashMap<>();

    /** @param clock Supplies the current game time in ticks (normally {@code level::getGameTime}). */
    public UnreachableEntityCache(LongSupplier clock) {
        this.clock = clock;
    }

    /** Records that {@code key} could not be reached, starting (or restarting) its {@link #EXPIRY_TICKS}
     *  countdown. A second failure therefore extends the memory rather than letting the first note run out —
     *  a robot that keeps failing keeps remembering. */
    public void unreachableDetected(K key) {
        if (key == null) {
            return;
        }
        deadlines.put(key, clock.getAsLong() + EXPIRY_TICKS);
    }

    /** @return true while a note for {@code key} is still within {@link #EXPIRY_TICKS}. Reading an expired
     *          note also drops it, so the map does not grow without bound for a long-lived robot that has
     *          failed to path to a great many things. */
    public boolean isKnownUnreachable(K key) {
        if (key == null) {
            return false;
        }
        Long deadline = deadlines.get(key);
        if (deadline == null) {
            return false;
        }
        if (clock.getAsLong() < deadline) {
            return true;
        }
        deadlines.remove(key);
        return false;
    }
}
