/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.StatementSlot;
import buildcraft.lib.misc.NBTUtilBC;

/** The cell {@link AIRobotGotoStation} pathfinds to before its final straight move onto the dock.
 *
 *  <p>7.1.x had two classes here: {@code AIRobotGotoStation}, which flew to {@code station + side}, and
 *  {@code AIRobotGoAndLinkToDock} — the take-as-main equivalent — which flew to {@code station + side*2}.
 *  The port merged them behind the {@code takeAsMain} flag; this pins that the flag still picks the right
 *  approach distance. */
public class AIRobotGotoStationTest {

    private static final BlockPos STATION = new BlockPos(4, 5, 6);

    @Test
    public void aPlainVisitApproachesOneCellOut() {
        Assertions.assertEquals(STATION.relative(Direction.EAST, 1),
                AIRobotGotoStation.approachCell(STATION, Direction.EAST, false),
                "a plain station visit stops one cell out (7.1.x AIRobotGotoStation)");
    }

    @Test
    public void linkingAsMainApproachesTwoCellsOut() {
        Assertions.assertEquals(STATION.relative(Direction.EAST, 2),
                AIRobotGotoStation.approachCell(STATION, Direction.EAST, true),
                "linking a home station stops two cells out (7.1.x AIRobotGoAndLinkToDock)");
    }

    @Test
    public void theApproachFollowsTheStationSide() {
        for (Direction side : Direction.values()) {
            Assertions.assertEquals(STATION.relative(side, 1),
                    AIRobotGotoStation.approachCell(STATION, side, false),
                    "the plain approach is one cell along " + side);
            Assertions.assertEquals(STATION.relative(side, 2),
                    AIRobotGotoStation.approachCell(STATION, side, true),
                    "the linking approach is two cells along " + side);
        }
    }

    // ── the chain: reserve → AIRobotGotoBlock → AIRobotStraightMoveTo → dock ─────────────────────────
    // Driven against a mock robot, so the one step that needs a live entity — the station.take() cast in
    // start() — is not reached here; the game test robot_goto_station_refuses_a_taken_station covers it.

    private final MotionRecordingRobot robot = new MotionRecordingRobot();
    private final StationRegistry registry = new StationRegistry();

    {
        robot.setRegistry(registry);
    }

    @Test
    public void aStationlessGotoFailsInsteadOfCrashing() {
        TrackedGotoStation ai = new TrackedGotoStation(robot, null);

        ai.start();

        Assertions.assertTrue(ai.ended, "a goto with no station ends at once");
        Assertions.assertFalse(ai.success(), "…as a failure, so the board can react");
        Assertions.assertNull(ai.getDelegateAI(), "and never starts flying");
    }

    @Test
    public void aStationThatIsNoLongerRegisteredFailsTheGoto() {
        StubStation station = new StubStation(STATION, Direction.UP);
        // Deliberately NOT put in the registry: the station was removed between the search and the goto.
        TrackedGotoStation ai = new TrackedGotoStation(robot, station);

        ai.start();

        Assertions.assertTrue(ai.ended);
        Assertions.assertFalse(ai.success(), "an unregistered station cannot be reached");
        Assertions.assertNull(ai.getDelegateAI());
    }

    @Test
    public void aRobotAlreadyDockedThereSucceedsWithoutMoving() {
        StubStation station = new StubStation(STATION, Direction.UP);
        registry.station = station;
        robot.setDockingStation(station);
        TrackedGotoStation ai = new TrackedGotoStation(robot, station);

        ai.start();

        Assertions.assertTrue(ai.ended);
        Assertions.assertTrue(ai.success(), "already there is a success");
        Assertions.assertNull(ai.getDelegateAI(), "no flight when the robot is already docked at the target");
        Assertions.assertEquals(0, robot.undocks, "and it is not undocked from the very station it wants");
    }

    @Test
    public void aSuccessfulApproachHandsOverToAStraightMoveOntoTheFaceCentre() {
        for (Direction side : Direction.values()) {
            MotionRecordingRobot r = new MotionRecordingRobot();
            StationRegistry reg = new StationRegistry();
            r.setRegistry(reg);
            StubStation station = new StubStation(STATION, side);
            reg.station = station;
            TrackedGotoStation ai = new TrackedGotoStation(r, station);

            ai.delegateAIEnded(new AIRobotGotoBlock(r, 0, 0, 0)); // a finished approach leg (success by default)

            Assertions.assertInstanceOf(AIRobotStraightMoveTo.class, ai.getDelegateAI(),
                    "after the approach the robot flies straight onto the dock (" + side + ")");
            assertTarget(ai.getDelegateAI(),
                    STATION.getX() + 0.5 + side.getStepX() * 0.5,
                    STATION.getY() + 0.5 + side.getStepY() * 0.5,
                    STATION.getZ() + 0.5 + side.getStepZ() * 0.5,
                    "the straight move aims at the centre of the station's " + side + " face");
            Assertions.assertFalse(ai.ended, "the goto is still running while the straight move flies");
        }
    }

