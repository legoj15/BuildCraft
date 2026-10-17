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
import java.util.Map;
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

    /** reg.accept("buildcraftunofficial:<id>", () -> ...MarkerTester::<method>) */
    private static final Pattern MARKER_TESTER_REGISTRATION = Pattern.compile(
        "reg\\.accept\\(\"buildcraftunofficial:([a-z0-9_/]+)\",\\s*\\(\\)\\s*->\\s*(?:[\\w.]*\\.)?MarkerTester::");

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

    private static List<String> markerTesterIds() {
        Path registrar = TestHelper.repoRoot().resolve(REGISTRAR);
        List<String> ids = new ArrayList<>();
        Matcher m = MARKER_TESTER_REGISTRATION.matcher(read(registrar));
        while (m.find()) {
            ids.add(m.group(1));
        }
        // Liveness: the five original marker tests plus the three hermeticity tests. An empty or short scan
        // would make every check below vacuous.
        Assertions.assertTrue(ids.size() >= 8,
            "expected at least 8 MarkerTester registrations in " + REGISTRAR + " but found " + ids
                + " - the registration shape drifted; update MARKER_TESTER_REGISTRATION");
        return ids;
    }

    /** Each marker test must own an environment (= its own batch = never concurrent with another marker test),
     *  and the environment must actually exist or the manifest fails to load. */
    @Test
    public void everyMarkerTestHasItsOwnEnvironment() {
        Path root = TestHelper.repoRoot().resolve(RESOURCES);
        List<String> problems = new ArrayList<>();
        for (String id : markerTesterIds()) {
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
            "marker tests must each run in their own batch (see MarkerTester's javadoc):\n    "
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
