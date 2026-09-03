/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementSlot;
import buildcraft.robotics.statements.ActionRobotWakeUp;

/** {@link AIRobotSleep} idles at its station for the 7.1.x 60-second timer (60*20 ticks) and then wakes itself.
 *  Termination is observed through a recording parent. The Ph6 {@code preempt} wake-up path (a station gate
 *  holding {@code ActionRobotWakeUp}) is covered by the game test
 *  {@code RobotGateTester.sleepTriggerAndWakeupPreemptsPicker}; the station the preempt READS (7.1.x:
 *  the robot's LINKED home station, not whatever it happens to be docked at) is pinned here. */
public class AIRobotSleepTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void wakesItselfAfterSixtySeconds() {
        AIRobot parent = new RecordingParent(robot);
        AIRobotSleep sleep = new AIRobotSleep(robot);
        parent.startDelegateAI(sleep); // sleep.start() is empty, so the parent holds it cleanly

        // SLEEPING_TIME is 1200 and the wake check is `sleptTime > SLEEPING_TIME`, so it wakes on the 1201st tick.
        for (int i = 0; i < 60 * 20 + 1; i++) {
            sleep.update();
        }
        Assertions.assertSame(sleep, ((RecordingParent) parent).ended,
                "the 60s timer wakes the sleeping robot");

        // And it does NOT wake early — one fewer tick and it is still asleep.
        AIRobot parent2 = new RecordingParent(robot);
        AIRobotSleep sleep2 = new AIRobotSleep(robot);
        parent2.startDelegateAI(sleep2);
        for (int i = 0; i < 60 * 20 - 1; i++) {
            sleep2.update();
        }
        Assertions.assertNull(((RecordingParent) parent2).ended,
                "one tick short of 60s it is still asleep");
    }

    @Test
    public void aWakeUpOnTheHomeStationWakesARobotSleepingElsewhere() {
        // 7.1.x polled robot.getLinkedStation() — the HOME station. A robot parked at some other station
        // must still obey the Wake Up action on its own home gate.
        robot.setLinkedStation(new GateStation(new ActionRobotWakeUp()));
        robot.setDockingStation(new GateStation(null));

        RecordingParent parent = new RecordingParent(robot);
        AIRobotSleep sleep = new AIRobotSleep(robot);
        parent.startDelegateAI(sleep);
        sleep.preempt(null);

        Assertions.assertSame(sleep, parent.ended,
                "the HOME station's Wake Up action must wake a robot sleeping away from home");
    }

    @Test
    public void aWakeUpOnSomeOtherStationDoesNotWakeTheRobot() {
        robot.setLinkedStation(new GateStation(null));
        robot.setDockingStation(new GateStation(new ActionRobotWakeUp()));

        RecordingParent parent = new RecordingParent(robot);
        AIRobotSleep sleep = new AIRobotSleep(robot);
        parent.startDelegateAI(sleep);
        sleep.preempt(null);

        Assertions.assertNull(parent.ended,
                "only the home station's gate wakes the robot — a foreign station's Wake Up does not");
    }

    @Test
    public void aRobotWithNoHomeStationKeepsSleeping() {
        robot.setLinkedStation(null);
        robot.setDockingStation(new GateStation(new ActionRobotWakeUp()));

        RecordingParent parent = new RecordingParent(robot);
        AIRobotSleep sleep = new AIRobotSleep(robot);
        parent.startDelegateAI(sleep);
        sleep.preempt(null);

        Assertions.assertNull(parent.ended,
                "no home station means nothing to poll — the 60s timer is the only wake-up left");
    }

    /** A station whose active actions are a single {@code action}, or none when it is null. */
    private static class GateStation extends DockingStation {
        private final List<StatementSlot> slots;

        GateStation(ActionRobotWakeUp action) {
            super();
            if (action == null) {
                slots = Collections.emptyList();
            } else {
                StatementSlot slot = new StatementSlot();
                slot.statement = action;
                slot.parameters = new IStatementParameter[0];
                slots = List.of(slot);
            }
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return slots;
        }
    }

    /** Records the delegate that ended, so termination is observable. */
    private static class RecordingParent extends AIRobot {
        AIRobot ended;

        RecordingParent(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            ended = ai;
        }
    }
}
