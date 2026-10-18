/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

    @Test
    public void midSearchSaveRestoreRestartsTheSearchInsteadOfCrashing() {
        // Saved while the A* search was still running: writeSelfToNBT only records "path" once a path
        // EXISTS, so this NBT has no path key. The restore must start a fresh search, not try to resume a
        // path that was never written.
        robot.setPosition(new Vec3(0.5, 4.5, 0.5));

        AIRobotGotoBlock writer = new AIRobotGotoBlock(robot, 30, 5, 30);
        CompoundTag nbt = new CompoundTag();
        writer.writeSelfToNBT(nbt);
        Assertions.assertFalse(nbt.contains("path"), "fixture: a mid-search save has no path to restore");

        AIRobotGotoBlock reader = new AIRobotGotoBlock(robot);
        reader.loadSelfFromNBT(nbt);

        RecordingParent parent = new RecordingParent(robot);
        parent.startDelegateAI(reader);

        Assertions.assertDoesNotThrow(reader::update,
                "a mid-search restore has no path to resume — the first update must start a fresh search, "
                        + "not dereference the path that was never written");
        Assertions.assertNull(parent.ended,
                "the fresh search is still running — nothing may terminate on the first tick");
    }

    @Test
    public void aSearchThatExhaustsItsExpansionBudgetFailsInsteadOfSearchingForever() {
        // 7.1.x ran this search on a background thread bounded to 50 × PATH_ITERATIONS expansions; the
        // synchronous port must keep that hard cap or a robot asked to cross an inexhaustible search space
        // iterates 50 expansions a tick, every tick, forever.
        robot.setPosition(new Vec3(0.5, 4.5, 0.5));

        AIRobotGotoBlock gotoBlock = new AIRobotGotoBlock(robot, 30, 5, 30);
        gotoBlock.maxTotalExpansions = 0; // no expansions granted: the first search tick hits the cap

        RecordingParent parent = new RecordingParent(robot);
        parent.startDelegateAI(gotoBlock);

        gotoBlock.update(); // constructs the PathFinding; no expansions yet
        Assertions.assertNull(parent.ended, "construction tick: the search has only just begun");

        // The cap check must fire BEFORE iterate() — an over-budget search may not expand even once more.
        // (With the mock's null level, any real expansion would NPE inside SoftBlockAccess, so reaching the
        // assertions below at all proves iterate was never called.)
        gotoBlock.update();
        Assertions.assertSame(gotoBlock, parent.ended, "an over-budget search must give up");
        Assertions.assertFalse(gotoBlock.success(), "giving up is a failure, not a silent success");
    }

    @Test
    public void aPathStepThatTurnsHardIsNeverFlownIntoOnTheNextTick() {
        // A planned path whose next cell stops being soft mid-flight (a chunk unloaded under it, a wall placed):
        // setNextInPath rejects the step and drops the path. The path list is the PathFinding's own result, so
        // re-reading that result on the next tick would hand back the rejected cell as an unchecked "first" step
        // and aim the robot straight into it. The goto must re-plan from where it is (here: no way through, so
        // it fails) and never aim at the rejected cell.
        Set<BlockPos> soft = new HashSet<>();
        for (int x = 0; x <= 6; x++) {
            soft.add(new BlockPos(x, 0, 0));
        }
        FlyingRobot flyer = new FlyingRobot();
        flyer.setPosition(new Vec3(0.5, 0.5, 0.5));

        AIRobotGotoBlock gotoBlock = new AIRobotGotoBlock(flyer, 6, 0, 0);
        gotoBlock.blockAccess = soft::contains;
        RecordingParent parent = new RecordingParent(flyer);
        parent.startDelegateAI(gotoBlock);

        for (int i = 0; i < 100 && flyer.aims.isEmpty(); i++) {
            gotoBlock.update();
        }
        Assertions.assertFalse(flyer.aims.isEmpty(), "fixture: the search plans a path and aims its first step");

        // Fly to the aimed cell, then one step on: arrive, overshoot, and the next step is aimed (and checked).
        flyToAimedCellAndOvershoot(flyer, gotoBlock);
        BlockPos current = flyer.aims.getLast();
        BlockPos rejected = current.east();
        Assertions.assertTrue(soft.contains(rejected), "fixture: the next planned step is still soft");

        soft.remove(rejected);
        int aimsBeforeRejection = flyer.aims.size();
        flyToAimedCellAndOvershoot(flyer, gotoBlock);

        for (int i = 0; i < 100 && parent.ended == null; i++) {
            gotoBlock.update();
        }
        Assertions.assertFalse(flyer.aims.subList(aimsBeforeRejection, flyer.aims.size()).contains(rejected),
                "a rejected step must never be aimed at - the goto re-plans from where the robot is instead");
        Assertions.assertSame(gotoBlock, parent.ended, "the re-plan finds no way through the blocked corridor");
        Assertions.assertFalse(gotoBlock.success(), "and the goto fails rather than reporting arrival");
    }

    /** A flight saved mid-way must come back aimed at the same cell. Floats are exact only up to 2^24: past that an
     *  odd coordinate rounded to its even neighbour on reload, and the restored flight aimed one cell off. The
     *  observable is {@code start()}'s "already there" check, made against the restored target. */
    @Test
    public void aTargetPastFloatPrecisionSurvivesASaveAndLoad() {
        int x = 20_000_001;
        int z = -20_000_001;
        AIRobotGotoBlock writer = new AIRobotGotoBlock(robot, x, 64, z);
        CompoundTag nbt = new CompoundTag();
        writer.writeSelfToNBT(nbt);
        AIRobotGotoBlock reader = new AIRobotGotoBlock(robot);
        reader.loadSelfFromNBT(nbt);

        robot.setPosition(new Vec3(x + 0.5, 64.5, z + 0.5));
        RecordingParent parent = new RecordingParent(robot);
        parent.startDelegateAI(reader);

        Assertions.assertSame(reader, parent.ended,
                "the reloaded flight's target is still the robot's own cell, not a neighbour of it");
    }

    /** A flight saved before the target went to doubles stored it as float tags; those still read back. */
    @Test
    public void aLegacyFloatTargetStillLoads() {
        CompoundTag nbt = new CompoundTag();
        nbt.putFloat("finalX", 3f);
        nbt.putFloat("finalY", 4f);
        nbt.putFloat("finalZ", 5f);
        AIRobotGotoBlock reader = new AIRobotGotoBlock(robot);
        reader.loadSelfFromNBT(nbt);

        robot.setPosition(new Vec3(3.5, 4.5, 5.5));
        RecordingParent parent = new RecordingParent(robot);
        parent.startDelegateAI(reader);

        Assertions.assertSame(reader, parent.ended, "a float-tagged target is read, widened, as before");
    }

    /** A flight handed a stored path (no search of its own) that finds its next step turned hard drops the path
     *  and re-plans; until the new search has a path, the robot must stop, not carry on toward the rejected cell. */
    @Test
    public void aRePlanFromAStoredPathStopsTheRobot() {
        MotionRecordingRobot flyer = new MotionRecordingRobot() {
            @Override
            public boolean isMoving() {
                return true;
            }
        };
        flyer.moveTo(0.5, 0.5, 0.5);
        LinkedList<BlockPos> path = new LinkedList<>(List.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
                new BlockPos(3, 0, 0)));
        AIRobotGotoBlock gotoBlock = new AIRobotGotoBlock(flyer, path);
        gotoBlock.blockAccess = pos -> false; // every step after the first has turned hard
        RecordingParent parent = new RecordingParent(flyer);
        parent.startDelegateAI(gotoBlock);

        // Arrive on the first step's centre, then drift past it: the distance grows, so the AI moves on to the
        // next step, checks it, and finds it hard.
        flyer.moveTo(1.5, 0.5, 0.5);
        gotoBlock.update();
        Assertions.assertNotEquals(Vec3.ZERO, flyer.motion, "fixture: the robot is flying the path");
        flyer.moveTo(1.51, 0.5, 0.5);
        gotoBlock.update();

        Assertions.assertNull(parent.ended, "fixture: the flight re-plans rather than ending");
        Assertions.assertEquals(Vec3.ZERO, flyer.motion, "the re-plan stops the robot while the new search runs");
    }

    /** Puts the robot on the centre of the cell it was last aimed at (one tick: distance 0 becomes the previous
     *  distance), then nudges it past (one tick: the distance grew, so the AI moves on to the next step). */
    private static void flyToAimedCellAndOvershoot(FlyingRobot flyer, AIRobotGotoBlock gotoBlock) {
        BlockPos aimed = flyer.aims.getLast();
        flyer.setPosition(new Vec3(aimed.getX() + 0.5, aimed.getY() + 0.5, aimed.getZ() + 0.5));
        gotoBlock.update();
        flyer.setPosition(new Vec3(aimed.getX() + 0.51, aimed.getY() + 0.5, aimed.getZ() + 0.5));
        gotoBlock.update();
    }

    /** A robot in flight that records every block it is aimed at. */
    private static class FlyingRobot extends MockRobotAccess {
        final List<BlockPos> aims = new ArrayList<>();

        @Override
        public void aimItemAt(BlockPos pos) {
            aims.add(pos);
        }

        @Override
        public boolean isMoving() {
            return true;
        }
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
