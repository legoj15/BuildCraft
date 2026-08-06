/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** {@link AIRobotGotoBlock} must short-circuit when the robot is already on its target — Ph0's tests pinned
 *  that {@code PathFinding(start == end)} yields the degenerate 3-cell out-and-back, so a robot already on
 *  the target would otherwise fly a pointless loop. Termination is observed through a recording parent, the
 *  only observable of {@code start()}'s short-circuit: {@code terminate()} is what fires
 *  {@code delegateAIEnded}, and {@code success()} alone cannot pin it (it defaults to true and is never set
 *  false in {@code start()}). */
public class AIRobotGotoBlockTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void alreadyAtTheTargetTerminatesDuringStart() {
        robot.setPosition(new Vec3(3.5, 4.5, 5.5));

        RecordingParent parent = new RecordingParent(robot);
        AIRobotGotoBlock gotoBlock = new AIRobotGotoBlock(robot, 3, 4, 5);
        parent.startDelegateAI(gotoBlock);

        Assertions.assertSame(gotoBlock, parent.ended,
                "a robot already on the target block must terminate inside start() — otherwise it begins "
                        + "the degenerate 3-cell out-and-back journey");
        Assertions.assertTrue(gotoBlock.success(), "the short-circuit reports success");
    }

    @Test
    public void anAdjacentRobotDoesNotShortCircuit() {
        robot.setPosition(new Vec3(1.5, 2.5, 3.5));

        RecordingParent parent = new RecordingParent(robot);
        AIRobotGotoBlock gotoBlock = new AIRobotGotoBlock(robot, 8, 2, 3);
        parent.startDelegateAI(gotoBlock);

        Assertions.assertNull(parent.ended,
                "a robot NOT on the target must not terminate in start() — the journey happens in update()");
    }

    /** Records the delegate that terminated, so {@code start()}'s short-circuit is observable. */
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
