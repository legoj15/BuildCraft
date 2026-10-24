/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers which (player, snapshot hash) pairs the server has already warned about, so a client that keeps
 * retrying a request for a hash the store does not hold (see {@link SnapshotRequestTracker}) logs one warning rather
 * than one per retry. Bounded, oldest forgotten first: an evicted pair merely warns once more.
 */
final class SnapshotMissWarnings {
    private final Map<String, Boolean> warned;

    SnapshotMissWarnings(int capacity) {
        // Insertion order is eviction order, and nothing reorders on lookup, so the oldest warning goes first.
        this.warned = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > capacity;
            }
        };
    }

    /** Whether this is the first time {@code player} has missed {@code hash} (within the memory's bounds), and so
     *  worth a warning; every later miss of the same pair answers {@code false}. */
    synchronized boolean firstMiss(UUID player, String hash) {
        return warned.put(player + ":" + hash, Boolean.TRUE) == null;
    }
}
