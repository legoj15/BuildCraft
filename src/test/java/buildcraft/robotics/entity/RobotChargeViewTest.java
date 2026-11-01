/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.IRobotAccess;

/**
 * Pins the Robot Goggles outline colour: red for an empty battery, green for a full one, blending between and never
 * leaving that range, scaled off the robot's real capacity. The "a robot on a server never reports the goggles view"
 * half of the feature needs a live level, so it is asserted in {@code EntityRobotTester}.
 */
public class RobotChargeViewTest {

    private static final int FULL_MJ = (int) (IRobotAccess.MAX_POWER / MjAPI.MJ);

    @Test
    public void rampRunsRedToGreenAndStaysWithinTheBattery() {
        Assertions.assertEquals(0xFF0000, RobotChargeView.outlineRgbForEnergy(0), "empty must read red");
        Assertions.assertEquals(0x00FF00, RobotChargeView.outlineRgbForEnergy(FULL_MJ), "full must read green");

        int quarter = RobotChargeView.outlineRgbForEnergy(FULL_MJ / 4);
        int threeQuarters = RobotChargeView.outlineRgbForEnergy(FULL_MJ * 3 / 4);
        Assertions.assertTrue(((quarter >> 16) & 0xFF) > ((threeQuarters >> 16) & 0xFF),
                "red must fall as the battery fills");
        Assertions.assertTrue(((quarter >> 8) & 0xFF) < ((threeQuarters >> 8) & 0xFF),
                "green must rise as the battery fills");
        Assertions.assertEquals(0, quarter & 0xFF, "the ramp never touches blue");

        Assertions.assertEquals(0xFF0000, RobotChargeView.outlineRgbForEnergy(-50), "below empty clamps to red");
        Assertions.assertEquals(0x00FF00, RobotChargeView.outlineRgbForEnergy(FULL_MJ * 3), "above full clamps to green");
    }

    @Test
    public void fractionIsScaledOffTheRealBatteryCapacity() {
        Assertions.assertEquals(1F, RobotChargeView.chargeFraction(FULL_MJ), "a full battery is exactly 1");
        Assertions.assertEquals(0.2F, RobotChargeView.chargeFraction(FULL_MJ / 5), 1e-4F,
                "a robot at SAFETY_POWER (a fifth) reads a fifth, whatever MAX_POWER is");
    }
}
