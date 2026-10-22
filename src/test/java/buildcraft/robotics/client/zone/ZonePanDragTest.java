/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Which mouse buttons start and end a Zone Planner pan, and what moves it. Only the left button starts one, and only
 * its release ends it: releasing any button used to end the pan, so a right or middle click made while dragging dropped
 * the map.
 *
 * <p>The pan follows cursor <em>moves</em>, not drag events, because that is the only stream that survives a second
 * button: Minecraft's {@code MouseHandler} clears its single active-button slot on any release and dispatches
 * {@code mouseDragged} only while that slot is set, but dispatches {@code mouseMoved} on every movement. So after a
 * right tap mid-pan the screen gets moves and no drags; a pan driven by drags would freeze until the left button was
 * released. The tests below replay that stream: {@link ZonePanDrag#move} once per movement, nothing on drags.
 */
public class ZonePanDragTest {

    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final int MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;
    /** The first number past left, right and middle, on any line's numbering (0 to 2 up to 26.2, 1 to 3 on 26.3). */
    private static final int SIDE = 1 + Math.max(InputConstants.MOUSE_BUTTON_LEFT,
            Math.max(InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.MOUSE_BUTTON_MIDDLE));

    private static final boolean HELD = true;
    private static final boolean UP = false;

    private static void assertDelta(ZonePanDrag.Delta delta, double dx, double dy, String what) {
        Assertions.assertNotNull(delta, what + ": the map pans");
        Assertions.assertEquals(dx, delta.dx(), 1e-9, what + ": dx");
        Assertions.assertEquals(dy, delta.dy(), 1e-9, what + ": dy");
    }

    @Test
    void aLeftPressStartsAPanAndItsReleaseEndsIt() {
        ZonePanDrag pan = new ZonePanDrag();
        Assertions.assertFalse(pan.isActive(), "no pan before a press");
        Assertions.assertTrue(pan.press(LEFT, 10, 20));
        Assertions.assertTrue(pan.isActive());
        Assertions.assertTrue(pan.release(LEFT), "letting go of the button that began it ends it");
        Assertions.assertFalse(pan.isActive());
    }

    @Test
    void movesPanByTheCursorTravelSinceTheLastOne() {
        ZonePanDrag pan = new ZonePanDrag();
        pan.press(LEFT, 10, 20);
        assertDelta(pan.move(13, 18, HELD), 3, -2, "first move");
        assertDelta(pan.move(13.5, 25, HELD), 0.5, 7, "second move");
    }

    @Test
    void noPanMeansMovesPanNothing() {
        ZonePanDrag pan = new ZonePanDrag();
        Assertions.assertNull(pan.move(5, 5, HELD), "a move with no pan active pans nothing");
        pan.press(LEFT, 0, 0);
        pan.release(LEFT);
        Assertions.assertNull(pan.move(9, 9, HELD), "nor does one after the pan ended");
    }

    @Test
    void onlyTheLeftButtonStartsAPan() {
        for (int button : new int[] {RIGHT, MIDDLE, SIDE}) {
            ZonePanDrag pan = new ZonePanDrag();
            Assertions.assertFalse(pan.press(button, 0, 0), "button " + button + " does not pan");
            Assertions.assertFalse(pan.isActive(), "and starts nothing (button " + button + ")");
            Assertions.assertNull(pan.move(4, 4, HELD), "so moves pan nothing (button " + button + ")");
        }
    }

    @Test
    void aTapOfAnotherButtonMidPanLeavesTheMapFollowingTheCursor() {
        // The reported sequence: hold left and drag, tap right (press + release), keep dragging with left held.
        // After the tap Minecraft sends this screen moves but no drags, so the pan must keep going on moves alone.
        for (int other : new int[] {RIGHT, MIDDLE, SIDE}) {
            ZonePanDrag pan = new ZonePanDrag();
            pan.press(LEFT, 0, 0);
            assertDelta(pan.move(5, 0, HELD), 5, 0, "dragging before the tap (button " + other + ")");
            Assertions.assertFalse(pan.press(other, 5, 0), "a second button takes no part in the pan");
            Assertions.assertFalse(pan.release(other), "button " + other + " did not start the pan, so it cannot end it");
            Assertions.assertTrue(pan.isActive(), "the pan carries on (button " + other + " released)");
            assertDelta(pan.move(5, 8, HELD), 0, 8, "dragging after the tap (button " + other + ")");
            assertDelta(pan.move(1, 8, HELD), -4, 0, "and on (button " + other + ")");
            Assertions.assertTrue(pan.release(LEFT));
            Assertions.assertFalse(pan.isActive());
            Assertions.assertNull(pan.move(0, 0, UP), "nothing pans once the left button is let go");
        }
    }

    @Test
    void aMoveWithTheLeftButtonUpDropsAPanWhoseReleaseWasLost() {
        // A release the screen never saw (the window lost focus mid-drag) must not leave the map stuck to a cursor
        // with no button held.
        ZonePanDrag pan = new ZonePanDrag();
        pan.press(LEFT, 0, 0);
        Assertions.assertNull(pan.move(6, 6, UP), "the left button is up, so the move pans nothing");
        Assertions.assertFalse(pan.isActive(), "and the stranded pan is dropped");
        Assertions.assertNull(pan.move(9, 9, HELD), "a later held move does not resume it without a fresh press");
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
        ZonePanDrag pan = new ZonePanDrag();
        pan.press(LEFT, 0, 0);
        Assertions.assertTrue(pan.press(LEFT, 30, 40));
        Assertions.assertTrue(pan.isActive());
        assertDelta(pan.move(31, 40, HELD), 1, 0, "the restarted pan measures from the new press");
        Assertions.assertTrue(pan.release(LEFT));
        Assertions.assertFalse(pan.isActive());
    }

    @Test
    void cancelDropsThePan() {
        ZonePanDrag pan = new ZonePanDrag();
        pan.press(LEFT, 0, 0);
        pan.cancel();
        Assertions.assertFalse(pan.isActive());
        Assertions.assertFalse(pan.release(LEFT));
        Assertions.assertNull(pan.move(3, 3, HELD));
    }
}
