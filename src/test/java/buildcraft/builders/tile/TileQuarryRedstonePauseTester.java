/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import static buildcraft.lib.test.GameTestUtil.check;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.BCBuildersBlocks;

/**
 * A redstone signal beside the Quarry pauses it in place, as its guide page says: no frame building, no mining, no
 * power spent, the job it was on kept; taking the signal away lets it carry on. (The Pump and the Mining Well have
 * their own copy of this, {@code MinerRedstonePauseTester}.)
 *
 * <p>Two paths set the pause, so it runs both ways: the signal arrives after the quarry has ticked (the block's
 * neighbour update sets it), and the quarry is placed beside a signal already there (nothing tells it, so its first
 * tick reads the world: the flag is not saved, so a reloaded quarry does the same).
 *
 * <p>Layout (local coordinates, one arena cell, quarry beside the frame box like a placed one):
 * <pre>
 *   quarry (1,2,2), signal (1,3,2) above it (outside the frame box)
 *   frame box  (2,2,1)..(5,4,4) - air, so the frame is all the quarry has to build
 *   mining box (3,*,2)..(4,3,3) - floored with bedrock at y=1
 * </pre>
 * Synchronous: the test drives {@code TileQuarry.tick()} by hand with a full battery, so no world tick is assumed.
 * The powered stretch is long against the ticks the quarry needs to place its first frame block when not paused, so a
 * quarry that works while powered fails by name.
 */
public class TileQuarryRedstonePauseTester {

    private static final BlockPos QUARRY = new BlockPos(1, 2, 2);
    private static final BlockPos SIGNAL = new BlockPos(1, 3, 2);
    private static final BlockPos FRAME_MIN = new BlockPos(2, 2, 1);
    private static final BlockPos FRAME_MAX = new BlockPos(5, 4, 4);

    /** Every pause is held this many ticks, against the few a frame block takes the unpaused quarry. */
    private static final int PAUSED_TICKS = 100;
    /** The ticks a resumed quarry gets to place its first frame block. */
    private static final int RESUME_TICKS = 400;

    /** The signal arrives after the quarry has queued its first frame block and begun it with no power. */
    public static void quarryPausesWhenSignalArrivesAndResumes(GameTestHelper helper) {
        run(helper, false);
    }

    /** A quarry placed beside a signal never starts. */
    public static void quarryPlacedBesideSignalStartsPaused(GameTestHelper helper) {
        run(helper, true);
    }

