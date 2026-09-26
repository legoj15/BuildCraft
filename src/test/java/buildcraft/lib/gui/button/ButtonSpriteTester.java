/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.TestHelper;

/**
 * Button icons are GUI-atlas sprites ({@code assets/<ns>/textures/gui/sprites/button/*.png}), so a typo'd id
 * renders vanilla's magenta missing-texture square and nothing else notices. This pins, both ways, that every
 * {@link ButtonSprite} constant has its PNG at the declared size, and that no icon PNG sits in the folder
 * without a constant (a dead icon a resource pack author would waste time re-painting).
 */
public class ButtonSpriteTester {

    private static final String SPRITE_DIR = "src/main/resources/assets/buildcraftunofficial/textures/gui/sprites/button";

    private static List<ButtonSprite> declared() throws IllegalAccessException {
        List<ButtonSprite> sprites = new ArrayList<>();
        for (Field f : ButtonSprite.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) && Modifier.isPublic(mod) && f.getType() == ButtonSprite.class) {
                sprites.add((ButtonSprite) f.get(null));
            }
        }
        return sprites;
    }

    private static Path pngFor(ButtonSprite sprite) {
        return TestHelper.repoRoot()
            .resolve("src/main/resources/assets")
            .resolve(sprite.id().getNamespace())
            .resolve("textures/gui/sprites")
            .resolve(sprite.id().getPath() + ".png");
    }

    /** Width and height straight out of the PNG IHDR chunk (no AWT/ImageIO in the FML test layer). */
    static int[] pngSize(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png)) {
            byte[] head = in.readNBytes(24);
            Assertions.assertEquals(24, head.length, png + " is truncated");
            Assertions.assertEquals((byte) 0x89, head[0], png + " is not a PNG");
            Assertions.assertEquals('P', head[1], png + " is not a PNG");
            int w = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
            int h = ((head[20] & 0xFF) << 24) | ((head[21] & 0xFF) << 16) | ((head[22] & 0xFF) << 8) | (head[23] & 0xFF);
            return new int[] { w, h };
        }
    }

    @Test
    public void thereAreButtonSpritesToCheck() throws Exception {
        // Liveness: a refactor that renames the constants' type must not turn the checks below vacuous.
        Assertions.assertTrue(declared().size() >= 9, "expected the button icon constants, found " + declared());
    }

    @Test
    public void everyDeclaredIconHasItsPngAtTheDeclaredSize() throws Exception {
        for (ButtonSprite sprite : declared()) {
            Assertions.assertEquals("buildcraftunofficial", sprite.id().getNamespace(), sprite + " namespace");
            Assertions.assertTrue(sprite.id().getPath().startsWith("button/"), sprite + " must live under sprites/button/");
            Assertions.assertTrue(sprite.size() > 0 && sprite.size() <= 16,
                sprite + " must fit the 16x16 icon slot of the smallest button face");
            Path png = pngFor(sprite);
            Assertions.assertTrue(Files.isRegularFile(png), sprite + " has no texture at " + png);
            int[] wh = pngSize(png);
            Assertions.assertEquals(sprite.size(), wh[0], sprite + " width");
            Assertions.assertEquals(sprite.size(), wh[1], sprite + " height");
        }
    }

    @Test
    public void everyIconPngInTheFolderIsDeclared() throws Exception {
        Set<String> declaredPaths = new TreeSet<>();
        for (ButtonSprite sprite : declared()) {
            declaredPaths.add(sprite.id().getPath() + ".png");
        }
        Set<String> onDisk = new TreeSet<>();
        try (Stream<Path> files = Files.list(TestHelper.repoRoot().resolve(SPRITE_DIR))) {
            files.filter(p -> p.getFileName().toString().endsWith(".png"))
                .forEach(p -> onDisk.add("button/" + p.getFileName()));
        }
        onDisk.removeAll(declaredPaths);
        Assertions.assertTrue(onDisk.isEmpty(), "button icon PNGs with no ButtonSprite constant (dead art): " + onDisk);
    }
}
