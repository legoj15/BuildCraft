/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** The {@link AIRobotMain} preempt ladder, driven against a {@link MockRobotAccess} and a real {@code MjBattery}:
 *  shutdown wins at flat power, recharge takes over below the safety reserve (and backs off on failure), the
 *  overriding AI only runs once power is comfortable. The failure cooldown is private, so the test reads it
 *  reflectively — the observable it pins is that a second low-power preempt does NOT re-arm it. */
public class AIRobotMainTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void flatPowerStartsAShutdown() {
        robot.getBattery().setStored(0); // == SHUTDOWN_POWER, and no power station (null docking station)

        AIRobotMain main = new AIRobotMain(robot);
        main.preempt(null);

        Assertions.assertInstanceOf(AIRobotShutdown.class, main.getDelegateAI(),
                "a flat robot with nowhere to charge is shut down");
    }

    @Test
    public void belowSafetyStartsARecharge() {
        robot.getBattery().setStored(MjAPI.MJ); // above 0, below SAFETY_POWER

        AIRobotMain main = new AIRobotMain(robot);
        main.preempt(null);

        // The recharge unwinds immediately (the mock's registry has no power station), and its failure arms the
        // cooldown — that arm is the observable, since the delegate itself is gone by the time preempt returns.
        Assertions.assertNull(main.getDelegateAI(),
                "a failed recharge leaves no running delegate");
        Assertions.assertEquals(120, rechargeCooldown(main),
                "and its failure arms the recharge cooldown (120 ticks)");
    }

    @Test
    public void rechargeCooldownSuppressesAnImmediateRetry() {
        robot.getBattery().setStored(MjAPI.MJ);
        AIRobotMain main = new AIRobotMain(robot);

        main.preempt(null); // arms cooldown at 120
        Assertions.assertEquals(120, rechargeCooldown(main));
        int afterFirst = rechargeCooldown(main);

        main.preempt(null); // a second low-power preempt within the cooldown

        Assertions.assertEquals(afterFirst - 1, rechargeCooldown(main),
                "the second preempt only decrements the cooldown — it must NOT re-arm a fresh 120");
    }

    @Test
    public void safePowerRunsTheOverridingAi() {
        robot.getBattery().setStored(robot.getBattery().getCapacity()); // full: well above safety

        AIRobotMain main = new AIRobotMain(robot);
        AIRobot override = new AIRobot(robot);
        main.setOverridingAI(override);
        main.preempt(null);

        Assertions.assertSame(override, main.getDelegateAI(),
                "with power comfortable and an override waiting, the override is the active delegate");
    }

    @Test
    public void flatPowerOnAChargingStationRechargesInsteadOfShuttingDown() {
        robot.getBattery().setStored(0);
        robot.setDockingStation(new PowerStationStub());

        AIRobotMain main = new AIRobotMain(robot);
        main.preempt(null);

        // Shutdown is skipped because the station provides power; the recharge branch wins instead (and fails
        // here only because the mock registry has nowhere to recharge from).
        Assertions.assertNull(main.getDelegateAI(), "a flat robot ON power recharges, not shuts down");
    }

    /** Reads the private {@code rechargeCooldown} field. */
    private static int rechargeCooldown(AIRobotMain main) {
        try {
            Field f = AIRobotMain.class.getDeclaredField("rechargeCooldown");
            f.setAccessible(true);
            return f.getInt(main);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not read AIRobotMain.rechargeCooldown", e);
        }
    }

    /** A docking station that claims to provide power, for the "don't shut down on a charging plate" check. */
    private static class PowerStationStub extends buildcraft.api.robots.DockingStation {
        @Override
        public Iterable<buildcraft.api.statements.StatementSlot> getActiveActions() {
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean providesPower() {
            return true;
        }
    }
}
