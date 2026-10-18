/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * {@link UnreachableBlockCache}: the block twin of {@link UnreachableEntityCache}. A robot that searched for a block
 * and could not path to a candidate remembers that for a while, so the next search does not spend its rounds on the
 * same candidates again. The clock is an {@link AtomicLong} standing in for {@code level::getGameTime}.
 */
public class UnreachableBlockCacheTest {

    /** Not 0: a robot in a real world meets the cache millions of ticks in. */
    private static final long START_TICK = 5_000_000L;

    private final AtomicLong clock = new AtomicLong(START_TICK);
    private final UnreachableBlockCache cache = new UnreachableBlockCache(clock::get);

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
}
