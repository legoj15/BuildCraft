/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.IStationFilter;

/** Flies the robot to a power-providing station and recharges. Releases every resource lock first (the robot
 *  is going away), drops all motion, and terminates once the battery reaches the headroom threshold —
 *  {@code MAX_POWER - MAX_POWER/200} (Decision 3's re-pin-safe headroom, not 7.1.x's hard 500). */
public class AIRobotRecharge extends AIRobot {

    public AIRobotRecharge(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public void start() {
        robot.releaseResources();
        robot.setDeltaMovement(Vec3.ZERO);

        startDelegateAI(new AIRobotSearchAndGotoStation(robot, new IStationFilter() {
            @Override
            public boolean matches(DockingStation station) {
                return station.providesPower();
            }
        }, null));
    }

    @Override
    public long getPowerCost() {
        return 0;
    }

    @Override
    public void update() {
        if (robot.getPower() >= IRobotAccess.MAX_POWER - IRobotAccess.MAX_POWER / 200) {
            terminate();
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoStation) {
            if (!ai.success()) {
                setSuccess(false);
                terminate();
            }
        }
    }
}
