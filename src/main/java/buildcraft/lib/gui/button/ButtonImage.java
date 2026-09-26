/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import net.minecraft.resources.Identifier;

/**
 * Art that IS the whole button — drawn by a {@link BCButton} built with {@code .art(...)} in place of vanilla's grey
 * button face. For places a button face would be wrong: the guide book's pages, the same way vanilla's own book uses
 * page arrows rather than buttons.
 * <p>
 * Every state is a GUI-atlas sprite under {@code assets/buildcraftunofficial/textures/gui/sprites/}, so resource packs
 * replace them like any vanilla widget sprite: {@code <name>} and {@code <name>_highlighted} (hover / keyboard focus),
 * plus — for a radio option — {@code <name>_selected} and {@code <name>_selected_highlighted} for the chosen one.
 * {@code ButtonImageTester} pins every constant to its PNGs at {@code width}×{@code height}, and every PNG in those
 * folders to a constant.
 * <p>
 * Common-side on purpose (only {@link Identifier}s), so the constants and {@link #sprite} stay unit-testable.
 */
public record ButtonImage(Identifier normal, Identifier highlighted, Identifier selected,
    Identifier selectedHighlighted, int width, int height) {

    /** Guide book: turn to the next spread (bottom right of the right page). */
    public static final ButtonImage GUIDE_PAGE_FORWARD = of("guide/page_forward", 18, 10);
    /** Guide book: turn to the previous spread (bottom left of the left page). */
    public static final ButtonImage GUIDE_PAGE_BACKWARD = of("guide/page_backward", 18, 10);
    /** Guide book: back to the previously opened page (bottom of the spine). */
    public static final ButtonImage GUIDE_BACK = of("guide/back", 17, 9);

    /** Guide contents: sort the entries by type / by mod then type / alphabetically (a radio group). */
    public static final ButtonImage GUIDE_SORT_TYPE = radio("guide/sort_type", 14, 14);
    public static final ButtonImage GUIDE_SORT_MOD = radio("guide/sort_mod", 14, 14);
    public static final ButtonImage GUIDE_SORT_ALPHABETICAL = radio("guide/sort_alphabetical", 14, 14);

    /** The sprite for a button in this state: {@code selected} = latched (the chosen radio option). */
    public Identifier sprite(boolean isSelected, boolean isHighlighted) {
        if (isSelected) {
            return isHighlighted ? selectedHighlighted : selected;
        }
        return isHighlighted ? highlighted : normal;
    }

    /** Whether the chosen state has its own art (a radio option), rather than looking like the normal one. */
    public boolean hasSelectedArt() {
        return !selected.equals(normal);
    }

    /** A plain button: normal + highlighted art; being latched does not change its look. */
    private static ButtonImage of(String path, int width, int height) {
        Identifier normal = id(path);
        Identifier highlighted = id(path + "_highlighted");
        return new ButtonImage(normal, highlighted, normal, highlighted, width, height);
    }

    /** A radio option: normal, highlighted, selected and selected + highlighted art. */
    private static ButtonImage radio(String path, int width, int height) {
        return new ButtonImage(id(path), id(path + "_highlighted"), id(path + "_selected"),
            id(path + "_selected_highlighted"), width, height);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("buildcraftunofficial", path);
    }
}
