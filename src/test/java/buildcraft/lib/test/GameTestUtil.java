/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Cross-version shims for game-test assertions.
 * <p>
 * MC 1.21.10's {@link GameTestHelper#assertTrue}/{@code assertFalse} take a {@code Component} message;
 * 1.21.11+ / 26.1 take a {@code String}. A gated Stonecutter regex replacement (see build.gradle.kts)
 * routes every {@code helper.assertTrue(...)} / {@code helper.assertFalse(...)} call through these on the
 * 1.21.10 node, so the ~15 game-test classes keep passing plain Strings and the divergence lives here.
 * ({@code helper.fail(String)} is left alone — 1.21.10 keeps a String overload of it.)
 */
public final class GameTestUtil {
    // NB: the param is `gth`, not `helper` — the build-script regex rewrites `helper.assertTrue(` calls,
    // so naming it `helper` here would make this method rewrite itself into infinite recursion.
    public static void assertTrue(GameTestHelper gth, boolean condition, String message) {
        //? if >=1.21.11 {
        gth.assertTrue(condition, message);
        //?} else {
        /*gth.assertTrue(condition, net.minecraft.network.chat.Component.literal(message));*/
        //?}
    }

    public static void assertFalse(GameTestHelper gth, boolean condition, String message) {
        //? if >=1.21.11 {
        gth.assertFalse(condition, message);
        //?} else {
        /*gth.assertFalse(condition, net.minecraft.network.chat.Component.literal(message));*/
        //?}
    }

    private GameTestUtil() {}
}
