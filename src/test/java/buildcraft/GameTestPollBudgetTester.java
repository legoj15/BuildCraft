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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.DropArenaUtil;

/**
 * Pins that a game test's framework watchdog ({@code max_ticks} in its {@code test_instance} manifest) outlasts
 * every poll the test body runs. It covers EVERY registered game test, not only the robot ones: any test that polls
 * (a fluid test waiting on a scheduled tick, a drop test waiting on its items) cleans up in the poll the same way.
 *
 * <p>{@code EntityArenaUtil.tickUntil}, {@code tickUntilThen}, {@code ConfigScope.succeedWhen} and the marker
 * session's {@code await}/{@code awaitThen} clean the test's entities, markers and rig up on EVERY way out of their
 * poll, including their own timeout. The framework's timeout is a second clock, and it knows nothing about that
 * cleanup: when it fires first, it fails the test and the poll's cleanup never runs, so a robot stays in the shared
 * level, keeps its station reservations and keeps flying into later tests (the leak
 * {@code robot_search_entity_nearest_and_range} had with a 20 tick manifest over a 60 tick poll), or a marker stays
 * in the dimension-wide marker cache that every later marker test scans. The failure is silent in the test that
 * caused it and surfaces as a flake in a later one, which is exactly why it is pinned here and not left to review.
 *
 * <p>The poll budget IS computable from source: every poll takes its {@code maxTicks} as the second argument, and
 * in practice that is an integer literal, a {@code static final int} of the same file or a {@code +} sum of those
 * ({@code READY + WORK}). The scan follows the registered method's body plus every same-file method it calls (a
 * poll hidden in a fixture helper still counts), ignores comments and string contents (the inactive branch of a
 * Stonecutter directive is a comment), and resolves the budget of each poll it finds. A budget it cannot resolve
 * to a number (a parameter, a computed value) FAILS the guard instead of being skipped: skipping would let a new
 * poll slip through unchecked, so use a literal or a named {@code static final int}. The one exception is
 * {@code DropArenaUtil.awaitDrops}, whose budget is fixed by the harness: it counts as the harness's
 * {@code POLL_TICKS}, read from its source. The guard needs {@code max_ticks > budget}, one tick of margin beyond
 * the poll's last tick, which is the only tick the framework's own check ({@code tickCount > max_ticks}) cannot beat.
 *
 * <p>The scan reads the raw source tree ({@code src/test/java}), not the Stonecutter-processed copy, so it sees the
 * active node's view: only the active node's arm of a Stonecutter directive is live there, and the other arms are
 * comments, which the scan ignores. A poll that exists only in another node's arm is therefore not seen. No
 * directive-gated polls exist today; the 1.21.1 node reads {@code max_ticks} from the same manifests (see
 * {@link GameTestManifestTester}).
 */
public class GameTestPollBudgetTester {

    private static final String REGISTRAR = "src/test/java/buildcraft/BuildCraftGameTests.java";
    private static final String TEST_SOURCES = "src/test/java";
    private static final String MANIFEST_DIR = "src/test/resources/data/buildcraftunofficial/test_instance";

    /** {@code reg.accept("buildcraftunofficial:<id>", () -> <fqcn>::<method>)}, single or multi line. */
    private static final Pattern REGISTRATION = Pattern.compile(
        "reg\\.accept\\(\"buildcraftunofficial:([a-z0-9_/]+)\"\\s*,\\s*\\(\\)\\s*->\\s*([\\w.]+)::(\\w+)\\s*\\)");

    /** Every registration, counted the way {@link GameTestManifestTester} counts them, to prove the richer
     *  pattern above did not silently drop one. */
    private static final Pattern ANY_REGISTRATION =
        Pattern.compile("reg\\.accept\\(\"buildcraftunofficial:([a-z0-9_/]+)\"");

