/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.NBTUtilBC;

/** {@link AIRobotStraightMoveTo} — the last leg of every station visit. It flies a straight line at a constant
 *  0.1 blocks/tick and never re-plans: the move is over on the first update where the distance to the target
 *  did not shrink, and it stops the robot there. The position is moved by hand between updates (the AI only
 *  ever reads it; the entity tick is what integrates velocity).
 *
 *  <p>Two defects found by writing these, both shared with 7.1.x: the target was held in {@code float}s, so a
 *  station more than ~8.4M blocks out (where float's ulp reaches 1.0) had its half-block face offset rounded
 *  away; and a move reloaded mid-flight never re-aimed, so it measured its distance to {@code (0,0,0)} — the
 *  unset destination — and flew on past the station for as long as that kept shrinking. */
public class AIRobotStraightMoveToTest {

    private final MotionRecordingRobot robot = new MotionRecordingRobot();

    private static final double EPS = 1.0E-9;

    @Test
    public void startUndocksAndFliesStraightAtATenthOfABlockPerTick() {
        robot.moveTo(0, 64, 0);
        TrackedStraightMove ai = new TrackedStraightMove(robot, 3, 68, 0);

        ai.start();

        Assertions.assertEquals(1, robot.undocks, "the final approach leaves the station it was docked at");
        Assertions.assertEquals(0.06, robot.motion.x, EPS, "3-4-5 triangle: x is 3/5 of 0.1");
        Assertions.assertEquals(0.08, robot.motion.y, EPS, "3-4-5 triangle: y is 4/5 of 0.1");
        Assertions.assertEquals(0.0, robot.motion.z, EPS);
    }

    @Test
    public void keepsFlyingWhileTheDistanceShrinksAndStopsOnTheFirstTickItDoesNot() {
        robot.moveTo(0, 64, 0);
        TrackedStraightMove ai = new TrackedStraightMove(robot, 1, 64, 0);
        ai.start();

        for (double x : new double[] { 0.5, 0.9, 1.0 }) {
            robot.moveTo(x, 64, 0);
            ai.update();
            Assertions.assertFalse(ai.ended, "still closing in at x=" + x + " — the move must go on");
        }

        robot.moveTo(1.1, 64, 0); // overshot by one step
        ai.update();

        Assertions.assertTrue(ai.ended, "the first update whose distance did not shrink ends the move");
        Assertions.assertEquals(Vec3.ZERO, robot.motion, "arrival stops the robot dead");
    }

    @Test
    public void aRobotThatStopsMovingEndsTheMoveRatherThanWaitingForever() {
        robot.moveTo(0, 64, 0);
        TrackedStraightMove ai = new TrackedStraightMove(robot, 5, 64, 0);
        ai.start();

        robot.moveTo(0.5, 64, 0);
        ai.update();
        Assertions.assertFalse(ai.ended);

        ai.update(); // blocked: the same distance again
        Assertions.assertTrue(ai.ended, "an unchanged distance counts as 'not shrinking' — no infinite hover");
    }

    @Test
    public void aMoveReloadedMidFlightReAimsAtItsSavedTarget() {
        // Saved mid-approach at x=11, flying -x toward a target at x=10.
        robot.moveTo(12, 64, 0);
        TrackedStraightMove before = new TrackedStraightMove(robot, 10, 64, 0);
        before.start();
        CompoundTag tag = new CompoundTag();
        before.writeSelfToNBT(tag);

        MotionRecordingRobot reloadedRobot = new MotionRecordingRobot();
        reloadedRobot.moveTo(11, 64, 0);
        TrackedStraightMove after = new TrackedStraightMove(reloadedRobot);
        after.loadSelfFromNBT(tag); // what AIRobot.loadFromNBT does for a saved delegate — start() never re-runs

        after.update();
        Assertions.assertTrue(reloadedRobot.motion.x < 0,
                "the first update after a reload must re-aim at the SAVED target (x=10, i.e. -x from x=11); "
                        + "motion was " + reloadedRobot.motion);
        Assertions.assertFalse(after.ended);

        // Closing on x=10, then past it. Every one of these positions is ALSO closer to the origin than the
        // last, so a move still measuring against the unset (0,0,0) destination never sees itself arrive.
        for (double x : new double[] { 10.5, 10.0 }) {
            reloadedRobot.moveTo(x, 64, 0);
            after.update();
            Assertions.assertFalse(after.ended, "still closing on the saved target at x=" + x);
        }
        reloadedRobot.moveTo(9.9, 64, 0);
        after.update();
        Assertions.assertTrue(after.ended,
                "a reloaded move must stop at its saved target, not fly on toward the world origin");
    }

    @Test
    public void theTargetSurvivesAnNbtRoundTrip() {
        TrackedStraightMove before = new TrackedStraightMove(robot, 3.5, 70.5, -2.5);
        CompoundTag tag = new CompoundTag();
        before.writeSelfToNBT(tag);

        TrackedStraightMove after = new TrackedStraightMove(robot);
        after.loadSelfFromNBT(tag);
        CompoundTag again = new CompoundTag();
        after.writeSelfToNBT(again);

        Assertions.assertEquals(3.5, NBTUtilBC.getDouble(again, "x", Double.NaN), EPS);
        Assertions.assertEquals(70.5, NBTUtilBC.getDouble(again, "y", Double.NaN), EPS);
        Assertions.assertEquals(-2.5, NBTUtilBC.getDouble(again, "z", Double.NaN), EPS);
    }

    @Test
    public void aFarAwayTargetKeepsItsHalfBlock() {
        // 14,000,001.5 is not a float (ulp there is 1.0) — the game-test arenas sit at |z| of about 1.4e7.
        TrackedStraightMove before = new TrackedStraightMove(robot, -13_999_999.5, 64.5, 14_000_001.5);
        CompoundTag tag = new CompoundTag();
        before.writeSelfToNBT(tag);

        Assertions.assertEquals(-13_999_999.5, NBTUtilBC.getDouble(tag, "x", Double.NaN), EPS,
                "the half-block offset must survive at far-out coordinates");
        Assertions.assertEquals(14_000_001.5, NBTUtilBC.getDouble(tag, "z", Double.NaN), EPS,
                "the half-block offset must survive at far-out coordinates");
    }

    @Test
    public void aLegacyFloatTargetStillLoads() {
        // Saves written before the target became a double carry float tags (7.1.x's setFloat shape).
        CompoundTag legacy = new CompoundTag();
        legacy.putFloat("x", 3.5F);
        legacy.putFloat("y", 70.5F);
        legacy.putFloat("z", -2.5F);

        TrackedStraightMove ai = new TrackedStraightMove(robot);
        ai.loadSelfFromNBT(legacy);
        CompoundTag out = new CompoundTag();
        ai.writeSelfToNBT(out);

        Assertions.assertEquals(3.5, NBTUtilBC.getDouble(out, "x", Double.NaN), EPS);
        Assertions.assertEquals(70.5, NBTUtilBC.getDouble(out, "y", Double.NaN), EPS);
        Assertions.assertEquals(-2.5, NBTUtilBC.getDouble(out, "z", Double.NaN), EPS);
    }

    /** Records termination through {@code end()}. */
    static class TrackedStraightMove extends AIRobotStraightMoveTo {
        boolean ended;

        TrackedStraightMove(IRobotAccess robot) {
            super(robot);
        }

        TrackedStraightMove(IRobotAccess robot, double x, double y, double z) {
            super(robot, x, y, z);
        }

        @Override
        public void end() {
            ended = true;
        }
    }
}
