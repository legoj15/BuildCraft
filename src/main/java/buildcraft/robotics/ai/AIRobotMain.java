/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.core.BCLog;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** The robot's top-level controller, held in {@code EntityRobot.mainAI}. Every cycle it first checks the
 *  energy ladder — shutdown at flat, recharge below the safety reserve — and otherwise hands control to the
 *  board, or to a caller's overriding AI. Ported from 7.1.x; the energy references map to the modern API
 *  ({@code getPower()}, {@code IRobotAccess.SHUTDOWN_POWER}/{@code SAFETY_POWER}). */
public class AIRobotMain extends AIRobot {

    private AIRobot overridingAI;
    private int rechargeCooldown;

    public AIRobotMain(IRobotAccess iRobot) {
        super(iRobot);
        rechargeCooldown = 0;
    }

    @Override
    public long getPowerCost() {
        return 0;
    }

    @Override
    public void preempt(AIRobot ai) {
        if (robot.getPower() <= IRobotAccess.SHUTDOWN_POWER
                && (robot.getDockingStation() == null || !robot.getDockingStation().providesPower())) {
            if (!(ai instanceof AIRobotShutdown)) {
                BCLog.logger.info("Shutting down robot " + robot + " - no power");
                startDelegateAI(new AIRobotShutdown(robot));
            }
        } else if (robot.getPower() < IRobotAccess.SAFETY_POWER) {
            if (!(ai instanceof AIRobotRecharge) && !(ai instanceof AIRobotShutdown)) {
                if (rechargeCooldown-- <= 0) {
                    startDelegateAI(new AIRobotRecharge(robot));
                }
            }
        } else if (!(ai instanceof AIRobotRecharge)) {
            if (overridingAI != null && ai != overridingAI) {
                startDelegateAI(overridingAI);
            }
        }
    }

    @Override
    public void update() {
        AIRobot board = robot.getBoard();

        if (board != null) {
            startDelegateAI(board);
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotRecharge) {
            if (!ai.success()) {
                rechargeCooldown = 120;
            }
        }
        if (ai == overridingAI) {
            overridingAI = null;
        }
    }

    public void setOverridingAI(AIRobot ai) {
        if (overridingAI == null) {
            overridingAI = ai;
        }
    }

    public AIRobot getOverridingAI() {
        return overridingAI;
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }
}
