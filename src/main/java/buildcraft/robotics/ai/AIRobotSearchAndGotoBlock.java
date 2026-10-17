/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.JobClaim;
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

    /** The block found, and whether this AI holds its reservation in this session. The claim is not saved: the unload
     *  that saved a mid-flight AI freed it, so a reloaded flight has to claim the block again ({@link #preempt})
     *  before it may land on it. */
    private final JobClaim<BlockPos> blockFound = JobClaim.block(robot, JobClaim.OnReload.RECLAIM);
    private AIRobotSearchBlock search;

    private IBlockFilter filter;
    @Nullable
    private IBlockFilter approve;
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
        this(iRobot, iRandom, iFilter, null, iMaxDistanceToEnd);
    }

    /** As the three-argument form, with {@code iApprove} asked once about the block the search settles on - for
     *  checks too costly to run on every scanned block, such as asking protection mods. */
    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot, boolean iRandom, IBlockFilter iFilter,
            @Nullable IBlockFilter iApprove) {
        this(iRobot, iRandom, iFilter, iApprove, 0);
    }

    public AIRobotSearchAndGotoBlock(IRobotAccess iRobot, boolean iRandom, IBlockFilter iFilter,
            @Nullable IBlockFilter iApprove, double iMaxDistanceToEnd) {
        super(iRobot);

        filter = iFilter;
        approve = iApprove;
        random = iRandom;
        maxDistanceToEnd = iMaxDistanceToEnd;
    }

    public BlockPos getBlockFound() {
        return blockFound.job();
    }

    @Override
    public void update() {
        if (filter == null) {
            // Loaded from NBT with no flight left to resume (the filter is not saved, so a fresh search cannot be
            // run either): report the block if the reload kept it reserved, otherwise fail and let the board
            // search again with its own filter.
            if (!blockFound.isHolding()) {
                blockFound.adopt(null, false);
            }
            terminate();
            return;
        }
        startDelegateAI(new AIRobotSearchBlock(robot, random, filter, approve, maxDistanceToEnd));
    }

    /** A flight reloaded from NBT remembers its block but no longer holds it. Re-claim it before the resumed goto
     *  leg moves; if another robot took it meanwhile, drop it and the leg (then {@link #update()} fails the AI),
     *  instead of landing on a block someone else is working ({@link JobClaim#preempt}). Not 7.1.x, which flew on
     *  and worked it unreserved. */
    @Override
    public void preempt(AIRobot ai) {
        blockFound.preempt(this, ai);
    }

    /** Reserves the found block (the search's {@code takeResource()}) and takes it as this AI's job; the caller
     *  releases it via the registry when done. */
    public boolean takeResource() {
        if (search == null || !search.takeResource()) {
            return false;
        }
        blockFound.adopt(search.blockFound, true);
        return true;
    }

    /** Whether the reported block is reserved for this robot by this AI in this session - what a board may rely on
     *  when it takes the block over. */
    public boolean holdsReservation() {
        return blockFound.isHolding();
    }

    @Override
    public boolean success() {
        return !blockFound.isEmpty();
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchBlock searchAI) {
            search = searchAI;
            if (searchAI.success() && takeResource()) {
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
        blockFound.release();
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);
        JobClaim.writeBlock(blockFound, nbt, "blockFound");
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);
        JobClaim.readBlock(blockFound, nbt, "blockFound");
    }
}
