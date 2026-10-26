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
import buildcraft.robotics.IStationFilter;

/** Finds a station the robot can UNLOAD its cargo into, and flies to it. {@link AIRobotUnload#unload} as a
 *  dry-run predicate makes only a station with a live, injectable output a candidate. */
public class AIRobotGotoStationToUnload extends AIRobot {

    public AIRobotGotoStationToUnload(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public void start() {
        // Home counts even outside the zone; trying every station keeps an unreachable home from hiding a
        // reachable in-zone one.
        startDelegateAI(new AIRobotSearchAndGotoStation(robot, takesCargo(robot),
                robot.getZoneToLoadUnload(), true).homeIgnoresZone());
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoStation) {
            setSuccess(ai.success());
            terminate();
        }
    }

    /** Whether the robot is held back from unloading only by Forbid Robot: a station it could unload at, searched
     *  for as this AI searches, exists, and every one forbids it. */
    static boolean onlyForbiddingStationsTakeCargo(IRobotAccess robot) {
        return AIRobotSearchStation.onlyForbiddingStationsMatch(robot, takesCargo(robot),
                robot.getZoneToLoadUnload(), true);
    }

    private static IStationFilter takesCargo(IRobotAccess robot) {
        return station -> AIRobotUnload.unload(robot, station, false);
    }
}
