/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.BCCoreConfig;

/**
 * Pins the two halves of the "Mining Max Depth" contract for {@link TileQuarry}:
 * <ul>
 *   <li>the depth is measured from the quarry block, not the frame top, so
 *       {@code miningBox.min().getY() == worldPosition.getY() - miningMaxDepth}
 *       (clamped to {@code level.getMinY()});</li>
 *   <li>{@link TileQuarry#tick()} reconciles a stale {@code miningBox.min().getY()} —
 *       e.g. one saved with the old frame-anchored formula, or one whose config has
 *       since changed — back to the configured floor on the next server tick.</li>
 * </ul>
 */
public class TileQuarryMiningDepthTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testTickReconcilesStaleMiningFloor(GameTestHelper helper) {
        try {
            BlockPos quarryLocal = new BlockPos(2, 3, 2);
            helper.setBlock(quarryLocal, BCBuildersBlocks.QUARRY.get());

            //? if >=1.21.10 {
            TileQuarry quarry = helper.getBlockEntity(quarryLocal, TileQuarry.class);
            //?} else {
            /*TileQuarry quarry = helper.getBlockEntity(quarryLocal);*/
            //?}
            assertTrue(quarry != null, "quarry block-entity must be present");

            BlockPos quarryAbs = helper.absolutePos(quarryLocal);

            // Stand in for a saved-NBT load: both boxes initialized, miningBox floored
            // higher than the configured floor (mimicking a quarry placed under the old
            // frame-anchored formula). Geometry mirrors a default 4-tall frame.
            quarry.frameBox.reset();
            quarry.frameBox.setMin(quarryAbs);
            quarry.frameBox.setMax(quarryAbs.offset(10, 4, 10));
            int staleFloor = quarryAbs.getY() - 1;
            quarry.miningBox.reset();
            quarry.miningBox.setMin(new BlockPos(quarryAbs.getX() + 1, staleFloor, quarryAbs.getZ() + 1));
            quarry.miningBox.setMax(new BlockPos(quarryAbs.getX() + 9, quarryAbs.getY() + 3, quarryAbs.getZ() + 9));

            quarry.tick();

            int configuredFloor = quarryAbs.getY() - BCCoreConfig.miningMaxDepth.get();
            int expectedFloor = Math.max(configuredFloor, helper.getLevel().getMinY());

            int actualFloor = quarry.miningBox.min().getY();
            assertTrue(actualFloor == expectedFloor,
                    "tick() must reconcile miningBox floor to worldPos.y - miningMaxDepth"
                            + " (or world floor when clamped). Expected " + expectedFloor
                            + ", got " + actualFloor + ". stale was " + staleFloor);

            // Mining box top stays anchored to the frame interior — only the floor moves.
            assertTrue(quarry.miningBox.max().getY() == quarryAbs.getY() + 3,
                    "reconcile must leave miningBox top untouched");
            // X/Z are likewise preserved (one block inset from the frame on each side).
            assertTrue(quarry.miningBox.min().getX() == quarryAbs.getX() + 1
                            && quarry.miningBox.min().getZ() == quarryAbs.getZ() + 1,
                    "reconcile must leave miningBox X/Z min untouched");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
