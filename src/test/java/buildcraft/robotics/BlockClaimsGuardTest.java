/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/** Every block claim goes through {@link BlockClaims}, the one place that pairs the search-cell ledger with the job
 *  ledger: a board or AI that built its own {@code ResourceIdBlock}, or reached the search cells directly, would
 *  check (or claim) only one of the two - exactly the hand-copied halves this guard exists to keep from coming back.
 *  The API package (which defines both) and the registry (which implements them) are the only other places allowed
 *  to name them. Reads this node's own compiled sources ({@link MainSourceSet}). */
public class BlockClaimsGuardTest {

    private static final Set<String> ALLOWED = Set.of(
            "buildcraft/robotics/BlockClaims.java",
            "buildcraft/robotics/RobotRegistry.java");

    @Test
    public void noBoardOrAiTouchesTheBlockLedgersDirectly() {
        List<String> offenders = new ArrayList<>();
        for (MainSourceSet.SourceFile file : MainSourceSet.javaFiles()) {
            String path = file.relativePath();
            if (path.startsWith("buildcraft/api/") || ALLOWED.contains(path)) {
                continue;
            }
            String code = MainSourceSet.codeOnly(file.text());
            if (code.contains("new ResourceIdBlock(") || code.contains("getBlockReservations(")) {
                offenders.add(path);
            }
        }
        Assertions.assertTrue(offenders.isEmpty(),
                "claim, check and release blocks through BlockClaims, not the ledgers directly: " + offenders);
    }
}
