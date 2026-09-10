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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Pins the game-test wiring: every test registered in {@code BuildCraftGameTests.registerAll}
 * must have a {@code test_instance} manifest whose file name and {@code function} field match the
 * registration, and every manifest must back a live registration.
 *
 * <p>This exists because the wiring fails SILENTLY. A registered test with a missing manifest (or
 * one whose {@code function} field does not match the registered id) is simply absent from the
 * run: {@code runGameTestServer} keeps reporting {@code N GAME TESTS COMPLETE} with a smaller N
 * and no error anywhere. That footgun silently disabled 34 registered tests until a manifest
 * sweep caught it.
 *
 * <p>{@code registerAll} is the single source of truth for every node line (only the registrar
 * lambda differs per node), so scanning the raw source file is node-agnostic: this test reads the
 * tree, not the Stonecutter-processed copy, and its findings hold on every node. The 1.21.1 node
 * also reads {@code max_ticks} from these same manifests, so a manifest problem hurts that node
 * even though it has no native {@code test_instance} support.
 *
 * <p>How to add a test correctly: see {@code .claude/skills/add-game-test/SKILL.md}. */
public class GameTestManifestTester {

    /** The one file that registers game tests. If this moves, update this constant. */
    private static final String REGISTRAR = "src/test/java/buildcraft/BuildCraftGameTests.java";

    private static final String MANIFEST_DIR = "src/test/resources/data/buildcraftunofficial/test_instance";

    /** A registration inside registerAll: reg.accept("buildcraftunofficial:<id>", () -> ...). */
    private static final Pattern REGISTRATION =
        Pattern.compile("reg\\.accept\\(\"buildcraftunofficial:([a-z0-9_/]+)\"");

    /** The function field inside a manifest: "function": "buildcraftunofficial:<id>". */
    private static final Pattern FUNCTION_FIELD =
        Pattern.compile("\"function\"\\s*:\\s*\"buildcraftunofficial:([a-z0-9_/]+)\"");

    /** Walks up from the working directory to the repo root. Tests run from a Stonecutter node
     * directory, not the root, so the location cannot be assumed (same approach as
     * CopyrightHeaderTester). */
    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java/buildcraft"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root from " + Paths.get("").toAbsolutePath());
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The registered ids (path part only, without the namespace), in file order. */
    private static List<String> registeredIds() {
        Path registrar = repoRoot().resolve(REGISTRAR);
        Assertions.assertTrue(Files.isRegularFile(registrar),
            REGISTRAR + " not found - if BuildCraftGameTests moved, update GameTestManifestTester.REGISTRAR");
        List<String> ids = new ArrayList<>();
        Matcher m = REGISTRATION.matcher(read(registrar));
        while (m.find()) {
            ids.add(m.group(1));
        }
        Assertions.assertFalse(ids.isEmpty(),
            "no reg.accept registrations found in " + REGISTRAR + " - the call shape must have drifted."
                + " Update REGISTRATION so this guard keeps working; an empty match set makes the"
                + " manifest cross-check vacuous.");
        return ids;
    }

    /** A registration that appears twice would silently shadow itself at runtime. */
    @Test
    public void noDuplicateRegistrations() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> dupes = new ArrayList<>();
        for (String id : registeredIds()) {
            if (!seen.add(id)) {
                dupes.add(id);
            }
        }
        Assertions.assertTrue(dupes.isEmpty(),
            "game-test id(s) registered more than once in BuildCraftGameTests.registerAll:\n    "
                + String.join("\n    ", dupes));
    }

    /** The footgun that motivated this guard: a registration without a working manifest never
     * runs, and nothing complains. */
    @Test
    public void everyRegisteredTestHasAManifest() {
        Path dir = repoRoot().resolve(MANIFEST_DIR);
        List<String> missing = new ArrayList<>();
        List<String> wrongFunction = new ArrayList<>();
        for (String id : registeredIds()) {
            Path manifest = dir.resolve(id + ".json");
            if (!Files.isRegularFile(manifest)) {
                missing.add("buildcraftunofficial:" + id);
                continue;
            }
            Matcher f = FUNCTION_FIELD.matcher(read(manifest));
            if (!f.find() || !id.equals(f.group(1))) {
                wrongFunction.add("buildcraftunofficial:" + id + "  (manifest: " + MANIFEST_DIR + "/" + id + ".json)");
            }
        }
        Assertions.assertTrue(missing.isEmpty() && wrongFunction.isEmpty(),
            "game test(s) registered in BuildCraftGameTests but silently skipped:\n    "
                + String.join("\n    ", missing)
                + (missing.isEmpty() || wrongFunction.isEmpty() ? "" : "\n")
                + String.join("\n    ", wrongFunction)
                + "\n    A test without a manifest whose file name AND \"function\" field match the registered id"
                + " never runs - runGameTestServer reports a smaller \"N GAME TESTS COMPLETE\" with no error."
                + " Create " + MANIFEST_DIR + "/<id>.json with \"function\": \"buildcraftunofficial:<id>\""
                + " - see .claude/skills/add-game-test/SKILL.md.");
    }

    /** The reverse direction: an orphan manifest never runs either, and usually means the
     * registration was renamed or deleted while the JSON was left behind. */
    @Test
    public void everyManifestBacksARegisteredTest() {
        Path dir = repoRoot().resolve(MANIFEST_DIR);
        Assertions.assertTrue(Files.isDirectory(dir),
            MANIFEST_DIR + " not found - if the manifests moved, update GameTestManifestTester.MANIFEST_DIR");
        LinkedHashSet<String> registered = new LinkedHashSet<>(registeredIds());
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Assertions.assertFalse(files.isEmpty(), MANIFEST_DIR + " contains no manifests at all");
        List<String> orphans = new ArrayList<>();
        List<String> wrongFunction = new ArrayList<>();
        for (Path p : files) {
            String name = p.getFileName().toString().replaceFirst("\\.json$", "");
            if (!registered.contains(name)) {
                orphans.add(name);
                continue;
            }
            Matcher f = FUNCTION_FIELD.matcher(read(p));
            if (!f.find() || !name.equals(f.group(1))) {
                wrongFunction.add(name);
            }
        }
        Assertions.assertTrue(orphans.isEmpty() && wrongFunction.isEmpty(),
            "manifest(s) in test_instance that no registered game test uses, or whose \"function\" field does"
                + " not match their own file name:\n    "
                + String.join("\n    ", orphans)
                + (orphans.isEmpty() || wrongFunction.isEmpty() ? "" : "\n")
                + String.join("\n    ", wrongFunction)
                + "\n    Orphans never run (dead weight); a mismatched \"function\" field usually means a rename"
                + " that only half-happened. Register the test or delete/fix the manifest.");
    }
}
