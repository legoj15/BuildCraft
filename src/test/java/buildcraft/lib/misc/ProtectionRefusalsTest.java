/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The refusal memory a machine keeps after a protection mod refused to let it break a block: a refusal holds while the
 * block's state is unchanged and the retry window has not elapsed, so the machine neither asks again nor spends power
 * on the block every cycle, yet a claim that is lifted (which fires no event) is noticed within a bounded time.
 */
public class ProtectionRefusalsTest {

    private static final BlockPos POS = new BlockPos(3, 64, -7);
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

    @Test
    public void nothingIsRefusedUntilItIsRemembered() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 8);
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 0));
    }

    @Test
    public void aRefusalHoldsWhileTheStateIsUnchanged() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 8);
        refusals.remember(POS, STONE, 10);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 10), "refused at once");
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 109), "and for the whole retry window");
    }

    @Test
    public void aChangedStateIsAskedAboutAgainAtOnce() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 8);
        refusals.remember(POS, STONE, 10);
        Assertions.assertFalse(refusals.isRefused(POS, DIRT, 11), "a different block is a new question");
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 12),
                "and the old refusal is forgotten, so the original block is asked about fresh too");
    }

    @Test
    public void aRefusalExpiresAfterTheRetryWindow() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 8);
        refusals.remember(POS, STONE, 10);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 109));
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 110),
                "after the window the claim may have been lifted: ask again");
        Assertions.assertEquals(0, refusals.size(), "an expired refusal is dropped, not kept");
    }

    @Test
    public void aFreshRefusalRestartsTheWindow() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 8);
        refusals.remember(POS, STONE, 0);
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 100));
        refusals.remember(POS, STONE, 100);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 199));
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 200));
    }

    @Test
    public void otherPositionsAreUnaffected() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 8);
        refusals.remember(POS, STONE, 0);
        Assertions.assertFalse(refusals.isRefused(POS.above(), STONE, 1));
    }

    @Test
    public void forgetDropsARefusal() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 8);
        refusals.remember(POS, STONE, 0);
        refusals.forget(POS);
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 1));
    }

    @Test
    public void theOldestRefusalIsDroppedPastCapacity() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 2);
        refusals.remember(POS, STONE, 0);
        refusals.remember(POS.above(), STONE, 0);
        refusals.remember(POS.above(2), STONE, 0);
        Assertions.assertEquals(2, refusals.size());
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 1), "the oldest was evicted (asked about afresh)");
        Assertions.assertTrue(refusals.isRefused(POS.above(2), STONE, 1));
    }

    @Test
    public void aMutablePositionIsCopiedNotAliased() {
        ProtectionRefusals refusals = new ProtectionRefusals(100, 8);
        BlockPos.MutableBlockPos cursor = POS.mutable();
        refusals.remember(cursor, STONE, 0);
        cursor.move(0, 5, 0);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 1), "the remembered position did not move with the cursor");
        Assertions.assertFalse(refusals.isRefused(cursor, STONE, 1));
    }
}
