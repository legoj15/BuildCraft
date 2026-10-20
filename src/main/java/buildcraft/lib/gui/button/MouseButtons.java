/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Bit masks of the mouse buttons a {@link BCButton} answers to. The masks are BuildCraft's own (left 1, right 2,
 * middle 4); the button numbers they translate are Minecraft's, which differ by line (0, 1, 2 for left, right,
 * middle up to 26.2; 1, 3, 2 on 26.3, where SDL numbers them) and so are always spelled
 * {@code InputConstants.MOUSE_BUTTON_*}, never as literals. Vanilla buttons answer the left button only; a
 * BuildCraft button can opt into the others (the Emzuli pipe's paint buttons step the colour back on a right click
 * and clear it on a middle click). Only compile-time constants of the client class {@link InputConstants} are used,
 * so this class never loads it and the dispatch rule stays unit-testable on every node.
 */
public final class MouseButtons {
    public static final int LEFT = 1;
    public static final int RIGHT = 1 << 1;
    public static final int MIDDLE = 1 << 2;
    public static final int ALL = LEFT | RIGHT | MIDDLE;

    private MouseButtons() {}

    /** Whether a button configured with {@code mask} is pressed by Minecraft mouse button {@code button}. */
    public static boolean accepts(int mask, int button) {
        return (mask & maskOf(button)) != 0;
    }

    /** The mask bit of Minecraft mouse button {@code button}; 0 for side buttons and numbers that are no button. */
    private static int maskOf(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> LEFT;
            case InputConstants.MOUSE_BUTTON_RIGHT -> RIGHT;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> MIDDLE;
            default -> 0;
        };
    }
}
