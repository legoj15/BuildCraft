/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import javax.annotation.Nullable;

/**
 * The state of one paint or erase stroke on the Zone Planner map, from the press that starts it to the release that
 * ends it. Pure (no Minecraft types) so the button rules can be unit-tested without a screen.
 *
 * <p>A stroke belongs to the mouse button that started it ({@link ZonePaintInput#strokeFor}: left paints, right
 * erases). While it is open no other press does anything, so a second button pressed part-way through can neither
 * restart the stroke nor turn a paint into an erase, and only the release of the starting button ends it: letting go
 * of the other button commits nothing.
 */
public final class ZonePaintStroke {

    /** A stroke as it stands: the layer it edits, whether it marks or clears, and its two corner columns (world blocks). */
    public record Stroke(int colorIndex, boolean paints, int startX, int startZ, int endX, int endZ) {}

    private int button = ZonePaintInput.NONE;
    private Stroke stroke;

    /** True from a successful {@link #press} until the starting button is released (or {@link #cancel}). */
    public boolean isOpen() {
        return stroke != null;
    }

    /** The open stroke as it stands, or null between strokes. */
    @Nullable
    public Stroke current() {
        return stroke;
    }

    /**
     * Starts a stroke on layer {@code colorIndex} at column {@code (x, z)} with {@code mouseButton}.
     *
     * @return false, changing nothing, if a stroke is already open or the button starts none (middle, side buttons).
     */
    public boolean press(int mouseButton, int colorIndex, int x, int z) {
        if (stroke != null || ZonePaintInput.strokeFor(mouseButton) == ZonePaintInput.NONE) {
            return false;
        }
        button = mouseButton;
        stroke = new Stroke(colorIndex, ZonePaintInput.strokeFor(mouseButton) == ZonePaintInput.PAINT, x, z, x, z);
        return true;
    }

    /** Moves the open stroke's far corner to column {@code (x, z)}; nothing happens between strokes. */
    public void drag(int x, int z) {
        if (stroke != null) {
            stroke = new Stroke(stroke.colorIndex(), stroke.paints(), stroke.startX(), stroke.startZ(), x, z);
        }
    }

    /**
     * A mouse button was released.
     *
     * @return the finished stroke if {@code mouseButton} is the one that started it (the stroke is then closed), else
     *         null: any other button leaves an open stroke open, and with none open there is nothing to end.
     */
    @Nullable
    public Stroke release(int mouseButton) {
        if (stroke == null || mouseButton != button) {
            return null;
        }
        Stroke done = stroke;
        cancel();
        return done;
    }

    /** Drops the open stroke without committing it. */
    public void cancel() {
        stroke = null;
        button = ZonePaintInput.NONE;
    }
}
