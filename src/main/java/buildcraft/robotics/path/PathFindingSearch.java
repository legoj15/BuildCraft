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
import java.util.function.Consumer;

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
 *     by a caller-supplied {@link Reservations} table, shared across the searches that should not collide (in
 *     production, {@code BlockClaims.searchCells}: reads see every robot's search cells and job claims, writes are
 *     the searching robot's own, so the registry can free a cell whose robot unloads or dies before the search is
 *     collected; in tests, a plain shared set). Each table operation is atomic, preserving the original's
 *     thread-safety contract.</li>
 * </ul>
 * The 7.1.x {@code (start.y + delta.y) > 0 ? … : 0} clamp is dropped — bounds are now the access
 * predicate's responsibility, which is correct on modern negative-Y worlds.
 */
public class PathFindingSearch implements IIterableAlgorithm {

    public static final int PATH_ITERATIONS = 1000;

    /** The wall-clock slice the SCAN phase of one {@link #iterate()} round may spend: a few milliseconds, so
     *  the candidate scanning a robot does on the server thread each tick stays a bounded cost however
     *  expensive an individual scan step turns out to be (a step asks the chunk-loaded gate and the world
     *  about up to seven cells). This REPLACES the fixed 10 000-steps-per-round scan cap: a count of steps
     *  was a budget only by assumption — on a world whose reads cost real time the count said nothing about
     *  the tick. The slice is the round's FIRST half, and the path finders' expansion shares below are
     *  guaranteed their rounds regardless: the scanners do not exhaust (the expanding cube holds two million
     *  cells, the random ones are endless), so a scan allowed the whole budget could spend every round
     *  scanning and the finders would never run. Rounds are per robot per tick; the tick's total scanning
     *  cost is this budget times the searching robots. */
    public static long TICK_BUDGET_NANOS = 2_000_000L;

    /** A search holds at most this many concurrent path finders; once reached, scanning pauses. */
    public static final int MAX_TARGETS = 5;

    /** How many nodes one path finder may expand before its target is given up as unreachable. Not 7.1.x, whose
     *  finders had no bound of their own: a candidate the robot cannot get to (a crop under glass, a log in a sealed
     *  room) passes the scan's soft-neighbour check, and its finder then floods everything in reach - a 96-block
     *  sphere, millions of cells - before it fails. The figure is {@code AIRobotGotoBlock}'s flight budget, 7.1.x's
     *  {@code IterableAlgorithmRunner(pathSearch, 50)} rounds of {@link PathFinding#PATH_ITERATIONS}: a target a
     *  flight would give up on is not worth more to the search that picks it. */
    public static final int FINDER_EXPANSION_BUDGET = 50 * PathFinding.PATH_ITERATIONS;

    private final SoftBlockAccess access;
    private final BlockPos start;
    private final List<PathFinding> pathFinders;
    private final IBlockFilter pathFound;
    private final IZone zone;
    private final float maxDistance;
    private final Iterator<BlockPos> blockIter;
    private final double maxDistanceToEnd;
    private final Reservations reservations;
    @Nullable
    private final IBlockFilter approveTarget;
    /** Told of each candidate whose path finder ended with no path; see {@link #onUnreachable}. */
    private Consumer<BlockPos> unreachableListener = pos -> {};

    /** The table concurrent searches share so that no two converge on one target: who holds a cell is read across
     *  every searcher, and a cell is reserved and freed for this one. */
    public interface Reservations {
        /** Whether anyone holds {@code pos}. */
        boolean isReserved(BlockPos pos);

        /** The test-and-set: reserves {@code pos} for this searcher, false when anyone holds it already. */
        boolean reserve(BlockPos pos);

        /** Frees {@code pos} if this searcher holds it. */
        void unreserve(BlockPos pos);

        /** A plain shared set, every searcher reading and writing the same cells (synchronised on the set). */
        static Reservations of(Set<BlockPos> shared) {
            return new Reservations() {
                @Override
                public boolean isReserved(BlockPos pos) {
                    synchronized (shared) {
                        return shared.contains(pos);
                    }
                }

                @Override
                public boolean reserve(BlockPos pos) {
                    synchronized (shared) {
                        return shared.add(pos);
                    }
                }

                @Override
                public void unreserve(BlockPos pos) {
                    synchronized (shared) {
                        shared.remove(pos);
                    }
                }
            };
        }
    }

