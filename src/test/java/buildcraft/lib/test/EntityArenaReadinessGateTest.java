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

    /** Polls {@code gate} once per tick, with {@code tick[0]} holding the current tick; returns the tick it answered
     *  true on, or -1 if it never did within {@code maxTicks}. A failure propagates. */
    private static int answeredAt(int[] tick, BooleanSupplier gate, int maxTicks) {
        for (tick[0] = 1; tick[0] <= maxTicks; tick[0]++) {
            if (gate.getAsBoolean()) {
                return tick[0];
            }
        }
        return -1;
    }

    /** Polls {@code gate} once per tick; returns the tick it failed on, or -1 if it never failed within
     *  {@code maxTicks}. */
    private static int failedAt(int[] tick, BooleanSupplier gate, int maxTicks) {
        for (tick[0] = 1; tick[0] <= maxTicks; tick[0]++) {
            try {
                gate.getAsBoolean();
            } catch (Failed e) {
                return tick[0];
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
        Assertions.assertEquals(5, answeredAt(tick, gate, 10), "a condition that is already true waits for the cell");
        Assertions.assertEquals(List.of(5), askedAt, "and is first asked on the tick the cell became ready");
    }

    @Test
    void theWorkBudgetCountsFromTheTickTheCellStartsTicking() {
        int[] tick = { 0 };
        // Ready on tick 200 (the machine was slow), done 99 ticks later: inside the 100-tick work budget, though
        // 299 ticks after the test began.
        Assertions.assertEquals(299, answeredAt(tick, EntityArenaUtil.gateOnReady(() -> tick[0] >= 200,
                EntityArenaReadinessGateTest::fail, 300, 100, () -> tick[0] >= 299), 400));
        // Ready on the first poll and done on the twentieth: the 20-tick budget's last tick still passes.
        Assertions.assertEquals(20, answeredAt(tick, EntityArenaUtil.gateOnReady(() -> true,
                EntityArenaReadinessGateTest::fail, 300, 20, () -> tick[0] >= 20), 100));
    }

    @Test
    void eachBudgetFailsOnTheTickItRunsOut() {
        int[] tick = { 0 };
        Assertions.assertEquals(30, failedAt(tick, EntityArenaUtil.gateOnReady(() -> tick[0] >= 10,
                EntityArenaReadinessGateTest::fail, 50, 20, () -> false), 100),
                "ten ticks to become ready plus the 20-tick work budget");
        Assertions.assertEquals(21, failedAt(tick, EntityArenaUtil.gateOnReady(() -> true,
                EntityArenaReadinessGateTest::fail, 300, 20, () -> false), 100),
                "ready on the first poll: the whole 20-tick work budget is spent before failing");
        Assertions.assertEquals(50, failedAt(tick, EntityArenaUtil.gateOnReady(() -> false,
                EntityArenaReadinessGateTest::fail, 50, 20, () -> true), 100),
                "a cell that never ticks fails on the readiness budget's last tick, whatever the work budget");
    }
}
