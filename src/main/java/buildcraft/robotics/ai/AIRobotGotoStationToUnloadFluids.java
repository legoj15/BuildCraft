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
import buildcraft.robotics.IStationFilter;

/** Finds a station the robot can UNLOAD its carried fluid into, and flies to it.
 *  {@link AIRobotUnloadFluids#unload} as a dry-run predicate makes only a station with room for the
 *  carried fluid a candidate. Ported from 7.1.x {@code AIRobotGotoStationToUnloadFluids}. */
public class AIRobotGotoStationToUnloadFluids extends AIRobot {

    public AIRobotGotoStationToUnloadFluids(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public void start() {
        startDelegateAI(new AIRobotSearchAndGotoStation(robot, new StationFilter(), robot.getZoneToLoadUnload()));
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoStation) {
            setSuccess(ai.success());
            terminate();
        }
    }

    private class StationFilter implements IStationFilter {

        @Override
        public boolean matches(DockingStation station) {
            return AIRobotUnloadFluids.unload(robot, station, false) > 0;
        }
    }
}
