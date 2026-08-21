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

/** Right-clicks the held tool on the block at {@code useToBlock} (the farmer's hoe-on-dirt), damaging the
 *  tool per use. Ported from 7.1.x {@code AIRobotUseToolOnBlock} (7.1.x used the block's
 *  {@code onBlockActivated}; the modern equivalent is the item's {@code useOn} through a
 *  {@code BlockHitResult}, with durability via {@code hurtAndBreak}).
 *
 *  <p>Red-baseline skeleton: the tool-use lands in the block-action step; until then the inherited
 *  {@code update()} terminates on the first cycle.</p> */
public class AIRobotUseToolOnBlock extends AIRobot {

    private BlockPos useToBlock;
    private int useCycles = 0;

    public AIRobotUseToolOnBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotUseToolOnBlock(IRobotAccess iRobot, BlockPos index) {
        super(iRobot);

        useToBlock = index;
    }
}
