/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

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

import buildcraft.TestHelper;

/**
 * Pins how robot game tests get their zones: through {@link ZonePinnedRobot}, and nowhere else.
 *
 * <p>A board robot that is added to the world runs its board every tick, and a board with no zone searches the whole
 * dimension: a picker takes a sibling arena's drops, a carrier flies to a sibling's station. That bleeds across game
 * tests (the loser fails or flakes, not the culprit), so a robot test that runs a board pins it to its own cell.
 * Two ways of getting that wrong are mechanical enough to scan for:
 * <ul>
 * <li>{@code new EntityRobot(level, boardNbt)}, the constructor that installs a board, without a zone. The type
 *     constructor ({@code new EntityRobot(BCRoboticsEntities.ROBOT.get(), level)}) installs none and stays legal.</li>
 * <li>A private {@code EntityRobot} subclass that overrides {@code getZoneToWork}/{@code getZoneToLoadUnload}, the
 *     copy that three testers once carried each. Variations (an explicit zone, a home station, an isolated registry)
 *     belong on {@link ZonePinnedRobot}.</li>
 * </ul>
 * Like {@code GameTestManifestTester}, this reads the source tree, so it holds on every node.
 */
public class ZonePinnedRobotUseTester {

    private static final String TEST_SOURCES = "src/test/java";

    /** The board-installing constructor: anything but the entity type as the first argument. */
    private static final Pattern BOARD_CONSTRUCTOR =
        Pattern.compile("\\bnew\\s+EntityRobot\\s*\\(\\s*(?!BCRoboticsEntities\\.ROBOT\\b)");

    private static final Pattern ZONE_OVERRIDE = Pattern.compile("\\bIZone\\s+getZoneTo(?:Work|LoadUnload)\\s*\\(");

    /** Files allowed to construct a board robot without {@link ZonePinnedRobot}, with how many such constructions
     *  they may hold and why. */
    private static final Map<String, Integer> UNPINNED_BOARD_ROBOTS = Map.of(
        "src/test/java/buildcraft/robotics/statements/RobotGateTester.java", 1
        // addHomedRobot: the point is a zone that comes from the home station's gate (Work in Area / Load/Unload
        // Area), exactly as ItemRobot.useOn places one, so a pinned zone would hide what is under test.
    );

    /** Files allowed to override a robot zone getter: the shared helper itself, and the mocks and fixtures that are
     *  not an {@code EntityRobot} (they implement the robot interface for code that never spawns an entity). */
    private static final List<String> ZONE_OVERRIDE_HOMES = List.of(
        "src/test/java/buildcraft/robotics/ZonePinnedRobot.java",
        "src/test/java/buildcraft/robotics/ai/MockRobotAccess.java",
        "src/test/java/buildcraft/api/robots/AIRobotFrameworkTest.java",
        "src/test/java/buildcraft/robotics/RobotStationPluggableTester.java");

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** Code with comments blanked (so a mention in prose never counts), same length and line structure. */
    private static String withoutComments(String source) {
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
            } else if (ch == '"') {
                out.append(ch);
                i++;
                while (i < n && source.charAt(i) != '"' && source.charAt(i) != '\n') {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        out.append(' ');
                        i++;
                    }
                    out.append(' ');
                    i++;
                }
                if (i < n) {
                    out.append(source.charAt(i));
                    i++;
                }
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static List<Path> testSources() {
        Path dir = TestHelper.repoRoot().resolve(TEST_SOURCES);
        try (Stream<Path> files = Files.walk(dir)) {
            List<Path> all = files.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
            Assertions.assertTrue(all.size() > 200, "found only " + all.size() + " test sources under " + dir
                + " - the walk has drifted, which makes this guard vacuous");
            return all;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void everyBoardRobotAGameTestSpawnsIsZonePinned() {
        Path root = TestHelper.repoRoot();
        List<String> offenders = new ArrayList<>();
        int allowed = 0;
        for (Path file : testSources()) {
            String rel = relative(root, file);
            String code = withoutComments(read(file));
            Matcher m = BOARD_CONSTRUCTOR.matcher(code);
            List<Integer> hits = new ArrayList<>();
            while (m.find()) {
                hits.add(lineOf(code, m.start()));
            }
            int quota = UNPINNED_BOARD_ROBOTS.getOrDefault(rel, 0);
            allowed += Math.min(quota, hits.size());
            if (hits.size() > quota) {
                offenders.add(rel + " line(s) " + hits + (quota > 0 ? " (allowed: " + quota + ")" : ""));
            }
        }
        Assertions.assertEquals(UNPINNED_BOARD_ROBOTS.values().stream().mapToInt(Integer::intValue).sum(), allowed,
            "an UNPINNED_BOARD_ROBOTS entry no longer matches its file - remove or fix it");
        Assertions.assertTrue(offenders.isEmpty(),
            "game test(s) constructing a board robot with new EntityRobot(level, board) and no zone. A board that "
                + "runs with no zone searches the whole dimension and takes other tests' drops and stations. Use "
                + "ZonePinnedRobot.spawn(helper, relPos, board) or new ZonePinnedRobot(helper, board):\n    "
                + String.join("\n    ", offenders));
    }

    @Test
    public void noTesterCarriesItsOwnZoneOverridingRobotSubclass() {
        Path root = TestHelper.repoRoot();
        List<String> offenders = new ArrayList<>();
        for (Path file : testSources()) {
            String rel = relative(root, file);
            if (ZONE_OVERRIDE_HOMES.contains(rel)) {
                continue;
            }
            if (ZONE_OVERRIDE.matcher(withoutComments(read(file))).find()) {
                offenders.add(rel);
            }
        }
        for (String home : ZONE_OVERRIDE_HOMES) {
            Assertions.assertTrue(Files.isRegularFile(root.resolve(home)),
                "ZONE_OVERRIDE_HOMES lists " + home + ", which no longer exists");
        }
        Assertions.assertTrue(offenders.isEmpty(),
            "test source(s) overriding getZoneToWork/getZoneToLoadUnload outside ZonePinnedRobot. Extend "
                + "ZonePinnedRobot (explicit zones, home station, isolated registry) instead of copying the "
                + "subclass:\n    " + String.join("\n    ", offenders));
    }
}
