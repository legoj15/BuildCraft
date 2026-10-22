/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * {@link UnreachableBlockCache}: the block twin of {@link UnreachableEntityCache}. A robot that searched for a block
 * and could not path to a candidate remembers that for a while, so the next search does not spend its rounds on the
 * same candidates again. The clock is an {@link AtomicLong} standing in for {@code level::getGameTime}, and the
 * robot's whereabouts an {@link AtomicReference} standing in for {@code robot::blockPosition}.
 */
public class UnreachableBlockCacheTest {

    /** Not 0: a robot in a real world meets the cache millions of ticks in. */
    private static final long START_TICK = 5_000_000L;

    private static final BlockPos HOME = new BlockPos(100, 64, 100);

    private final AtomicLong clock = new AtomicLong(START_TICK);
    private final AtomicReference<BlockPos> robotAt = new AtomicReference<>(HOME);
    private final UnreachableBlockCache cache = new UnreachableBlockCache(clock::get, robotAt::get);

    @Test
    public void aBlockNeverFailedIsNotUnreachable() {
        Assertions.assertFalse(cache.isKnownUnreachable(new BlockPos(1, 2, 3)));
    }

    @Test
    public void aDetectedBlockIsUnreachableByValueNotByInstance() {
        cache.unreachableDetected(new BlockPos(1, 2, 3));

        Assertions.assertTrue(cache.isKnownUnreachable(new BlockPos(1, 2, 3)),
                "a search asks with a fresh BlockPos for the same cell: the note is about the cell");
        Assertions.assertFalse(cache.isKnownUnreachable(new BlockPos(1, 2, 4)), "and only that cell");
    }

    @Test
    public void aNoteOutlastsAFailedSearchAndItsSleep() {
        Assertions.assertTrue(UnreachableBlockCache.EXPIRY_TICKS > 1000 + 1200,
                "a note must outlive one fruitless search (1000 rounds) plus the sleep after it (1200 ticks), or "
                        + "the next search starts from nothing and the cache never helps the case it is for");
    }

    @Test
    public void aNoteIsAliveOneTickBeforeItsDeadlineAndDeadAtIt() {
        BlockPos pos = new BlockPos(7, 8, 9);
        cache.unreachableDetected(pos);

        clock.set(START_TICK + UnreachableBlockCache.EXPIRY_TICKS - 1);
        Assertions.assertTrue(cache.isKnownUnreachable(pos), "still remembered one tick before it runs out");

        clock.set(START_TICK + UnreachableBlockCache.EXPIRY_TICKS);
        Assertions.assertFalse(cache.isKnownUnreachable(pos), "forgotten at the deadline");
    }

    @Test
    public void aSecondFailureExtendsTheMemory() {
        BlockPos pos = new BlockPos(7, 8, 9);
        cache.unreachableDetected(pos);
        clock.set(START_TICK + UnreachableBlockCache.EXPIRY_TICKS - 10);
        cache.unreachableDetected(pos);

        clock.set(START_TICK + UnreachableBlockCache.EXPIRY_TICKS + 5);
        Assertions.assertTrue(cache.isKnownUnreachable(pos), "the second failure restarted the countdown");
    }

    @Test
    public void theCacheKeepsAtMostItsCapacityDroppingTheOldest() {
        for (int i = 0; i < UnreachableBlockCache.CAPACITY + 1; i++) {
            cache.unreachableDetected(new BlockPos(i, 0, 0));
            clock.incrementAndGet();
        }

        Assertions.assertFalse(cache.isKnownUnreachable(new BlockPos(0, 0, 0)), "the oldest note made room");
        Assertions.assertTrue(cache.isKnownUnreachable(new BlockPos(1, 0, 0)), "the next oldest is kept");
        Assertions.assertTrue(cache.isKnownUnreachable(new BlockPos(UnreachableBlockCache.CAPACITY, 0, 0)),
                "and so is the newest");
    }

    @Test
    public void aRenotedBlockIsTheNewestNotTheOldest() {
        for (int i = 0; i < UnreachableBlockCache.CAPACITY; i++) {
            cache.unreachableDetected(new BlockPos(i, 0, 0));
            clock.incrementAndGet();
        }
        cache.unreachableDetected(new BlockPos(0, 0, 0)); // noted again: now the newest
        cache.unreachableDetected(new BlockPos(-1, 0, 0)); // one more over the cap

        Assertions.assertTrue(cache.isKnownUnreachable(new BlockPos(0, 0, 0)), "the refreshed note survived");
        Assertions.assertFalse(cache.isKnownUnreachable(new BlockPos(1, 0, 0)), "the oldest one made room instead");
    }

    @Test
    public void anEvictionLeavesALiveNotesOriginAlone() {
        // A live note whose packed position equals the evicted note's deadline tick: an eviction that dropped the
        // origin by the deadline instead of by the key would take this note's origin, not the evicted one's.
        BlockPos live = new BlockPos(0, 100, 1221);
        clock.set(live.asLong() - UnreachableBlockCache.EXPIRY_TICKS);
        cache.unreachableDetected(new BlockPos(-5, 0, 0)); // the oldest, deadline == live.asLong()
        clock.incrementAndGet();
        cache.unreachableDetected(live);
        for (int i = 0; i < UnreachableBlockCache.CAPACITY - 1; i++) {
            cache.unreachableDetected(new BlockPos(i, 0, 0)); // the last one tips the oldest out
        }

        Assertions.assertFalse(cache.isKnownUnreachable(new BlockPos(-5, 0, 0)), "precondition: the oldest left");
        Assertions.assertTrue(cache.isKnownUnreachable(live),
                "the robot still stands where it noted the live block: an unrelated eviction must not unbind it");
    }

