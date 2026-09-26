/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.mj.MjAPI;

/** The charging latch's rule table — what one REAL delivery of {@code accepted} micro-MJ does to
 *  {@code ticksCharging} (7.1.x {@code EntityRobot.receiveEnergy}: {@code if (!simulate && energyReceived > 5
 *  && ticksCharging <= 25) ticksCharging += 5}, so 30 is an implicit ceiling; 5 RF is re-pinned as
 *  {@link RobotChargeReceiver#CHARGE_DETECT_THRESHOLD}).
 *  The receiver wiring around it (simulate never bumps; the threshold applies to the accepted amount, not the
 *  offer) and the per-tick decay are the {@code robot_charging_latch_rules} game test's half. */
public class RobotChargingLatchTest {

    private static final long THRESHOLD = RobotChargeReceiver.CHARGE_DETECT_THRESHOLD;
    private static final long NOTICEABLE = THRESHOLD + 1;

    @Test
    public void theThresholdIsHalfAnMj() {
        Assertions.assertEquals(MjAPI.MJ / 2, THRESHOLD, "chosen, not derived: 7.1.x's 5 RF at 10 RF/MJ");
    }

    @Test
    public void aDeliveryAtOrBelowTheThresholdIsNotNoticed() {
        Assertions.assertEquals(0, EntityRobot.latchAfterDelivery(0, 0));
        Assertions.assertEquals(0, EntityRobot.latchAfterDelivery(0, THRESHOLD - 1));
        Assertions.assertEquals(0, EntityRobot.latchAfterDelivery(0, THRESHOLD),
                "the comparison is strict: exactly the threshold does not count as charging");
        Assertions.assertEquals(12, EntityRobot.latchAfterDelivery(12, THRESHOLD),
                "…and a below-threshold trickle does not top up a running latch either");
    }

    @Test
    public void eachNoticedDeliveryAddsFiveTicksUpToThirty() {
        int latch = 0;
        for (int expected : new int[] { 5, 10, 15, 20, 25, 30, 30 }) {
            latch = EntityRobot.latchAfterDelivery(latch, NOTICEABLE);
            Assertions.assertEquals(expected, latch, "the latch climbs by 5 per delivery and caps at 30");
        }
    }

    @Test
    public void aLatchAboveTwentyFiveIsNotToppedUp() {
        for (int latch = 26; latch <= 30; latch++) {
            Assertions.assertEquals(latch, EntityRobot.latchAfterDelivery(latch, 1000L * MjAPI.MJ),
                    "at " + latch + " (> 25) a delivery leaves the latch alone");
        }
    }

    @Test
    public void aLatchAtOrBelowTwentyFiveIsToppedUpButNeverPastThirty() {
        Assertions.assertEquals(30, EntityRobot.latchAfterDelivery(25, NOTICEABLE), "25 is still topped up");
        Assertions.assertEquals(29, EntityRobot.latchAfterDelivery(24, NOTICEABLE));
        Assertions.assertEquals(30, EntityRobot.latchAfterDelivery(25, 5000L * MjAPI.MJ),
                "the size of a delivery does not matter once it is noticed — the step is always 5");
    }

    @Test
    public void aStationDeliveringEveryTickPinsTheLatchNearTheTop() {
        // Deliver, then the entity tick decrements — the steady state a kinesis-fed station produces.
        int latch = 0;
        for (int tick = 0; tick < 200; tick++) {
            latch = EntityRobot.latchAfterDelivery(latch, NOTICEABLE);
            Assertions.assertTrue(latch > 0, "a robot being charged every tick never reads as not-charging");
            latch--; // EntityRobot.tick()
        }
        Assertions.assertTrue(latch >= 24, "the latch rides between 25 and 30 while charging continues: " + latch);
    }
}
