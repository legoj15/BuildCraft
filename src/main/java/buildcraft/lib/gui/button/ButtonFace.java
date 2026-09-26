/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

/**
 * Which of vanilla's three button sprites ({@code widget/button}, {@code widget/button_highlighted},
 * {@code widget/button_disabled}) a {@link BCButton} shows, plus the matching icon tint.
 * <p>
 * Deliberately free of client classes: this is the state logic of every BuildCraft button, and it is pinned by a
 * unit test that runs on every node.
 */
public enum ButtonFace {
    NORMAL,
    HIGHLIGHTED,
    DISABLED;

    /** Vanilla's label colour for an inactive button; inactive icons are tinted the same. */
    private static final int INACTIVE_RGB = 0xA0A0A0;

    /**
     * @param active  whether the button can be pressed at all ({@code AbstractWidget.active})
     * @param hovered hovered or keyboard-focused
     * @param latched a switched-on toggle, or the chosen option of a radio group: shown "pressed in" (vanilla's
     *                disabled face) while staying clickable and keeping a full-brightness label/icon. The same
     *                idiom the List's match-mode toggles established.
     */
    public static ButtonFace of(boolean active, boolean hovered, boolean latched) {
        if (!active || latched) {
            return DISABLED;
        }
        return hovered ? HIGHLIGHTED : NORMAL;
    }

    /** First argument of vanilla {@code WidgetSprites.get(enabled, focused)} for this face. */
    public boolean enabledSprite() {
        return this != DISABLED;
    }

    /** Second argument of vanilla {@code WidgetSprites.get(enabled, focused)} for this face. */
    public boolean focusedSprite() {
        return this == HIGHLIGHTED;
    }

    /** ARGB tint for an icon: untinted when active, vanilla's inactive grey otherwise; carries the widget alpha. */
    public static int iconTint(boolean active, float alpha) {
        return alphaChannel(alpha) | (active ? 0xFFFFFF : INACTIVE_RGB);
    }

    /** Opaque-white ARGB at the widget alpha — the same rounding as modern vanilla {@code ARGB.white(float)}. */
    public static int white(float alpha) {
        return alphaChannel(alpha) | 0xFFFFFF;
    }

    private static int alphaChannel(float alpha) {
        int a = (int) Math.floor(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return a << 24;
    }
}
