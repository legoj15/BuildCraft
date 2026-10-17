/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** {@link AIRobotSearchRandomGroundBlock} must stop the moment it terminates. Its give-up branches (no filter, the
 *  attempt limit) used to fall through into the column scan, which could terminate the AI a second time in the same
 *  update - and a second {@code terminate()} reaches the parent after it has already started its next delegate
 *  (the bomber's sleep), clearing that delegate. The mock robot has no level, so any fall-through past a give-up
 *  throws. */
public class AIRobotSearchRandomGroundBlockTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void noFilterEndsTheSearchOnce() {
        Parent parent = new Parent(robot);
        AIRobotSearchRandomGroundBlock search = new AIRobotSearchRandomGroundBlock(robot, 10, null, null);
        parent.startDelegateAI(search);

        Assertions.assertDoesNotThrow(search::update, "a search with no filter gives up without scanning");
        Assertions.assertEquals(1, parent.endings, "...and ends exactly once");
        Assertions.assertFalse(search.success());
    }

    @Test
    public void theAttemptLimitEndsTheSearchOnce() throws ReflectiveOperationException {
        Parent parent = new Parent(robot);
        AIRobotSearchRandomGroundBlock search = new AIRobotSearchRandomGroundBlock(robot, 10, pos -> true, null);
        parent.startDelegateAI(search);
        Field attempts = AIRobotSearchRandomGroundBlock.class.getDeclaredField("attempts");
        attempts.setAccessible(true);
        attempts.setInt(search, 4096); // the limit: the next update is one attempt too many

        Assertions.assertDoesNotThrow(search::update, "a search out of attempts gives up without scanning");
        Assertions.assertEquals(1, parent.endings, "...and ends exactly once");
        Assertions.assertFalse(search.success());
    }

    private static final class Parent extends AIRobot {
        int endings;

        Parent(IRobotAccess robot) {
            super(robot);
        }

        @Override
        public void delegateAIEnded(AIRobot ai) {
            endings++;
        }
    }
}
