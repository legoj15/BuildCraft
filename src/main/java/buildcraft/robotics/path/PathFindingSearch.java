/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;

/**
 * Drives several {@link PathFinding} instances in parallel to locate the nearest block matching a
 * filter, ported from 7.1.x {@code buildcraft.core.lib.utils.PathFindingSearch}.
 *
 * <p>Two couplings to a live {@code World} were lifted out for testability and correctness:
 * <ul>
 * <li>block traversability / chunk-loaded checks go through the injectable {@link SoftBlockAccess};</li>
 * <li>the 7.1.x {@code static HashMap<dimensionId, HashSet<BlockIndex>>} reservation table is replaced
 *     by a caller-owned {@link Set} of reserved positions, shared across the searches that should not
 *     collide (in production, an owner-tagged view of the dimension's one set, so the registry can free a
 *     cell whose robot unloads or dies before the search is collected; in tests, a fresh set). All access
 *     is synchronised on that set, preserving the original's thread-safety contract.</li>
 * </ul>
 * The 7.1.x {@code (start.y + delta.y) > 0 ? … : 0} clamp is dropped — bounds are now the access
 * predicate's responsibility, which is correct on modern negative-Y worlds.
 */
public class PathFindingSearch implements IIterableAlgorithm {

    public static final int PATH_ITERATIONS = 1000;

    /** A search holds at most this many concurrent path finders; once reached, scanning pauses. */
    public static final int MAX_TARGETS = 5;

    private final SoftBlockAccess access;
    private final BlockPos start;
    private final List<PathFinding> pathFinders;
    private final IBlockFilter pathFound;
    private final IZone zone;
    private final float maxDistance;
    private final Iterator<BlockPos> blockIter;
    private final double maxDistanceToEnd;
    private final Set<BlockPos> reservations;
    @Nullable
    private final IBlockFilter approveTarget;

    public PathFindingSearch(SoftBlockAccess access, BlockPos start, Iterator<BlockPos> blockIter,
            IBlockFilter pathFound, double maxDistanceToEnd, float maxDistance, IZone zone,
            Set<BlockPos> reservations) {
        this(access, start, blockIter, pathFound, maxDistanceToEnd, maxDistance, zone, reservations, null);
    }

    /**
     * @param approveTarget asked ONCE about each target a path was found to - not about every block the scan
     *            visits, which {@code pathFound} is - before that target is reserved; a target it refuses is dropped
     *            and the search carries on with the others. For checks that are too costly to run per scanned block,
     *            such as asking protection mods. It is given the cell the path ends on, which is the target itself
     *            when {@code maxDistanceToEnd} is 0. Null approves everything.
     */
    public PathFindingSearch(SoftBlockAccess access, BlockPos start, Iterator<BlockPos> blockIter,
            IBlockFilter pathFound, double maxDistanceToEnd, float maxDistance, IZone zone,
            Set<BlockPos> reservations, @Nullable IBlockFilter approveTarget) {
        this.approveTarget = approveTarget;
        this.access = access;
        this.start = start;
        this.pathFound = pathFound;
        this.maxDistance = maxDistance;
        this.maxDistanceToEnd = maxDistanceToEnd;
        this.zone = zone;
        this.blockIter = blockIter;
        this.reservations = reservations;

        this.pathFinders = new LinkedList<>();
    }

    @Override
    public void iterate() {
        if (pathFinders.size() < MAX_TARGETS && blockIter.hasNext()) {
            iterateSearch(PATH_ITERATIONS * 10);
        }
        iteratePathFind(PATH_ITERATIONS);
    }

    private void iterateSearch(int itNumber) {
        for (int i = 0; i < itNumber; ++i) {
            if (!blockIter.hasNext()) {
                return;
            }

            BlockPos delta = blockIter.next();
            BlockPos block = new BlockPos(start.getX() + delta.getX(), start.getY() + delta.getY(),
                    start.getZ() + delta.getZ());
            if (access.isChunkLoaded(block)) {
                if (isTarget(block)) {
                    pathFinders.add(new PathFinding(access, start, block, maxDistanceToEnd, maxDistance));
                }
            }

            if (pathFinders.size() >= MAX_TARGETS) {
                return;
            }
        }
    }

    private boolean isTarget(BlockPos block) {
        if (zone != null && !zone.contains(new Vec3(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5))) {
            return false;
        }
        if (!pathFound.matches(block)) {
            return false;
        }
        synchronized (reservations) {
            if (reservations.contains(block)) {
                return false;
            }
        }
        // The target block itself is solid (that is what makes it a target); it is only reachable if at
        // least one orthogonally adjacent cell is soft enough for the robot to stand in.
        if (!access.isSoft(block.west())
                && !access.isSoft(block.east())
                && !access.isSoft(block.north())
                && !access.isSoft(block.south())
                && !access.isSoft(block.below())
                && !access.isSoft(block.above())) {
            return false;
        }
        return true;
    }

    public void iteratePathFind(int itNumber) {
        for (PathFinding pathFinding : new ArrayList<>(pathFinders)) {
            pathFinding.iterate(itNumber / pathFinders.size());
            if (pathFinding.isDone()) {
                LinkedList<BlockPos> path = pathFinding.getResult();
                if (path != null && path.size() > 0) {
                    BlockPos end = pathFinding.end();
                    // The approver sits between "a path exists" and "reserve": it is asked about a target only once
                    // it is actually chosen (and not when another search already holds it), and a refused target
                    // never holds a reservation.
                    if (!isReserved(end) && approves(end) && reserve(end)) {
                        return;
                    }
                }
                pathFinders.remove(pathFinding);
            }
        }
    }

    @Override
    public boolean isDone() {
        for (PathFinding pathFinding : pathFinders) {
            if (pathFinding.isDone()) {
                return true;
            }
        }
        return !blockIter.hasNext();
    }

    public LinkedList<BlockPos> getResult() {
        for (PathFinding pathFinding : pathFinders) {
            if (pathFinding.isDone()) {
                return pathFinding.getResult();
            }
        }
        return new LinkedList<>();
    }

    public BlockPos getResultTarget() {
        for (PathFinding pathFinding : pathFinders) {
            if (pathFinding.isDone()) {
                return pathFinding.end();
            }
        }
        return null;
    }

    private boolean approves(BlockPos block) {
        return approveTarget == null || approveTarget.matches(block);
    }

    private boolean isReserved(BlockPos block) {
        synchronized (reservations) {
            return reservations.contains(block);
        }
    }

    private boolean reserve(BlockPos block) {
        synchronized (reservations) {
            // add() is the test-and-set: false when the cell is already reserved (by anyone). Relying on its
            // result rather than contains()-then-add() keeps it atomic for an owner-tagged view, whose
            // monitor is its own, not this set's.
            return reservations.add(block);
        }
    }

    public void unreserve(BlockPos block) {
        synchronized (reservations) {
            reservations.remove(block);
        }
    }
}