    private static final Pattern MAX_TICKS = Pattern.compile("\"max_ticks\"\\s*:\\s*(\\d+)");

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One registered game test: its id, the class holding it and the method that is the test. */
    private record Registration(String id, String className, String method) {
        Path source() {
            return TestHelper.repoRoot().resolve(TEST_SOURCES).resolve(className.replace('.', '/') + ".java");
        }
    }

    /** {@code import <fqcn>;} lines of the registrar, by simple class name. */
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+([\\w.]+\\.(\\w+))\\s*;");

    private static List<Registration> registrations() {
        String registrar = read(TestHelper.repoRoot().resolve(REGISTRAR));
        Map<String, String> imports = new HashMap<>();
        Matcher imp = IMPORT.matcher(registrar);
        while (imp.find()) {
            imports.put(imp.group(2), imp.group(1));
        }
        List<Registration> all = new ArrayList<>();
        Matcher m = REGISTRATION.matcher(registrar);
        while (m.find()) {
            // A simple class name is an import of the registrar, or else a class of the registrar's own package.
            String className = m.group(2).contains(".") ? m.group(2)
                : imports.getOrDefault(m.group(2), "buildcraft." + m.group(2));
            all.add(new Registration(m.group(1), className, m.group(3)));
        }
        int expected = 0;
        Matcher any = ANY_REGISTRATION.matcher(registrar);
        while (any.find()) {
            expected++;
        }
        Assertions.assertEquals(expected, all.size(),
            "some reg.accept registrations in " + REGISTRAR + " do not match the shape this guard parses"
                + " (id, then () -> <fully qualified class>::<method>): update REGISTRATION, or the guard"
                + " silently stops covering them");
        return all;
    }

    // ---------------------------------------------------------------------------------------------------------
    // The real tree
    // ---------------------------------------------------------------------------------------------------------

    @Test
    public void everyGameTestsWatchdogOutlastsItsPollBudget() {
        Path manifests = TestHelper.repoRoot().resolve(MANIFEST_DIR);
        List<String> tooShort = new ArrayList<>();
        List<String> uncomputable = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int tests = 0;
        int testsWithPolls = 0;
        for (Registration r : registrations()) {
            tests++;
            Path source = r.source();
            Path manifest = manifests.resolve(r.id() + ".json");
            if (!Files.isRegularFile(source)) {
                missing.add(r.id() + ": source " + source + " not found for " + r.className());
                continue;
            }
            Matcher ticks = MAX_TICKS.matcher(Files.isRegularFile(manifest) ? read(manifest) : "");
            if (!ticks.find()) {
                missing.add(r.id() + ": no max_ticks in " + MANIFEST_DIR + "/" + r.id() + ".json");
                continue;
            }
            int maxTicks = Integer.parseInt(ticks.group(1));
            PollBudgets budgets = PollBudgets.of(read(source), r.method());
            if (budgets.methodNotFound) {
                missing.add(r.id() + ": method " + r.method() + " not found in " + r.className());
                continue;
            }
            if (!budgets.unresolved.isEmpty()) {
                uncomputable.add(r.id() + " (" + r.className() + "::" + r.method() + "): " + budgets.unresolved);
            }
            if (budgets.polls.isEmpty()) {
                continue;
            }
            testsWithPolls++;
            if (budgets.max() >= maxTicks) {
                tooShort.add(r.id() + ": max_ticks " + maxTicks + " but a poll runs for " + budgets.max()
                    + " ticks " + budgets.polls);
            }
        }
        // Liveness: an empty or tiny match set would make every check above vacuous. The floors sit well under the
        // real counts, so deleting a few tests does not trip them, while a drifted registration pattern or poll
        // scan, which finds nothing or little, does.
        Assertions.assertTrue(tests >= 750,
            "only " + tests + " game tests were registered - the registration scan has drifted, which makes this"
                + " guard vacuous");
        Assertions.assertTrue(testsWithPolls >= 110,
            "only " + testsWithPolls + " game tests had a poll found - the poll scan has drifted, which makes"
                + " this guard vacuous");
        Assertions.assertTrue(missing.isEmpty(), "game test(s) this guard could not read:\n    "
            + String.join("\n    ", missing));
        Assertions.assertTrue(uncomputable.isEmpty(),
            "game test(s) with a poll whose tick budget is not an integer literal or a static final int of the"
                + " same file, so the watchdog cannot be checked against it. Use a literal or a named constant:\n    "
                + String.join("\n    ", uncomputable));
        Assertions.assertTrue(tooShort.isEmpty(),
            "game test(s) whose test_instance max_ticks does not outlast their tickUntil/tickUntilThen/"
                + "succeedWhen budget. The framework's timeout then fires first and the poll's cleanup never runs,"
                + " leaving the test's robots, markers or entities in the shared level. Raise max_ticks above the"
                + " budget:\n    " + String.join("\n    ", tooShort));
    }

