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
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the structural half of the marker game tests' isolation (the runtime half is {@code MarkerArenaUtil} and
 * the hermeticity tests in {@code MarkerTester}).
 *
 * <p>Marker connections live in dimension-wide singleton caches that every game-test arena shares, so a marker
 * test is only reliable if (a) it never runs concurrently with another marker test and (b) nothing leaves a marker
 * behind. Both fail silently: a merged environment or a forgotten cleanup shows up weeks later as a flake in a
 * different test (the 2026-09-26 flake of marker_orientation / marker_volume_los / marker_volume_triangulation_3d
 * was exactly that). Like {@link GameTestManifestTester}, this reads the source tree, so it holds on every node.
 */
public class MarkerTestIsolationTester {

    private static final String REGISTRAR = "src/test/java/buildcraft/BuildCraftGameTests.java";
    private static final String TEST_SOURCES = "src/test/java";
    private static final String RESOURCES = "src/test/resources/data/buildcraftunofficial";

    /** reg.accept("buildcraftunofficial:<id>", () -> <package>.<Class>::<method>): the id, the class, the method. */
    private static final Pattern REGISTRATION = Pattern.compile(
        "reg\\.accept\\(\"buildcraftunofficial:([a-z0-9_/]+)\",\\s*\\(\\)\\s*->\\s*(?:[\\w.]*\\.)?(\\w+)::(\\w+)");

    /** Every test of this class is a marker test, whether it opens a session or tests the sessions themselves. */
    private static final String MARKER_TESTER = "MarkerTester";

    /** The call that opens a marker session. Opening one clears every stray marker in scan range, so a test that
     *  does it removes the markers of any marker test running beside it. */
    private static final String OPENS_SESSION = "MarkerArenaUtil.open(";

    /** A static method declared directly in a test class (one indent step), up to its name. */
    private static final Pattern METHOD_HEAD =
        Pattern.compile("(?m)^    (?:public |private |protected )?static [\\w<>\\[\\], .?]+? (\\w+)\\(");

    private static final Pattern ENVIRONMENT_FIELD =
        Pattern.compile("\"environment\"\\s*:\\s*\"([a-z0-9_:/]+)\"");

    private static final Pattern PLACES_MARKER = Pattern.compile("\\bMARKER_(VOLUME|PATH)\\b");

    /**
     * Test sources that reference a marker block yet need no {@code MarkerArenaUtil}, mapped to why. Adding an
     * entry is a conscious decision that the test cannot leave a marker in the shared caches.
     */
    private static final Map<String, String> MARKER_REFERENCES_WITHOUT_CLEANUP = Map.of(
        "src/test/java/buildcraft/BlockDropsTester.java",
        "breaks the path marker it places (breaking removes it from the cache) and only inspects the dropped item");

    /** Files that are part of the machinery itself or merely mention the names. */
    private static final List<String> MACHINERY = List.of(
        "src/test/java/buildcraft/core/marker/MarkerTester.java",
        "src/test/java/buildcraft/lib/test/MarkerArenaUtil.java",
        "src/test/java/buildcraft/MarkerTestIsolationTester.java");

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The static methods of {@code source} that open a marker session, themselves or through another method of the
     * same file (a shared body two tests call). A method runs from its head to the next method's head, so the
     * Javadoc of the method after it counts as its text: a mention there errs on the side of asking for isolation.
     */
    private static Set<String> sessionOpeners(String source) {
        Map<String, String> bodies = new HashMap<>();
        Matcher head = METHOD_HEAD.matcher(source);
        String name = null;
        int bodyStart = 0;
        while (head.find()) {
            if (name != null) {
                bodies.merge(name, source.substring(bodyStart, head.start()), String::concat);
            }
            name = head.group(1);
            bodyStart = head.end();
        }
        if (name != null) {
            bodies.merge(name, source.substring(bodyStart), String::concat);
        }
        Set<String> openers = new HashSet<>();
        for (boolean grew = true; grew;) {
            grew = false;
            for (Map.Entry<String, String> method : bodies.entrySet()) {
                if (openers.contains(method.getKey())) {
                    continue;
                }
                String body = method.getValue();
                boolean opens = body.contains(OPENS_SESSION);
                for (String opener : openers) {
                    opens = opens || Pattern.compile("\\b" + opener + "\\s*\\(").matcher(body).find();
                }
                if (opens) {
                    openers.add(method.getKey());
                    grew = true;
                }
            }
        }
        return openers;
    }

