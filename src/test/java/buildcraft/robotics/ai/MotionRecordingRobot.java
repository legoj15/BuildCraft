/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.DockingStation;

/** A {@link MockRobotAccess} that remembers what the movement AIs asked of it: the live velocity (the base mock
 *  throws {@code setDeltaMovement} away), every {@code dock}/{@code undock}, and a settable position the test
 *  moves by hand between updates — the movement AIs only read position, they never integrate it. */
class MotionRecordingRobot extends MockRobotAccess {

    Vec3 motion = Vec3.ZERO;
    final List<DockingStation> docks = new ArrayList<>();
    int undocks;

    void moveTo(double x, double y, double z) {
        setPosition(new Vec3(x, y, z));
    }

    @Override
    public Vec3 getDeltaMovement() {
        return motion;
    }

    @Override
    public void setDeltaMovement(Vec3 movement) {
        motion = movement;
    }

    @Override
    public void dock(DockingStation station) {
        docks.add(station);
        setDockingStation(station);
    }

    @Override
    public void undock() {
        undocks++;
        setDockingStation(null);
    }
}
