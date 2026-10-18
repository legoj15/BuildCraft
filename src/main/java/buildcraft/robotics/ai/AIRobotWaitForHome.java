/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;

/** A robot that needs a charge, finds no charger it can reach, and whose home's chunk is not loaded, hovers where it is
 *  at no cost until the home comes back. {@link AIRobotMain} starts it after a failed recharge and keeps retrying the
 *  recharge on its usual back-off meanwhile, so a charger that comes into reach is still found.
 *
 *  <p>Not 7.1.x, where this never arose: a robot's home was always loaded alongside it (7.1.x's station lookup, like
 *  this port's until 2026-10, loaded the home's chunk). Without the wait the robot went back to work between the failed
 *  searches, ran itself flat and shut down in the field with whatever it carried, far from the home that would have
 *  charged it. It ends as soon as the home's chunk is loaded, and {@link AIRobotMain} then retries the recharge at
 *  once. Never saved: a reloaded robot simply works out again that it has to wait. */
public class AIRobotWaitForHome extends AIRobot {

    public AIRobotWaitForHome(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** Whether {@code robot} has a home whose chunk is not loaded: no flight can reach it, and its gates and charger
     *  are out of sight. */
    static boolean homeOutOfReach(IRobotAccess robot) {
        DockingStation home = robot.getLinkedStation();
        return home != null && !home.isChunkLoaded();
    }

    @Override
    public void start() {
        robot.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void update() {
        robot.setDeltaMovement(Vec3.ZERO);
        if (!homeOutOfReach(robot)) {
            terminate();
        }
    }

    /** Waiting is free, so the charge the robot stopped with is the charge it has when the home is back. */
    @Override
    public long getPowerCost() {
        return 0;
    }
}
