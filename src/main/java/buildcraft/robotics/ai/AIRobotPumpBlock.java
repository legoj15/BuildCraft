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

/** Pumps fluid out of the source block at {@code blockToPump} (through the in-tree
 *  {@code BlockUtil.drainBlock}) into the robot's tank, topping up a few mB per cycle while there is room.
 *  Ported from 7.1.x {@code AIRobotPumpBlock} (7.1.x pumped the tank via the robot's fluid transactor; the
 *  modern equivalent is {@link IRobotAccess#getFluidHandler()}).
 *
 *  <p>Red-baseline skeleton: the pump lands in the fluid step; until then the inherited {@code update()}
 *  terminates on the first cycle.</p> */
public class AIRobotPumpBlock extends AIRobot {

    private BlockPos blockToPump;
    private long waited = 0;
    private int pumped = 0;

    public AIRobotPumpBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotPumpBlock(IRobotAccess iRobot, BlockPos iBlockToPump) {
        super(iRobot);

        blockToPump = iBlockToPump;
    }
}
