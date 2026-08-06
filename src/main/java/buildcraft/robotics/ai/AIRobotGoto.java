/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Abstract movement AI: points the robot at a destination and drives it at a constant 0.1 blocks per tick
 *  along that direction (the direction vector is normalised, then scaled by 1/10 — 7.1.x did exactly this;
 *  the speed does not ease as the robot closes in). Concrete subclasses supply the destination and decide
 *  when the move is done. */
public abstract class AIRobotGoto extends AIRobot {

    protected float nextX, nextY, nextZ;
    protected double dirX, dirY, dirZ;

    public AIRobotGoto(IRobotAccess iRobot) {
        super(iRobot);
    }

    protected void setDestination(IRobotAccess robot, float x, float y, float z) {
        nextX = x;
        nextY = y;
        nextZ = z;

        dirX = nextX - robot.position().x;
        dirY = nextY - robot.position().y;
        dirZ = nextZ - robot.position().z;

        double magnitude = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);

        if (magnitude != 0) {
            dirX /= magnitude;
            dirY /= magnitude;
            dirZ /= magnitude;
        } else {
            dirX = 0;
            dirY = 0;
            dirZ = 0;
        }

        robot.setDeltaMovement(new net.minecraft.world.phys.Vec3(dirX / 10F, dirY / 10F, dirZ / 10F));
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged 3 RF per tick; at the canonical 1 MJ = 10 RF bridge (MjRfConversion) that is
        // 3 * 100_000 = 300_000 micro-MJ.
        return 300_000;
    }
}
