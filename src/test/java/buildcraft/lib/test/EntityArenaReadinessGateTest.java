/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@link EntityArenaUtil#gateOnReady}: a robot test's work budget counts from the moment its arena cell is
 *  entity-ticking, not from the test's first tick.
 *
 *  <p>Why it exists: a forced chunk's promotion to entity-ticking is asynchronous and an arena can straddle a chunk
 *  border, so one half of the cell can start ticking ~20 ticks after the other on an idle machine (measured on 26.3
 *  with only the robot's chunk forced) and far later when six game-test servers share the machine. A poll budget
 *  that starts at tick 1 then measures the machine, not the robot: {@code robot_break_canopy_tree_vines_fall_with_decay}
 *  and {@code robot_picker_picks_up_dropped_item} flaked that way under {@code fullTestSuite}. Driven poll by poll,
 *  one call of the returned condition being one game tick, the way {@link EntityArenaUtil#tickUntil} drives it. */
public class EntityArenaReadinessGateTest {

    /** A stand-in for {@code GameTestHelper.fail}, which throws on every node. */
    private static final class Failed extends RuntimeException {
        Failed(String message) {
            super(message);
        }
    }

    private static void fail(String message) {
        throw new Failed(message);
    }

    /** Polls the gate once per tick until it answers true or fails; returns the tick it answered true on. */
    private static int pollUntilTrue(BooleanSupplier gate, int maxPolls) {
        for (int tick = 1; tick <= maxPolls; tick++) {
            if (gate.getAsBoolean()) {
                return tick;
            }
        }
        return -1;
    }

    @Test
    void theConditionIsNotAskedBeforeTheCellIsTicking() {
        int[] tick = { 0 };
        List<Integer> askedAt = new ArrayList<>();
        BooleanSupplier gate = EntityArenaUtil.gateOnReady(() -> tick[0] >= 5, EntityArenaReadinessGateTest::fail,
                100, 100, () -> {
                    askedAt.add(tick[0]);
                    return true;
                });
        int answeredAt = -1;
        for (tick[0] = 1; tick[0] <= 10 && answeredAt < 0; tick[0]++) {
            if (gate.getAsBoolean()) {
                answeredAt = tick[0];
            }
        }
        Assertions.assertEquals(5, answeredAt, "a condition that is already true waits for the cell");
        Assertions.assertEquals(List.of(5), askedAt, "and is first asked on the tick the cell became ready");
    }

    @Test
    void theWorkBudgetStartsWhenTheCellStartsTicking() {
        int[] tick = { 0 };
        // Ready on tick 200 (the machine was slow), done 99 ticks later: inside the 100-tick work budget, though
        // 299 ticks after the test began.
        BooleanSupplier gate = EntityArenaUtil.gateOnReady(() -> tick[0] >= 200, EntityArenaReadinessGateTest::fail,
                300, 100, () -> tick[0] >= 299);
        int answeredAt = -1;
        for (tick[0] = 1; tick[0] <= 400 && answeredAt < 0; tick[0]++) {
            if (gate.getAsBoolean()) {
                answeredAt = tick[0];
            }
        }
        Assertions.assertEquals(299, answeredAt);
    }

    @Test
    void workThatOverrunsItsBudgetAfterReadinessFailsAndSaysWhen() {
        int[] tick = { 0 };
        BooleanSupplier gate = EntityArenaUtil.gateOnReady(() -> tick[0] >= 10, EntityArenaReadinessGateTest::fail,
                50, 20, () -> false);
        Failed failure = null;
        for (tick[0] = 1; tick[0] <= 100 && failure == null; tick[0]++) {
            try {
                gate.getAsBoolean();
            } catch (Failed e) {
                failure = e;
            }
        }
        Assertions.assertNotNull(failure, "the work budget must run out");
        Assertions.assertEquals(30, tick[0] - 1, "ten ticks to become ready plus the 20-tick work budget");
        Assertions.assertTrue(failure.getMessage().contains("20 ticks after the cell became entity-ticking"),
                failure.getMessage());
        Assertions.assertTrue(failure.getMessage().contains("tick 10"), failure.getMessage());
    }

    @Test
    void aCellThatNeverTicksFailsAsAMachineProblemNotARobotOne() {
        int[] tick = { 0 };
        BooleanSupplier gate = EntityArenaUtil.gateOnReady(() -> false, EntityArenaReadinessGateTest::fail,
                50, 20, () -> true);
        Failed failure = null;
        for (tick[0] = 1; tick[0] <= 100 && failure == null; tick[0]++) {
            try {
                gate.getAsBoolean();
            } catch (Failed e) {
                failure = e;
            }
        }
        Assertions.assertNotNull(failure, "a cell that never ticks must fail the poll");
        Assertions.assertEquals(50, tick[0] - 1, "on the last tick of the readiness budget");
        Assertions.assertTrue(failure.getMessage().contains("never became entity-ticking within 50 ticks"),
                failure.getMessage());
    }

    @Test
    void aCellThatIsAlreadyTickingGivesTheWholeWorkBudget() {
        int[] tick = { 0 };
        int[] doneAt = { 20 };
        BooleanSupplier gate = EntityArenaUtil.gateOnReady(() -> true, EntityArenaReadinessGateTest::fail,
                300, 20, () -> tick[0] >= doneAt[0]);
        int answeredAt = -1;
        for (tick[0] = 1; tick[0] <= 100 && answeredAt < 0; tick[0]++) {
            if (gate.getAsBoolean()) {
                answeredAt = tick[0];
            }
        }
        Assertions.assertEquals(20, answeredAt, "ready on the first poll, done on the twentieth: the budget's last tick");
        Assertions.assertEquals(-1, pollUntilTrue(EntityArenaUtil.gateOnReady(() -> true, m -> { }, 300, 20,
                () -> false), 19), "and nothing fails before the work budget is spent");
    }
}
