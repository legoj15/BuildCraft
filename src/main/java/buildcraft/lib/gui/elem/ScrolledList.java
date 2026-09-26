/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.elem;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A selectable list shown through a {@link ScrollWindow}: it remembers the list exactly as last shown, follows a
 * selection that changed from outside ({@link SelectionFollower}), and resolves clicks against what the player saw.
 * <p>
 * A click is hit-tested against the list and window of the last {@link #refresh}, never against a fresh read:
 * refreshing inside the click handler could follow a selection that a server sync delivered since the last frame
 * (scrolling the window), or pick up a list that gained or lost entries, and the click would then land on a
 * neighbouring row. Pure, with no client dependencies, so it is unit-tested directly ({@code ScrolledListTester}).
 */
public final class ScrolledList<K> {
    private final ScrollWindow window;
    private final SelectionFollower<K> follower = new SelectionFollower<>();
    private List<K> shown = List.of();

    public ScrolledList(ScrollWindow window) {
        this.window = window;
    }

    /** Adopt the current list for display: resize the window to it and scroll to a selection that changed from
     *  outside. Call once per tick/frame before drawing. The list is snapshotted ({@link List#copyOf}, a no-op for
     *  a {@code List.of}/{@code copyOf} list), so a caller mutating its own list afterwards cannot move the rows a
     *  click is resolved against. @return the snapshot now shown, for chaining. */
    public List<K> refresh(List<K> list, @Nullable K selected) {
        shown = List.copyOf(list);
        window.setTotal(shown.size());
        follower.follow(window, shown, selected);
        return shown;
    }

    /** The list as of the last {@link #refresh}. */
    public List<K> shown() {
        return shown;
    }

    /** The entry drawn at {@code yFromTop} pixels below the list's top edge, or null for no row. The pick counts
     *  as a selection already on screen, so it is not scrolled to afterwards. */
    @Nullable
    public K pick(double yFromTop, int rowHeight) {
        int index = window.indexAt(yFromTop, rowHeight);
        if (index < 0 || index >= shown.size()) return null;
        K key = shown.get(index);
        follower.markShown(key);
        return key;
    }
}
