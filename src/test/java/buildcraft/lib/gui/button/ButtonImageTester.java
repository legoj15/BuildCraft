/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

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

import net.minecraft.resources.Identifier;

import buildcraft.TestHelper;

/**
 * Whole-art buttons ({@link ButtonImage}, drawn by {@link BCButton} in place of vanilla's face) are GUI-atlas sprite
 * sets, so a typo'd id renders vanilla's magenta missing-texture square and nothing else notices. Pins, both ways,
 * that every constant has each state's PNG at the declared size under vanilla's naming convention, and that no PNG
 * under a {@link ButtonImage} sprite folder is left without a constant. Also pins the state → sprite choice.
 */
public class ButtonImageTester {

    private static List<ButtonImage> declared() throws IllegalAccessException {
        List<ButtonImage> images = new ArrayList<>();
        for (Field f : ButtonImage.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) && Modifier.isPublic(mod) && f.getType() == ButtonImage.class) {
                images.add((ButtonImage) f.get(null));
            }
        }
        return images;
    }

    /** The distinct sprites of {@code image} (a plain button reuses its normal/highlighted art for "selected"). */
    private static Set<Identifier> states(ButtonImage image) {
        return new java.util.LinkedHashSet<>(
            List.of(image.normal(), image.highlighted(), image.selected(), image.selectedHighlighted()));
    }

    private static Path spriteRoot(String namespace) {
        return TestHelper.repoRoot().resolve("src/main/resources/assets").resolve(namespace).resolve("textures/gui/sprites");
    }

    private static Path pngFor(Identifier id) {
        return spriteRoot(id.getNamespace()).resolve(id.getPath() + ".png");
    }

    @Test
    public void thereAreButtonImagesToCheck() throws Exception {
        // Liveness: the guide book's page-turn/back arrows and its three sort-order options at least.
        Assertions.assertTrue(declared().size() >= 6, "expected the button image constants, found " + declared());
        Assertions.assertTrue(declared().stream().anyMatch(ButtonImage::hasSelectedArt), "no radio art to check");
    }

    @Test
    public void everyStateFollowsVanillasNamingConvention() throws Exception {
        for (ButtonImage image : declared()) {
            String base = image.normal().getPath();
            Assertions.assertEquals(base + "_highlighted", image.highlighted().getPath(), image + " highlighted");
            if (image.hasSelectedArt()) {
                Assertions.assertEquals(base + "_selected", image.selected().getPath(), image + " selected");
                Assertions.assertEquals(base + "_selected_highlighted", image.selectedHighlighted().getPath(),
                    image + " selected + highlighted");
            } else {
                // A plain button looks the same latched or not.
                Assertions.assertEquals(image.highlighted(), image.selectedHighlighted(), image + " selected + highlighted");
            }
        }
    }

    @Test
    public void everyDeclaredStateHasItsPngAtTheDeclaredSize() throws Exception {
        for (ButtonImage image : declared()) {
            Assertions.assertTrue(image.width() > 0 && image.height() > 0, image + " size");
            for (Identifier id : states(image)) {
                Assertions.assertEquals("buildcraftunofficial", id.getNamespace(), image + " namespace");
                Path png = pngFor(id);
                Assertions.assertTrue(Files.isRegularFile(png), image + " has no texture at " + png);
                int[] wh = ButtonSpriteTester.pngSize(png);
                Assertions.assertEquals(image.width(), wh[0], id + " width");
                Assertions.assertEquals(image.height(), wh[1], id + " height");
            }
        }
    }

    @Test
    public void everyPngInAnImageFolderIsDeclared() throws Exception {
        Set<String> declaredPaths = new TreeSet<>();
        Set<String> folders = new TreeSet<>();
        for (ButtonImage image : declared()) {
            for (Identifier id : states(image)) {
                declaredPaths.add(id.getPath() + ".png");
            }
            String path = image.normal().getPath();
            folders.add(path.substring(0, path.lastIndexOf('/') + 1));
        }
        Set<String> onDisk = new TreeSet<>();
        Path root = spriteRoot("buildcraftunofficial");
        for (String folder : folders) {
            try (Stream<Path> files = Files.list(root.resolve(folder))) {
                files.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .forEach(p -> onDisk.add(folder + p.getFileName()));
            }
        }
        onDisk.removeAll(declaredPaths);
        Assertions.assertTrue(onDisk.isEmpty(), "button image PNGs with no ButtonImage constant (dead art): " + onDisk);
    }

    @Test
    public void radioOptionPicksItsArtByStateAndHover() {
        ButtonImage radio = ButtonImage.GUIDE_SORT_TYPE;
        Assertions.assertEquals(radio.normal(), radio.sprite(false, false));
        Assertions.assertEquals(radio.highlighted(), radio.sprite(false, true));
        Assertions.assertEquals(radio.selected(), radio.sprite(true, false));
        Assertions.assertEquals(radio.selectedHighlighted(), radio.sprite(true, true));
        Assertions.assertEquals(4, states(radio).size(), "a radio option has four distinct looks");
    }

    @Test
    public void plainButtonIgnoresLatchedState() {
        ButtonImage plain = ButtonImage.GUIDE_PAGE_FORWARD;
        Assertions.assertFalse(plain.hasSelectedArt());
        Assertions.assertEquals(plain.sprite(false, false), plain.sprite(true, false));
        Assertions.assertEquals(plain.sprite(false, true), plain.sprite(true, true));
        Assertions.assertNotEquals(plain.sprite(false, false), plain.sprite(false, true), "hover must show");
    }
}
