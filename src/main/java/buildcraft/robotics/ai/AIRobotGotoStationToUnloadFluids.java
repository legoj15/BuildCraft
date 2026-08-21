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

/** Finds a station whose fluid output accepts the robot's tank fluid and docks the robot there, leaving it
 *  docked (the caller runs {@link AIRobotUnloadFluids}). Ported from 7.1.x
 *  {@code AIRobotGotoStationToUnloadFluids} (the fluid twin of the Ph4
 *  {@code AIRobotGotoStationToUnload}).
 *
 *  <p>Red-baseline skeleton: the fluid-station search lands in the fluid step; until then the inherited
 *  {@code update()} terminates on the first cycle.</p> */
public class AIRobotGotoStationToUnloadFluids extends AIRobot {

    public AIRobotGotoStationToUnloadFluids(IRobotAccess iRobot) {
        super(iRobot);
    }
}
