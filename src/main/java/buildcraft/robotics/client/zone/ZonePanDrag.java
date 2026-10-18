/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

/**
 * The state of one pan drag on the Zone Planner map, from the press that starts it to the release that ends it. Pure
 * (no Minecraft types) so the button rules can be unit-tested without a screen, and the pan twin of
 * {@link ZonePaintStroke}.
 *
 * <p>Only the left button pans ({@link #PAN_BUTTON}). The pan belongs to the button that started it: letting go of
 * another button while the left one is still held (a right or middle click mid-drag) does not end it.
 */
public final class ZonePanDrag {

    /** The mouse button that pans (left). */
    public static final int PAN_BUTTON = 0;

    private boolean active;

    /** True from a {@link #press} of the pan button until its release (or {@link #cancel}). */
    public boolean isActive() {
        return active;
    }

    /**
     * A mouse button was pressed on the map with an empty hand.
     *
     * @return true, and the pan is active, if {@code mouseButton} pans; false, changing nothing, for any other
     *         button.
     */
    public boolean press(int mouseButton) {
        if (mouseButton != PAN_BUTTON) {
            return false;
        }
        active = true;
        return true;
    }

    /**
     * A mouse button was released.
     *
     * @return true if that ended the pan; false if no pan was active or the button is not the one that started it.
     */
    public boolean release(int mouseButton) {
        if (!active || mouseButton != PAN_BUTTON) {
            return false;
        }
        cancel();
        return true;
    }

    /** Drops the pan, if any. */
    public void cancel() {
        active = false;
    }
}
