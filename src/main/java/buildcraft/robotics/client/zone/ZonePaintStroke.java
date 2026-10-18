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
 *
 * <p>The open stroke follows the cursor through {@link #move}, driven by cursor moves rather than drag events for the
 * reason {@link ZonePanDrag} gives: a drag stops arriving once any second button has been released, although the
 * starting button is still down.
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

    /**
     * The mouse button that started the open stroke ({@code 0} left, {@code 1} right), or {@link ZonePaintInput#NONE}
     * between strokes: the button the screen asks whether it is still physically down, for {@link #move}. Not the
     * latest one pressed, which an open stroke ignores.
     */
    public int button() {
        return stroke == null ? ZonePaintInput.NONE : button;
    }

    /**
     * The cursor moved to column {@code (x, z)}: the open stroke follows it, as the twin of
     * {@link ZonePanDrag#move}. Minecraft's mouse handler keeps one active-button slot and clears it on <em>any</em>
     * release, and sends drag events only while it is set, so a stroke driven by drag events alone freezes once a
     * second button has been tapped mid-stroke (hold left with a brush, tap right, keep dragging) although the
     * starting button is still down. Cursor moves keep arriving, so the screen forwards each one with whether the
     * starting button is physically held.
     *
     * <p>Unlike a pan, a stroke is not dropped when the button turns out not to be held: the release is a separate
     * event that commits the stroke, and it may be only a moment behind this move (or lost), so a move that finds the
     * button up changes nothing and leaves the stroke for that release to end.
     *
     * @param startingButtonHeld whether {@link #button()} is physically down right now
     * @return true if the far corner moved (the preview needs rebuilding); false if there is no open stroke, the
     *         button is not held, or the cursor is still over the corner's column
     */
    public boolean move(int x, int z, boolean startingButtonHeld) {
        if (stroke == null || !startingButtonHeld || (stroke.endX() == x && stroke.endZ() == z)) {
            return false;
        }
        drag(x, z);
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
