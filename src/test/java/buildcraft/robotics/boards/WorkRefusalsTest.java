/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import buildcraft.robotics.ai.MockRobotAccess;

/** The give-up count of the farming boards: the Nth failed attempt IN A ROW tells the board to sleep, a worked
 *  attempt in between starts the count over, and giving up starts it over too. (What the memory does with the blocks
 *  is covered against a real level by {@code FarmPlantRefusalTester}.) */
public class WorkRefusalsTest {

    private static final BlockPos SPOT = new BlockPos(1, 64, 1);

    private static boolean failOnce(WorkRefusals refusals) {
        return refusals.failed(null, SPOT);
    }

    @Test
    public void theNthFailureInARowGivesUp() {
        WorkRefusals refusals = new WorkRefusals(new MockRobotAccess());
        for (int i = 1; i < WorkRefusals.GIVE_UP_AFTER; i++) {
            Assertions.assertFalse(failOnce(refusals), "failure " + i + " is not yet the last straw");
        }
        Assertions.assertTrue(failOnce(refusals), "failure " + WorkRefusals.GIVE_UP_AFTER + " gives up");
    }

    @Test
    public void aWorkedAttemptStartsTheCountOver() {
        WorkRefusals refusals = new WorkRefusals(new MockRobotAccess());
        for (int i = 1; i < WorkRefusals.GIVE_UP_AFTER; i++) {
            failOnce(refusals);
        }
        refusals.succeeded();
        for (int i = 1; i < WorkRefusals.GIVE_UP_AFTER; i++) {
            Assertions.assertFalse(failOnce(refusals),
                    "failures before the success do not count towards giving up (" + i + " since)");
        }
        Assertions.assertTrue(failOnce(refusals), "the count restarted: it takes a full run of failures again");
    }

    @Test
    public void givingUpStartsTheCountOver() {
        WorkRefusals refusals = new WorkRefusals(new MockRobotAccess());
        for (int i = 1; i < WorkRefusals.GIVE_UP_AFTER; i++) {
            failOnce(refusals);
        }
        Assertions.assertTrue(failOnce(refusals), "precondition: gave up");
        Assertions.assertFalse(failOnce(refusals), "the robot that slept and woke starts counting from zero");
    }

    @Test
    public void aBlockRefusedWhileSearchingIsNotAnAttempt() {
        WorkRefusals refusals = new WorkRefusals(new MockRobotAccess());
        for (int i = 0; i < WorkRefusals.GIVE_UP_AFTER * 2; i++) {
            refusals.remember(null, SPOT);
        }
        for (int i = 1; i < WorkRefusals.GIVE_UP_AFTER; i++) {
            Assertions.assertFalse(failOnce(refusals), "refusals met while searching do not count as failed attempts");
        }
    }
}
