/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import javax.annotation.Nullable;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.statements.ActionRobotWakeUp;

/** Idles the robot at its station for 60 seconds ({@code SLEEPING_TIME} = 60*20 ticks, the 7.1.x value),
 *  drawing a token 0.1 RF per tick. 7.1.x woke early on an {@code ActionRobotWakeUp} statement from the
 *  station's gates; the Ph6 {@code preempt} check terminates the sleep when the linked station's active
 *  actions hold one. */
public class AIRobotSleep extends AIRobot {

    private static final int SLEEPING_TIME = 60 * 20;
    private int sleptTime = 0;

    public AIRobotSleep(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public void preempt(AIRobot ai) {
        // The LINKED (home) station, not the station the robot happens to be docked at — 7.1.x polled
        // robot.getLinkedStation(), so a robot sleeping away from home still obeys its own home gate.
        //
        // 7.1.x had no null guard and simply NPE'd on a stationless robot, which the cycle() catch turned
        // into an abort — i.e. an accidental wake-up. A stationless robot has no gate to obey at all, so
        // the honest reading is "keep sleeping" and let the 60-second timer wake it.
        DockingStation station = robot.getLinkedStation();
        if (station == null) {
            return;
        }
        for (StatementSlot s : station.getActiveActions()) {
            if (s.statement instanceof ActionRobotWakeUp) {
                terminate();
            }
        }
    }

    @Override
    public void update() {
        sleptTime++;

        if (sleptTime > SLEEPING_TIME) {
            terminate();
        }
    }

    @Override
    public long getPowerCost() {
        // This trick is so we get 0.1 RF per tick: 1 RF is MJ/10 micro-MJ, and we pay it every tenth tick.
        return sleptTime % 10 == 0 ? MjAPI.MJ / 10 : 0;
    }
}
