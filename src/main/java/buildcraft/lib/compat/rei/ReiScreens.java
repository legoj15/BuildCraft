/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import buildcraft.lib.gui.GuiBC8;

/** Screen-side REI registrations shared by the BuildCraft client plugins. */
public final class ReiScreens {
    private ReiScreens() {}

    /**
     * Makes a rectangle of a BuildCraft GUI open {@code categories} when clicked. The rectangle is relative to the
     * GUI's top-left corner, exactly as JEI's {@code addRecipeClickArea} takes it, so both viewers share one set of
     * numbers.
     */
    public static <G extends GuiBC8<?>> void clickArea(ScreenRegistry registry, Class<G> screenClass, int x, int y,
            int width, int height, CategoryIdentifier<?>... categories) {
        registry.registerClickArea(
                screen -> new Rectangle(screen.windowLeft() + x, screen.windowTop() + y, width, height),
                screenClass, categories);
    }
}
