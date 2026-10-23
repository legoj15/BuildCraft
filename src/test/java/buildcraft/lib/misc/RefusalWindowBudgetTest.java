/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.builders.snapshot.BuilderClaimTester;
import buildcraft.builders.tile.TileQuarryUnminableFrameTester;

/**
 * The game tests that wait for a machine to re-ask about a refused cell poll for a budget written as a literal (so
 * {@code GameTestPollBudgetTester} can read it), not computed from {@link RefusalMemory}'s window. This is the other
 * half: each literal still covers its window, with the margin the test needs after it, so widening a window fails
 * here, in the unit run, instead of as a timeout in a game test (or, worse, as a game test class that cannot load).
 *
 * <p>The budgets are compile-time constants, inlined into this class, so reading them loads no game-test code.
 */
public class RefusalWindowBudgetTest {

    @Test
    public void theBuilderRetryBudgetCoversItsWindowAndSomeBuilding() {
        Assertions.assertTrue(BuilderClaimTester.RETRY_BUDGET >= RefusalMemory.BUILDER_PLACE.windowTicks() + 200,
                "BuilderClaimTester.RETRY_BUDGET (" + BuilderClaimTester.RETRY_BUDGET + ") must outlast the Builder's"
                        + " refusal window (" + RefusalMemory.BUILDER_PLACE.windowTicks() + ") by 200 ticks of building:"
                        + " raise it, and the manifests' max_ticks above it");
    }

    @Test
    public void theQuarryFrameRetryBudgetCoversThreeWindows() {
        Assertions.assertTrue(TileQuarryUnminableFrameTester.FRAME_RETRY_TICKS
                        >= RefusalMemory.QUARRY_FRAME.windowTicks() * 3,
                "TileQuarryUnminableFrameTester.FRAME_RETRY_TICKS (" + TileQuarryUnminableFrameTester.FRAME_RETRY_TICKS
                        + ") must cover three quarry-frame refusal windows (" + RefusalMemory.QUARRY_FRAME.windowTicks()
                        + " each): raise it, and the manifests' max_ticks above it");
    }

    @Test
    public void theStripesRetryBudgetCoversItsWindowAndSomeMargin() {
        Assertions.assertTrue(MachineProtectionTester.STRIPES_RETRY_TICKS >= RefusalMemory.STRIPES.windowTicks() + 100,
                "MachineProtectionTester.STRIPES_RETRY_TICKS (" + MachineProtectionTester.STRIPES_RETRY_TICKS
                        + ") must outlast the stripes refusal window (" + RefusalMemory.STRIPES.windowTicks()
                        + ") by 100 ticks of margin: raise it, and the manifest's max_ticks above it");
    }
}