    public PathFindingSearch(SoftBlockAccess access, BlockPos start, Iterator<BlockPos> blockIter,
            IBlockFilter pathFound, double maxDistanceToEnd, float maxDistance, IZone zone,
            Set<BlockPos> reservations) {
        this(access, start, blockIter, pathFound, maxDistanceToEnd, maxDistance, zone, reservations, null);
    }

    /** As the {@link Reservations} form, over a plain set every searcher shares. */
    public PathFindingSearch(SoftBlockAccess access, BlockPos start, Iterator<BlockPos> blockIter,
            IBlockFilter pathFound, double maxDistanceToEnd, float maxDistance, IZone zone,
            Set<BlockPos> reservations, @Nullable IBlockFilter approveTarget) {
        this(access, start, blockIter, pathFound, maxDistanceToEnd, maxDistance, zone, Reservations.of(reservations),
                approveTarget);
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
            Reservations reservations, @Nullable IBlockFilter approveTarget) {
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

    /** Asks to be told of every candidate this search gave up on because its path finder ended with no path (out of
     *  budget, or the space the robot can reach holds no way to it) - not of one that was reserved by another search
     *  or refused by the approver, which is reachable. Lets the caller remember them so the next search skips them. */
    public PathFindingSearch onUnreachable(Consumer<BlockPos> listener) {
        this.unreachableListener = listener;
        return this;
    }

    @Override
    public void iterate() {
        long start = System.nanoTime();
        if (pathFinders.size() < MAX_TARGETS && blockIter.hasNext()) {
            // The scan gets the round's FIRST half only (see TICK_BUDGET_NANOS): the finders' shares below are
            // count-budgeted and guaranteed to run every round, so the scan may not eat the whole slice.
            iterateSearch(start + TICK_BUDGET_NANOS / 2);
        }
        iteratePathFind(PATH_ITERATIONS);
    }

    /** Scans for new candidate targets until the scan is exhausted, {@link #MAX_TARGETS} finders are running,
     *  or the scan's half of the round's wall-clock budget is spent. The deadline is checked BEFORE each step, so
     *  a caller passing an already-stale deadline gets zero steps — only the round's own caller (which computes
     *  the deadline immediately before the call, leaving a fresh slice) is guaranteed progress. */
    private void iterateSearch(long deadline) {
        while (blockIter.hasNext() && System.nanoTime() < deadline) {
            BlockPos delta = blockIter.next();
            BlockPos block = new BlockPos(start.getX() + delta.getX(), start.getY() + delta.getY(),
                    start.getZ() + delta.getZ());
            if (access.isChunkLoaded(block)) {
                if (isTarget(block)) {
                    PathFinding finder = new PathFinding(access, start, block, maxDistanceToEnd, maxDistance);
                    finder.setExpansionBudget(FINDER_EXPANSION_BUDGET);
                    pathFinders.add(finder);
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
        if (reservations.isReserved(block)) {
            return false;
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

    /** Advances every running path finder, fairly: each is granted an equal slice of the round's
     *  {@code itNumber} expansions, every round, in list order — the wall-clock budget ({@link
     *  #TICK_BUDGET_NANOS}) governs the scan phase, while a finder's per-expansion cost is bounded by the
     *  priority-queue open list (see {@link PathFinding}), so a fixed expansion count is an honest measure of
     *  a share's cost. Withholding later finders' shares when a round runs long was tried and rejected: the
     *  finders earlier in the list would eat every round and the later ones would starve, so which target a
     *  search locks could come to depend on machine speed. */
    public void iteratePathFind(int itNumber) {
        for (PathFinding pathFinding : new ArrayList<>(pathFinders)) {
            // A finder out of budget ends with no path, so the target is dropped below like any unreachable one.
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
                } else {
                    // No path: the candidate cannot be reached from here. Told to the caller, which may remember it
                    // so the next search does not start the same finder (and spend the same rounds) on it again.
                    unreachableListener.accept(pathFinding.end());
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
        // A used-up scan is only the end once every path finder it started has finished too: 7.1.x answered
        // !hasNext() alone, dropping the targets whose paths were still being searched.
        return !blockIter.hasNext() && pathFinders.isEmpty();
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
        return reservations.isReserved(block);
    }

    private boolean reserve(BlockPos block) {
        // reserve() is the test-and-set: false when the cell is already reserved (by anyone). Relying on its result
        // rather than isReserved()-then-reserve() keeps it atomic whatever monitor the table guards itself with.
        return reservations.reserve(block);
    }

    public void unreserve(BlockPos block) {
        reservations.unreserve(block);
    }
}
