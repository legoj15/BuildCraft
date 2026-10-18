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

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import buildcraft.api.core.IZone;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.BlockClaims;
import buildcraft.robotics.RobotUtils;
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
    @Nullable
    private IBlockFilter approveFound;
    private Iterator<BlockPos> blockIter;
    private double maxDistanceToEnd;
    private IZone zone;
    private int rounds;

    /** How many {@code iterate()} rounds a search gets before it gives up with nothing found. 7.1.x ran every search
     *  on an {@code IterableAlgorithmRunner}, whose default bound was 1000 rounds, and the synchronous port runs one
     *  round per update. The random scanners never run out, so for them this bound is what ends a search with no
     *  candidate (and sends an idle planter or stripes robot to sleep). The expanding cube does run out, but each
     *  candidate it finds and cannot reach (a crop under glass) holds a path finder for up to
     *  {@link PathFindingSearch#FINDER_EXPANSION_BUDGET} expansions, so a cube full of them is bounded only by this.
     *  The robot remembers the candidates it gave up on ({@link IRobotAccess#unreachableBlockDetected}), so the next
     *  search skips them and gets further instead of spending the same rounds on the same nearest ones. */
    static final int SEARCH_ROUNDS = 1000;
    /** {@link #SEARCH_ROUNDS}; package-private so tests can shrink it. */
    int maxRounds = SEARCH_ROUNDS;
    /** The block-traversability oracle; null (production) reads the robot's level. Package-private so tests can
     *  search an in-memory world. */
    SoftBlockAccess blockAccess;

    public AIRobotSearchBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchBlock(IRobotAccess iRobot, boolean random, IBlockFilter iPathFound,
            double iMaxDistanceToEnd) {
        this(iRobot, random, iPathFound, null, iMaxDistanceToEnd);
    }

    /** @param iApproveFound asked once about the block the search settles on (see
     *            {@link PathFindingSearch#PathFindingSearch}), for checks too costly to run on every scanned block;
     *            null approves any */
    public AIRobotSearchBlock(IRobotAccess iRobot, boolean random, IBlockFilter iPathFound,
            @Nullable IBlockFilter iApproveFound, double iMaxDistanceToEnd) {
        super(iRobot);

        pathFound = iPathFound;
        approveFound = iApproveFound;
        zone = iRobot.getZoneToWork();
        // Scanner selection, as 7.1.x: the expanding cube is the default; random gets the zone's own
        // distribution when the robot has a work zone, else a uniform sphere of radius 64.
        Level level = iRobot.level();
        if (!random) {
            blockIter = new BlockScannerExpanding();
        } else if (zone != null && level != null) {
            blockIter = new BlockScannerZoneRandom(iRobot.blockPosition(), level.getRandom(), zone,
                    RobotUtils.minBuildY(level), RobotUtils.maxBuildY(level));
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
        // Ph5 cost table: 2 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 200_000;
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
            SoftBlockAccess access = blockAccess != null ? blockAccess : SoftBlockAccess.of(robot.level());
            // A candidate a recent search of this robot's gave up on, from about where it stands now, is not a
            // candidate (asked last, once the filter has matched, so the memory is read for matching blocks only).
            // Not 7.1.x, which re-flooded the same unreachable candidates from the centre outwards in every search.
            IBlockFilter reachableCandidates = pos -> pathFound.matches(pos) && !robot.isKnownUnreachableBlock(pos);
            blockScanner = new PathFindingSearch(access, robot.blockPosition(),
                    blockIter, reachableCandidates, maxDistanceToEnd, 96, zone,
                    // Owner-tagged cells: the registry frees this robot's cells itself if it unloads or dies
                    // between the search locking onto a cell and this AI collecting it (takeResource). A cell any
                    // robot holds, as a search cell or as a job, is skipped.
                    BlockClaims.searchCells(robot.getRegistry(), robot.getRobotId()), approveFound)
                    .onUnreachable(robot::unreachableBlockDetected);
        }
        if (blockScanner.isDone() || rounds >= maxRounds) {
            // Out of rounds, a path found is still taken (getResult reports the finished path finder); otherwise the
            // search ends with nothing found, as 7.1.x's runner did.
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
            rounds++;
        }
    }

    /** Reserves the found block in the registry (7.1.x: registry take of a ResourceIdBlock) and drops the
     *  search's own reservation - the hand-over from the search-cell ledger to the job ledger, in
     *  {@link BlockClaims#claim}. */
    public boolean takeResource() {
        if (blockFound == null) {
            return false;
        }
        return BlockClaims.claim(robot.getRegistry(), blockFound, robot.getRobotId());
    }

    public void unreserve() {
        if (blockScanner != null && blockFound != null) {
            blockScanner.unreserve(blockFound);
        }
    }

    /** A search that locks onto a cell reserves it in {@code iterate()}, but only reports it (and lets the caller
     *  {@link #takeResource()} it) on the NEXT cycle's {@code update()}. An abort in between would leave the
     *  cell reserved for a search that no longer exists. A search that finished normally has set
     *  {@link #blockFound} by now and its caller takes over the reservation, so only the uncollected one is
     *  given back. (A robot that unloads or dies in the window never reaches here; the registry frees its cells
     *  itself.) */
    @Override
    public void end() {
        if (blockScanner != null && blockFound == null) {
            BlockPos locked = blockScanner.getResultTarget();
            if (locked != null) {
                blockScanner.unreserve(locked);
            }
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
