/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

/**
 * Bit masks of the mouse buttons a {@link BCButton} answers to, over GLFW's numbering (0 = left, 1 = right,
 * 2 = middle). Vanilla buttons answer the left button only; a BuildCraft button can opt into the others (the
 * Emzuli pipe's paint buttons step the colour back on a right click and clear it on a middle click).
 * Free of client classes so the dispatch rule is unit-testable on every node.
 */
public final class MouseButtons {
    public static final int LEFT = 1;
    public static final int RIGHT = 1 << 1;
    public static final int MIDDLE = 1 << 2;
    public static final int ALL = LEFT | RIGHT | MIDDLE;

    private MouseButtons() {}

    /** Whether a button configured with {@code mask} is pressed by GLFW mouse button {@code button}. */
    public static boolean accepts(int mask, int button) {
        return button >= 0 && button <= 2 && (mask & (1 << button)) != 0;
    }
}
