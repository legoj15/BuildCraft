/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

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

    /**
     * A machine's acting identity is its real, named owner, or "[BuildCraft]": an owner restored from a save that
     * stored only an id has an EMPTY name (authlib rejects null), and acting as a blank-named fake player shows claim
     * mods nobody at all. The probe (BlockUtil.canMachineBreak), the leased fake player and the robots all resolve it
     * through the same rule now; they used to apply three different ones.
     */
    @Test
    public void aNamelessOwnerActsAsBuildCraft() {
        GameProfile nameless = new GameProfile(UUID.nameUUIDFromBytes("bc_nameless".getBytes(StandardCharsets.UTF_8)),
                "");
        Assertions.assertSame(FakePlayerUtil.BUILDCRAFT_PROFILE, FakePlayerUtil.resolveProfile(nameless),
                "the leased fake player of a nameless owner is the [BuildCraft] one");
        Assertions.assertSame(FakePlayerUtil.BUILDCRAFT_PROFILE, FakePlayerUtil.resolveProfile(
                new GameProfile(UUID.nameUUIDFromBytes("bc_blank".getBytes(StandardCharsets.UTF_8)), "  ")),
                "a blank name is no name either");
        GameProfile named = new GameProfile(UUID.nameUUIDFromBytes("bc_named".getBytes(StandardCharsets.UTF_8)),
                "bc_named");
        Assertions.assertSame(named, FakePlayerUtil.resolveProfile(named), "a real named owner acts as themselves");
    }

    /** Scans this node's view of the main sources ({@link MainSourceSet}), the code javac actually compiled here. */
    @Test
    public void definedExactlyOnce() {
        List<String> definers = MainSourceSet.javaFiles().stream()
                .filter(f -> definesTheProfile(f.text()))
                .map(MainSourceSet.SourceFile::relativePath)
                .toList();
        Assertions.assertEquals(List.of("buildcraft/lib/misc/FakePlayerUtil.java"), definers,
                "the [BuildCraft] profile must be built in FakePlayerUtil only — reference "
                        + "FakePlayerUtil.BUILDCRAFT_PROFILE instead of re-creating it");
    }

    private static boolean definesTheProfile(String source) {
        // Comments stripped but literals kept (MainSourceSet.codeOnly would blank the very literal searched for):
        // prose may quote the name ("the \"[BuildCraft]\" player") without re-creating the profile.
        String code = source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\\n]*", "");
        return code.contains("\"[BuildCraft]\"") || code.contains("nameUUIDFromBytes(\"BuildCraft\"");
    }
}
