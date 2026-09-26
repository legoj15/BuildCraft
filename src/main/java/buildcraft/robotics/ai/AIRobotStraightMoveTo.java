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
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.NBTUtilBC;

/** Flies the robot in a straight line to an exact position — the final approach of {@link AIRobotGotoStation}.
 *  It never re-plans; once the distance stops shrinking the move is over, so the robot is placed exactly on
 *  the destination rather than eased forever.
 *
 *  <p>Two deliberate departures from 7.1.x, both pinned by {@code AIRobotStraightMoveToTest}:
 *  <ul>
 *  <li>The target is held in {@code double}s, not 7.1.x's {@code float}s. Past ~8.4M blocks from the origin
 *      float's ulp reaches 1.0, so a station's half-block face offset was silently rounded away and the robot
 *      aimed at the wrong point (the parent {@link AIRobotGoto} had already been widened for the same
 *      reason). Legacy float tags still load: {@code getDouble} widens any numeric tag.</li>
 *  <li>A move reloaded from a save re-aims on its first update. {@code start()} never re-runs for a saved
 *      delegate, so 7.1.x resumed measuring its distance to the never-set destination {@code (0,0,0)} and
 *      flew on past the station for as long as that distance kept shrinking.</li>
 *  </ul> */
public class AIRobotStraightMoveTo extends AIRobotGoto {

    private double prevDistance = Double.MAX_VALUE;

    private double x, y, z;

    /** Set by {@link #loadSelfFromNBT}: the destination/velocity {@code start()} would have set are gone. */
    private boolean resumeAfterLoad;

    public AIRobotStraightMoveTo(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotStraightMoveTo(IRobotAccess iRobot, double ix, double iy, double iz) {
        this(iRobot);
        x = ix;
        y = iy;
        z = iz;
        robot.aimItemAt(BlockPos.containing(x, y, z));
    }

    @Override
    public void start() {
        robot.undock();
        setDestination(robot, x, y, z);
    }

    @Override
    public void update() {
        if (resumeAfterLoad) {
            resumeAfterLoad = false;
            setDestination(robot, x, y, z);
        }

        double distance = robot.getDistance(nextX, nextY, nextZ);

        if (distance < prevDistance) {
            prevDistance = distance;
        } else {
            // Arrived: stop moving. 7.1.x snapped the position here; modern IRobotAccess has no setPos, but the
            // robot's velocity is now zero so the entity tick parks it where it is, and the docking snap (for a
            // station) puts it exactly on the mount face once the caller docks.
            robot.setDeltaMovement(Vec3.ZERO);
            terminate();
        }
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        nbt.putDouble("x", x);
        nbt.putDouble("y", y);
        nbt.putDouble("z", z);
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        if (nbt.contains("x")) {
            x = NBTUtilBC.getDouble(nbt, "x", 0);
            y = NBTUtilBC.getDouble(nbt, "y", 0);
            z = NBTUtilBC.getDouble(nbt, "z", 0);
            resumeAfterLoad = true;
        }
    }
}