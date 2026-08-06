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
