/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Idles the robot at its station for 60 seconds ({@code SLEEPING_TIME} = 60*20 ticks, the 7.1.x value),
 *  drawing a token 0.1 RF per tick. 7.1.x woke early on an {@code ActionRobotWakeUp} statement from the
 *  station's gates; those statements are Ph6, so in Ph4 the timer alone wakes it (the {@code preempt}
 *  wake-up check is a deliberate no-op). */
public class AIRobotSleep extends AIRobot {

    private static final int SLEEPING_TIME = 60 * 20;
    private int sleptTime = 0;

    public AIRobotSleep(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public void preempt(AIRobot ai) {
        // Ph6: wake early if the station holds an ActionRobotWakeUp statement. No statements exist yet, so
        // nothing can wake a sleeping robot except the timer in update().
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