    /** The ids of the marker tests: every test of {@code MarkerTester}, and every other registered test that opens a
     *  marker session, in whatever class it lives. */
    private static List<String> markerTestIds() {
        Path root = TestHelper.repoRoot();
        // The session-opening methods of each test class that has any, by the class's simple name.
        Map<String, Set<String>> openers = new HashMap<>();
        try (Stream<Path> files = Files.walk(root.resolve(TEST_SOURCES))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                String source = read(file);
                if (MACHINERY.contains(rel) || !source.contains(OPENS_SESSION)) {
                    continue;
                }
                String simpleName = file.getFileName().toString().replace(".java", "");
                openers.put(simpleName, sessionOpeners(source));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> ids = new ArrayList<>();
        int outsideMarkerTester = 0;
        Matcher m = REGISTRATION.matcher(read(root.resolve(REGISTRAR)));
        while (m.find()) {
            if (MARKER_TESTER.equals(m.group(2))) {
                ids.add(m.group(1));
            } else if (openers.getOrDefault(m.group(2), Set.of()).contains(m.group(3))) {
                ids.add(m.group(1));
                outsideMarkerTester++;
            }
        }
        // Liveness: MarkerTester alone registers eight and more, and machines that take markers are tested outside
        // it. A short scan on either side would make the check below vacuous.
        Assertions.assertTrue(ids.size() - outsideMarkerTester >= 8 && outsideMarkerTester >= 4,
            "expected at least 8 MarkerTester registrations and 4 session-opening tests of other classes in "
                + REGISTRAR + " but found " + ids + " - the registration or method shape drifted; update the patterns");
        return ids;
    }

    /** Each marker test must own an environment (= its own batch = never concurrent with another marker test),
     *  and the environment must actually exist or the manifest fails to load. */
    @Test
    public void everyMarkerTestHasItsOwnEnvironment() {
        Path root = TestHelper.repoRoot().resolve(RESOURCES);
        List<String> problems = new ArrayList<>();
        for (String id : markerTestIds()) {
            Path manifest = root.resolve("test_instance/" + id + ".json");
            if (!Files.isRegularFile(manifest)) {
                problems.add(id + ": no manifest");
                continue;
            }
            Matcher env = ENVIRONMENT_FIELD.matcher(read(manifest));
            String actual = env.find() ? env.group(1) : "absent";
            String expected = "buildcraftunofficial:" + id;
            if (!expected.equals(actual)) {
                problems.add(id + ": environment must be " + expected + " but is " + actual
                    + " (a shared environment runs marker tests concurrently)");
            }
            if (!Files.isRegularFile(root.resolve("test_environment/" + id + ".json"))) {
                problems.add(id + ": test_environment/" + id + ".json is missing");
            }
        }
        Assertions.assertTrue(problems.isEmpty(),
            "marker tests must each run in their own batch (see MarkerTester's javadoc; a test is one when it is "
                + "MarkerTester's or opens a marker session):\n    "
                + String.join("\n    ", problems));
    }

    /** A game test outside {@code MarkerTester} that places a marker leaves it in the shared caches unless it
     *  removes it, and every later marker test in scan range then sees it. Cheap, file-level, but it is the
     *  guard the four leftover sites of 2026-09-26 (waterlogging, fluid-mode, robot pump) would have tripped. */
    @Test
    public void gameTestsThatPlaceMarkersCleanUp() {
        Path root = TestHelper.repoRoot();
        List<String> offenders = new ArrayList<>();
        int referencing = 0;
        try (Stream<Path> files = Files.walk(root.resolve(TEST_SOURCES))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                if (MACHINERY.contains(rel)) {
                    continue;
                }
                String text = read(file);
                if (!PLACES_MARKER.matcher(text).find()) {
                    continue;
                }
                referencing++;
                if (MARKER_REFERENCES_WITHOUT_CLEANUP.containsKey(rel)) {
                    continue;
                }
                if (!text.contains("MarkerArenaUtil")) {
                    offenders.add(rel);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Assertions.assertTrue(referencing >= 3,
            "expected several game-test sources to reference MARKER_VOLUME/MARKER_PATH but found " + referencing
                + " - the scan is vacuous");
        Assertions.assertTrue(offenders.isEmpty(),
            "game test source(s) place marker blocks without MarkerArenaUtil.clearMarkers (or a Session): a marker "
                + "left in the grid is a connection candidate for every later marker test in scan range:\n    "
                + String.join("\n    ", offenders)
                + "\n    Remove the marker before the test ends (a finally for synchronous tests, the last step of the "
                + "assertion for succeedWhen ones), or add the file to MARKER_REFERENCES_WITHOUT_CLEANUP with a reason.");
    }

    /** An allowlist entry for a file that no longer references a marker is dead weight that hides future gaps. */
    @Test
    public void markerCleanupAllowlistHasNoStaleEntries() {
        Path root = TestHelper.repoRoot();
        for (String rel : MARKER_REFERENCES_WITHOUT_CLEANUP.keySet()) {
            Path file = root.resolve(rel);
            Assertions.assertTrue(Files.isRegularFile(file), "allowlisted file is gone: " + rel);
            Assertions.assertTrue(PLACES_MARKER.matcher(read(file)).find(),
                rel + " no longer references a marker block - drop it from MARKER_REFERENCES_WITHOUT_CLEANUP");
        }
    }
}
