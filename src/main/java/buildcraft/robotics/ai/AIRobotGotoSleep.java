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

/** Returns the robot to its linked station and puts it to sleep — the "parked home" behaviour every board
 *  falls back to when there is nothing to do. Releases all resource locks first (the robot is going away). */
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
            startDelegateAI(new AIRobotSleep(robot));
        } else if (ai instanceof AIRobotSleep) {
            terminate();
        }
    }
}
