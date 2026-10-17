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
    /** Whether this AI holds the registry reservation on {@link #blockFound} in this session. Not saved: the unload
     *  that saved a mid-flight AI freed the reservation, so a reloaded flight has to claim the block again
     *  ({@link #preempt}) before it may land on it. */
    private boolean taken;
    private AIRobotSearchBlock search;

    private IBlockFilter filter;
    private boolean random;
    private double maxDistanceToEnd;

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot, boolean iRandom, IBlockFilter iFilter) {
        // 7.1.x's board-facing default is 0, not a radius: with maxDistanceToEnd == 0 the search's
        // PathFinding only ends ON the target cell, so the reported path is the real approach path (minus
        // the target cell, which AIRobotSearchBlock drops). Any positive radius ends the pathfind at the
        // first soft cell in range — a two-cell stub — and the goto leg then flies the robot nowhere.
        this(iRobot, iRandom, iFilter, 0);
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
        if (filter == null) {
            // Loaded from NBT with no flight left to resume (the filter is not saved, so a fresh search cannot be
            // run either): report the block if the reload kept it reserved, otherwise fail and let the board
            // search again with its own filter.
            if (!taken) {
                blockFound = null;
            }
            terminate();
            return;
        }
        startDelegateAI(new AIRobotSearchBlock(robot, random, filter, maxDistanceToEnd));
    }

    /** A flight reloaded from NBT remembers its block but no longer holds it. Re-claim it before the resumed goto
     *  leg moves; if another robot took it meanwhile, drop it and the leg (then {@link #update()} fails the AI),
     *  instead of landing on a block someone else is working. Not 7.1.x, which flew on and worked it unreserved. */
    @Override
    public void preempt(AIRobot ai) {
        if (blockFound != null && !taken) {
            if (robot.getRegistry().reclaim(new ResourceIdBlock(blockFound), robot.getRobotId())) {
                taken = true;
            } else {
                blockFound = null;
                abortDelegateAI();
            }
        }
    }

    /** Reserves the found block (the search's {@code takeResource()}); the caller releases it via the
     *  registry when done. */
    public boolean takeResource() {
        return search != null && (taken = search.takeResource());
    }

    /** Whether the reported block is reserved for this robot by this AI in this session - what a board may rely on
     *  when it takes the block over. */
    public boolean holdsReservation() {
        return blockFound != null && taken;
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

    /** A delegate that is aborted (rather than ending on its own) means the composition is dead - it is how this
     *  AI's own {@code abort()} reaches its running leg - so the block reserved for the flight goes back to the
     *  registry. Without this only a goto that FAILED released it, and an AI aborted mid-flight left the block
     *  untargetable until the robot's next sleep or recharge. (A robot that unloads or dies never reaches here;
     *  the registry frees the reservation itself.) */
    @Override
    public void delegateAIAborted(AIRobot ai) {
        releaseBlockFound();
    }

    private void releaseBlockFound() {
        if (blockFound != null) {
            robot.getRegistry().release(new ResourceIdBlock(blockFound), robot.getRobotId());
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
