/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The cleanup callback of {@link EntityArenaUtil#tickUntil}/{@link EntityArenaUtil#tickUntilThen} runs exactly
 *  once on every way out of the poll: success, a failed assertion inside the condition or a phase, and the poll's
 *  own timeout. Before it existed, cleanup lived at the end of each test's success block, so a failed robot test
 *  left its robot (and the station reservations it held) in the shared level for later tests to trip over.
 *
 *  <p>Driven through the GameTestHelper-free core with a fake scheduler that behaves like {@code GameTestInfo}: it
 *  runs each tick's runnables in order and ends the test at the first one that throws. */
public class EntityArenaUtilCleanupTest {

    /** A stand-in for {@code GameTestHelper.fail}, which throws on every node. */
    private static final class Failed extends RuntimeException {
        Failed(String message) {
            super(message);
        }
    }

    /** Collects scheduled runnables per tick and replays them the way the game-test framework does. */
    private static final class FakeTicks implements EntityArenaUtil.Scheduler {
        private final TreeMap<Integer, List<Runnable>> byTick = new TreeMap<>();
        RuntimeException thrown;

        @Override
        public void runAfterDelay(int ticks, Runnable runnable) {
            byTick.computeIfAbsent(ticks, t -> new ArrayList<>()).add(runnable);
        }

        /** Runs every tick; stops after the tick on which a runnable threw (the test is then over). */
        void runAll() {
            for (List<Runnable> runnables : byTick.values()) {
                for (Runnable runnable : runnables) {
                    try {
                        runnable.run();
                    } catch (RuntimeException e) {
                        thrown = e;
                    }
                }
                if (thrown != null) {
                    return;
                }
            }
        }
    }

    private static void fail(String message) {
        throw new Failed(message);
    }

    @Test
    void cleanupRunsOnceAfterTheSuccessBlock() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        int[] polls = { 0 };
        EntityArenaUtil.schedulePoll(ticks, EntityArenaUtilCleanupTest::fail, 10,
                () -> ++polls[0] >= 3, () -> log.add("then"), "never", () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertNull(ticks.thrown);
        Assertions.assertEquals(List.of("then", "cleanup"), log);
    }

    @Test
    void cleanupRunsWhenTheSuccessBlockFails() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        EntityArenaUtil.schedulePoll(ticks, EntityArenaUtilCleanupTest::fail, 10,
                () -> true, () -> fail("assertion in then"), "never", () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertInstanceOf(Failed.class, ticks.thrown);
        Assertions.assertEquals("assertion in then", ticks.thrown.getMessage());
        Assertions.assertEquals(List.of("cleanup"), log);
    }

    @Test
    void cleanupRunsWhenTheConditionFails() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        int[] polls = { 0 };
        EntityArenaUtil.schedulePoll(ticks, EntityArenaUtilCleanupTest::fail, 10,
                () -> {
                    if (++polls[0] == 2) {
                        fail("assertion in condition");
                    }
                    return false;
                }, () -> log.add("then"), "never", () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertEquals("assertion in condition", ticks.thrown.getMessage());
        Assertions.assertEquals(List.of("cleanup"), log);
    }

    @Test
    void cleanupRunsOnTimeout() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        EntityArenaUtil.schedulePoll(ticks, EntityArenaUtilCleanupTest::fail, 5,
                () -> false, () -> log.add("then"), "the thing never happened", () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertTrue(ticks.thrown.getMessage().startsWith("the thing never happened"),
                ticks.thrown.getMessage());
        Assertions.assertEquals(List.of("cleanup"), log);
    }

    @Test
    void aFailingCleanupDoesNotHideTheOriginalFailure() {
        FakeTicks ticks = new FakeTicks();
        EntityArenaUtil.schedulePoll(ticks, EntityArenaUtilCleanupTest::fail, 5,
                () -> true, () -> fail("the real failure"), "never", () -> fail("cleanup broke"));
        ticks.runAll();
        Assertions.assertEquals("the real failure", ticks.thrown.getMessage());
        Assertions.assertEquals("cleanup broke", ticks.thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void twoPhaseCleanupRunsAfterTheSecondPhaseOnly() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        EntityArenaUtil.schedulePollThen(ticks, EntityArenaUtilCleanupTest::fail, 20,
                () -> true, () -> log.add("first"), 3, () -> log.add("second"), "never", () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertNull(ticks.thrown);
        Assertions.assertEquals(List.of("first", "second", "cleanup"), log);
    }

    @Test
    void twoPhaseCleanupRunsWhenTheFirstPhaseFails() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        EntityArenaUtil.schedulePollThen(ticks, EntityArenaUtilCleanupTest::fail, 20,
                () -> true, () -> fail("first broke"), 3, () -> log.add("second"), "never", () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertEquals("first broke", ticks.thrown.getMessage());
        Assertions.assertEquals(List.of("cleanup"), log);
    }

    @Test
    void twoPhaseCleanupRunsWhenTheSecondPhaseFails() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        EntityArenaUtil.schedulePollThen(ticks, EntityArenaUtilCleanupTest::fail, 20,
                () -> true, () -> log.add("first"), 3, () -> fail("second broke"), "never", () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertEquals("second broke", ticks.thrown.getMessage());
        Assertions.assertEquals(List.of("first", "cleanup"), log);
    }

    @Test
    void twoPhaseCleanupRunsOnTimeout() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        EntityArenaUtil.schedulePollThen(ticks, EntityArenaUtilCleanupTest::fail, 10,
                () -> false, () -> log.add("first"), 3, () -> log.add("second"), "never came true",
                () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertTrue(ticks.thrown.getMessage().startsWith("never came true"), ticks.thrown.getMessage());
        Assertions.assertEquals(List.of("cleanup"), log);
    }

    /** A condition that comes true too late for the second phase to fit never runs the first phase: the poll
     *  gives up on the last tick a {@code gapTicks} gap still fits after (60 of 120), fails, and cleans up. */
    @Test
    void twoPhaseLateConditionFailsBeforeTheFirstPhaseAndCleansUp() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        int[] polls = { 0 };
        EntityArenaUtil.schedulePollThen(ticks, EntityArenaUtilCleanupTest::fail, 120,
                () -> ++polls[0] >= 61, () -> log.add("first"), 60, () -> log.add("second"), "too late",
                () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertNotNull(ticks.thrown, "the poll must fail, not fall through to the framework timeout");
        Assertions.assertTrue(ticks.thrown.getMessage().startsWith("too late"), ticks.thrown.getMessage());
        Assertions.assertEquals(60, polls[0], "gave up on tick 60, the last tick a 60-tick gap fits after");
        Assertions.assertEquals(List.of("cleanup"), log);
    }

    /** A gap at least as long as the poll: the first phase fires on tick 1, but the second would land past
     *  {@code maxTicks}. The poll must still fail with its own message on its last tick and clean up, instead of
     *  leaving the test to the framework's generic timeout with its entities still in the level. */
    @Test
    void twoPhaseSecondPhasePastMaxTicksFailsAndCleansUp() {
        FakeTicks ticks = new FakeTicks();
        List<String> log = new ArrayList<>();
        EntityArenaUtil.schedulePollThen(ticks, EntityArenaUtilCleanupTest::fail, 5,
                () -> true, () -> log.add("first"), 10, () -> log.add("second"), "second phase never ran",
                () -> log.add("cleanup"));
        ticks.runAll();
        Assertions.assertNotNull(ticks.thrown, "the poll must fail on its last tick");
        Assertions.assertTrue(ticks.thrown.getMessage().startsWith("second phase never ran"),
                ticks.thrown.getMessage());
        Assertions.assertEquals(List.of("first", "cleanup"), log);
    }
}
