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

import buildcraft.lib.test.MainSourceSet;

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
        List<String> list = entries(40);

        // Control: the scenario really does move the rows. Had the click handler refreshed first (the old GUI),
        // the refresh would follow the synced e30 and scroll, and the click would land on another entry.
        ScrollWindow refreshedWindow = new ScrollWindow(ROWS);
        ScrolledList<String> refreshedFirst = new ScrolledList<>(refreshedWindow);
        refreshedFirst.refresh(list, null);
        refreshedFirst.refresh(list, "e30"); // the sync, applied inside the click
        Assertions.assertNotEquals(0, refreshedWindow.getOffset(), "control: following e30 scrolls the window");
        Assertions.assertNotEquals("e2", refreshedFirst.pick(rowY(2), ROW_H),
            "control: a refresh inside the click shifts the row under the cursor");

        // The contract: the frame showed rows e0..e12; the sync (e30) arrives before the click; the click only
        // picks (the synced e30 sits on the tile, unread), so it resolves against the drawn rows.
        ScrollWindow w = new ScrollWindow(ROWS);
        ScrolledList<String> l = new ScrolledList<>(w);
        l.refresh(list, null);
        Assertions.assertEquals("e2", l.pick(rowY(2), ROW_H), "the row drawn under the cursor wins");
        Assertions.assertEquals(0, w.getOffset(), "a click never scrolls the window");
    }

    @Test
    public void aSourceListChangedInPlaceAfterTheFrameDoesNotChangeThePick() {
        ScrollWindow w = new ScrollWindow(ROWS);
        ScrolledList<String> l = new ScrolledList<>(w);
        List<String> source = entries(5);
        l.refresh(source, null); // drawn: e0..e4
        // The caller's list mutates before the click: an entry sorting first arrives, one is deleted.
        source.add(0, "aaa");
        source.remove("e3");
        Assertions.assertEquals("e1", l.pick(rowY(1), ROW_H), "the click resolves against the rows as drawn");
        Assertions.assertEquals("e4", l.pick(rowY(4), ROW_H), "even past an entry that was removed since");
        Assertions.assertEquals(entries(5), l.shown(), "shown() is the drawn snapshot, not a live view");
    }

    /** The contract above only holds if the screen's click handler picks WITHOUT refreshing first. The Electronic
     *  Library is a client screen with no headless harness (GuiTester drives server-side menus only), so its click
     *  path is pinned on this node's compiled source instead: {@code clickList} picks from {@code rows} and never
     *  refreshes the list before (or instead of) that pick. */
    @Test
    public void theLibraryClickPicksFromTheDrawnRowsWithoutRefreshing() {
        String path = "buildcraft/builders/gui/GuiElectronicLibrary.java";
        String code = MainSourceSet.javaFiles().stream()
            .filter(f -> f.relativePath().equals(path))
            .map(f -> MainSourceSet.codeOnly(f.text()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("this node did not compile " + path));
        String body = methodBody(code, "boolean clickList(");
        Assertions.assertTrue(body.contains("rows.pick("), "clickList must hit-test through ScrolledList.pick");
        Assertions.assertFalse(body.contains("refreshList("),
            "clickList must not refresh the list before picking: a sync since the frame would shift the rows");
        Assertions.assertFalse(body.contains(".refresh("), "clickList must not refresh the ScrolledList itself");
    }

    /** The brace-balanced body of the first method whose declaration contains {@code signature}. */
    private static String methodBody(String code, String signature) {
        int at = code.indexOf(signature);
        Assertions.assertTrue(at >= 0, "method not found: " + signature);
        int open = code.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
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
