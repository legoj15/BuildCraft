/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The state of one pan drag on the Zone Planner map, from the press that starts it to the release that ends it. Pure
 * (no Minecraft types, only {@code InputConstants}' inlined button numbers) so the button rules can be unit-tested
 * without a screen, and the pan twin of {@link ZonePaintStroke}.
 *
 * <p>Only the left button pans ({@link #PAN_BUTTON}). The pan belongs to the button that started it: letting go of
 * another button while the left one is still held (a right or middle click mid-drag) does not end it.
 *
 * <p>The pan is driven by cursor moves ({@link #move}), never by drag events. Minecraft's {@code MouseHandler} keeps a
 * single active-button slot, clears it on <em>any</em> release, and dispatches {@code mouseDragged} only while it is
 * set; {@code mouseMoved} it dispatches on every movement. After a right tap mid-pan the screen therefore gets moves but
 * no drags, and a drag-driven pan would freeze until the left button was released. Because moves keep arriving with no
 * button down at all, each one is told whether the left button is physically held, and a pan whose release never
 * reached the screen (focus lost mid-drag) is dropped on the first move without it.
 */
public final class ZonePanDrag {

    /** The mouse button that pans (left). */
    public static final int PAN_BUTTON = InputConstants.MOUSE_BUTTON_LEFT;

    /** How far the map moves for one cursor move, in screen pixels. */
    public record Delta(double dx, double dy) {}

    private boolean active;
    private double lastX, lastY;

    /** True from a {@link #press} of the pan button until its release (or {@link #cancel}). */
    public boolean isActive() {
        return active;
    }

    /**
     * A mouse button was pressed on the map with an empty hand, at {@code (x, y)}.
     *
     * @return true, and the pan is active and measures from {@code (x, y)}, if {@code mouseButton} pans; false,
     *         changing nothing, for any other button.
     */
    public boolean press(int mouseButton, double x, double y) {
        if (mouseButton != PAN_BUTTON) {
            return false;
        }
        active = true;
        lastX = x;
        lastY = y;
        return true;
    }

    /**
     * The cursor moved to {@code (x, y)}.
     *
     * @param leftHeld whether the pan button is physically down right now. False while a pan is active means its
     *        release was lost: the pan is dropped.
     * @return how far to pan since the last move (or the press), or null if no pan is active (or it was just dropped).
     */
    @Nullable
    public Delta move(double x, double y, boolean leftHeld) {
        if (!active) {
            return null;
        }
        if (!leftHeld) {
            cancel();
            return null;
        }
        Delta delta = new Delta(x - lastX, y - lastY);
        lastX = x;
        lastY = y;
        return delta;
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
