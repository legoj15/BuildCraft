/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft;

/**
 * Maps a game test's {@code test_instance} {@code environment} to the batch NAME the 1.21.1 node registers it under.
 *
 * <p>1.21.5+ batches tests by {@code environment} (tests of one batch run concurrently, batches strictly one after
 * another), and the marker tests give themselves private environments so they never run concurrently. 1.21.1 has no
 * {@code test_environment} registry but batches by a plain batch-name string, so {@code BuildCraftGameTests} feeds
 * the manifest's environment through here to get the same isolation instead of one shared {@link #DEFAULT} batch.
 * The mapping lives in this class (not in the 1.21.1-only registration branch, which is commented out on every
 * other node) so a unit test can pin it on any node.
 */
final class GameTestBatchNames {

    /** The batch every test without a private environment shares (the 1.21.1 {@code @GameTest} default). */
    static final String DEFAULT = "defaultBatch";

    private GameTestBatchNames() {}

    /** @param environment the manifest's {@code environment} value, or null if it has none */
    static String forEnvironment(String environment) {
        if (environment == null || environment.isBlank() || environment.equals("minecraft:default")) {
            return DEFAULT;
        }
        return environment;
    }
}
