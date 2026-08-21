/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.path.IBlockFilter;

/** Composes {@link AIRobotSearchBlock} (find) and {@link AIRobotGotoBlock} (fly the found path), reporting
 *  the target through {@link #getBlockFound()} and the registry reservation through {@link #takeResource()}.
 *  Ported from 7.1.x {@code AIRobotSearchAndGotoBlock}.
 *
 *  <p>Like the search it wraps, the composition starts on the first {@link #update()} cycle rather than in
 *  {@code start()} — the AI is cycled, not started, by the game tests. Once the search finds a block the
 *  reservation is taken (via the search's {@code takeResource()}), the found block latches into
 *  {@link #blockFound} (so {@link #success()} stays true while the goto leg flies), and the goto leg is
 *  started; a failed search, a lost reservation, or a failed goto unwinds the whole AI, releasing the
 *  reservation on the way down. */
public class AIRobotSearchAndGotoBlock extends AIRobot {

    private BlockPos blockFound;
    private boolean taken;
    private AIRobotSearchBlock search;

    private IBlockFilter filter;
    private boolean random;
    private double maxDistanceToEnd;

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot, boolean iRandom, IBlockFilter iFilter) {
        this(iRobot, iRandom, iFilter, 64);
    }

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot, boolean iRandom, IBlockFilter iFilter,
            double iMaxDistanceToEnd) {
        super(iRobot);

        filter = iFilter;
        random = iRandom;
        maxDistanceToEnd = iMaxDistanceToEnd;
    }

    public BlockPos getBlockFound() {
        return blockFound;
    }

    @Override
    public void update() {
        startDelegateAI(new AIRobotSearchBlock(robot, random, filter, maxDistanceToEnd));
    }

    /** Reserves the found block (the search's {@code takeResource()}); the caller releases it via the
     *  registry when done. */
    public boolean takeResource() {
        return search != null && (taken = search.takeResource());
    }

    @Override
    public boolean success() {
        return blockFound != null;
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchBlock searchAI) {
            search = searchAI;
            if (searchAI.success() && takeResource()) {
                blockFound = searchAI.blockFound;
                if (searchAI.path != null && searchAI.path.size() > 0) {
                    startDelegateAI(new AIRobotGotoBlock(robot, searchAI.path));
                } else {
                    // The path was the target cell alone — the robot already stands beside it.
                    terminate();
                }
            } else {
                terminate();
            }
        } else if (ai instanceof AIRobotGotoBlock gotoAI) {
            if (!gotoAI.success()) {
                releaseBlockFound();
            }
            terminate();
        } else {
            terminate();
        }
    }

    private void releaseBlockFound() {
        if (blockFound != null) {
            robot.getRegistry().release(new ResourceIdBlock(blockFound));
            blockFound = null;
            taken = false;
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
