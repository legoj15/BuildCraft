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

/** {@link AIRobotSleep} idles at its station for the 7.1.x 60-second timer (60*20 ticks) and then wakes itself.
 *  Termination is observed through a recording parent (the {@code preempt} wake-up statement is deliberately a
 *  Ph6 no-op, so the timer is the only thing that wakes it). */
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
