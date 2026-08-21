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

/** Harvests a mature crop at {@code blockFound} through the
 *  {@link buildcraft.api.crops.CropManager} (maturity check, crop drops) and drops the result at the block.
 *  Ported from 7.1.x {@code AIRobotHarvest} (7.1.x dropped items via {@code BlockUtils.dropItem}; the modern
 *  equivalent is an {@code ItemEntity} spawn).
 *
 *  <p>Red-baseline skeleton: the harvest lands in the block-action step; until then the inherited
 *  {@code update()} terminates on the first cycle.</p> */
public class AIRobotHarvest extends AIRobot {

    private BlockPos blockFound;
    private int delay = 0;

    public AIRobotHarvest(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotHarvest(IRobotAccess iRobot, BlockPos iBlockFound) {
        super(iRobot);

        blockFound = iBlockFound;
    }
}
