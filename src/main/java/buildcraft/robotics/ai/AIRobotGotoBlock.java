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

    private PathFinding pathSearch;
    private LinkedList<BlockPos> path;
    private double prevDistance = Double.MAX_VALUE;
    private float finalX, finalY, finalZ;
    private double maxDistance = 0;
    private BlockPos lastBlockInPath;
    private boolean loadedFromNBT;

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
        finalX = path.getLast().getX();
        finalY = path.getLast().getY();
        finalZ = path.getLast().getZ();
        setNextInPath();
    }

    @Override
    public void start() {
        robot.undock();
        // Already at the target block? PathFinding(start == end) would return the degenerate out-and-back, so
        // succeed immediately rather than fly a pointless loop.
        BlockPos here = robot.blockPosition();
        if (here.getX() == (int) Math.floor(finalX) && here.getY() == (int) Math.floor(finalY)
                && here.getZ() == (int) Math.floor(finalZ)) {
            setSuccess(true);
            terminate();
        }
    }

    @Override
    public void update() {
        if (loadedFromNBT) {
            // Prevent a race condition with terminate() being called in setNextInPath.
            setNextInPath();
            loadedFromNBT = false;
        }

        if (path == null && pathSearch == null) {
            BlockPos start = new BlockPos((int) Math.floor(robot.position().x),
                    (int) Math.floor(robot.position().y), (int) Math.floor(robot.position().z));
            BlockPos end = new BlockPos((int) Math.floor(finalX), (int) Math.floor(finalY),
                    (int) Math.floor(finalZ));
            // A robot already on the target (or where a 0 distance is reported) short-circuits above in start();
            // here we only reach this point when there is genuinely somewhere to go.
            pathSearch = new PathFinding(SoftBlockAccess.of(robot.level()), start, end, maxDistance, 96);
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
            // pathSearch != null, path == null — the search is running.
            pathSearch.iterate(50);

            if (pathSearch.isDone()) {
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
            robot.setDeltaMovement(Vec3.ZERO);

            if (lastBlockInPath != null) {
                // Snap to the last path cell. IRobotAccess has no setPos; the destination cell is the one the
                // velocity was aimed at, and once motion stops the entity tick parks the robot there.
                robot.aimItemAt(lastBlockInPath);
            }
            setSuccess(true);
            terminate();
        }
    }

    private void setNextInPath() {
        if (path.size() > 0) {
            boolean isFirst = prevDistance == Double.MAX_VALUE;

            BlockPos next = path.getFirst();
            prevDistance = Double.MAX_VALUE;

            if (isFirst || SoftBlockAccess.of(robot.level()).isSoft(next)) {
                setDestination(robot, next.getX() + 0.5F, next.getY() + 0.5F, next.getZ() + 0.5F);
                robot.aimItemAt(next);
            } else {
                // Path invalid! (a chunk reshaped, a wall moved in the way) — give up and stop.
                path = null;

                if (pathSearch != null) {
                    robot.setDeltaMovement(Vec3.ZERO);
                }
            }
        }
    }

    @Override
    public void end() {
        if (pathSearch != null) {
            robot.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        nbt.putFloat("finalX", finalX);
        nbt.putFloat("finalY", finalY);
        nbt.putFloat("finalZ", finalZ);
        nbt.putDouble("maxDistance", maxDistance);

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

        finalX = NBTUtilBC.getFloat(nbt, "finalX", 0);
        finalY = NBTUtilBC.getFloat(nbt, "finalY", 0);
        finalZ = NBTUtilBC.getFloat(nbt, "finalZ", 0);
        maxDistance = NBTUtilBC.getDouble(nbt, "maxDistance", 0);

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
        }

        loadedFromNBT = true;
    }
}
