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
public class RefusalMemoryFixedWindowTest {

    private static final BlockPos POS = new BlockPos(3, 64, -7);
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

    @Test
    public void nothingIsRefusedUntilItIsRemembered() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 0, null));
    }

    @Test
    public void aRefusalHoldsWhileTheStateIsUnchanged() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 10, null);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 10, null), "refused at once");
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 109, null), "and for the whole retry window");
    }

    @Test
    public void aChangedStateIsAskedAboutAgainAtOnce() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 10, null);
        Assertions.assertFalse(refusals.isRefused(POS, DIRT, 11, null), "a different block is a new question");
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 12, null),
                "and the old refusal is forgotten, so the original block is asked about fresh too");
    }

    @Test
    public void aRefusalExpiresAfterTheRetryWindow() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 10, null);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 109, null));
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 110, null),
                "after the window the claim may have been lifted: ask again");
        Assertions.assertEquals(0, refusals.size(), "an expired refusal is dropped, not kept");
    }

    @Test
    public void aFreshRefusalRestartsTheWindow() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 0, null);
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 100, null));
        refusals.remember(POS, STONE, 100, null);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 199, null));
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 200, null));
    }

    @Test
    public void otherPositionsAreUnaffected() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 0, null);
        Assertions.assertFalse(refusals.isRefused(POS.above(), STONE, 1, null));
    }

    @Test
    public void forgetDropsARefusal() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 0, null);
        refusals.forget(POS);
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 1, null));
    }

    @Test
    public void theOldestRefusalIsDroppedPastCapacity() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 2));
        refusals.remember(POS, STONE, 0, null);
        refusals.remember(POS.above(), STONE, 0, null);
        refusals.remember(POS.above(2), STONE, 0, null);
        Assertions.assertEquals(2, refusals.size());
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 1, null), "the oldest was evicted (asked about afresh)");
        Assertions.assertTrue(refusals.isRefused(POS.above(2), STONE, 1, null));
    }

    @Test
    public void aMutablePositionIsCopiedNotAliased() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        BlockPos.MutableBlockPos cursor = POS.mutable();
        refusals.remember(cursor, STONE, 0, null);
        cursor.move(0, 5, 0);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 1, null), "the remembered position did not move with the cursor");
        Assertions.assertFalse(refusals.isRefused(cursor, STONE, 1, null));
    }

    @Test
    public void clearDropsEveryRefusal() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 0, null);
        refusals.remember(POS.above(), STONE, 0, null);
        Assertions.assertFalse(refusals.isEmpty());
        refusals.clear();
        Assertions.assertTrue(refusals.isEmpty());
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 1, null));
    }

    @Test
    public void takeExpiredHandsBackOnlyRefusalsWhoseWindowElapsed() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 0, null);
        refusals.remember(POS.above(), STONE, 50, null);
        Assertions.assertTrue(refusals.takeExpired(99, 8).isEmpty(), "nothing is due yet");
        Assertions.assertEquals(java.util.List.of(POS), refusals.takeExpired(100, 8), "only the older one is due");
        Assertions.assertEquals(java.util.List.of(POS.above()), refusals.takeExpired(150, 8));
    }

    @Test
    public void takeExpiredRestartsTheWindowSoACellIsNotHandedBackTwice() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 0, null);
        Assertions.assertEquals(java.util.List.of(POS), refusals.takeExpired(100, 8));
        Assertions.assertTrue(refusals.takeExpired(101, 8).isEmpty(), "the window restarted");
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 199, null), "and the refusal still holds in that window");
        Assertions.assertEquals(java.util.List.of(POS), refusals.takeExpired(200, 8), "due again a window later");
    }

    @Test
    public void takeExpiredRespectsTheLimitAndLeavesTheRestDue() {
        RefusalMemory refusals = new RefusalMemory(PositionMemory.Policy.fixed(100, 8));
        refusals.remember(POS, STONE, 0, null);
        refusals.remember(POS.above(), STONE, 0, null);
        refusals.remember(POS.above(2), STONE, 0, null);
        Assertions.assertEquals(2, refusals.takeExpired(100, 2).size());
        Assertions.assertEquals(java.util.List.of(POS.above(2)), refusals.takeExpired(100, 2),
                "the one left over is handed back next call");
    }

    @Test
    public void takeExpiredOnAnEmptyMemoryIsCheap() {
        Assertions.assertTrue(new RefusalMemory(PositionMemory.Policy.fixed(100, 8)).takeExpired(1_000, 8).isEmpty());
    }
}
