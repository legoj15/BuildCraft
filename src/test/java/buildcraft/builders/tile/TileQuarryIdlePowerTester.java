/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.builders.BCBuildersBlocks;

/**
 * Pins the "a finished quarry releases its engines" contract: {@link TileQuarry}'s MJ receiver only
 * requests power while there is actual work queued ({@link TileQuarry#hasPendingWork()}). A quarry
 * that has mined out — or one freshly placed with nothing to do yet — must request zero rather than
 * keep pulling MJ to top its 24k battery off. (Holding the battery full after the job just overheats
 * the feeding engines and is voided when the quarry is torn down.)
 */
public class TileQuarryIdlePowerTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    public static void testIdleQuarryRequestsNoPower(GameTestHelper helper) {
        try {
            BlockPos quarryLocal = new BlockPos(2, 3, 2);
            helper.setBlock(quarryLocal, BCBuildersBlocks.QUARRY.get());

            //? if >=1.21.10 {
            TileQuarry quarry = helper.getBlockEntity(quarryLocal, TileQuarry.class);
            //?} else {
            /*TileQuarry quarry = helper.getBlockEntity(quarryLocal);*/
            //?}
            assertTrue(quarry != null, "quarry block-entity must be present");

            // Fresh, un-ticked quarry: no task, no frame work, no box iterator -> nothing to power.
            // The battery is empty, so the OLD (work-agnostic) receiver would request the full
            // 24k-MJ deficit. The gate must instead request zero.
            assertTrue(quarry.getBattery().getStored() == 0, "fresh battery should be empty");
            assertTrue(!quarry.hasPendingWork(), "a fresh, un-ticked quarry has no pending work");
            long idleRequest = quarry.getMjReceiver().getPowerRequested();
            assertTrue(idleRequest == 0,
                    "an idle quarry must request 0 MJ even with an empty battery, got " + idleRequest);

            // Queue a task -> the quarry now has work, so the receiver must request its battery
            // deficit again (the full capacity here, since the battery is still empty).
            quarry.currentTask = quarry.new TaskBreakBlock();
            assertTrue(quarry.hasPendingWork(), "a quarry with a current task has pending work");
            long workingRequest = quarry.getMjReceiver().getPowerRequested();
            assertTrue(workingRequest == quarry.getBattery().getCapacity(),
                    "a working quarry with an empty battery must request its full capacity, got "
                            + workingRequest);

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
