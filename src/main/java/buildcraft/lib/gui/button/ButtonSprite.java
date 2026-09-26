/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import net.minecraft.resources.Identifier;

/**
 * A BuildCraft button icon: a GUI-atlas sprite drawn centred on vanilla's button face at its native
 * {@code size}×{@code size}. The PNG lives at {@code assets/buildcraftunofficial/textures/gui/sprites/<path>.png}
 * — vanilla's {@code atlases/gui.json} is a namespace-agnostic {@code directory} source {@code gui/sprites} with an
 * empty prefix on every supported line (checked in the 1.21.1, 1.21.10, 1.21.11, 26.1.2 and 26.2 client jars), so
 * each icon is an ordinary, resource-pack-replaceable GUI sprite. {@code ButtonSpriteTester} pins every constant
 * below to its PNG (and every PNG in the folder to a constant).
 * <p>
 * Common-side on purpose (only an {@link Identifier}), so the constants stay unit-testable.
 */
public record ButtonSprite(Identifier id, int size) {

    /** Auto Workbench / Advanced Crafting Table: cycle the chosen output between conflicting recipes. */
    public static final ButtonSprite CYCLE = of("cycle", 10);

    /** Diamond-wood (emerald) pipe filter modes. White bars = list of what passes; hollow bars = what is held back. */
    public static final ButtonSprite WHITELIST = of("whitelist", 16);
    public static final ButtonSprite BLACKLIST = of("blacklist", 16);
    public static final ButtonSprite ROUND_ROBIN = of("round_robin", 16);

    /** Emzuli pipe paint button with no colour set (a painted slot shows the paintbrush item instead). */
    public static final ButtonSprite NO_PAINT = of("no_paint", 16);

    /** Filler / Filler Planner toggles. The icon is the state: struck-through shovel = won't dig first. */
    public static final ButtonSprite EXCAVATE_ON = of("excavate_on", 16);
    public static final ButtonSprite EXCAVATE_OFF = of("excavate_off", 16);
    /** Solid square = the pattern as drawn; hollow square = inverted (fill where the pattern is empty). */
    public static final ButtonSprite INVERT_OFF = of("invert_off", 16);
    public static final ButtonSprite INVERT_ON = of("invert_on", 16);

    private static ButtonSprite of(String name, int size) {
        return new ButtonSprite(Identifier.fromNamespaceAndPath("buildcraftunofficial", "button/" + name), size);
    }
}
