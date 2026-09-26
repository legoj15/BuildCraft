/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.elem;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Covers {@link ScrolledList}: a click picks the row the player saw, even when a selection sync or a changed
 *  source list arrives between the last frame and the click. First user: the Electronic Library's snapshot list. */
public class ScrolledListTester {

    private static final int ROWS = 13;
    private static final int ROW_H = 8;

    private static List<String> entries(int count) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add("e" + i);
        }
        return list;
    }

    /** Y offset (from the list top) of the middle of visible row {@code row}. */
    private static double rowY(int row) {
        return row * ROW_H + ROW_H / 2.0;
    }

    @Test
    public void aSelectionSyncBetweenFrameAndClickDoesNotShiftTheClickedRow() {
        ScrollWindow w = new ScrollWindow(ROWS);
        ScrolledList<String> l = new ScrolledList<>(w);
        List<String> list = entries(40);
        l.refresh(list, null); // the frame the player sees: rows e0..e12
        // A server sync selects e30 before the click is handled. The old GUI re-refreshed inside the click
        // handler, which followed e30 and scrolled the window, so the click resolved to a different row.
        Assertions.assertEquals("e2", l.pick(rowY(2), ROW_H), "the row drawn under the cursor wins");
        Assertions.assertEquals(0, w.getOffset(), "a click never scrolls the window");
    }

    @Test
    public void aClickedRowIsNotFollowedAfterwards() {
        ScrollWindow w = new ScrollWindow(ROWS);
        ScrolledList<String> l = new ScrolledList<>(w);
        List<String> list = entries(40);
        l.refresh(list, null);
        w.setOffset(20);
        Assertions.assertEquals("e25", l.pick(rowY(5), ROW_H));
        l.refresh(list, "e25"); // the optimistic / echoed selection
        Assertions.assertEquals(20, w.getOffset());
    }

    @Test
    public void aClickUsesTheListAsShownNotANewerOne() {
        ScrollWindow w = new ScrollWindow(ROWS);
        ScrolledList<String> l = new ScrolledList<>(w);
        List<String> shown = entries(5);
        l.refresh(shown, null);
        // The source list gained an entry that sorts first, but no frame has shown it yet.
        List<String> newer = new ArrayList<>(shown);
        newer.add(0, "aaa");
        Assertions.assertEquals("e1", l.pick(rowY(1), ROW_H));
        l.refresh(newer, "e1");
        Assertions.assertEquals(newer, l.shown());
    }

    @Test
    public void clicksOutsideTheRowsPickNothing() {
        ScrollWindow w = new ScrollWindow(ROWS);
        ScrolledList<String> l = new ScrolledList<>(w);
        l.refresh(entries(3), null);
        Assertions.assertNull(l.pick(rowY(3), ROW_H), "below the last entry");
        Assertions.assertNull(l.pick(-1, ROW_H), "above the list");
        Assertions.assertNull(l.pick(rowY(ROWS), ROW_H), "below the window");
    }

    @Test
    public void refreshFollowsAnOutsideSelection() {
        ScrollWindow w = new ScrollWindow(ROWS);
        ScrolledList<String> l = new ScrolledList<>(w);
        List<String> list = entries(40);
        l.refresh(list, "e30");
        Assertions.assertTrue(w.isVisible(30));
        Assertions.assertEquals(40, w.getTotal());
    }
}