    // ---------------------------------------------------------------------------------------------------------
    // The scanner, pinned on synthetic sources
    // ---------------------------------------------------------------------------------------------------------

    private static final String CLASS_HEAD = "package x;\npublic class T {\n";

    private static PollBudgets scan(String body) {
        return PollBudgets.of(CLASS_HEAD + body + "\n}\n", "test");
    }

    @Test
    public void scannerReadsALiteralBudget() {
        PollBudgets b = scan("""
            public static void test(GameTestHelper helper) {
                EntityArenaUtil.tickUntil(helper, 240, () -> true, helper::succeed, "never");
            }""");
        Assertions.assertEquals(240, b.max());
        Assertions.assertTrue(b.unresolved.isEmpty());
    }

    @Test
    public void scannerTakesTheLargestOfSeveralPollsAndEveryPollKind() {
        PollBudgets b = scan("""
            public static void test(GameTestHelper helper) {
                EntityArenaUtil.tickUntil(helper, 60, () -> true, helper::succeed, "a");
                EntityArenaUtil.tickUntilThen(helper, 120, () -> true, () -> { }, 60, () -> { }, "b");
                scope.succeedWhen(helper, 90, () -> true, "c");
            }""");
        Assertions.assertEquals(120, b.max());
        Assertions.assertEquals(3, b.polls.size());
    }

    @Test
    public void scannerReadsAMarkerSessionAwait() {
        PollBudgets b = scan("""
            private static final int REGISTER_TICKS = 40;
            public static void test(GameTestHelper helper) {
                arena.await(REGISTER_TICKS, () -> true, helper::succeed, "a");
                arena.awaitThen(REGISTER_TICKS + 20, () -> true, () -> { }, 5, () -> { }, "b");
            }""");
        Assertions.assertEquals(60, b.max());
        Assertions.assertEquals(2, b.polls.size());
    }

    @Test
    public void scannerCountsTheDropHarnessAtItsFixedBudget() {
        PollBudgets b = scan("""
            public static void test(GameTestHelper helper) {
                DropArenaUtil.awaitDrops(helper, () -> { }, Drops.near(pos, 2.0).present(item));
            }""");
        Assertions.assertEquals(1, b.polls.size());
        Assertions.assertEquals(DropArenaUtil.POLL_TICKS, b.max(),
            "the harness's poll counts as its POLL_TICKS, read from its source");
        Assertions.assertTrue(b.unresolved.isEmpty());
    }

    @Test
    public void scannerResolvesANamedConstant() {
        PollBudgets b = scan("""
            private static final int RESYNC_TICKS = 180;
            public static void test(GameTestHelper helper) {
                EntityArenaUtil.tickUntil(helper, RESYNC_TICKS, () -> true, helper::succeed, "a");
            }""");
        Assertions.assertEquals(180, b.max());
    }

