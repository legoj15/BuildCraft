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

/** A robot that needs a charge, finds no charger it can reach, and whose home's chunk is not loaded (or whose home
 *  forbids it, Forbid/Force Robot), hovers where it is at no cost until the home comes back. {@link AIRobotMain}
 *  starts it after a failed recharge and keeps retrying the recharge on its usual back-off meanwhile, so a charger that
 *  comes into reach is still found.
 *
 *  <p>The forbid case (2026-10-26, issue #46): Forbid Robot evicts a robot from a station and keeps it from charging
 *  there, and a robot whose only charger is its forbidding home otherwise ran flat and shut down for good (a shutdown
 *  is never undone on its own), however soon the gate turned off. Waiting keeps it until the forbid lifts; a robot
 *  already flat when it is evicted waits too, instead of shutting down ({@link #homeForbids}).
 *
 *  <p>Not 7.1.x, where this never arose: a robot's home was always loaded alongside it (7.1.x's station lookup, like
 *  this port's until 2026-10, loaded the home's chunk). Without the wait the robot went back to work between the failed
 *  searches, ran itself flat and shut down in the field with whatever it carried, far from the home that would have
 *  charged it. It ends as soon as the home's chunk is loaded, and {@link AIRobotMain} then retries the recharge at
 *  once. Never saved: a reloaded robot simply works out again that it has to wait. It waits even when no powered
 *  station exists anywhere: holding in place was chosen (2026-10-18) over shutting down, as the kinder outcome.
 *
 *  <p>Deliberately deaf to a Wake Up gate action, unlike {@link AIRobotSleep}, although the robot reads as sleeping to
 *  gates: waking a robot that is below the safety line with no charger in reach would only send it back to work until
 *  it shuts down in the field, which is what this AI exists to prevent. */
public class AIRobotWaitForHome extends AIRobot {

    public AIRobotWaitForHome(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** Whether {@code robot} has a home whose chunk is not loaded (no flight can reach it, and its gates and charger
     *  are out of sight), or a home whose gate forbids it (no flight is allowed to dock there). */
    static boolean homeOutOfReach(IRobotAccess robot) {
        DockingStation home = robot.getLinkedStation();
        return home != null && (!home.isChunkLoaded() || home.isRobotForbidden(robot));
    }

    /** Whether {@code robot}'s loaded home forbids it: the one out-of-reach home a flat robot waits for rather than
     *  shutting down, since the eviction that left it flat off its charger is undone only by the gate. (A flat robot
     *  whose home's chunk is unloaded still shuts down, as before.) */
    static boolean homeForbids(IRobotAccess robot) {
        DockingStation home = robot.getLinkedStation();
        return home != null && home.isChunkLoaded() && home.isRobotForbidden(robot);
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
