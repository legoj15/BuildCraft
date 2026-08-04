/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;
import buildcraft.robotics.ai.AIRobotLoad;

/** Loads from a supply station (any quantity) when empty, carries the cargo to another station, unloads, and
 *  sleeps when nothing is available. The supply-side input is the wooden-pipe discovery on the station
 *  ({@code DockingStation.getItemInput}, D6) — NOT the Ph8 request network. */
public class BoardRobotCarrier extends RedstoneBoardRobot {

    public BoardRobotCarrier(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotCarrierNBT.INSTANCE;
    }

    @Override
    public void update() {
        if (!robot.containsItems()) {
            DockingStation linked = robot.getLinkedStation();
            IStackFilter filter = linked != null ? linked.getRobotItemFilter() : stack -> true;
            startDelegateAI(new AIRobotGotoStationAndLoad(robot, filter, AIRobotLoad.ANY_QUANTITY));
        } else {
            startDelegateAI(new AIRobotGotoStationAndUnload(robot));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationAndLoad) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotGotoStationAndUnload) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }
}
