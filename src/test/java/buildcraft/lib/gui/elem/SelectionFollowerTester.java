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

/** Covers {@link SelectionFollower}: a scrollable list scrolls to a selection that changed from outside (GUI opened
 *  on a saved selection, a server sync), exactly once, and never chases a selection the player has scrolled away
 *  from. First user: the Electronic Library's snapshot list. */
public class SelectionFollowerTester {

    private static final int ROWS = 13;

    private static List<String> entries(int count) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add("e" + i);
        }
        return list;
    }

    private static ScrollWindow window(List<String> list) {
        ScrollWindow w = new ScrollWindow(ROWS);
        w.setTotal(list.size());
        return w;
    }

    @Test
    public void aNewSelectionOffScreenIsScrolledIntoView() {
        List<String> list = entries(40);
        ScrollWindow w = window(list);
        SelectionFollower<String> f = new SelectionFollower<>();
        Assertions.assertTrue(f.follow(w, list, "e30"));
        Assertions.assertTrue(w.isVisible(30));
        Assertions.assertEquals(30, w.indexAtRow(ROWS - 1), "minimal scroll: it lands on the bottom row");
    }

    @Test
    public void anUnchangedSelectionIsNotChasedAfterAManualScroll() {
        List<String> list = entries(40);
        ScrollWindow w = window(list);
        SelectionFollower<String> f = new SelectionFollower<>();
        f.follow(w, list, "e30");
        w.setOffset(0); // the player scrolls back to the top
        for (int tick = 0; tick < 5; tick++) {
            Assertions.assertFalse(f.follow(w, list, "e30"), "per-tick refresh must not snap back (tick " + tick + ")");
        }
        Assertions.assertEquals(0, w.getOffset());
    }

    @Test
    public void anEqualButDistinctSelectionCountsAsUnchanged() {
        List<String> list = entries(40);
        ScrollWindow w = window(list);
        SelectionFollower<String> f = new SelectionFollower<>();
        f.follow(w, list, "e30");
        w.setOffset(0);
        // A server re-sync decodes a fresh, equal key; that is not a new selection.
        Assertions.assertFalse(f.follow(w, list, new String("e30")));
        Assertions.assertEquals(0, w.getOffset());
    }

    @Test
    public void aSelectionNotYetInTheListIsFollowedOnceItAppears() {
        List<String> list = entries(20);
        ScrollWindow w = window(list);
        SelectionFollower<String> f = new SelectionFollower<>();
        Assertions.assertFalse(f.follow(w, list, "late"), "nothing to scroll to yet");
        Assertions.assertEquals(0, w.getOffset());

        list.add("late"); // the snapshot arrives in the client library
        w.setTotal(list.size());
        Assertions.assertTrue(f.follow(w, list, "late"), "the deferred follow fires once the entry exists");
        Assertions.assertTrue(w.isVisible(20));

        w.setOffset(0);
        Assertions.assertFalse(f.follow(w, list, "late"), "and it fires only once");
    }

    @Test
    public void clearingTheSelectionNeverScrolls() {
        List<String> list = entries(40);
        ScrollWindow w = window(list);
        SelectionFollower<String> f = new SelectionFollower<>();
        f.follow(w, list, "e30");
        int offset = w.getOffset();
        Assertions.assertFalse(f.follow(w, list, null), "a delete clears the selection");
        Assertions.assertEquals(offset, w.getOffset());

        w.setOffset(0);
        Assertions.assertTrue(f.follow(w, list, "e30"), "re-selecting after a clear is a change again");
    }

    @Test
    public void aSelectionMadeByClickingIsNotFollowed() {
        List<String> list = entries(40);
        ScrollWindow w = window(list);
        w.setOffset(20);
        SelectionFollower<String> f = new SelectionFollower<>();
        f.markShown("e25"); // the player clicked the row it is drawn on
        Assertions.assertFalse(f.follow(w, list, "e25"));
        Assertions.assertEquals(20, w.getOffset());
    }

    @Test
    public void switchingToAnotherSelectionFollowsTheNewOne() {
        List<String> list = entries(40);
        ScrollWindow w = window(list);
        SelectionFollower<String> f = new SelectionFollower<>();
        f.follow(w, list, "e30");
        Assertions.assertTrue(f.follow(w, list, "e2"));
        Assertions.assertEquals(2, w.indexAtRow(0), "a selection above the window lands on the top row");
    }
}
