/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import java.util.LinkedList;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.robotics.path.PathFinding;
import buildcraft.robotics.path.SoftBlockAccess;

/** Flies the robot along an A* path to a target block. Ported from 7.1.x {@code buildcraft.robotics.ai.
 *  AIRobotGotoBlock}: the threaded {@code IterableAlgorithmRunner} is gone — main's {@link PathFinding} is a
 *  synchronous {@code IIterableAlgorithm}, so each {@code update()} calls {@code iterate(50)} to spread the
 *  same per-tick budget across frames with no runner class (D3). Must short-circuit "already there": with
 *  {@code start == end} {@code PathFinding} yields the degenerate 3-cell out-and-back, so a robot already on
 *  its target succeeds without pathfinding. */
public class AIRobotGotoBlock extends AIRobotGoto {

    /** The flight's reach in blocks: 7.1.x's {@code maxTotalDistance} argument to {@code PathFinding}, verbatim.
     *  The finder rejects every cell whose path-so-far plus squared distance to the target exceeds this squared,
     *  so a target farther than this from the robot's block fails at once without the robot moving. */
    public static final int FLIGHT_REACH = 96;

    private PathFinding pathSearch;
    private LinkedList<BlockPos> path;
    private double prevDistance = Double.MAX_VALUE;
    private double finalX, finalY, finalZ;
    private double maxDistance = 0;
    private BlockPos lastBlockInPath;
    /** The cell of the CURRENT plan the robot was last sent to: where it stands when it stops short of a hard target.
     *  Null until a step of that plan has been taken (a new or reloaded flight, or one whose plan a re-plan dropped):
     *  {@link #endAtHardTarget} then falls back to the cell the robot stands in, never a cell of a dropped plan. */
    private BlockPos currentStep;
    /** Whether ending beside a target cell the robot cannot enter counts as arriving ({@link #stopBesideHardTarget}).
     *  Saved with the flight, so a reloaded one ends as the uninterrupted one would. */
    private boolean stopBesideHardTarget;
    /** Set when the flight ends refused by a hard target it may not stop beside: the empty-path end then fails. */
    private boolean hardTargetRefused;
    /** Set when the flight ended beside a hard target as an arrival ({@link #stoppedBesideTarget}). */
    private boolean stoppedBeside;
    /** True while the flight holds a path whose first step has not been taken yet: a stored path handed to the
     *  constructor (taken in start()) or one read back from a save (never started, so taken on its first update()). */
    private boolean firstStepPending;

    /** Hard cap on total search expansions — 7.1.x ran this search on a background thread bounded to
     *  {@code new IterableAlgorithmRunner(pathSearch, 50)} rounds of {@link PathFinding#PATH_ITERATIONS}
     *  each; the synchronous port spreads those same expansions across ticks and must keep the same total
     *  bound, or a robot asked to cross an inexhaustible search space searches 50 expansions a tick
     *  forever. Package-private so tests can shrink it. */
    int maxTotalExpansions = 50 * PathFinding.PATH_ITERATIONS;
    private int expansions;
    /** The block-traversability oracle; null (production) reads the robot's level. Package-private so tests can
     *  fly the robot over an in-memory grid. */
    SoftBlockAccess blockAccess;

    /** Whether a flight from {@code from} (the robot's block) to {@code to} is inside {@link #FLIGHT_REACH}.
     *  Straight-line distance: sufficient in open air (each step toward the target lowers the finder's
     *  weight), so a board that checks this before sending a robot never picks a target the flight rejects
     *  outright; detours around obstacles can still run out of reach, which the flight reports as a failure. */
    public static boolean withinFlightReach(BlockPos from, BlockPos to) {
        return from.distSqr(to) <= (double) FLIGHT_REACH * FLIGHT_REACH;
    }

    public AIRobotGotoBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotGotoBlock(IRobotAccess robot, int x, int y, int z) {
        this(robot);
        finalX = x;
        finalY = y;
        finalZ = z;
    }

    public AIRobotGotoBlock(IRobotAccess robot, int x, int y, int z, double iMaxDistance) {
        this(robot, x, y, z);

        maxDistance = iMaxDistance;
    }

    public AIRobotGotoBlock(IRobotAccess robot, LinkedList<BlockPos> iPath) {
        this(robot);
        path = iPath;
        lastBlockInPath = path.getLast();
        finalX = lastBlockInPath.getX();
        finalY = lastBlockInPath.getY();
        finalZ = lastBlockInPath.getZ();
        firstStepPending = true;
    }

    /** Lets this flight end beside its target when the target cell is one the robot cannot enter (an item lying on a
     *  pipe, a mob on a slab), as an arrival. For callers whose work reaches a cell away, the Picker's pickup and the
     *  attack's swing; every other flight to such a cell fails instead, since it needs to be IN the cell. */
    public AIRobotGotoBlock stopBesideHardTarget() {
        stopBesideHardTarget = true;
        return this;
    }

