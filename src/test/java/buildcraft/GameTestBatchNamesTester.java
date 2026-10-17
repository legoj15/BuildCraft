/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Pins the 1.21.1 batch-name mapping that gives each marker test its own batch on a node without
 *  {@code test_environment} (see {@link GameTestBatchNames}). */
public class GameTestBatchNamesTester {

    @Test
    public void defaultAndMissingEnvironmentsShareTheDefaultBatch() {
        Assertions.assertEquals(GameTestBatchNames.DEFAULT, GameTestBatchNames.forEnvironment(null));
        Assertions.assertEquals(GameTestBatchNames.DEFAULT, GameTestBatchNames.forEnvironment(""));
        Assertions.assertEquals(GameTestBatchNames.DEFAULT, GameTestBatchNames.forEnvironment("  "));
        Assertions.assertEquals(GameTestBatchNames.DEFAULT, GameTestBatchNames.forEnvironment("minecraft:default"));
    }

    @Test
    public void privateEnvironmentsGetTheirOwnBatch() {
        String los = GameTestBatchNames.forEnvironment("buildcraftunofficial:marker_volume_los");
        String orientation = GameTestBatchNames.forEnvironment("buildcraftunofficial:marker_orientation");
        Assertions.assertNotEquals(GameTestBatchNames.DEFAULT, los);
        Assertions.assertNotEquals(los, orientation, "two private environments must not share a batch");
        Assertions.assertEquals(los, GameTestBatchNames.forEnvironment("buildcraftunofficial:marker_volume_los"),
            "the same environment must always map to the same batch");
    }
}
