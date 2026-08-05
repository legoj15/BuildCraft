/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** {@link AIRobotRecharge} completes once the battery reaches the re-pin-safe headroom — Decision 3's
 *  {@code MAX_POWER - MAX_POWER/200}, not 7.1.x's hard 500. Termination is observed through a subclass that
 *  records {@code end()}. */
public class AIRobotRechargeTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void terminatesAtTheHeadroomThreshold() {
        robot.getBattery().setStored(IRobotAccess.MAX_POWER); // full — past the headroom line
        TrackedRecharge recharge = new TrackedRecharge(robot);

        recharge.update();

        Assertions.assertTrue(recharge.ended, "a robot at full charge has nowhere left to store, so it terminates");
    }

    @Test
    public void doesNotTerminateBelowTheThreshold() {
        robot.getBattery().setStored(IRobotAccess.MAX_POWER / 2); // safely below the headroom line
        TrackedRecharge recharge = new TrackedRecharge(robot);

        recharge.update();

        Assertions.assertFalse(recharge.ended, "a half-charged robot keeps charging");
    }

    @Test
    public void theThresholdIsTheHeadroomNotAHardNumber() {
        long max = IRobotAccess.MAX_POWER;
        long headroom = max - max / 200;
        // 7.1.x used a hard 500 RF; Decision 3's MAX_POWER/200 headroom is different and load-bearing.
        Assertions.assertNotEquals(500L, headroom, "the headroom is a proportion of the battery, not a constant");
    }

    /** Records termination through {@code end()}. */
    private static class TrackedRecharge extends AIRobotRecharge {
        boolean ended;

        TrackedRecharge(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public void end() {
            ended = true;
        }
    }
}
