/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import java.util.Iterator;
import java.util.LinkedList;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import buildcraft.api.core.IZone;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.path.BlockScannerExpanding;
import buildcraft.robotics.path.BlockScannerRandom;
import buildcraft.robotics.path.BlockScannerZoneRandom;
import buildcraft.robotics.path.IBlockFilter;
import buildcraft.robotics.path.PathFindingSearch;
import buildcraft.robotics.path.SoftBlockAccess;

/** Searches for the nearest block matching {@code pathFound} and reports it as {@link #blockFound} (plus the
 *  approach {@link #path} minus the target cell, for a {@link AIRobotGotoBlock}). Ported from 7.1.x
 *  {@code AIRobotSearchBlock}: a {@code PathFindingSearch} driven by a block-scanner iterator (expanding
 *  cube by default, random when {@code random}, zone-biased random when the robot has a work zone) and
 *  the dimension's shared reservation set, so two concurrent searches never converge on the same target.
 *
 *  <p>The {@code PathFindingSearch} is created lazily in {@link #update()} rather than {@code start()}:
 *  the AI is cycled, not started, by the game tests, and a robot whose search is restored mid-flight
 *  resumes on its first cycled tick. A search loaded from NBT (the 1-arg constructor) has no scanner
 *  iterator to resume with — 7.1.x treated a runner-less job as dead, so {@link #update()} aborts it. */
public class AIRobotSearchBlock extends AIRobot {

    public BlockPos blockFound;
    public LinkedList<BlockPos> path;

    private PathFindingSearch blockScanner = null;
    private IBlockFilter pathFound;
    private Iterator<BlockPos> blockIter;
    private double maxDistanceToEnd;
    private IZone zone;

    public AIRobotSearchBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchBlock(IRobotAccess iRobot, boolean random, IBlockFilter iPathFound,
            double iMaxDistanceToEnd) {
        super(iRobot);

        pathFound = iPathFound;
        zone = iRobot.getZoneToWork();
        // Scanner selection, as 7.1.x: the expanding cube is the default; random gets the zone's own
        // distribution when the robot has a work zone, else a uniform sphere of radius 64.
        Level level = iRobot.level();
        if (!random) {
            blockIter = new BlockScannerExpanding();
        } else if (zone != null && level != null) {
            blockIter = new BlockScannerZoneRandom(iRobot.blockPosition(), level.getRandom(), zone);
        } else if (level != null) {
            blockIter = new BlockScannerRandom(level.getRandom(), 64);
        } else {
            blockIter = null; // no world to draw randomness from — update() aborts a runner-less job
        }
        blockFound = null;
        path = null;
        maxDistanceToEnd = iMaxDistanceToEnd;
    }

    @Override
    public long getPowerCost() {
        // 7.1.x: 2 RF, at the 1 RF = 100_000 µMJ bridge.
        return 2 * MjAPI.MJ;
    }

    @Override
    public boolean success() {
        return blockFound != null;
    }

    @Override
    public void update() {
        if (blockIter == null) {
            // Loaded from NBT (or built without a world): the scanner iterator is not serialised, so the
            // search cannot resume — abort, as 7.1.x did for a job with no runner.
            abort();
            return;
        }
        if (blockScanner == null) {
            blockScanner = new PathFindingSearch(SoftBlockAccess.of(robot.level()), robot.blockPosition(),
                    blockIter, pathFound, maxDistanceToEnd, 96, zone,
                    robot.getRegistry().getBlockReservations());
        }
        if (blockScanner.isDone()) {
            path = blockScanner.getResult();
            if (path.size() > 0) {
                // Drop the target cell itself — the robot flies to the cell BEFORE it, not into it.
                path.removeLast();
                blockFound = blockScanner.getResultTarget();
            } else {
                path = null;
            }
            terminate();
        } else {
            blockScanner.iterate();
        }
    }

    /** Reserves the found block in the registry (7.1.x: registry take of a ResourceIdBlock) and drops the
     *  search's own reservation. */
    public boolean takeResource() {
        if (blockFound == null) {
            return false;
        }
        boolean taken = robot.getRegistry().take(new ResourceIdBlock(blockFound), robot.getRobotId());
        unreserve();
        return taken;
    }

    public void unreserve() {
        if (blockScanner != null && blockFound != null) {
            blockScanner.unreserve(blockFound);
        }
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (blockFound != null) {
            int[] arr = {blockFound.getX(), blockFound.getY(), blockFound.getZ()};
            nbt.putIntArray("blockFound", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "blockFound", null);
        if (arr != null && arr.length == 3) {
            blockFound = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
