/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the unowned "[BuildCraft]" machine identity: server operators whitelist it by UUID or name in their
 * protection-mod configs, so its value must never drift, and it must be defined in exactly one place so the
 * permission check (BlockUtil.canMachineBreak) and the generic fake player can never disagree again — they
 * used to be two separate copies of the same constant.
 */
public class FakePlayerProfileTester {

    @Test
    public void identityIsStable() {
        Assertions.assertEquals(UUID.fromString("1504af80-a4c0-3084-adda-32c4f4ac26d4"),
                GameProfileUtil.getId(FakePlayerUtil.BUILDCRAFT_PROFILE),
                "the [BuildCraft] UUID is what protection-mod whitelists already hold");
        Assertions.assertEquals("[BuildCraft]", GameProfileUtil.getName(FakePlayerUtil.BUILDCRAFT_PROFILE));
    }

    @Test
    public void providerHandsOutTheSameIdentity() {
        Assertions.assertSame(FakePlayerUtil.BUILDCRAFT_PROFILE, FakePlayerUtil.resolveProfile(null),
                "an unowned caller falls back to the one shared profile");
    }

    @Test
    public void definedExactlyOnce() {
        Path root = repoRoot().resolve("src/main/java");
        List<Path> definers;
        try (Stream<Path> files = Files.walk(root)) {
            definers = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(FakePlayerProfileTester::definesTheProfile)
                    .map(root::relativize)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Assertions.assertEquals(List.of(Paths.get("buildcraft/lib/misc/FakePlayerUtil.java")), definers,
                "the [BuildCraft] profile must be built in FakePlayerUtil only — reference "
                        + "FakePlayerUtil.BUILDCRAFT_PROFILE instead of re-creating it");
    }

    private static boolean definesTheProfile(Path file) {
        try {
            // Code only: prose may quote the name ("the \"[BuildCraft]\" player") without re-creating the profile.
            String code = Files.readString(file, StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("//[^\\n]*", "");
            return code.contains("\"[BuildCraft]\"") || code.contains("nameUUIDFromBytes(\"BuildCraft\"");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tests run from a Stonecutter node directory ({@code versions/<id>}), so walk up to the repo root. */
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
}
