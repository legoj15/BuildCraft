/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib.test;

import java.lang.management.ManagementFactory;

/**
 * Measures how many bytes the calling thread allocates while running an action, for the tests that pin "this path
 * allocates nothing per cell". A measurement is either real or an exception: the JVM's per-thread counter reads -1
 * while tracking is switched off, which would turn every before/after pair into a difference of zero and let an
 * allocation guard pass without having measured anything.
 */
public final class ThreadAllocationMeter {

    private ThreadAllocationMeter() {}

    /** Readings taken to find what taking a measurement itself costs. */
    private static final int FLOOR_READINGS = 5;

    /**
     * The least number of bytes the calling thread allocated over {@code rounds} runs of {@code action}, less the
     * cost of the measurement itself. The least of several: a stray allocation elsewhere on the thread cannot fail
     * a guard, only a steady per-run one can. {@code action} must not allocate for its own bookkeeping (no string
     * building, no boxing): that is counted as the action's.
     *
     * @throws IllegalStateException when the JVM cannot measure per-thread allocation, even after tracking is
     *         switched on, so a guard built on this can never pass without measuring
     */
    public static long leastAllocatedBytes(int rounds, Runnable action) {
        return leastAllocatedBytes((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean(), rounds,
                action);
    }

    /** {@link #leastAllocatedBytes(int, Runnable)} on a given bean; the tests substitute a bean that cannot count. */
    static long leastAllocatedBytes(com.sun.management.ThreadMXBean threads, int rounds, Runnable action) {
        if (rounds < 1) {
            throw new IllegalArgumentException("at least one round, got " + rounds);
        }
        if (!threads.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("the JVM does not report per-thread allocation");
        }
        if (!threads.isThreadAllocatedMemoryEnabled()) {
            // Supported but switched off (by whoever launched the JVM, or an earlier test): every reading would be
            // -1. Left on afterwards, it being the JVM's default.
            threads.setThreadAllocatedMemoryEnabled(true);
            if (!threads.isThreadAllocatedMemoryEnabled()) {
                throw new IllegalStateException("per-thread allocation tracking cannot be switched on");
            }
        }
        long thread = Thread.currentThread().threadId();
        long floor = Long.MAX_VALUE;
        for (int i = 0; i < FLOOR_READINGS; i++) {
            long before = reading(threads, thread);
            floor = Math.min(floor, reading(threads, thread) - before);
        }
        long allocated = Long.MAX_VALUE;
        for (int i = 0; i < rounds; i++) {
            long before = reading(threads, thread);
            action.run();
            long after = reading(threads, thread);
            allocated = Math.min(allocated, after - before - floor);
        }
        return allocated;
    }

    /** The thread's allocated bytes, never the -1 of a counter that is off (or of a thread that is gone). */
    private static long reading(com.sun.management.ThreadMXBean threads, long thread) {
        long bytes = threads.getThreadAllocatedBytes(thread);
        if (bytes < 0) {
            throw new IllegalStateException("the JVM reported " + bytes + " allocated bytes for thread " + thread
                    + ": tracking is off or was switched off mid-measurement");
        }
        return bytes;
    }
}
