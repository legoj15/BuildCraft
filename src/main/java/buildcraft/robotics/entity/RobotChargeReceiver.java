/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import buildcraft.api.mj.MjAPI;

import buildcraft.lib.mj.MjBatteryReceiver;

/**
 * The MJ receiver a docking station hands out for a docked robot.
 *
 * <p>Extends {@link MjBatteryReceiver} rather than reimplementing {@code IMjReceiver} so the robot keeps
 * exposing {@code IMjReadable} too — {@code TriggerPower} instanceof-checks the station's receiver in order to
 * read a docked robot's stored energy, and a receiver-only wrapper would silently kill that gate trigger.
 *
 * <p>Its one job beyond the plain battery hand-off is to latch "this robot is being charged right now" so the
 * renderer can keep a charging robot looking awake (a robot parked on a station to recharge is asleep by the
 * AI's reckoning, and it would otherwise show the sleep indicator through the whole recharge). The latch itself
 * lives on the {@link EntityRobot}, not here, because {@code RobotStationPluggable} re-fetches the receiver on
 * every capability query and the latch has to outlive any one receiver instance.
 *
 * <p>Two things about the bump rule are easy to get wrong and are pinned by tests:
 * <ul>
 * <li><b>{@code simulate == true} must not bump it.</b> Simulating callers exist in-repo — the obsidian pipe
 *     behaviour and the pulsar pluggable both probe with {@code simulate} every tick — and a latch that moved
 *     on a probe would report a robot as "charging" forever next to a pipe that never delivers anything.</li>
 * <li><b>{@code MjBatteryReceiver.receivePower} returns the EXCESS, not the amount accepted</b> (7.1.x's RF
 *     {@code receiveEnergy} returned the amount received). The threshold therefore has to be applied to
 *     {@code offered - excess}; testing the return value directly would latch on a REJECTED transfer and never
 *     on an accepted one.</li>
 * </ul>
 */
public class RobotChargeReceiver extends MjBatteryReceiver {

    /** How much has to actually land for a transfer to count as "being charged". Chosen, not derived — 7.1.x
     *  used 5 RF, i.e. half an MJ at BuildCraft's canonical 10 RF/MJ bridge. */
    public static final long CHARGE_DETECT_THRESHOLD = MjAPI.MJ / 2;

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
        long excess = super.receivePower(microJoules, simulate);
        if (!simulate) {
            robot.onChargeReceived(microJoules - excess);
        }
        return excess;
    }
}
