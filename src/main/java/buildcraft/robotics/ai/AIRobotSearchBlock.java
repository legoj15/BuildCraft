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

import buildcraft.api.core.IZone;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.path.IBlockFilter;
import buildcraft.robotics.path.PathFindingSearch;

/** Searches for the nearest block matching {@code pathFound} and reports it as {@link #blockFound} (plus the
 *  approach {@link #path} minus the target cell, for a {@link AIRobotGotoBlock}). Ported from 7.1.x
 *  {@code AIRobotSearchBlock}: a {@code PathFindingSearch} driven by a block-scanner iterator (expanding
 *  cube by default, random when {@code random}, zone-biased random when the robot has a work zone) and
 *  the dimension's shared reservation set, so two concurrent searches never converge on the same target.
 *
 *  <p>Red-baseline skeleton: the scanner wiring lands with the 3 {@code BlockScanner} classes in the
 *  foundations commit; until then {@link #start()} does nothing and the inherited {@code update()}
 *  terminates on the first cycle. */
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
        // Scanner iterator selection (expanding / zone-random / random-64) lands in the foundations commit.
        blockIter = null;
        blockFound = null;
        path = null;
        maxDistanceToEnd = iMaxDistanceToEnd;
    }

    @Override
    public void start() {
        // Red-baseline skeleton — PathFindingSearch(SoftBlockAccess.of(level), robot.blockPosition(),
        // blockIter, pathFound, maxDistanceToEnd, 96, zone, registry reservations) in the foundations commit.
    }

    @Override
    public boolean success() {
        return blockFound != null;
    }

    /** Reserves the found block in the registry (7.1.x: registry take of a ResourceIdBlock) and drops the
     *  search's own reservation. */
    public boolean takeResource() {
        // Red-baseline skeleton — registry.take(new ResourceIdBlock(blockFound)) + unreserve() in the
        // foundations commit.
        return false;
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