    @Test
    public void aFarAwayStationIsStillApproachedToTheHalfBlock() {
        // The game-test arenas (and any base past ~8.4M blocks) sit where float can no longer hold a .5.
        BlockPos far = new BlockPos(-13_999_999, 64, 14_000_001);
        StubStation station = new StubStation(far, Direction.UP);
        registry.station = station;
        TrackedGotoStation ai = new TrackedGotoStation(robot, station);

        ai.delegateAIEnded(new AIRobotGotoBlock(robot, 0, 0, 0));

        assertTarget(ai.getDelegateAI(), far.getX() + 0.5, far.getY() + 1.0, far.getZ() + 0.5,
                "the dock point of a far-away station must keep its half-block offsets");
    }

    @Test
    public void aFailedApproachFailsTheGotoWithoutDocking() {
        StubStation station = new StubStation(STATION, Direction.NORTH);
        registry.station = station;
        TrackedGotoStation ai = new TrackedGotoStation(robot, station);

        ai.delegateAIEnded(new FailedGotoBlock(robot));

        Assertions.assertTrue(ai.ended);
        Assertions.assertFalse(ai.success(), "an unreachable station fails the goto");
        Assertions.assertTrue(robot.docks.isEmpty(), "and the robot is never docked");
        Assertions.assertNull(ai.getDelegateAI(), "no straight move after a failed approach");
    }

    @Test
    public void theWholeFlightEndsDockedAtTheStation() {
        StubStation station = new StubStation(STATION, Direction.EAST);
        registry.station = station;
        TrackedGotoStation ai = new TrackedGotoStation(robot, station);
        // Where the approach leg left the robot: the cell in front of the east face.
        robot.moveTo(STATION.getX() + 1.5, STATION.getY() + 0.5, STATION.getZ() + 0.5);

        ai.delegateAIEnded(new AIRobotGotoBlock(robot, 0, 0, 0));
        AIRobotStraightMoveTo straight = (AIRobotStraightMoveTo) ai.getDelegateAI();
        Assertions.assertTrue(robot.motion.x < 0, "the straight move flies -x, onto the east face");

        double dockX = STATION.getX() + 1.0;
        for (double x : new double[] { dockX + 0.4, dockX + 0.1, dockX - 0.15 }) { // the last one overshot
            robot.moveTo(x, STATION.getY() + 0.5, STATION.getZ() + 0.5);
            straight.update();
        }

        Assertions.assertTrue(ai.ended, "the straight move's arrival ends the goto");
        Assertions.assertTrue(ai.success(), "…as a success");
        Assertions.assertEquals(1, robot.docks.size(), "the robot docks exactly once");
        Assertions.assertSame(station, robot.docks.get(0), "at the station it flew to");
    }

    @Test
    public void aStationRemovedMidFlightFailsTheGotoWithoutDocking() {
        StubStation station = new StubStation(STATION, Direction.UP);
        registry.station = station;
        TrackedGotoStation ai = new TrackedGotoStation(robot, station);
        ai.delegateAIEnded(new AIRobotGotoBlock(robot, 0, 0, 0));
        AIRobotStraightMoveTo straight = (AIRobotStraightMoveTo) ai.getDelegateAI();

        registry.station = null; // the pipe was broken while the robot was on its final approach
        straight.terminate();

        Assertions.assertTrue(ai.ended);
        Assertions.assertFalse(ai.success(), "a vanished station is not reached");
        Assertions.assertTrue(robot.docks.isEmpty(), "and nothing is docked to a station that no longer exists");
    }

    private static void assertTarget(AIRobot ai, double x, double y, double z, String message) {
        CompoundTag tag = new CompoundTag();
        ai.writeSelfToNBT(tag);
        Assertions.assertEquals(x, NBTUtilBC.getDouble(tag, "x", Double.NaN), 1.0E-9, message + " (x)");
        Assertions.assertEquals(y, NBTUtilBC.getDouble(tag, "y", Double.NaN), 1.0E-9, message + " (y)");
        Assertions.assertEquals(z, NBTUtilBC.getDouble(tag, "z", Double.NaN), 1.0E-9, message + " (z)");
    }

    /** Records termination through {@code end()}. */
    private static final class TrackedGotoStation extends AIRobotGotoStation {
        boolean ended;

        TrackedGotoStation(IRobotAccess robot, DockingStation station) {
            super(robot, station);
        }

        @Override
        public void end() {
            ended = true;
        }
    }

    /** An approach leg that did not make it. */
    private static final class FailedGotoBlock extends AIRobotGotoBlock {
        FailedGotoBlock(IRobotAccess robot) {
            super(robot, 0, 0, 0);
            setSuccess(false);
        }
    }

    /** A registry that resolves exactly one station (or none), whatever pos/side is asked for. */
    private static final class StationRegistry extends MockRobotAccess.InertRobotRegistry {
        DockingStation station;

        @Override
        public DockingStation getStation(BlockPos pos, Direction side) {
            return station != null && station.getPos().equals(pos) && station.side() == side ? station : null;
        }
    }

    private static final class StubStation extends DockingStation {
        StubStation(BlockPos pos, Direction side) {
            super(pos, side);
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }
    }
}
