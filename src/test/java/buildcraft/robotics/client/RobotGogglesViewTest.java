/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Which robot the Goggles label picks is the nearest one the look ray reaches, with a little aiming slack that grows
 *  with distance; walls are not a thing the ray knows about. The pick itself needs a client world, so this pins the
 *  geometry it is built on. */
public class RobotGogglesViewTest {

    private static final Vec3 EYE = new Vec3(0.5, 64, 0.5);

    /** A robot's pick box (half a block either way of its centre) {@code x} blocks east of the eye, {@code dy} up. */
    private static AABB robotAt(double x, double dy) {
        return new AABB(EYE.x + x - 0.25, EYE.y + dy - 0.25, EYE.z - 0.25, EYE.x + x + 0.25, EYE.y + dy + 0.25,
                EYE.z + 0.25);
    }

    private static Vec3 lookEast() {
        return EYE.add(RobotGogglesView.LOOK_RANGE, 0, 0);
    }

    @Test
    public void aRobotOnTheRayIsHitAtItsNearFaceAndANearerOneComesFirst() {
        double near = RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(10, 0));
        double far = RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(30, 0));

        Assertions.assertTrue(near > 9 && near < 10, "the ray enters the padded box just short of the robot: " + near);
        Assertions.assertTrue(near < far, "the nearer robot is picked, whatever order they are asked in");
    }

    @Test
    public void theAimingSlackGrowsWithDistance() {
        Assertions.assertTrue(Double.isFinite(RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(8, 0.4))),
                "a robot a little off the ray at 8 blocks still counts");
        Assertions.assertTrue(Double.isInfinite(RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(8, 0.8))),
                "0.8 blocks off the ray is a miss at 8 blocks...");
        Assertions.assertTrue(Double.isFinite(RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(60, 0.8))),
                "...and a hit at 60, where a robot is far smaller on screen");
        Assertions.assertTrue(Double.isInfinite(RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(8, 3))),
                "a robot well off the ray is not looked at");
    }

    @Test
    public void aRobotBeyondTheRangeOrBehindTheEyeIsNotLookedAt() {
        Assertions.assertTrue(Double.isInfinite(RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(70, 0))),
                "past 64 blocks");
        Assertions.assertTrue(Double.isInfinite(RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(-10, 0))),
                "behind the player");
    }

    @Test
    public void aRobotAtTheEyeIsLookedAtFromDistanceZero() {
        Assertions.assertEquals(0, RobotGogglesView.rayDistance(EYE, lookEast(), robotAt(0.1, 0)));
    }
}
