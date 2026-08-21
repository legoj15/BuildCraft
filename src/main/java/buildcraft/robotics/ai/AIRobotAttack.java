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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Melee attack loop: fly within range of {@code target}, and every 20 ticks (with a 10-tick delay on
 *  arrival) swing the held weapon through
 *  {@link IRobotAccess#attackTargetEntityWithCurrentItem(Entity)}; a preempt that finds the target in
 *  range skips the goto and starts swinging immediately, and a failed goto reports the target as
 *  unreachable before terminating. Ported from 7.1.x {@code AIRobotAttack}.
 *
 *  <p>No NBT: the target is a live entity reference 7.1.x never serialised either, so a robot killed
 *  mid-attack restarts its board from scratch (the board re-searches for a target). */
public class AIRobotAttack extends AIRobot {

    private static final float ATTACK_RANGE = 2.0F;

    private Entity target;
    private int delay = 10;

    public AIRobotAttack(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotAttack(IRobotAccess iRobot, Entity iTarget) {
        super(iRobot);

        target = iTarget;
    }

    @Override
    public void preempt(AIRobot ai) {
        if (ai instanceof AIRobotGotoBlock) {
            // The target may have moved since the goto started; if it is in range now, skip the rest of
            // the flight and start swinging immediately.
            if (target != null && !target.isRemoved()
                    && robot.position().distanceTo(target.position()) <= ATTACK_RANGE) {
                abortDelegateAI();
                robot.setItemActive(true);
            }
        }
    }

    @Override
    public void update() {
        if (target == null || target.isRemoved()) {
            terminate();
            return;
        }

        if (robot.position().distanceTo(target.position()) > ATTACK_RANGE) {
            startDelegateAI(new AIRobotGotoBlock(robot,
                    Mth.floor(target.getX()), Mth.floor(target.getY()), Mth.floor(target.getZ())));
            robot.setItemActive(false);
            return;
        }

        delay++;
        if (delay > 20) {
            delay = 0;
            robot.attackTargetEntityWithCurrentItem(target);
            robot.aimItemAt(new BlockPos(
                    Mth.floor(target.getX()), Mth.floor(target.getY()), Mth.floor(target.getZ())));
        }
    }

    @Override
    public void end() {
        robot.setItemActive(false);
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoBlock) {
            if (!ai.success()) {
                robot.unreachableEntityDetected(target);
            }
            terminate();
        }
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged BREAK_ENERGY * 2 / 20 = 16 RF; at the 1 RF = 100_000 micro-MJ bridge.
        return 1_600_000;
    }
}