    @Test
    public void scannerResolvesASumOfLiteralsAndConstants() {
        PollBudgets b = scan("""
            private static final int RETRY = 400;
            private static final int SETTLE = 20;
            public static void test(GameTestHelper helper) {
                EntityArenaUtil.tickUntil(helper, RETRY + 300, () -> true, helper::succeed, "a");
                EntityArenaUtil.tickUntil(helper, 5 + RETRY + SETTLE, () -> true, helper::succeed, "b");
            }""");
        Assertions.assertEquals(700, b.max());
        Assertions.assertTrue(b.unresolved.isEmpty(), "sums of literals and constants resolve: " + b.unresolved);
    }

    @Test
    public void scannerFollowsSameFileHelpers() {
        PollBudgets b = scan("""
            private static void run(GameTestHelper helper, Runnable body) {
                EntityArenaUtil.tickUntil(helper, 300, () -> true, body, "a");
            }
            public static void test(GameTestHelper helper) {
                run(helper, helper::succeed);
            }
            private static void unrelated(GameTestHelper helper) {
                EntityArenaUtil.tickUntil(helper, 999, () -> true, () -> { }, "never reached from the test");
            }""");
        Assertions.assertEquals(300, b.max(), "a helper the test calls counts; one it does not call does not");
    }

    @Test
    public void scannerIgnoresCommentsAndStringsAndTheFrameworksOwnSucceedWhen() {
        PollBudgets b = scan("""
            public static void test(GameTestHelper helper) {
                // EntityArenaUtil.tickUntil(helper, 777, () -> true, () -> { }, "line comment");
                /* EntityArenaUtil.tickUntil(helper, 888, () -> true, () -> { }, "block comment"); */
                //? if >=1.21.10 {
                String s = "EntityArenaUtil.tickUntil(helper, 555, x, y, z)";
                //?} else {
                /*EntityArenaUtil.tickUntil(helper, 666, () -> true, () -> { }, "inactive branch");*/
                //?}
                helper.succeedWhen(() -> helper.assertTrue(true, "framework poll, its own clock"));
                EntityArenaUtil.tickUntil(helper, 40, () -> true, helper::succeed, "the only real one");
            }""");
        Assertions.assertEquals(40, b.max());
        Assertions.assertEquals(1, b.polls.size());
    }

    @Test
    public void scannerReportsABudgetItCannotResolve() {
        PollBudgets b = scan("""
            public static void test(GameTestHelper helper) {
                int limit = compute();
                EntityArenaUtil.tickUntil(helper, limit, () -> true, helper::succeed, "a");
                EntityArenaUtil.tickUntil(helper, (int) Thing.windowTicks() + 100, () -> true, helper::succeed, "b");
            }""");
        Assertions.assertEquals(2, b.unresolved.size(), "a computed budget is reported, not skipped: " + b.unresolved);
    }

    @Test
    public void scannerSeesBracesInsideStringsAndCharLiterals() {
        PollBudgets b = scan("""
            public static void test(GameTestHelper helper) {
                String open = "{ not a block";
                char brace = '{';
                EntityArenaUtil.tickUntil(helper, 77, () -> true, helper::succeed, "a");
            }
            public static void after(GameTestHelper helper) {
                EntityArenaUtil.tickUntil(helper, 5000, () -> true, helper::succeed, "other test");
            }""");
        Assertions.assertEquals(77, b.max());
    }

    @Test
    public void scannerFlagsAMissingMethod() {
        Assertions.assertTrue(PollBudgets.of(CLASS_HEAD + "}\n", "test").methodNotFound);
    }

    // ---------------------------------------------------------------------------------------------------------
    // The scanner
    // ---------------------------------------------------------------------------------------------------------

    /** The polls reachable from one test method. */
    static final class PollBudgets {
        /** {@code "tickUntil 240"}-style descriptions of every resolved poll, in discovery order. */
        final List<String> polls = new ArrayList<>();
        private int max = -1;
        /** The raw budget text of every poll that is not a literal or a same-file constant. */
        final List<String> unresolved = new ArrayList<>();
        boolean methodNotFound;

        int max() {
            return max;
        }

