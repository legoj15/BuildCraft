/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins that no game test schedules from inside a runnable the framework is already running.
 *
 * <p>{@code GameTestInfo.tickInternal} runs the runnables that {@code runAfterDelay}/{@code runAtTickTime} queue while
 * iterating the fastutil map they sit in, removing each entry after it has run. A scheduling call from inside one of
 * those runnables inserts into that live map. An insert that makes the map rehash invalidates the iterator's
 * position, so another entry can be skipped, run twice or removed in place of the one that just ran; whether the map
 * rehashes depends on how many runnables are pending, so the test passes for months and then flakes. The polls
 * ({@code EntityArenaUtil.tickUntil}/{@code tickUntilThen}, {@code MarkerArenaUtil.Session.await}/{@code awaitThen})
 * therefore queue every tick up front. A test with several phases does the same: it queues each phase at an
 * absolute delay from the test body, or waits on observed state with a poll, or runs its steps through a step
 * runner that polls up front. The framework's own sequences ({@code helper.succeedWhen}, {@code startSequence}) are
 * not affected: they are ticked after the map loop, not inside it.
 *
 * <p>The scan is lexical, over the comment- and string-free source of every game-test file: it finds a scheduling
 * call written inside a lambda passed to another scheduling call. It cannot see a helper method that schedules and is
 * called from such a lambda, so keep scheduling helpers to the poll utilities above.
 */
public class GameTestNestedSchedulingTester {

    private static final String TEST_SOURCES = "src/test/java";

    /** Calls that put a runnable into the framework's per-tick map, directly or as a poll queued up front. */
    private static final Pattern SCHEDULER = Pattern.compile(
        "(?<![\\w])(runAfterDelay|runAtTickTime|tickUntilThen|tickUntil|awaitThen|await)\\s*\\(");

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int matching(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** A method declaration, not a call: the closing parenthesis is followed by {@code {} (or a {@code throws}). */
    private static boolean isDeclaration(String code, int close) {
        String after = code.substring(close + 1).stripLeading();
        return after.startsWith("{") || after.startsWith("throws ");
    }

    /** One scheduling call and where it sits. */
    private record Call(String name, int start, int open, int close, int line) {}

    private static List<Call> schedulingCalls(String code) {
        List<Call> calls = new ArrayList<>();
        Matcher m = SCHEDULER.matcher(code);
        while (m.find()) {
            int open = m.end() - 1;
            int close = matching(code, open);
            if (close < 0 || isDeclaration(code, close)) {
                continue;
            }
            int line = 1 + (int) code.substring(0, m.start()).chars().filter(c -> c == '\n').count();
            calls.add(new Call(m.group(1), m.start(), open, close, line));
        }
        return calls;
    }

    /** Every scheduling call that sits inside a lambda passed to another scheduling call, as {@code "line: inner
     *  inside outer"}. */
    static List<String> nestedSchedules(String rawSource) {
        String code = GameTestPollBudgetTester.PollBudgets.strip(rawSource);
        List<Call> calls = schedulingCalls(code);
        List<String> found = new ArrayList<>();
        for (Call inner : calls) {
            for (Call outer : calls) {
                if (outer.open < inner.start && inner.start < outer.close
                    && code.substring(outer.open, inner.start).contains("->")) {
                    found.add(inner.line + ": " + inner.name + " inside " + outer.name + " (line " + outer.line + ")");
                }
            }
        }
        return found;
    }

    // ---------------------------------------------------------------------------------------------------------
    // The real tree
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void noGameTestSchedulesFromInsideAScheduledRunnable() throws IOException {
        Path root = TestHelper.repoRoot();
        List<String> offenders = new ArrayList<>();
        int files = 0;
        int calls = 0;
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(root.resolve(TEST_SOURCES))) {
            sources = walk.filter(f -> f.toString().endsWith(".java")).toList();
        }
        for (Path file : sources) {
            String source = read(file);
            int here = schedulingCalls(GameTestPollBudgetTester.PollBudgets.strip(source)).size();
            if (here == 0) {
                continue;
            }
            files++;
            calls += here;
            String relative = root.relativize(file).toString().replace('\\', '/');
            for (String nested : nestedSchedules(source)) {
                offenders.add(relative + ":" + nested);
            }
        }
        // Liveness: a scan that stopped seeing the calls would pass vacuously.
        Assertions.assertTrue(files >= 15 && calls >= 60,
            "the scan saw only " + calls + " scheduling calls in " + files + " files - the call pattern drifted");
        Assertions.assertTrue(offenders.isEmpty(),
            "game tests that schedule from inside a scheduled runnable. The framework runs those while iterating the"
                + " map the new entry goes into: queue each phase from the test body at an absolute delay, or wait"
                + " on observed state with EntityArenaUtil.tickUntil / a step runner:\n    "
                + String.join("\n    ", offenders));
    }

    // ---------------------------------------------------------------------------------------------------------
    // The scanner, pinned on synthetic sources
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void scannerFlagsAScheduleInsideAScheduledRunnable() {
        List<String> found = nestedSchedules("""
            class T {
                static void test(GameTestHelper helper) {
                    helper.runAfterDelay(1, () -> {
                        helper.runAfterDelay(5, () -> helper.succeed());
                    });
                }
            }""");
        Assertions.assertEquals(1, found.size(), found.toString());
        Assertions.assertTrue(found.get(0).contains("runAfterDelay inside runAfterDelay"), found.get(0));
    }

    @Test
    public void scannerFlagsAPollStartedFromAPollsThenBlock() {
        List<String> found = nestedSchedules("""
            class T {
                static void test(GameTestHelper helper) {
                    EntityArenaUtil.tickUntil(helper, 50, () -> true, () -> {
                        EntityArenaUtil.tickUntil(helper, 50, () -> true, helper::succeed, "inner");
                    }, "outer");
                }
            }""");
        Assertions.assertEquals(1, found.size(), found.toString());
        Assertions.assertTrue(found.get(0).contains("tickUntil inside tickUntil"), found.get(0));
    }

    @Test
    public void scannerLetsSequentialAndFlattenedSchedulesBe() {
        List<String> found = nestedSchedules("""
            class T {
                static void test(GameTestHelper helper) {
                    helper.runAfterDelay(1, () -> setup(helper));
                    helper.runAfterDelay(1 + 20, () -> helper.succeed());
                    EntityArenaUtil.tickUntil(helper, 50, () -> cond(), helper::succeed, "plain");
                    helper.succeedWhen(() -> helper.runAfterDelay(3, () -> { }));
                }
            }""");
        // The last line schedules from a framework SEQUENCE (succeedWhen), which is ticked outside the map loop.
        Assertions.assertTrue(found.isEmpty(), found.toString());
    }

    @Test
    public void scannerIgnoresDeclarationsCommentsAndStrings() {
        List<String> found = nestedSchedules("""
            class T {
                void runAfterDelay(int ticks, Runnable r) { runAfterDelay(ticks, () -> { }); }
                void await(int ticks, BooleanSupplier c, Runnable then) throws Exception { }
                static void test(GameTestHelper helper) {
                    // helper.runAfterDelay(1, () -> helper.runAfterDelay(2, () -> { }));
                    String s = "helper.runAfterDelay(1, () -> helper.runAfterDelay(2, () -> { }))";
                    helper.runAfterDelay(1, () -> { });
                }
            }""");
        Assertions.assertTrue(found.isEmpty(), found.toString());
    }
}
