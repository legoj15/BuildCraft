/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the state logic every BuildCraft button shares ({@link ButtonFace}, {@link MouseButtons}): which of
 * vanilla's three button sprites a button shows, how its icon is tinted, and which mouse buttons press it.
 * Kept free of client classes so it runs on every node's unit-test JVM.
 */
public class ButtonFaceTester {

    @Test
    public void anIdleButtonShowsTheNormalFace() {
        Assertions.assertEquals(ButtonFace.NORMAL, ButtonFace.of(true, false, false));
    }

    @Test
    public void hoveringHighlights() {
        Assertions.assertEquals(ButtonFace.HIGHLIGHTED, ButtonFace.of(true, true, false));
    }

    @Test
    public void anInactiveButtonIsDisabledEvenWhenHovered() {
        Assertions.assertEquals(ButtonFace.DISABLED, ButtonFace.of(false, false, false));
        Assertions.assertEquals(ButtonFace.DISABLED, ButtonFace.of(false, true, false));
        Assertions.assertEquals(ButtonFace.DISABLED, ButtonFace.of(false, true, true));
    }

    @Test
    public void aLatchedToggleShowsThePressedInFaceEvenWhenHovered() {
        // A switched-on toggle / the chosen radio option reads as "pressed in" (vanilla's disabled face)
        // and must not flicker back to the raised face when the cursor passes over it.
        Assertions.assertEquals(ButtonFace.DISABLED, ButtonFace.of(true, false, true));
        Assertions.assertEquals(ButtonFace.DISABLED, ButtonFace.of(true, true, true));
    }

    @Test
    public void facesMapOntoVanillaWidgetSpriteLookups() {
        // WidgetSprites.get(enabled, focused): normal = (true,false), highlighted = (true,true), disabled = (false,_).
        Assertions.assertTrue(ButtonFace.NORMAL.enabledSprite());
        Assertions.assertFalse(ButtonFace.NORMAL.focusedSprite());
        Assertions.assertTrue(ButtonFace.HIGHLIGHTED.enabledSprite());
        Assertions.assertTrue(ButtonFace.HIGHLIGHTED.focusedSprite());
        Assertions.assertFalse(ButtonFace.DISABLED.enabledSprite());
        Assertions.assertFalse(ButtonFace.DISABLED.focusedSprite());
    }

    @Test
    public void iconsDimLikeVanillaInactiveTextOnlyWhenTheButtonIsInactive() {
        Assertions.assertEquals(0xFFFFFFFF, ButtonFace.iconTint(true, 1.0F));
        // Vanilla draws an inactive button's label in 0xA0A0A0; icons follow suit.
        Assertions.assertEquals(0xFFA0A0A0, ButtonFace.iconTint(false, 1.0F));
    }

    @Test
    public void iconTintCarriesTheWidgetAlpha() {
        // Same rounding as modern vanilla ARGB.white(float): floor(alpha * 255).
        Assertions.assertEquals(0x00FFFFFF, ButtonFace.iconTint(true, 0.0F));
        Assertions.assertEquals(0x7FA0A0A0, ButtonFace.iconTint(false, 0.5F));
        Assertions.assertEquals(0x7FFFFFFF, ButtonFace.white(0.5F));
        Assertions.assertEquals(0xFFFFFFFF, ButtonFace.white(1.0F));
    }

    @Test
    public void aDefaultButtonAnswersOnlyTheLeftMouseButton() {
        Assertions.assertTrue(MouseButtons.accepts(MouseButtons.LEFT, 0));
        Assertions.assertFalse(MouseButtons.accepts(MouseButtons.LEFT, 1));
        Assertions.assertFalse(MouseButtons.accepts(MouseButtons.LEFT, 2));
    }

    @Test
    public void aMultiButtonButtonAnswersLeftRightAndMiddle() {
        Assertions.assertTrue(MouseButtons.accepts(MouseButtons.ALL, 0));
        Assertions.assertTrue(MouseButtons.accepts(MouseButtons.ALL, 1));
        Assertions.assertTrue(MouseButtons.accepts(MouseButtons.ALL, 2));
        // Side buttons (back/forward) and garbage never press a BuildCraft button.
        Assertions.assertFalse(MouseButtons.accepts(MouseButtons.ALL, 3));
        Assertions.assertFalse(MouseButtons.accepts(MouseButtons.ALL, -1));
        Assertions.assertFalse(MouseButtons.accepts(MouseButtons.ALL, 40));
    }

    @Test
    public void masksCombine() {
        int leftAndRight = MouseButtons.LEFT | MouseButtons.RIGHT;
        Assertions.assertTrue(MouseButtons.accepts(leftAndRight, 0));
        Assertions.assertTrue(MouseButtons.accepts(leftAndRight, 1));
        Assertions.assertFalse(MouseButtons.accepts(leftAndRight, 2));
    }
}
