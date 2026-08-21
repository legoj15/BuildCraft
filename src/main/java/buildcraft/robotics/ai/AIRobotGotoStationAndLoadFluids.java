/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Composes {@link AIRobotGotoStationToLoadFluids} (find + dock) and {@link AIRobotLoadFluids} (fill the
 *  tank) for a fluid matching {@code filter}. Ported from 7.1.x {@code AIRobotGotoStationAndLoadFluids}
 *  (the fluid twin of the Ph4 {@code AIRobotGotoStationAndLoad}).
 *
 *  <p>Red-baseline skeleton: the compose-logic lands in the fluid step; until then the inherited
 *  {@code update()} terminates on the first cycle.</p> */
public class AIRobotGotoStationAndLoadFluids extends AIRobot {

    private IFluidFilter filter;

    public AIRobotGotoStationAndLoadFluids(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotGotoStationAndLoadFluids(IRobotAccess iRobot, IFluidFilter iFilter) {
        this(iRobot);

        filter = iFilter;
    }
}