    /** The quarry, its boxes and its bedrock floor, built at {@link #QUARRY}; any signal is placed by the caller. */
    private static TileQuarry placeQuarry(GameTestHelper helper) {
        for (int x = 3; x <= 4; x++) {
            for (int z = 2; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.BEDROCK);
            }
        }
        helper.setBlock(QUARRY, BCBuildersBlocks.QUARRY.get());
        TileQuarry quarry = (TileQuarry) helper.getLevel().getBlockEntity(helper.absolutePos(QUARRY));
        check(quarry != null, "quarry block-entity must be present");
        BlockPos quarryAbs = helper.absolutePos(QUARRY);
        BlockPos min = helper.absolutePos(FRAME_MIN);
        BlockPos max = helper.absolutePos(FRAME_MAX);
        quarry.frameBox.reset();
        quarry.frameBox.setMin(min);
        quarry.frameBox.setMax(max);
        quarry.miningBox.reset();
        // Same shape TileQuarry.onPlacedBy builds; tick() re-floors Y from the configured depth.
        quarry.miningBox.setMin(new BlockPos(min.getX() + 1, quarryAbs.getY() - 1, min.getZ() + 1));
        quarry.miningBox.setMax(new BlockPos(max.getX() - 1, max.getY() - 1, max.getZ() - 1));
        quarry.updatePoses();
        return quarry;
    }

    /**
     * A paused quarry keeps its collision rig: the rig entities are not saved, so a quarry that loads paused would
     * otherwise stand rig-less (players falling through its beams) until the signal went. The drill sits mid-column
     * where a quarry that is part-way through digging would be.
     */
    public static void pausedQuarryKeepsItsRig(GameTestHelper helper) {
        TileQuarry quarry = null;
        try {
            helper.setBlock(SIGNAL, Blocks.REDSTONE_BLOCK);
            quarry = placeQuarry(helper);
            BlockPos quarryAbs = helper.absolutePos(QUARRY);
            quarry.drillPos = new Vec3(quarryAbs.getX() + 3, quarryAbs.getY() - 1, quarryAbs.getZ());
            long full = quarry.getBattery().getCapacity();
            quarry.getBattery().setStored(full);
            quarry.tick();
            check(quarry.getBattery().getStored() == full,
                    "precondition: the quarry beside a signal must be paused, but it spent power");
            check(quarry.rigRebuildCount == 1,
                    "a paused quarry must still build its collision rig, rebuilds=" + quarry.rigRebuildCount);
            cleanUp(helper, quarry);
            helper.succeed();
        } catch (Throwable t) {
            cleanUp(helper, quarry);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static void run(GameTestHelper helper, boolean signalFirst) {
        TileQuarry quarry = null;
        try {
            if (signalFirst) {
                helper.setBlock(SIGNAL, Blocks.REDSTONE_BLOCK);
            }
            quarry = placeQuarry(helper);

            if (!signalFirst) {
                // With no power the quarry still scans its frame and starts on the first block, so it is mid-job.
                quarry.tick();
                check(quarry.currentTask instanceof TileQuarry.TaskAddFrame,
                        "precondition: the unpowered quarry must have started on a frame block, task="
                                + quarry.currentTask);
                helper.setBlock(SIGNAL, Blocks.REDSTONE_BLOCK);
            }
            TileQuarry.Task task = quarry.currentTask;
            long taskPower = task == null ? 0 : task.power;

            long full = quarry.getBattery().getCapacity();
            quarry.getBattery().setStored(full);
            for (int i = 0; i < PAUSED_TICKS; i++) {
                quarry.tick();
            }
            check(countFrame(helper, quarry) == 0, "a quarry beside a redstone signal built frame blocks anyway: "
                    + countFrame(helper, quarry));
            check(quarry.getBattery().getStored() == full,
                    "a paused quarry spent power: " + quarry.getBattery().getStored() + " of " + full + " left");
            check(quarry.currentTask == task && (task == null || task.power == taskPower),
                    "a paused quarry moved on its task: " + task + " -> " + quarry.currentTask);
            check(quarry.drillPos == null, "a paused quarry that never started digging moved its drill: "
                    + quarry.drillPos);

            helper.setBlock(SIGNAL, Blocks.AIR);
            for (int i = 0; i < RESUME_TICKS && countFrame(helper, quarry) == 0; i++) {
                quarry.getBattery().setStored(full);
                quarry.tick();
            }
            check(countFrame(helper, quarry) > 0,
                    "the quarry did not carry on once the redstone signal went, within " + RESUME_TICKS + " ticks");
            cleanUp(helper, quarry);
            helper.succeed();
        } catch (Throwable t) {
            cleanUp(helper, quarry);
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static int countFrame(GameTestHelper helper, TileQuarry quarry) {
        int frames = 0;
        for (BlockPos p : BlockPos.betweenClosed(quarry.frameBox.min(), quarry.frameBox.max())) {
            if (helper.getLevel().getBlockState(p).is(BCBuildersBlocks.FRAME.get())) {
                frames++;
            }
        }
        return frames;
    }

    /** Drops the frame, the rig entities and the force-load tickets so nothing leaks into neighbouring arenas. */
    private static void cleanUp(GameTestHelper helper, TileQuarry quarry) {
        if (quarry != null) {
            if (quarry.frameBox.isInitialized()) {
                for (BlockPos p : BlockPos.betweenClosed(quarry.frameBox.min(), quarry.frameBox.max())) {
                    if (helper.getLevel().getBlockState(p).is(BCBuildersBlocks.FRAME.get())) {
                        helper.getLevel().removeBlock(p, false);
                    }
                }
            }
            quarry.currentTask = null;
            quarry.drillPos = null;
            quarry.updateRigs();
        }
        helper.setBlock(SIGNAL, Blocks.AIR);
        helper.setBlock(QUARRY, Blocks.AIR);
        for (int x = 3; x <= 4; x++) {
            for (int z = 2; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
            }
        }
    }
}
