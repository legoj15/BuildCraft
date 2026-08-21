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

/** Breaks {@code blockToBreak} with the held tool, replicating vanilla's destroy-progress math (hardness,
 *  tool speed, efficiency's squared bonus, the correct-tool 30-vs-100 divisor) a few ticks per cycle, then
 *  finishes the job through the in-tree {@code BlockUtil.breakBlockAndGetDrops} (tier gate + drops).
 *  Ported from 7.1.x {@code AIRobotBreak}.
 *
 *  <p>Red-baseline skeleton: the break math lands in the block-action step; until then the inherited
 *  {@code update()} terminates on the first cycle.</p> */
public class AIRobotBreak extends AIRobot {

    private BlockPos blockToBreak;
    private float blockDamage = 0;

    public AIRobotBreak(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotBreak(IRobotAccess iRobot, BlockPos iBlockToBreak) {
        super(iRobot);

        blockToBreak = iBlockToBreak;
    }
}
