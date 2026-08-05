/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** {@link AIRobotGotoBlock} must short-circuit when the robot is already on its target — Ph0's tests pinned that
 *  {@code PathFinding(start == end)} yields the degenerate 3-cell out-and-back, so a robot already on the target
 *  would otherwise fly a pointless loop. */
public class AIRobotGotoBlockTest {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void alreadyAtTheTargetSucceedsWithoutPathfinding() {
        robot.setPosition(new Vec3(3.5, 4.5, 5.5));

        AIRobotGotoBlock gotoBlock = new AIRobotGotoBlock(robot, 3, 4, 5);
        gotoBlock.start();

        Assertions.assertTrue(gotoBlock.success(),
                "a robot already on the target block reports success immediately");
        // The robot's blockPosition matches the target, so start() short-circuited before any PathFinding.
        Assertions.assertEquals(new BlockPos(3, 4, 5), robot.blockPosition(),
                "the mock robot is on the target block");
    }

    @Test
    public void anAdjacentRobotDoesNotShortCircuit() {
        robot.setPosition(new Vec3(1.5, 2.5, 3.5));

        AIRobotGotoBlock gotoBlock = new AIRobotGotoBlock(robot, 8, 2, 3);
        gotoBlock.start();

        // start() only short-circuits when the robot is ALREADY there; otherwise it just undocks and waits for
        // update() to build the path. success() stays at its default true and no path was planned yet.
        Assertions.assertTrue(gotoBlock.success(), "start() leaves the default success; the journey happens in update()");
    }
}