    // ---- a note is about where the robot searched from ----

    @Test
    public void aRobotThatMovedFarAwayTriesTheBlockAgain() {
        BlockPos pos = new BlockPos(1, 2, 3);
        cache.unreachableDetected(pos);

        robotAt.set(HOME.offset(UnreachableBlockCache.NOTE_RADIUS * 3, 0, 0));
        Assertions.assertFalse(cache.isKnownUnreachable(pos),
                "from a spot well away from where it failed, the block is a new question: it may be cheap to reach now");
    }

    @Test
    public void aRobotThatStaysPutOrDriftsASmallWayKeepsSkippingTheBlock() {
        BlockPos pos = new BlockPos(1, 2, 3);
        cache.unreachableDetected(pos);

        Assertions.assertTrue(cache.isKnownUnreachable(pos), "standing where it searched from");
        robotAt.set(HOME.offset(3, -2, 4)); // a hovering, working robot never holds one block position exactly
        Assertions.assertTrue(cache.isKnownUnreachable(pos), "a few blocks off is still the same spot");
    }

    @Test
    public void theNoteReachesExactlyTheNoteRadiusInAnyDirection() {
        BlockPos pos = new BlockPos(1, 2, 3);
        int radius = UnreachableBlockCache.NOTE_RADIUS;
        cache.unreachableDetected(pos);

        for (BlockPos edge : new BlockPos[] {HOME.offset(radius, 0, 0), HOME.offset(-radius, 0, 0),
                HOME.offset(0, radius, 0), HOME.offset(0, -radius, 0),
                HOME.offset(0, 0, radius), HOME.offset(0, 0, -radius)}) {
            robotAt.set(edge);
            Assertions.assertTrue(cache.isKnownUnreachable(pos), "still the same spot at " + edge);
        }
        for (BlockPos beyond : new BlockPos[] {HOME.offset(radius + 1, 0, 0), HOME.offset(-radius - 1, 0, 0),
                HOME.offset(0, radius + 1, 0), HOME.offset(0, -radius - 1, 0),
                HOME.offset(0, 0, radius + 1), HOME.offset(0, 0, -radius - 1)}) {
            robotAt.set(beyond);
            Assertions.assertFalse(cache.isKnownUnreachable(pos), "a new question at " + beyond);
        }
    }

    @Test
    public void theNoteStillHoldsWhenTheRobotComesBackToWhereItSearchedFrom() {
        BlockPos pos = new BlockPos(1, 2, 3);
        cache.unreachableDetected(pos);

        robotAt.set(HOME.offset(200, 0, 0));
        Assertions.assertFalse(cache.isKnownUnreachable(pos), "precondition: far away it does not apply");
        robotAt.set(HOME);
        Assertions.assertTrue(cache.isKnownUnreachable(pos),
                "away did not erase the note: the glass over that crop is still there when the robot returns");
    }

    @Test
    public void aFailureFromANewSpotRebindsTheNoteToThatSpot() {
        BlockPos pos = new BlockPos(1, 2, 3);
        cache.unreachableDetected(pos);

        BlockPos elsewhere = HOME.offset(300, 0, 0);
        robotAt.set(elsewhere);
        cache.unreachableDetected(pos);

        Assertions.assertTrue(cache.isKnownUnreachable(pos), "unreachable from where it now searched");
        robotAt.set(HOME);
        Assertions.assertFalse(cache.isKnownUnreachable(pos), "and the note is that spot's now, not the old one's");
    }

    @Test
    public void notesFromDifferentSpotsAreKeptApartPerBlock() {
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(4, 5, 6);
        cache.unreachableDetected(a);
        BlockPos elsewhere = HOME.offset(300, 0, 0);
        robotAt.set(elsewhere);
        cache.unreachableDetected(b);

        Assertions.assertFalse(cache.isKnownUnreachable(a), "a was noted from home, not from here");
        Assertions.assertTrue(cache.isKnownUnreachable(b));
        robotAt.set(HOME);
        Assertions.assertTrue(cache.isKnownUnreachable(a));
        Assertions.assertFalse(cache.isKnownUnreachable(b));
    }

    @Test
    public void aNoteOutOfRangeStillExpiresAndStillCountsTowardsTheCapacity() {
        BlockPos pos = new BlockPos(1, 2, 3);
        cache.unreachableDetected(pos);
        robotAt.set(HOME.offset(300, 0, 0));
        clock.set(START_TICK + UnreachableBlockCache.EXPIRY_TICKS);
        robotAt.set(HOME);
        Assertions.assertFalse(cache.isKnownUnreachable(pos), "back home, but the note is long expired");

        // Far-off notes are evicted oldest first like any other.
        for (int i = 0; i < UnreachableBlockCache.CAPACITY + 1; i++) {
            cache.unreachableDetected(new BlockPos(i, 0, 0));
        }
        Assertions.assertFalse(cache.isKnownUnreachable(new BlockPos(0, 0, 0)), "the oldest made room");
        Assertions.assertTrue(cache.isKnownUnreachable(new BlockPos(1, 0, 0)));
    }
}
