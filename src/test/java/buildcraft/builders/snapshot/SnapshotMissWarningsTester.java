/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A client retries a request the server could not answer every few seconds for as long as the preview is drawn, so
 * the server's "snapshot not found" warning must fire once per player and hash and drop to quiet logging after that;
 * the memory of what was warned about is bounded so a long-running server does not grow it without limit.
 */
public class SnapshotMissWarningsTester {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);

    @Test
    public void aRepeatedMissWarnsOnlyTheFirstTime() {
        SnapshotMissWarnings warnings = new SnapshotMissWarnings(8);
        Assertions.assertTrue(warnings.firstMiss(ALICE, "aa"), "the first miss is worth a warning");
        Assertions.assertFalse(warnings.firstMiss(ALICE, "aa"), "a retry of the same request must stay quiet");
        Assertions.assertFalse(warnings.firstMiss(ALICE, "aa"), "and keep staying quiet");
    }

    @Test
    public void eachPlayerAndHashIsItsOwnMiss() {
        SnapshotMissWarnings warnings = new SnapshotMissWarnings(8);
        Assertions.assertTrue(warnings.firstMiss(ALICE, "aa"));
        Assertions.assertTrue(warnings.firstMiss(BOB, "aa"), "another player missing the same hash is new information");
        Assertions.assertTrue(warnings.firstMiss(ALICE, "bb"), "another hash for the same player is new information");
    }

    @Test
    public void theMemoryIsBoundedAndForgetsTheOldestFirst() {
        SnapshotMissWarnings warnings = new SnapshotMissWarnings(2);
        Assertions.assertTrue(warnings.firstMiss(ALICE, "aa"));
        Assertions.assertTrue(warnings.firstMiss(ALICE, "bb"));
        Assertions.assertTrue(warnings.firstMiss(ALICE, "cc"), "a third pair pushes the oldest out");
        Assertions.assertFalse(warnings.firstMiss(ALICE, "cc"), "the newest is still remembered");
        Assertions.assertTrue(warnings.firstMiss(ALICE, "aa"), "the evicted pair may warn again");
    }
}