        private static final Pattern POLL = Pattern.compile(
            "\\b(tickUntilThen|tickUntil|succeedWhen)\\s*\\(\\s*\\w+\\s*,\\s*((?:[^,()]|\\([^()]*\\))+?)\\s*,");

        /** The marker session's own polls ({@code MarkerArenaUtil.Session#await}/{@code awaitThen}): the budget is the
         *  first argument, and they clean the markers up on their timeout like the polls above. */
        private static final Pattern AWAIT_POLL = Pattern.compile(
            "\\b(awaitThen|await)\\s*\\(\\s*((?:[^,()]|\\([^()]*\\))+?)\\s*,");

        /** The drop-test harness ({@code DropArenaUtil.awaitDrops}): one poll whose budget the harness fixes. */
        private static final Pattern DROP_POLL = Pattern.compile("\\bawaitDrops\\s*\\(");

        private static final String DROP_HARNESS = "src/test/java/buildcraft/lib/test/DropArenaUtil.java";

        private static final Pattern CONSTANT =
            Pattern.compile("\\bstatic\\s+final\\s+(?:int|long)\\s+(\\w+)\\s*=\\s*(\\d+)L?\\s*;");

        private static final Pattern POLL_TICKS =
            Pattern.compile("\\bstatic\\s+final\\s+int\\s+POLL_TICKS\\s*=\\s*([^;]+);");

        /** The harness's {@code POLL_TICKS}: a {@code +} sum of its own constants, resolved like any other budget. */
        private static int dropPollBudget() {
            String harness = strip(read(TestHelper.repoRoot().resolve(DROP_HARNESS)));
            Map<String, Integer> constants = new HashMap<>();
            Matcher c = CONSTANT.matcher(harness);
            while (c.find()) {
                constants.put(c.group(1), Integer.parseInt(c.group(2)));
            }
            Matcher budget = POLL_TICKS.matcher(harness);
            Assertions.assertTrue(budget.find(), "no POLL_TICKS constant in " + DROP_HARNESS
                + ": the guard cannot read the budget of awaitDrops");
            Integer value = resolve(budget.group(1).trim(), constants);
            Assertions.assertNotNull(value, "POLL_TICKS in " + DROP_HARNESS + " is `" + budget.group(1).trim()
                + "`, not a literal, a constant of that file or a sum of those");
            return value;
        }

        private static final Pattern CALL = Pattern.compile("(?<![.\\w])([A-Za-z_]\\w*)\\s*\\(");

        private static final Set<String> KEYWORDS =
            Set.of("if", "for", "while", "switch", "catch", "synchronized", "return", "new", "this", "super");

        static PollBudgets of(String rawSource, String method) {
            String source = strip(rawSource);
            PollBudgets result = new PollBudgets();
            Map<String, Integer> constants = new HashMap<>();
            Matcher c = CONSTANT.matcher(source);
            while (c.find()) {
                constants.put(c.group(1), Integer.parseInt(c.group(2)));
            }
            List<String> roots = bodiesOf(source, method);
            if (roots.isEmpty()) {
                result.methodNotFound = true;
                return result;
            }
            Set<String> visited = new HashSet<>();
            visited.add(method);
            for (String body : roots) {
                result.scan(source, body, constants, visited);
            }
            return result;
        }

        /** The value of a budget written as an integer literal, a same-file {@code static final int} or a {@code +}
         *  sum of those ({@code RETRY_BUDGET + 300}); {@code null} for anything else (a parameter, a call, a cast). */
        private static Integer resolve(String budget, Map<String, Integer> constants) {
            int sum = 0;
            for (String term : budget.split("\\+")) {
                String t = term.trim();
                if (t.matches("\\d+")) {
                    sum += Integer.parseInt(t);
                } else if (constants.containsKey(t)) {
                    sum += constants.get(t);
                } else {
                    return null;
                }
            }
            return sum;
        }

