/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import buildcraft.lib.mj.MjBatteryReceiver;

/**
 * The MJ receiver a docking station hands out for a docked robot.
 *
 * <p>Extends {@link MjBatteryReceiver} rather than reimplementing {@code IMjReceiver} so the robot keeps
 * exposing {@code IMjReadable} too — {@code TriggerPower} instanceof-checks the station's receiver in order to
 * read a docked robot's stored energy, and a receiver-only wrapper would silently kill that gate trigger.
 *
 * <p>Its one job beyond the plain battery hand-off is to latch "this robot is being charged right now" so the
 * renderer can keep a charging robot looking awake. That latch must NOT move on a simulated transfer —
 * simulating callers exist in-repo (the obsidian pipe behaviour and the pulsar pluggable both probe with
 * {@code simulate == true}).
 *
 * <p><b>Skeleton — {@code receivePower} is still the plain super call.</b>
 */
public class RobotChargeReceiver extends MjBatteryReceiver {

    private final EntityRobot robot;

    public RobotChargeReceiver(EntityRobot robot) {
        super(robot.getBattery());
        this.robot = robot;
    }

    public EntityRobot getRobot() {
        return robot;
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return super.receivePower(microJoules, simulate);
    }
}
