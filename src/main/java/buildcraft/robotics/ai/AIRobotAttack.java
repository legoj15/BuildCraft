/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.world.entity.Entity;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Melee attack loop: fly within range of {@code target}, and every 20 ticks (with a 10-tick delay on
 *  arrival) swing the held weapon through
 *  {@link IRobotAccess#attackTargetEntityWithCurrentItem(Entity)}; a preempt that finds the target in
 *  range skips the goto and starts swinging immediately, and a failed goto reports the target as
 *  unreachable before terminating. Ported from 7.1.x {@code AIRobotAttack}.
 *
 *  <p>Red-baseline skeleton: the attack timing lands with the combat step (and the real
 *  {@code attackTargetEntityWithCurrentItem} in the foundations commit); until then the inherited
 *  {@code update()} terminates on the first cycle.</p> */
public class AIRobotAttack extends AIRobot {

    private Entity target;
    private int delay = 10;

    public AIRobotAttack(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotAttack(IRobotAccess iRobot, Entity iTarget) {
        super(iRobot);

        target = iTarget;
    }
}
