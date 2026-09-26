/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.elem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Covers {@link ScrollWindow}, the pure visible-window math behind scrollable GUI lists (first user: the
 *  Electronic Library's snapshot list, 13 visible rows). Everything here is client-free: row indices in,
 *  row indices / pixel offsets out. */
public class ScrollWindowTester {

    private static final int ROWS = 13;

    private static ScrollWindow window(int total) {
        ScrollWindow w = new ScrollWindow(ROWS);
        w.setTotal(total);
        return w;
    }

    // ---- window shape ----------------------------------------------------------------------------------

    @Test
    public void emptyListShowsNothingAndCannotScroll() {
        ScrollWindow w = window(0);
        Assertions.assertEquals(0, w.getOffset());
        Assertions.assertEquals(0, w.getMaxOffset());
        Assertions.assertEquals(0, w.getFirstVisible());
        Assertions.assertEquals(0, w.getEndVisible(), "no rows to draw");
        Assertions.assertFalse(w.isScrollable());
        Assertions.assertFalse(w.scrollBy(5), "scrolling an empty list must be a no-op");
        Assertions.assertEquals(-1, w.indexAtRow(0), "no entry under the first row");
    }

    @Test
    public void shortListShowsEverythingAndCannotScroll() {
        ScrollWindow w = window(5);
        Assertions.assertEquals(0, w.getFirstVisible());
        Assertions.assertEquals(5, w.getEndVisible());
        Assertions.assertFalse(w.isScrollable());
        Assertions.assertFalse(w.scrollBy(1));
        Assertions.assertFalse(w.scrollBy(-1));
        Assertions.assertEquals(4, w.indexAtRow(4));
        Assertions.assertEquals(-1, w.indexAtRow(5), "rows past the end of a short list are empty");
    }

    @Test
    public void exactlyOneWindowFullCannotScroll() {
        ScrollWindow w = window(ROWS);
        Assertions.assertEquals(0, w.getMaxOffset());
        Assertions.assertEquals(ROWS, w.getEndVisible());
        Assertions.assertFalse(w.isScrollable(), "13 entries fit in 13 rows exactly");
        Assertions.assertFalse(w.scrollBy(1));
        Assertions.assertEquals(ROWS - 1, w.indexAtRow(ROWS - 1));
    }

    @Test
    public void longListScrollsThroughEveryEntry() {
        ScrollWindow w = window(40);
        Assertions.assertEquals(27, w.getMaxOffset());
        Assertions.assertTrue(w.isScrollable());
        Assertions.assertEquals(0, w.getOffset(), "starts at the top");
        Assertions.assertEquals(ROWS, w.getEndVisible(), "only one window is drawn, not the whole list");

        Assertions.assertTrue(w.scrollBy(3));
        Assertions.assertEquals(3, w.getFirstVisible());
        Assertions.assertEquals(3 + ROWS, w.getEndVisible());
        Assertions.assertEquals(3, w.indexAtRow(0), "row 0 maps to the first visible entry");
        Assertions.assertEquals(3 + ROWS - 1, w.indexAtRow(ROWS - 1));

        // The last entry becomes reachable.
        w.setOffset(w.getMaxOffset());
        Assertions.assertEquals(39, w.indexAtRow(ROWS - 1), "last row shows the last entry at the bottom");
        Assertions.assertEquals(40, w.getEndVisible());
        Assertions.assertFalse(w.scrollBy(1), "nothing further below the last entry");
    }

    @Test
    public void rowsOutsideTheWindowMapToNothing() {
        ScrollWindow w = window(40);
        Assertions.assertEquals(-1, w.indexAtRow(-1));
        Assertions.assertEquals(-1, w.indexAtRow(ROWS), "the row below the window is not a list row");
    }

    // ---- clamping --------------------------------------------------------------------------------------

    @Test
    public void scrollClampsAtBothEnds() {
        ScrollWindow w = window(20);
        Assertions.assertFalse(w.scrollBy(-1), "already at the top");
        Assertions.assertEquals(0, w.getOffset());

        Assertions.assertTrue(w.scrollBy(1000), "a huge scroll still moves as far as it can");
        Assertions.assertEquals(7, w.getOffset(), "clamped to total - visible");
        Assertions.assertFalse(w.scrollBy(1), "already at the bottom");
        Assertions.assertEquals(7, w.getOffset());

        Assertions.assertTrue(w.scrollBy(-1000));
        Assertions.assertEquals(0, w.getOffset());

        w.setOffset(-4);
        Assertions.assertEquals(0, w.getOffset());
        w.setOffset(99);
        Assertions.assertEquals(7, w.getOffset());
    }

    @Test
    public void offsetSurvivesARefreshOfTheSameSize() {
        ScrollWindow w = window(30);
        w.setOffset(10);
        w.setTotal(30); // the per-tick refresh
        Assertions.assertEquals(10, w.getOffset(), "a refresh that doesn't change the list must not move the view");
        w.setTotal(35); // an entry was added
        Assertions.assertEquals(10, w.getOffset(), "growing the list must not move the view");
    }

    @Test
    public void shrinkingUnderTheOffsetClamps() {
        ScrollWindow w = window(30);
        w.setOffset(w.getMaxOffset()); // 17
        w.setTotal(20);
        Assertions.assertEquals(7, w.getOffset(), "the window slides up so the bottom row is still the last entry");
        Assertions.assertEquals(20, w.getEndVisible());

        w.setTotal(4);
        Assertions.assertEquals(0, w.getOffset(), "a list that fits snaps back to the top");
        Assertions.assertFalse(w.isScrollable());

        w.setTotal(0);
        Assertions.assertEquals(0, w.getOffset());
        Assertions.assertEquals(0, w.getEndVisible());
    }

    @Test
    public void negativeTotalIsTreatedAsEmpty() {
        ScrollWindow w = window(-3);
        Assertions.assertEquals(0, w.getTotal());
        Assertions.assertEquals(0, w.getEndVisible());
    }

    @Test
    public void rejectsANonPositiveRowCount() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ScrollWindow(0));
    }

    // ---- selection visibility -------------------------------------------------------------------------

    @Test
    public void ensureVisibleScrollsTheMinimumAmount() {
        ScrollWindow w = window(40);
        Assertions.assertFalse(w.ensureVisible(5), "an already-visible entry doesn't move the view");
        Assertions.assertEquals(0, w.getOffset());

        Assertions.assertTrue(w.ensureVisible(20));
        Assertions.assertEquals(8, w.getOffset(), "a selection below the window lands on the bottom row");
        Assertions.assertTrue(w.isVisible(20));
        Assertions.assertEquals(20, w.indexAtRow(ROWS - 1));

        Assertions.assertTrue(w.ensureVisible(2));
        Assertions.assertEquals(2, w.getOffset(), "a selection above the window lands on the top row");
        Assertions.assertEquals(2, w.indexAtRow(0));

        Assertions.assertTrue(w.ensureVisible(39));
        Assertions.assertEquals(w.getMaxOffset(), w.getOffset());
    }

    @Test
    public void ensureVisibleIgnoresMissingSelections() {
        ScrollWindow w = window(40);
        w.setOffset(6);
        Assertions.assertFalse(w.ensureVisible(-1), "no selection (indexOf == -1) must not move the view");
        Assertions.assertFalse(w.ensureVisible(40), "a stale index past the end must not move the view");
        Assertions.assertEquals(6, w.getOffset());
    }

    @Test
    public void isVisibleMatchesTheDrawnWindow() {
        ScrollWindow w = window(40);
        w.setOffset(10);
        Assertions.assertFalse(w.isVisible(9));
        Assertions.assertTrue(w.isVisible(10));
        Assertions.assertTrue(w.isVisible(22));
        Assertions.assertFalse(w.isVisible(23));
        Assertions.assertFalse(w.isVisible(-1));
    }

    // ---- mouse wheel -----------------------------------------------------------------------------------

    @Test
    public void wheelNotchesMapToRows() {
        Assertions.assertEquals(-1, ScrollWindow.wheelRows(1.0), "wheel up (positive) scrolls towards the top");
        Assertions.assertEquals(1, ScrollWindow.wheelRows(-1.0), "wheel down (negative) scrolls towards the bottom");
        Assertions.assertEquals(-3, ScrollWindow.wheelRows(3.0), "a multi-notch event moves one row per notch");
        Assertions.assertEquals(-1, ScrollWindow.wheelRows(0.2), "a fractional touchpad delta still moves one row");
        Assertions.assertEquals(1, ScrollWindow.wheelRows(-0.2));
        Assertions.assertEquals(0, ScrollWindow.wheelRows(0.0));
    }

    // ---- scrollbar mapping -----------------------------------------------------------------------------

    private static final int TRACK = 110, THUMB = 12;

    @Test
    public void thumbSitsAtTheEndsOfTheTrack() {
        ScrollWindow w = window(40);
        Assertions.assertEquals(0, w.thumbTop(TRACK, THUMB));
        w.setOffset(w.getMaxOffset());
        Assertions.assertEquals(TRACK - THUMB, w.thumbTop(TRACK, THUMB), "bottom of the list = bottom of the track");
    }

    @Test
    public void thumbIsParkedAtTheTopWhenNothingScrolls() {
        Assertions.assertEquals(0, window(5).thumbTop(TRACK, THUMB));
        Assertions.assertEquals(0, window(0).thumbTop(TRACK, THUMB));
    }

    @Test
    public void thumbPositionRoundTripsThroughTheOffset() {
        ScrollWindow w = window(40);
        for (int offset = 0; offset <= w.getMaxOffset(); offset++) {
            w.setOffset(offset);
            int top = w.thumbTop(TRACK, THUMB);
            Assertions.assertEquals(offset, w.offsetForThumbTop(top, TRACK, THUMB),
                "dragging the thumb to where it is drawn must not move the list (offset " + offset + ")");
        }
    }

    /** With more offsets than track pixels (112+ entries on the library's 98 px of travel) several offsets share a
     *  thumb pixel, so a plain pixel -> offset conversion can't round-trip. Grabbing the thumb without moving it
     *  must still leave the list exactly where it was, for every offset. */
    @Test
    public void holdingTheThumbStillNeverMovesAnOverfullList() {
        ScrollWindow w = window(150);
        Assertions.assertTrue(w.getMaxOffset() > TRACK - THUMB, "precondition: more offsets than track pixels");
        for (int offset = 0; offset <= w.getMaxOffset(); offset++) {
            w.setOffset(offset);
            int top = w.thumbTop(TRACK, THUMB);
            Assertions.assertEquals(offset, w.offsetForThumbDrag(top, TRACK, THUMB),
                "a drag that leaves the thumb on its drawn pixel must not scroll (offset " + offset + ")");
        }
    }

    @Test
    public void thumbDragToAnotherPixelLandsOnThatPixel() {
        ScrollWindow w = window(150);
        w.setOffset(61);
        int top = w.thumbTop(TRACK, THUMB);
        for (int target = 0; target <= TRACK - THUMB; target++) {
            if (target == top) continue;
            int offset = w.offsetForThumbDrag(target, TRACK, THUMB);
            ScrollWindow probe = window(150);
            probe.setOffset(offset);
            Assertions.assertEquals(target, probe.thumbTop(TRACK, THUMB),
                "dragging to pixel " + target + " must draw the thumb on that pixel");
        }
    }

    @Test
    public void thumbDragClampsOutsideTheTrack() {
        ScrollWindow w = window(40);
        Assertions.assertEquals(0, w.offsetForThumbTop(-50, TRACK, THUMB));
        Assertions.assertEquals(w.getMaxOffset(), w.offsetForThumbTop(500, TRACK, THUMB));
        Assertions.assertEquals(0, window(5).offsetForThumbTop(60, TRACK, THUMB), "non-scrollable stays at 0");
    }

    @Test
    public void thumbDragTravelsMonotonically() {
        ScrollWindow w = window(200);
        int last = -1;
        for (int top = 0; top <= TRACK - THUMB; top++) {
            int offset = w.offsetForThumbTop(top, TRACK, THUMB);
            Assertions.assertTrue(offset >= last, "moving the thumb down never scrolls the list up");
            last = offset;
        }
        Assertions.assertEquals(w.getMaxOffset(), last);
    }

    // ---- cursor -> row -----------------------------------------------------------------------------------

    @Test
    public void cursorMapsToTheEntryDrawnUnderIt() {
        ScrollWindow w = window(40);
        w.setOffset(10);
        int rowH = 8;
        Assertions.assertEquals(10, w.indexAt(0, rowH), "top pixel of the first row");
        Assertions.assertEquals(10, w.indexAt(7.9, rowH), "bottom pixel of the first row");
        Assertions.assertEquals(11, w.indexAt(8, rowH));
        Assertions.assertEquals(22, w.indexAt(ROWS * rowH - 0.5, rowH), "last pixel of the last row");
        Assertions.assertEquals(-1, w.indexAt(ROWS * rowH, rowH), "just below the window");
        Assertions.assertEquals(-1, w.indexAt(-0.5, rowH),
            "just above the window must miss, not truncate towards zero onto the first row");
        Assertions.assertEquals(-1, window(3).indexAt(3 * rowH + 1, rowH), "empty rows under a short list");
    }

    // ---- scrollbar grab / track click -----------------------------------------------------------------

    @Test
    public void grabbingTheThumbKeepsItUnderTheCursor() {
        // 150 entries: more offsets than track pixels, so every thumb pixel is reachable and the drag is exactly 1:1.
        ScrollWindow w = window(150);
        w.setOffset(40);
        int top = w.thumbTop(TRACK, THUMB);
        double cursor = top + 4.0;
        double grab = w.thumbGrabOffset(cursor, TRACK, THUMB);
        Assertions.assertEquals(4.0, grab, 1e-9, "a press on the thumb grabs it where it was pressed");
        Assertions.assertEquals(40, w.offsetForCursor(cursor, grab, TRACK, THUMB), "the press alone never scrolls");

        w.setOffset(w.offsetForCursor(cursor + 20, grab, TRACK, THUMB));
        Assertions.assertEquals(top + 20, w.thumbTop(TRACK, THUMB), "the thumb follows the cursor 1:1 while dragged");
    }

    @Test
    public void clickingTheTrackCentresTheThumbThere() {
        ScrollWindow w = window(40);
        double cursor = 60.0; // well below the thumb, which starts at the top
        double grab = w.thumbGrabOffset(cursor, TRACK, THUMB);
        Assertions.assertEquals(THUMB / 2.0, grab, 1e-9, "a track click grabs the thumb by its middle");
        w.setOffset(w.offsetForCursor(cursor, grab, TRACK, THUMB));
        Assertions.assertEquals(60 - THUMB / 2, w.thumbTop(TRACK, THUMB), "the thumb is centred under the cursor");

        w.setOffset(w.offsetForCursor(TRACK - 1, THUMB / 2.0, TRACK, THUMB));
        Assertions.assertEquals(w.getMaxOffset(), w.getOffset(), "a click at the very bottom clamps to the end");
        w.setOffset(w.offsetForCursor(0, THUMB / 2.0, TRACK, THUMB));
        Assertions.assertEquals(0, w.getOffset(), "a click at the very top clamps to the start");
    }
}
