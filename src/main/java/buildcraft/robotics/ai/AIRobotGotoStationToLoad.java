/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.IStationFilter;

/** Finds a station the robot can LOAD {@code quantity} items matching {@code filter} from, and flies to it.
 *  The station search uses {@link AIRobotLoad#load} as a dry-run predicate, so only a station with enough
 *  compatible supply is a candidate. */
public class AIRobotGotoStationToLoad extends AIRobot {

    private IStackFilter filter;
    private int quantity;

    public AIRobotGotoStationToLoad(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotGotoStationToLoad(IRobotAccess iRobot, IStackFilter iFilter, int iQuantity) {
        this(iRobot);

        filter = iFilter;
        quantity = iQuantity;
    }

    @Override
    public void update() {
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
            return AIRobotLoad.load(robot, station, filter, quantity, false);
        }
    }
}
