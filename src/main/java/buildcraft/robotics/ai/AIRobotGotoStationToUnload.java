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
public class AIRobotGotoStationToUnload extends AIRobot implements StationOutcome {

    private boolean stationUnavailable;

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
            stationUnavailable = !ai.success() && StationOutcome.unavailable(ai);
            setSuccess(ai.success());
            terminate();
        }
    }

    @Override
    public boolean stationUnavailable() {
        return stationUnavailable;
    }

    /** What, if anything, the robot may wait for to unload, searched for as this AI searches
     *  ({@link AIRobotSearchStation#unavailableTakers} lists what passes). */
    static AIRobotSearchStation.Hold unavailableTakers(IRobotAccess robot) {
        return AIRobotSearchStation.unavailableTakers(robot, takesCargo(robot), robot.getZoneToLoadUnload(), true);
    }

    private static IStationFilter takesCargo(IRobotAccess robot) {
        return station -> AIRobotUnload.unload(robot, station, false);
    }
}