        private void scan(String source, String body, Map<String, Integer> constants, Set<String> visited) {
            for (Pattern poll : List.of(POLL, AWAIT_POLL)) {
                Matcher p = poll.matcher(body);
                while (p.find()) {
                    String kind = p.group(1);
                    String budget = p.group(2).trim();
                    Integer value = resolve(budget, constants);
                    if (value == null) {
                        unresolved.add(kind + " budget `" + budget + "`");
                    } else {
                        polls.add(kind + " " + value);
                        max = Math.max(max, value);
                    }
                }
            }
            Matcher drops = DROP_POLL.matcher(body);
            while (drops.find()) {
                int value = dropPollBudget();
                polls.add("awaitDrops " + value);
                max = Math.max(max, value);
            }
            Matcher call = CALL.matcher(body);
            while (call.find()) {
                String name = call.group(1);
                if (KEYWORDS.contains(name) || !visited.add(name)) {
                    continue;
                }
                for (String helperBody : bodiesOf(source, name)) {
                    scan(source, helperBody, constants, visited);
                }
            }
        }

        /** The bodies of every method called {@code name} that is declared (modifiers first) in {@code source}. */
        private static List<String> bodiesOf(String source, String name) {
            List<String> bodies = new ArrayList<>();
            Matcher decl = Pattern.compile(
                "(?m)^[ \\t]*(?:(?:public|private|protected|static|final|synchronized)\\s+)+[\\w<>\\[\\]?,.\\s]*?\\b"
                    + Pattern.quote(name) + "\\s*\\(").matcher(source);
            while (decl.find()) {
                int open = decl.end() - 1;
                int close = matching(source, open, '(', ')');
                if (close < 0) {
                    continue;
                }
                int brace = close + 1;
                while (brace < source.length() && source.charAt(brace) != '{' && source.charAt(brace) != ';') {
                    brace++;
                }
                if (brace >= source.length() || source.charAt(brace) != '{') {
                    continue;
                }
                int end = matching(source, brace, '{', '}');
                if (end > 0) {
                    bodies.add(source.substring(brace + 1, end));
                }
            }
            return bodies;
        }

        private static int matching(String s, int open, char openCh, char closeCh) {
            int depth = 0;
            for (int i = open; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == openCh) {
                    depth++;
                } else if (ch == closeCh && --depth == 0) {
                    return i;
                }
            }
            return -1;
        }

        /** {@code source} with comments blanked and string / char literal CONTENTS blanked (quotes kept), same
         *  length, newlines kept: what is left is code only, so braces, parentheses and calls can be matched
         *  without a parser. */
        static String strip(String source) {
            StringBuilder out = new StringBuilder(source.length());
            int n = source.length();
            int i = 0;
            while (i < n) {
                char ch = source.charAt(i);
                char next = i + 1 < n ? source.charAt(i + 1) : '\0';
                if (ch == '/' && next == '/') {
                    while (i < n && source.charAt(i) != '\n') {
                        out.append(' ');
                        i++;
                    }
                } else if (ch == '/' && next == '*') {
                    int end = source.indexOf("*/", i + 2);
                    end = end < 0 ? n : end + 2;
                    for (; i < end; i++) {
                        out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    }
                } else if (source.startsWith("\"\"\"", i)) {
                    int end = source.indexOf("\"\"\"", i + 3);
                    end = end < 0 ? n : end + 3;
                    for (; i < end; i++) {
                        out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    }
                } else if (ch == '"' || ch == '\'') {
                    out.append(ch);
                    i++;
                    while (i < n && source.charAt(i) != ch && source.charAt(i) != '\n') {
                        if (source.charAt(i) == '\\' && i + 1 < n) {
                            out.append(' ');
                            i++;
                        }
                        out.append(' ');
                        i++;
                    }
                    if (i < n && source.charAt(i) == ch) {
                        out.append(ch);
                        i++;
                    }
                } else {
                    out.append(ch);
                    i++;
                }
            }
            return out.toString();
        }
    }
}
