/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Which mouse buttons start and end a Zone Planner pan. Only the left button starts one, and only its release ends
 * it: releasing any button used to end the pan, so a right or middle click made while dragging dropped the map.
 */
public class ZonePanDragTest {

    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int MIDDLE = 2;
    private static final int SIDE = 3;

    @Test
    void aLeftPressStartsAPanAndItsReleaseEndsIt() {
        ZonePanDrag pan = new ZonePanDrag();
        Assertions.assertFalse(pan.isActive(), "no pan before a press");
        Assertions.assertTrue(pan.press(LEFT));
        Assertions.assertTrue(pan.isActive());
        Assertions.assertTrue(pan.release(LEFT), "letting go of the button that began it ends it");
        Assertions.assertFalse(pan.isActive());
    }

    @Test
    void onlyTheLeftButtonStartsAPan() {
        for (int button : new int[] {RIGHT, MIDDLE, SIDE}) {
            ZonePanDrag pan = new ZonePanDrag();
            Assertions.assertFalse(pan.press(button), "button " + button + " does not pan");
            Assertions.assertFalse(pan.isActive(), "and starts nothing (button " + button + ")");
        }
    }

    @Test
    void releasingAnotherButtonMidPanDoesNotEndIt() {
        for (int other : new int[] {RIGHT, MIDDLE, SIDE}) {
            ZonePanDrag pan = new ZonePanDrag();
            pan.press(LEFT);
            Assertions.assertFalse(pan.release(other), "button " + other + " did not start the pan, so it cannot end it");
            Assertions.assertTrue(pan.isActive(), "the pan carries on (button " + other + " released)");
            Assertions.assertTrue(pan.release(LEFT));
            Assertions.assertFalse(pan.isActive());
        }
    }

    @Test
    void pressingAnotherButtonMidPanDoesNotChangeIt() {
        ZonePanDrag pan = new ZonePanDrag();
        pan.press(LEFT);
        Assertions.assertFalse(pan.press(RIGHT), "a second button takes no part in the pan");
        Assertions.assertTrue(pan.isActive());
        Assertions.assertFalse(pan.release(RIGHT));
        Assertions.assertTrue(pan.isActive(), "and its release leaves the pan running");
        Assertions.assertTrue(pan.release(LEFT));
    }

    @Test
    void aReleaseWithNoPanActiveEndsNothing() {
        ZonePanDrag pan = new ZonePanDrag();
        Assertions.assertFalse(pan.release(LEFT));
        Assertions.assertFalse(pan.release(RIGHT));
        Assertions.assertFalse(pan.isActive());
    }

    @Test
    void aFreshLeftPressRestartsAPanWhoseReleaseWasLost() {
        // A release the screen never saw (the window lost focus mid-drag) must not strand the pan for good.
        ZonePanDrag pan = new ZonePanDrag();
        pan.press(LEFT);
        Assertions.assertTrue(pan.press(LEFT));
        Assertions.assertTrue(pan.isActive());
        Assertions.assertTrue(pan.release(LEFT));
        Assertions.assertFalse(pan.isActive());
    }

    @Test
    void cancelDropsThePan() {
        ZonePanDrag pan = new ZonePanDrag();
        pan.press(LEFT);
        pan.cancel();
        Assertions.assertFalse(pan.isActive());
        Assertions.assertFalse(pan.release(LEFT));
    }
}