    /** Whether this flight's success was ending beside a target cell it could not enter rather than in it. */
    public boolean stoppedBesideTarget() {
        return stoppedBeside;
    }

    @Override
    public void start() {
        robot.undock();
        // Already at the target block? PathFinding(start == end) would return the degenerate out-and-back, so
        // succeed immediately rather than fly a pointless loop.
        if (isTarget(robot.blockPosition())) {
            setSuccess(true);
            terminate();
            return;
        }
        // A stored path's first step is taken here, not in the constructor, so it goes through the same rule as every
        // later step with the caller's opt-in (stopBesideHardTarget) already set. The step may end the flight at a hard
        // target, which empties the path, or drop the path to re-plan, which update() carries out on the next cycle.
        // (The game never starts a flight read back from a save, which takes this step on its first update(); one
        // that is started anyway takes it here.)
        if (firstStepPending) {
            firstStepPending = false;
            setNextInPath();
            if (path != null && path.isEmpty()) {
                finishFlight();
            }
        }
    }

    @Override
    public void update() {
        if (firstStepPending) {
            // A flight read back from a save is never started, only cycled: its first step is taken here.
            setNextInPath();
            firstStepPending = false;
        }

        if (path == null && pathSearch == null) {
            BlockPos start = new BlockPos((int) Math.floor(robot.position().x),
                    (int) Math.floor(robot.position().y), (int) Math.floor(robot.position().z));
            BlockPos end = new BlockPos((int) Math.floor(finalX), (int) Math.floor(finalY),
                    (int) Math.floor(finalZ));
            // A robot already on the target (or where a 0 distance is reported) short-circuits above in start();
            // here we only reach this point when there is genuinely somewhere to go. (A flight read back from a
            // save is not started again, but it was started before it was saved, with the robot off its target.)
            pathSearch = new PathFinding(blocks(), start, end, maxDistance, FLIGHT_REACH);
        } else if (path != null) {
            double distance = robot.getDistance(nextX, nextY, nextZ);

            if (!robot.isMoving() || distance > prevDistance) {
                if (path.size() > 0) {
                    path.removeFirst();
                }

                setNextInPath();
            } else {
                prevDistance = robot.getDistance(nextX, nextY, nextZ);
            }
        } else {
            // pathSearch != null, path == null — the search is running. The budget check comes FIRST: an
            // over-budget search must not expand even once more before giving up.
            if (expansions < maxTotalExpansions) {
                pathSearch.iterate(50);
                expansions += 50;
            }

            if (pathSearch.isDone() || expansions >= maxTotalExpansions) {
                path = pathSearch.getResult();

                if (path.size() == 0) {
                    setSuccess(false);
                    terminate();
                    return;
                }

                lastBlockInPath = path.getLast();

                setNextInPath();
            }
        }

        if (path != null && path.size() == 0) {
            finishFlight();
        }
    }

    /** Ends a flight whose path is used up: a failure if a hard target refused it, else an arrival on the centre of the
     *  last path cell. */
    private void finishFlight() {
        robot.setDeltaMovement(Vec3.ZERO);

        if (hardTargetRefused) {
            setSuccess(false);
            terminate();
            return;
        }

        if (lastBlockInPath != null) {
            // Snap onto the last path cell's centre, as 7.1.x did (posX/Y/Z = cell + 0.5): the last step only
            // ends once the robot has stalled or overshot, which leaves it a few tenths of a block off. Double
            // maths, not 7.1.x's 0.5F, so the half-block survives far from the origin. Every flight has one: a
            // fresh search sets it from its result, a flight handed a stored path (or read back from a save)
            // from that path's last cell. Not 7.1.x, whose stored-path flights ended where they stood.
            robot.setPos(lastBlockInPath.getX() + 0.5, lastBlockInPath.getY() + 0.5,
                    lastBlockInPath.getZ() + 0.5);
        }
        setSuccess(true);
        terminate();
    }

