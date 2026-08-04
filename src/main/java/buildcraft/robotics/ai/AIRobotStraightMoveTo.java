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
 *  the destination rather than eased forever. */
public class AIRobotStraightMoveTo extends AIRobotGoto {

    private double prevDistance = Double.MAX_VALUE;

    private float x, y, z;

    public AIRobotStraightMoveTo(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotStraightMoveTo(IRobotAccess iRobot, float ix, float iy, float iz) {
        this(iRobot);
        x = ix;
        y = iy;
        z = iz;
        robot.aimItemAt(new BlockPos(net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(y),
                net.minecraft.util.Mth.floor(z)));
    }

    @Override
    public void start() {
        robot.undock();
        setDestination(robot, x, y, z);
    }

    @Override
    public void update() {
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

        nbt.putFloat("x", x);
        nbt.putFloat("y", y);
        nbt.putFloat("z", z);
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        if (nbt.contains("x")) {
            x = NBTUtilBC.getFloat(nbt, "x", 0);
            y = NBTUtilBC.getFloat(nbt, "y", 0);
            z = NBTUtilBC.getFloat(nbt, "z", 0);
        }
    }
}
