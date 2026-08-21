/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Plants the held seed at {@code blockFound} through the
 *  {@link buildcraft.api.crops.CropManager} (sustainability check + crop placement via a fake player).
 *  Ported from 7.1.x {@code AIRobotPlant}.
 *
 *  <p>Red-baseline skeleton: the planting lands in the block-action step; until then the inherited
 *  {@code update()} terminates on the first cycle.</p> */
public class AIRobotPlant extends AIRobot {

    private BlockPos blockFound;
    private int delay = 0;

    public AIRobotPlant(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotPlant(IRobotAccess iRobot, BlockPos iBlockFound) {
        super(iRobot);

        blockFound = iBlockFound;
    }
}
