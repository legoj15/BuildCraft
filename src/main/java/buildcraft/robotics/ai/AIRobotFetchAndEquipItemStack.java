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
import buildcraft.api.robots.IRobotAccess;

/** Docks at the robot's station and equips, in the robot's hands, one stack matching {@code filter} taken
 *  from the station's item input. Ported from 7.1.x {@code AIRobotFetchAndEquipItemStack} (7.1.x reached
 *  the same through {@code AIRobotLoad.takeSingle} on the station input; the modern equivalent extracts one
 *  matching stack through the station's input transactor and then
 *  {@code robot.setItemInUse}). The 7.1.x gate-tool filter ({@code ActionRobotFilterTool}) is Ph6, so the
 *  effective filter is just {@code filter} (D1).
 *
 *  <p>Red-baseline skeleton: the equip lands in the block-action step; until then the inherited
 *  {@code update()} terminates on the first cycle.</p> */
public class AIRobotFetchAndEquipItemStack extends AIRobot {

    private IStackFilter filter;
    private int delay = 0;

    public AIRobotFetchAndEquipItemStack(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotFetchAndEquipItemStack(IRobotAccess iRobot, IStackFilter iFilter) {
        super(iRobot);

        filter = iFilter;
    }
}