    private void setNextInPath() {
        if (path.size() > 0) {
            BlockPos next = path.getFirst();
            prevDistance = Double.MAX_VALUE;

            if (isHardTarget(next)) {
                // The next step is the target cell itself and the robot cannot enter it (an item lying on a pipe, a
                // mob on a slab, or a cell whose chunk is not loaded). The finder always routes into the exact target
                // whether or not it is soft, so re-planning finds this same route: the robot flew back and forth
                // forever (7.1.x did too). The robot is as near as it gets, so the flight ends here: an arrival on the
                // cell it was last sent to for a caller that asked for it, a failure for the rest. A flight read back
                // from a save with only the target left has sent the robot nowhere yet: it fails, or (opted in) stops
                // on the cell it stands on. The empty-path check in start() or update() then ends the flight.
                endAtHardTarget();
            } else if (next.equals(robot.blockPosition()) || blocks().isSoft(next)) {
                // The cell the robot stands in is always a step it may take: a robot must be able to leave a cell that
                // is not soft, and a found path starts with the cell the robot was in when its search began. Every
                // other cell must be soft: the first cell of a stored or reloaded path, and the start cell of a found
                // path when the robot was moved out of it while the search ran (the flight then re-plans from where
                // the robot is: it was stopped, so unless it is moved again the new path starts on the cell it stands
                // in). 7.1.x waved through any step taken before a distance had been measured, which let a robot be
                // aimed into a cell that had turned hard since the path was found.
                currentStep = next;
                setDestination(robot, next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
                robot.aimItemAt(next);
            } else {
                // Path invalid (a chunk unloaded or reshaped, a wall moved in the way): drop it and re-plan from here.
                // Not 7.1.x: the path list IS pathSearch's result, so keeping pathSearch would make the flight re-read
                // the same rejected list every tick and never re-plan. Dropping the search re-plans with a fresh
                // budget, as a given path already did. The robot stops either way, a flight handed a stored path (no
                // search of its own) included: it would otherwise drift on toward the rejected cell while the new
                // search runs. currentStep goes with the plan, since it names a cell of the dropped one.
                path = null;
                currentStep = null;
                robot.setDeltaMovement(Vec3.ZERO);
                pathSearch = null;
                expansions = 0;
            }
        }
    }

    /** The exact target cell of a flight with no reach slack, when the robot cannot enter it. */
    private boolean isHardTarget(BlockPos pos) {
        return maxDistance == 0 && isTarget(pos) && !blocks().isSoft(pos);
    }

    /** Empties the path so the flight ends at a hard target: refused, or (opted in) arrived on the cell the robot was
     *  last sent to, or the one it stands on. Drops the search too, whose result list the path is. */
    private void endAtHardTarget() {
        path.clear();
        pathSearch = null;
        hardTargetRefused = !stopBesideHardTarget;
        stoppedBeside = stopBesideHardTarget;
        lastBlockInPath = currentStep != null && !isTarget(currentStep) ? currentStep : robot.blockPosition();
    }

    private boolean isTarget(BlockPos pos) {
        return pos.getX() == (int) Math.floor(finalX) && pos.getY() == (int) Math.floor(finalY)
                && pos.getZ() == (int) Math.floor(finalZ);
    }

    private SoftBlockAccess blocks() {
        return blockAccess != null ? blockAccess : SoftBlockAccess.of(robot.level());
    }

    /** Whatever ends the flight, the robot is left still. 7.1.x did this only for a flight that had a search of its
     *  own; one handed a stored path (a search-and-goto's, or one read back from a save) kept the velocity of its last
     *  step when aborted, and whatever ran next that sets none of its own found the robot drifting through walls.
     *  Safe for the flights that end by arriving too (they stopped the robot already) and for whatever follows: a
     *  parent starts its next AI after this runs. */
    @Override
    public void end() {
        robot.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        // Doubles, not 7.1.x's floats: a float is exact only to 2^24, so a target farther out than that came back a
        // cell off. A float tag from an older save still reads (getDouble widens any numeric tag).
        nbt.putDouble("finalX", finalX);
        nbt.putDouble("finalY", finalY);
        nbt.putDouble("finalZ", finalZ);
        nbt.putDouble("maxDistance", maxDistance);
        nbt.putBoolean("stopBesideHardTarget", stopBesideHardTarget);

        if (path != null) {
            long[] asLong = new long[path.size()];
            int i = 0;
            for (BlockPos p : path) {
                asLong[i++] = p.asLong();
            }
            nbt.putLongArray("path", asLong);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        finalX = NBTUtilBC.getDouble(nbt, "finalX", 0);
        finalY = NBTUtilBC.getDouble(nbt, "finalY", 0);
        finalZ = NBTUtilBC.getDouble(nbt, "finalZ", 0);
        maxDistance = NBTUtilBC.getDouble(nbt, "maxDistance", 0);
        stopBesideHardTarget = NBTUtilBC.getBoolean(nbt, "stopBesideHardTarget", false);

        if (nbt.contains("path")) {
            //? if >=1.21.10 {
            long[] asLong = nbt.getLongArray("path").orElse(new long[0]);
            //?} else {
            /*long[] asLong = nbt.getLongArray("path");*/
            //?}
            path = new LinkedList<>();
            for (long l : asLong) {
                path.add(BlockPos.of(l));
            }
            if (!path.isEmpty()) {
                // The arrival snap needs it, and a fresh search is what used to set it: without this a reloaded
                // flight ended off-centre where an uninterrupted one snapped.
                lastBlockInPath = path.getLast();
            }
        }

        // A path is only resumable when one was actually written: a mid-search save has no "path" key, and
        // the first update must fall through to the fresh-search branch below rather than dereference the
        // path that was never restored.
        firstStepPending = path != null;
    }
}
