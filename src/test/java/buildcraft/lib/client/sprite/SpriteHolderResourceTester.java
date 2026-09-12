/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.sprite;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.builders.BCBuildersSprites;
import buildcraft.core.BCCoreSprites;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.robotics.BCRoboticsSprites;
import buildcraft.silicon.BCSiliconSprites;
import buildcraft.transport.BCTransportSprites;

/** Pins every {@link SpriteHolder} constant in the {@code BC*Sprites} catalogs to a real PNG in
 * {@code src/main/resources}.
 *
 * <p>This exists because the failure is INVISIBLE in code and near-invisible in-game: a constant
 * like {@code h("triggers/trigger_robot_linked")} compiles fine, the holder resolves at render time
 * to the magenta-and-black missing-texture sprite with only a log line, and the player just sees
 * checkerboard in the gate GUI (18 docking-station trigger/action icons shipped that way — the
 * textures were never copied out of the 1.12.2 tree). A texture that exists but was never added to
 * an atlas is a different failure this test deliberately does NOT cover: the atlas JSONs are
 * appended across resource packs and differ per MC line, so verifying stitching needs a client.
 *
 * <p>When adding a new {@code BC*Sprites} catalog class, add it to {@link #SPRITE_CLASSES} — the
 * list is explicit rather than classpath-scanned so the test stays fast and server-safe. */
public class SpriteHolderResourceTester {

    private static final Class<?>[] SPRITE_CLASSES = {
        BCCoreSprites.class,
        BCRoboticsSprites.class,
        BCBuildersSprites.class,
        BCSiliconSprites.class,
        BCTransportSprites.class,
    };

    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    /** Well under the true total (~300 across the five catalogs) but far above what a reflection
     *  or classloading mixup would find — keeps the test from passing vacuously. */
    private static final int MINIMUM_EXPECTED_HOLDERS = 50;

    @Test
    public void everySpriteHolderConstantResolvesToAPng() {
        List<String> missing = new ArrayList<>();
        int checked = 0;

        for (Class<?> clazz : SPRITE_CLASSES) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() != SpriteHolder.class || !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                SpriteHolder holder;
                try {
                    holder = (SpriteHolder) field.get(null);
                } catch (IllegalAccessException e) {
                    throw new AssertionError("Failed to read " + clazz.getSimpleName() + "." + field.getName(), e);
                }
                String location = holder.getLocation();
                int colon = location.indexOf(':');
                String namespace = location.substring(0, colon);
                String subPath = location.substring(colon + 1);
                if (!namespace.equals("buildcraftunofficial")) {
                    // e.g. TRIGGER_REDSTONE_ACTIVE borrows minecraft:block/redstone_torch straight from
                    // the client jar — nothing in our tree to check.
                    continue;
                }
                // Sprite ids are atlas-relative: every directory source scans textures/<dir>/, so
                // "buildcraftunofficial:triggers/foo" lives at assets/.../textures/triggers/foo.png
                String resource = "assets/" + namespace + "/textures/" + subPath + ".png";
                String error = checkPng(resource);
                checked++;
                if (error != null) {
                    missing.add(clazz.getSimpleName() + "." + field.getName() + " -> " + resource + ": " + error);
                }
            }
        }

        Assertions.assertTrue(checked >= MINIMUM_EXPECTED_HOLDERS,
                "Only found " + checked + " SpriteHolder constants — SPRITE_CLASSES is probably "
                        + "stale or reflection stopped matching (expected 300ish). NOT asserting textures.");
        Assertions.assertTrue(missing.isEmpty(),
                "Sprite holders pointing at missing/corrupt textures (" + missing.size() + "):\n  "
                        + String.join("\n  ", missing));
    }

    /** Null when the resource exists as a PNG, otherwise a human-readable reason why not. */
    private static String checkPng(String resource) {
        ClassLoader[] loaders = { SpriteHolderResourceTester.class.getClassLoader(),
            Thread.currentThread().getContextClassLoader() };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                byte[] head = in.readNBytes(PNG_SIGNATURE.length);
                if (head.length != PNG_SIGNATURE.length) {
                    return "file is truncated (no PNG header)";
                }
                for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                    if (head[i] != PNG_SIGNATURE[i]) {
                        return "not a PNG file";
                    }
                }
                return null;
            } catch (IOException e) {
                return "unreadable: " + e.getMessage();
            }
        }
        return "no such resource";
    }
}
