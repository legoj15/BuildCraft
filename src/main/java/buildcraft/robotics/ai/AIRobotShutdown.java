/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Undocks a robot and lets it fall to the ground, parking it where it stops. The shutdown entry point —
 *  {@code EntityRobot.shutdown(String)} starts this AI. The parked robot stays an entity (7.1.x behaviour
 *  too); it only converts back into drops when a player hits it with an attack or wrench. */
public class AIRobotShutdown extends AIRobot {
    private int skip;
    private double motionX;
    private double motionZ;

    public AIRobotShutdown(IRobotAccess iRobot) {
        super(iRobot);
        skip = 0;
        Vec3 motion = iRobot.getDeltaMovement();
        motionX = motion.x;
        motionZ = motion.z;
    }

    @Override
    public void start() {
        robot.undock();
        robot.setDeltaMovement(new Vec3(motionX, -0.075f, motionZ));
    }

    private boolean isBlocked(float yOffset) {
        Vec3 motion = robot.getDeltaMovement();
        AABB box = robot.getBoundingBox().expandTowards(motion.x, yOffset, motion.z);
        // getCollisions takes a @Nullable Entity source; the robot is not an Entity here, and the null source
        // skips the entity's own exclusion (irrelevant for a parking check), so it is fine.
        return robot.level().getCollisions(null, box).iterator().hasNext();
    }

    @Override
    public void update() {
        if (skip == 0) {
            if (!isBlocked(-0.075f)) {
                robot.setDeltaMovement(new Vec3(motionX, -0.075f, motionZ));
            } else {
                // Parked on a surface: stop falling. Once horizontal motion is spent too, hold for 20 ticks
                // (7.1.x nudged the robot up out of a block here; modern IRobotAccess has no setPos, so the
                // robot simply rests where it landed).
                robot.setDeltaMovement(Vec3.ZERO);
                skip = 20;
            }
        } else {
            skip--;
        }
    }

    @Override
    public long getPowerCost() {
        return 0;
    }
}
