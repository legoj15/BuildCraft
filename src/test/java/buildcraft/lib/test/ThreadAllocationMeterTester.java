/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib.test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link ThreadAllocationMeter}: an allocation guard must measure or fail, never read a counter that is switched
 * off (-1 before and after, a difference of zero) as "allocated nothing".
 */
public class ThreadAllocationMeterTester {

    /** Where the measured actions put what they allocate, so the JIT cannot prove the allocation away. */
    private static volatile Object sink;

    private static final int CHUNK = 64 * 1024;

    private static com.sun.management.ThreadMXBean platformBean() {
        return (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    }

    @Test
    public void anAllocatingActionIsSeenToAllocate() {
        com.sun.management.ThreadMXBean threads = platformBean();
        Assumptions.assumeTrue(threads.isThreadAllocatedMemorySupported(), "no per-thread allocation counter here");
        boolean wasEnabled = threads.isThreadAllocatedMemoryEnabled();
        try {
            long bytes = ThreadAllocationMeter.leastAllocatedBytes(3, () -> sink = new byte[CHUNK]);
            Assertions.assertTrue(bytes >= CHUNK / 2, "a " + CHUNK + " byte array measured as " + bytes + " bytes");
        } finally {
            threads.setThreadAllocatedMemoryEnabled(wasEnabled);
        }
    }

    @Test
    public void switchedOffTrackingIsSwitchedBackOnRatherThanMeasuredAsNothing() {
        com.sun.management.ThreadMXBean threads = platformBean();
        Assumptions.assumeTrue(threads.isThreadAllocatedMemorySupported(), "no per-thread allocation counter here");
        boolean wasEnabled = threads.isThreadAllocatedMemoryEnabled();
        try {
            threads.setThreadAllocatedMemoryEnabled(false);
            long bytes = ThreadAllocationMeter.leastAllocatedBytes(3, () -> sink = new byte[CHUNK]);
            Assertions.assertTrue(threads.isThreadAllocatedMemoryEnabled(), "the meter must switch tracking on");
            Assertions.assertTrue(bytes >= CHUNK / 2, "with tracking off to begin with, a " + CHUNK
                    + " byte array measured as " + bytes + " bytes");
        } finally {
            threads.setThreadAllocatedMemoryEnabled(wasEnabled);
        }
    }

    @Test
    public void aCounterThatCannotBeSwitchedOnIsAnErrorNotZero() {
        // A bean that claims support but stays off and reads -1, as a JVM with tracking disabled would.
        com.sun.management.ThreadMXBean off = (com.sun.management.ThreadMXBean) Proxy.newProxyInstance(
                ThreadAllocationMeterTester.class.getClassLoader(),
                new Class<?>[] { com.sun.management.ThreadMXBean.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "isThreadAllocatedMemorySupported" -> true;
                        case "isThreadAllocatedMemoryEnabled" -> false;
                        case "setThreadAllocatedMemoryEnabled" -> null;
                        case "getThreadAllocatedBytes" -> -1L;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        Assertions.assertThrows(IllegalStateException.class,
                () -> ThreadAllocationMeter.leastAllocatedBytes(off, 3, () -> sink = new byte[CHUNK]),
                "an unmeasurable run must not come back as a number");
    }

    @Test
    public void aJvmWithoutTheCounterIsAnErrorNotZero() {
        com.sun.management.ThreadMXBean none = (com.sun.management.ThreadMXBean) Proxy.newProxyInstance(
                ThreadAllocationMeterTester.class.getClassLoader(),
                new Class<?>[] { com.sun.management.ThreadMXBean.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "isThreadAllocatedMemorySupported" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        Assertions.assertThrows(IllegalStateException.class,
                () -> ThreadAllocationMeter.leastAllocatedBytes(none, 3, () -> sink = new byte[CHUNK]));
    }
}
