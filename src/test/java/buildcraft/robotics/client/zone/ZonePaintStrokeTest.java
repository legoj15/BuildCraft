/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Which mouse buttons start, change and end a Zone Planner stroke. A stroke belongs to the button that started it:
 * a second button pressed mid-stroke used to overwrite the stroke (a left-drag paint could be turned into a right-button
 * erase), and letting go of either button committed it.
 */
public class ZonePaintStrokeTest {

    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int MIDDLE = 2;

    @Test
    void aLeftStrokePaintsFromPressToRelease() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        Assertions.assertFalse(stroke.isOpen());
        Assertions.assertTrue(stroke.press(LEFT, 5, 10, 20));
        Assertions.assertTrue(stroke.isOpen());
        Assertions.assertEquals(new ZonePaintStroke.Stroke(5, true, 10, 20, 10, 20), stroke.current(),
                "a press alone is a one-column stroke");
        stroke.drag(14, 22);
        Assertions.assertEquals(new ZonePaintStroke.Stroke(5, true, 10, 20, 14, 22), stroke.current());

        Assertions.assertEquals(new ZonePaintStroke.Stroke(5, true, 10, 20, 14, 22), stroke.release(LEFT));
        Assertions.assertFalse(stroke.isOpen());
        Assertions.assertNull(stroke.current());
    }

    @Test
    void aRightStrokeErases() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        Assertions.assertTrue(stroke.press(RIGHT, 2, 0, 0));
        stroke.drag(3, 3);
        ZonePaintStroke.Stroke done = stroke.release(RIGHT);
        Assertions.assertNotNull(done);
        Assertions.assertFalse(done.paints(), "the right button erases");
    }

    @Test
    void aSecondButtonPressedMidStrokeIsIgnored() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        stroke.press(LEFT, 5, 10, 20);
        stroke.drag(12, 12);

        Assertions.assertFalse(stroke.press(RIGHT, 7, 99, 99), "an open stroke takes no second press");
        Assertions.assertEquals(new ZonePaintStroke.Stroke(5, true, 10, 20, 12, 12), stroke.current(),
                "the stroke is unchanged: still a paint, same layer, same corners");

        stroke.drag(15, 15);
        ZonePaintStroke.Stroke done = stroke.release(LEFT);
        Assertions.assertEquals(new ZonePaintStroke.Stroke(5, true, 10, 20, 15, 15), done,
                "and it commits as the paint it began as, not an erase");
    }

    @Test
    void anEraseStrokeIsNotTakenOverByTheLeftButton() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        stroke.press(RIGHT, 5, 10, 20);
        Assertions.assertFalse(stroke.press(LEFT, 5, 0, 0));
        Assertions.assertNull(stroke.release(LEFT), "the left button did not start it, so its release does not end it");
        Assertions.assertTrue(stroke.isOpen());
        ZonePaintStroke.Stroke done = stroke.release(RIGHT);
        Assertions.assertNotNull(done);
        Assertions.assertFalse(done.paints());
        Assertions.assertEquals(10, done.startX());
    }

    @Test
    void releasingAnotherButtonCommitsNothing() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        stroke.press(LEFT, 5, 1, 1);
        stroke.press(RIGHT, 5, 2, 2); // ignored
        Assertions.assertNull(stroke.release(RIGHT), "letting go of the other button must not commit the stroke");
        Assertions.assertTrue(stroke.isOpen(), "it is still being drawn");
        Assertions.assertNull(stroke.release(MIDDLE));
        Assertions.assertTrue(stroke.isOpen());
        Assertions.assertNotNull(stroke.release(LEFT), "only the starting button's release ends it");
        Assertions.assertFalse(stroke.isOpen());
    }

    @Test
    void buttonsThatStartNoStrokeStartNone() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        Assertions.assertFalse(stroke.press(MIDDLE, 5, 1, 1));
        Assertions.assertFalse(stroke.press(3, 5, 1, 1));
        Assertions.assertFalse(stroke.isOpen());
        Assertions.assertNull(stroke.release(MIDDLE), "and their release ends nothing");
    }

    @Test
    void aReleaseWithNoStrokeOpenDoesNothing() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        Assertions.assertNull(stroke.release(LEFT));
        stroke.drag(5, 5); // a stray drag does not conjure one up
        Assertions.assertFalse(stroke.isOpen());
    }

    @Test
    void aNewStrokeCanBeginOnceTheLastEnded() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        stroke.press(LEFT, 1, 0, 0);
        stroke.release(LEFT);
        Assertions.assertTrue(stroke.press(RIGHT, 9, 4, 4));
        Assertions.assertEquals(new ZonePaintStroke.Stroke(9, false, 4, 4, 4, 4), stroke.current());
    }

    @Test
    void cancelDropsTheStrokeWithoutCommitting() {
        ZonePaintStroke stroke = new ZonePaintStroke();
        stroke.press(LEFT, 1, 0, 0);
        stroke.cancel();
        Assertions.assertFalse(stroke.isOpen());
        Assertions.assertNull(stroke.release(LEFT));
    }
}
