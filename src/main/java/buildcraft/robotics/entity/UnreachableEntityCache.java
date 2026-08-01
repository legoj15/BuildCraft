/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.function.LongSupplier;

import net.minecraft.world.entity.Entity;

/**
 * Short-lived "I could not reach that" memory, extracted out of the robot entity so it can be unit-tested
 * without a {@code Level}.
 *
 * <p>7.1.x kept this inline as a {@code WeakHashMap<Entity, Long>} on {@code EntityRobot}, expiring each note
 * 1200 ticks after it was made. Keeping it a plain object with an injected clock means the expiry rule is
 * testable in pure JUnit; the weak keys mean an entity that unloads takes its note with it.
 *
 * <p><b>Skeleton — no behaviour yet.</b> Ph3 implementation fills these in.
 */
public class UnreachableEntityCache {

    /** How long a note survives, in ticks. Matches 7.1.x. */
    public static final long EXPIRY_TICKS = 1200;

    @SuppressWarnings("unused")
    private final LongSupplier clock;

    /** @param clock Supplies the current game time in ticks (normally {@code level::getGameTime}). */
    public UnreachableEntityCache(LongSupplier clock) {
        this.clock = clock;
    }

    /** Records that {@code entity} could not be reached, starting (or restarting) its {@link #EXPIRY_TICKS}
     *  countdown. */
    public void unreachableDetected(Entity entity) {
    }

    /** @return true while a note for {@code entity} is still within {@link #EXPIRY_TICKS}. Reading an expired
     *          note also drops it, so the map does not grow without bound for a long-lived robot. */
    public boolean isKnownUnreachable(Entity entity) {
        return false;
    }
}
