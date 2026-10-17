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
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;

/** Returns the robot to its linked station and puts it to sleep — the "parked home" behaviour every board
 *  falls back to when there is nothing to do. Releases all resource locks first (the robot is going away).
 *
 *  <p>Not 7.1.x, which slept wherever it was when home could not be reached: a robot that cannot get home docks at
 *  the nearest powered station it CAN reach, trying them in order of distance, and sleeps there, so a robot that
 *  wandered out of its home's reach (a goto is capped at ~96 blocks) is not left to drain its battery in mid-air.
 *  With no station in reach - or no home at all - it still sleeps where it is. */
public class AIRobotGotoSleep extends AIRobot {

    public AIRobotGotoSleep(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public void start() {
        robot.releaseResources();
        startDelegateAI(new AIRobotGotoStation(robot, robot.getLinkedStation()));
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStation) {
            DockingStation home = robot.getLinkedStation();
            if (ai.success() || home == null) {
                // Home, or a robot with no home (a summoned one), which keeps 7.1.x's sleep-in-place.
                startDelegateAI(new AIRobotSleep(robot));
            } else {
                AIRobotSearchAndGotoStation fallback =
                        new AIRobotSearchAndGotoStation(robot, AIRobotRecharge.POWERED, null, true);
                fallback.exclude(home); // already tried
                startDelegateAI(fallback);
            }
        } else if (ai instanceof AIRobotSearchAndGotoStation) {
            startDelegateAI(new AIRobotSleep(robot));
        } else if (ai instanceof AIRobotSleep) {
            terminate();
        }
    }
}
