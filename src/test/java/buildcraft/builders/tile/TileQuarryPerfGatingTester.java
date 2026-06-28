/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.BCBuildersBlocks;

/**
 * Pins the per-tick cost gates added to {@link TileQuarry} so a finished quarry stops draining TPS:
 * <ul>
 *   <li>{@link TileQuarry#isActive()} — the single predicate that gates BOTH the never-draining frame
 *       re-scan loop and the HARD chunk-loading tickets. It is true while bootstrapping
 *       ({@code !firstChecked}) or while {@link TileQuarry#hasPendingWork()}, and false once the quarry
 *       has finished — at which point the frame scan stops and the chunk tickets are released.</li>
 *   <li>{@link TileQuarry#updateRigs()} — must no-op when the drill is stationary instead of
 *       re-allocating segment lists and re-applying identical boxes to every rig entity every tick.</li>
 * </ul>
 * Both were "massive performance drain" contributors (CurseForge report): the frame loop and chunk
 * tickets kept a finished quarry ticking and force-loading its footprint forever.
 */
public class TileQuarryPerfGatingTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** A bootstrapping quarry is active; a finished one (firstChecked, no work) is inactive; queuing a
     *  task re-activates it. isActive() gates the frame-scan loop and the chunk tickets. */
    public static void testFinishedQuarryGoesInactive(GameTestHelper helper) {
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
            // Default-shaped 4-tall frame + 1-block-inset mining box (mirrors the depth tester setup).
            quarry.frameBox.reset();
            quarry.frameBox.setMin(quarryAbs);
            quarry.frameBox.setMax(quarryAbs.offset(10, 4, 10));
            quarry.miningBox.reset();
            quarry.miningBox.setMin(new BlockPos(quarryAbs.getX() + 1, quarryAbs.getY() - 1, quarryAbs.getZ() + 1));
            quarry.miningBox.setMax(new BlockPos(quarryAbs.getX() + 9, quarryAbs.getY() + 3, quarryAbs.getZ() + 9));

            // Bootstrapping: the frame box hasn't been scanned once yet, so the quarry is active even
            // with no work queued — it must run the initial scan to discover its frame/mining work.
            quarry.firstChecked = false;
            assertTrue(quarry.isActive(),
                    "a quarry that hasn't completed its first frame scan must be active (bootstrapping)");

            // Finished: scanned once, no task, no frame work, no box iterator -> nothing left to do.
            // The frame-scan loop and chunk tickets must shut off.
            quarry.firstChecked = true;
            quarry.currentTask = null;
            assertTrue(!quarry.hasPendingWork(),
                    "a scanned-out quarry with no task/frame-work/iterator has no pending work");
            assertTrue(!quarry.isActive(),
                    "a finished quarry (firstChecked, no pending work) must be inactive so the frame scan"
                            + " and chunk tickets stop");

            // Queue a task -> the quarry has work again and must reactivate (chunks re-acquired, scan resumes).
            quarry.currentTask = quarry.new TaskBreakBlock();
            assertTrue(quarry.hasPendingWork(), "a quarry with a current task has pending work");
            assertTrue(quarry.isActive(), "a quarry with queued work must be active again");

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** updateRigs() rebuilds the rig segments on a drill move but is a no-op while the drill is
     *  stationary (same drillPos + phasing), the way getCollisionBoxes() is already cached. */
    public static void testUpdateRigsSkipsWhenStationary(GameTestHelper helper) {
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
            quarry.frameBox.reset();
            quarry.frameBox.setMin(quarryAbs);
            quarry.frameBox.setMax(quarryAbs.offset(10, 4, 10));

            // Stationary drill mid-column.
            quarry.drillPos = new Vec3(quarryAbs.getX() + 5, quarryAbs.getY() - 3, quarryAbs.getZ() + 5);

            quarry.updateRigs();
            int afterFirst = quarry.rigRebuildCount;
            assertTrue(afterFirst == 1, "first updateRigs() must build the rig once, got rebuildCount " + afterFirst);
            assertTrue(!quarry.getCollisionBoxes().isEmpty(), "a positioned drill must have collision boxes");

            // Same drill position: must be a no-op (no re-alloc / re-apply).
            quarry.updateRigs();
            assertTrue(quarry.rigRebuildCount == afterFirst,
                    "updateRigs() with an unchanged drill position must not rebuild; rebuildCount went "
                            + afterFirst + " -> " + quarry.rigRebuildCount);

            // Move the drill: the geometry changed, so it must rebuild exactly once more.
            quarry.drillPos = new Vec3(quarryAbs.getX() + 5, quarryAbs.getY() - 4, quarryAbs.getZ() + 5);
            quarry.updateRigs();
            assertTrue(quarry.rigRebuildCount == afterFirst + 1,
                    "a moved drill must rebuild the rig once; rebuildCount " + quarry.rigRebuildCount);

            // Tidy up the spawned collision entities so they don't linger in the arena.
            quarry.drillPos = null;
            quarry.updateRigs();

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
