/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.container;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit pins for {@link PaintRateLimiter}, the per-viewer cap on Zone Planner stroke messages: a burst a quick
 * human (or a lagging connection delivering a backlog at once) can reach, a sustained rate none can, and a refill
 * driven by the game clock alone.
 */
public class PaintRateLimiterTest {

    private static int acquireMany(PaintRateLimiter limiter, long now, int attempts) {
        int ok = 0;
        for (int i = 0; i < attempts; i++) {
            if (limiter.tryAcquire(now)) {
                ok++;
            }
        }
        return ok;
    }

    @Test
    public void testABurstIsAllowedThenRefused() {
        PaintRateLimiter limiter = new PaintRateLimiter();
        Assertions.assertEquals(PaintRateLimiter.BURST, acquireMany(limiter, 1000, PaintRateLimiter.BURST + 50),
                "a fresh viewer may send a full burst in one tick and no more");
        Assertions.assertFalse(limiter.tryAcquire(1000));
    }

    @Test
    public void testItRefillsOneStrokeAtATime() {
        PaintRateLimiter limiter = new PaintRateLimiter();
        acquireMany(limiter, 1000, PaintRateLimiter.BURST);
        Assertions.assertFalse(limiter.tryAcquire(1000 + PaintRateLimiter.TICKS_PER_STROKE - 1),
                "not a full stroke's worth of ticks yet");
        Assertions.assertTrue(limiter.tryAcquire(1000 + PaintRateLimiter.TICKS_PER_STROKE), "one stroke's worth");
        Assertions.assertFalse(limiter.tryAcquire(1000 + PaintRateLimiter.TICKS_PER_STROKE), "and only one");
        Assertions.assertEquals(3, acquireMany(limiter, 1000 + PaintRateLimiter.TICKS_PER_STROKE * 4, 10),
                "six ticks since the last stroke buy three more");
    }

    @Test
    public void testIdleTimeNeverBanksMoreThanOneBurst() {
        PaintRateLimiter limiter = new PaintRateLimiter();
        acquireMany(limiter, 0, PaintRateLimiter.BURST);
        Assertions.assertEquals(PaintRateLimiter.BURST, acquireMany(limiter, 1_000_000, 1000),
                "a long wait refills to a burst, not to a backlog");
    }

    @Test
    public void testASustainedFloodIsHeldToTheRefillRate() {
        PaintRateLimiter limiter = new PaintRateLimiter();
        int ticks = 2000;
        int ok = 0;
        for (int t = 0; t < ticks; t++) {
            ok += acquireMany(limiter, t, 5); // five messages every tick
        }
        int expected = PaintRateLimiter.BURST + ticks / PaintRateLimiter.TICKS_PER_STROKE;
        Assertions.assertTrue(Math.abs(ok - expected) <= 2, "accepted " + ok + ", expected about " + expected);
    }

    @Test
    public void testNormalPaintingIsNeverRefused() {
        PaintRateLimiter limiter = new PaintRateLimiter();
        // A fast clicker: ten strokes a second, i.e. one every two ticks, for a minute.
        for (int t = 0; t < 20 * 60; t += 2) {
            Assertions.assertTrue(limiter.tryAcquire(t), "tick " + t);
        }
    }

    @Test
    public void testRefilledMeansIndistinguishableFromANewBucket() {
        PaintRateLimiter limiter = new PaintRateLimiter();
        Assertions.assertTrue(limiter.isRefilled(0), "a new bucket is full");
        acquireMany(limiter, 1000, 3);
        Assertions.assertFalse(limiter.isRefilled(1000), "three strokes spent");
        Assertions.assertFalse(limiter.isRefilled(1000 + 3 * PaintRateLimiter.TICKS_PER_STROKE - 1));
        Assertions.assertTrue(limiter.isRefilled(1000 + 3 * PaintRateLimiter.TICKS_PER_STROKE), "refilled exactly when earned back");
        Assertions.assertFalse(limiter.isRefilled(10), "a clock behind the last stroke is not a refill");
    }

    @Test
    public void testAClockThatStepsBackwardsDoesNotRefillOrThrow() {
        PaintRateLimiter limiter = new PaintRateLimiter();
        acquireMany(limiter, 1000, PaintRateLimiter.BURST);
        Assertions.assertFalse(limiter.tryAcquire(10), "time going backwards earns nothing");
        Assertions.assertFalse(limiter.tryAcquire(10 + PaintRateLimiter.TICKS_PER_STROKE - 1));
        Assertions.assertTrue(limiter.tryAcquire(10 + PaintRateLimiter.TICKS_PER_STROKE), "and refilling resumes from there");
    }
}
