/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.elem;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Decides when a scrollable list should scroll to its selection. It scrolls when the selection changed by a route
 * other than a click on a visible row, such as the GUI opening on a saved selection or a server sync. It scrolls
 * only once per change, so the player can scroll away from an unchanged selection freely. A selection that is not in
 * the list yet stays pending and is followed once it appears.
 * <p>
 * Pure, with no client dependencies, so it is unit-tested directly ({@code SelectionFollowerTester}). Selections are
 * compared with {@link Objects#equals}, so a re-synced but equal key does not count as a change.
 */
public final class SelectionFollower<K> {
    @Nullable
    private K followed;

    /** Call on every list refresh, after {@link ScrollWindow#setTotal} has been given {@code list.size()}.
     * @return true if the window scrolled. */
    public boolean follow(ScrollWindow window, List<? extends K> list, @Nullable K selected) {
        if (Objects.equals(selected, followed)) return false;
        if (selected == null) {
            followed = null;
            return false;
        }
        int index = list.indexOf(selected);
        if (index < 0) return false; // not in the local list yet: stay pending
        followed = selected;
        return window.ensureVisible(index);
    }

    /** Record a selection the player made on a row that is already on screen, so it is not followed. */
    public void markShown(@Nullable K selected) {
        followed = selected;
    }
}
