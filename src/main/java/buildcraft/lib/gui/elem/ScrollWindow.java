/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.elem;

/**
 * The visible window of a fixed-row-count list that is longer than the space it is drawn in: which entries are
 * on screen, how far it can scroll, and where a scrollbar thumb sits for that position.
 * <p>
 * Pure integer math with no client dependencies, so it is unit-tested directly ({@code ScrollWindowTester}).
 * The offset is the index of the first visible entry. It only moves when asked to, or when the list shrinks
 * under it (then it clamps so the bottom row still shows the last entry), so a GUI can call
 * {@link #setTotal(int)} every tick without disturbing the player's scroll position.
 */
public final class ScrollWindow {
    private final int visibleRows;
    private int total;
    private int offset;

    public ScrollWindow(int visibleRows) {
        if (visibleRows <= 0) {
            throw new IllegalArgumentException("visibleRows must be positive, got " + visibleRows);
        }
        this.visibleRows = visibleRows;
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public int getTotal() {
        return total;
    }

    public int getOffset() {
        return offset;
    }

    /** The largest valid offset: 0 when everything fits. */
    public int getMaxOffset() {
        return Math.max(0, total - visibleRows);
    }

    /** Update the list length (call on every refresh). Clamps the offset if the list shrank under it. */
    public void setTotal(int total) {
        this.total = Math.max(0, total);
        setOffset(offset);
    }

    public void setOffset(int offset) {
        this.offset = Math.max(0, Math.min(offset, getMaxOffset()));
    }

    /** Scroll by {@code rows} (positive = towards the end of the list), clamped.
     * @return true if the view moved. */
    public boolean scrollBy(int rows) {
        int before = offset;
        setOffset(offset + rows);
        return offset != before;
    }

    public boolean isScrollable() {
        return getMaxOffset() > 0;
    }

    /** Index of the first drawn entry. */
    public int getFirstVisible() {
        return offset;
    }

    /** Exclusive end index of the drawn entries — iterate {@code [getFirstVisible(), getEndVisible())}. */
    public int getEndVisible() {
        return Math.min(total, offset + visibleRows);
    }

    public boolean isVisible(int index) {
        return index >= offset && index < getEndVisible();
    }

    /** The list index drawn on window row {@code row} (0 = top row), or -1 if that row is empty or outside
     * the window. */
    public int indexAtRow(int row) {
        if (row < 0 || row >= visibleRows) return -1;
        int index = offset + row;
        return index < total ? index : -1;
    }

    /** The list index drawn under a cursor {@code yFromTop} pixels below the top of the window's first row, or -1.
     * Floors rather than truncates, so a cursor just above the window misses instead of hitting the first row. */
    public int indexAt(double yFromTop, int rowHeight) {
        if (rowHeight <= 0 || Double.isNaN(yFromTop)) return -1;
        return indexAtRow((int) Math.floor(yFromTop / rowHeight));
    }

    /** Scroll the least amount that brings {@code index} into view: an entry above the window lands on the top
     * row, one below it lands on the bottom row. Out-of-range indices (e.g. {@code indexOf} returning -1 for a
     * missing selection) are ignored.
     * @return true if the view moved. */
    public boolean ensureVisible(int index) {
        if (index < 0 || index >= total) return false;
        if (index < offset) {
            return scrollBy(index - offset);
        }
        if (index >= offset + visibleRows) {
            return scrollBy(index - (offset + visibleRows - 1));
        }
        return false;
    }

    /** Converts a mouse-wheel delta (vanilla {@code mouseScrolled}'s {@code scrollY}: positive = wheel up) into
     * a row delta for {@link #scrollBy}: one row per notch, and at least one row for any non-zero delta so a
     * fine-grained touchpad still scrolls. */
    public static int wheelRows(double scrollY) {
        if (scrollY == 0 || Double.isNaN(scrollY)) return 0;
        int notches = (int) Math.max(1, Math.round(Math.abs(scrollY)));
        return scrollY > 0 ? -notches : notches;
    }

    /** Pixel offset of a scrollbar thumb's top edge from the top of its track for the current offset. The thumb
     * is parked at the top when nothing scrolls. */
    public int thumbTop(int trackLength, int thumbLength) {
        int travel = trackLength - thumbLength;
        int max = getMaxOffset();
        if (travel <= 0 || max == 0) return 0;
        return (offset * travel + max / 2) / max;
    }

    /** An offset whose thumb is drawn at {@code thumbTop} pixels down the track (clamped to the track). Does not
     * change this window. When the list has more offsets than the track has pixels several offsets share a
     * pixel, so this is not a strict inverse of {@link #thumbTop}; use {@link #offsetForThumbDrag} while dragging. */
    public int offsetForThumbTop(int thumbTop, int trackLength, int thumbLength) {
        int travel = trackLength - thumbLength;
        int max = getMaxOffset();
        if (travel <= 0 || max == 0) return 0;
        int clamped = Math.max(0, Math.min(thumbTop, travel));
        return (clamped * max + travel / 2) / travel;
    }

    /** The offset for a thumb dragged to {@code thumbTop}: the current offset if the thumb would stay on the pixel
     * it is already drawn on (so grabbing the thumb, or jiggling it within one pixel, never scrolls), otherwise
     * {@link #offsetForThumbTop}. Does not change this window. */
    public int offsetForThumbDrag(int thumbTop, int trackLength, int thumbLength) {
        int clamped = Math.max(0, Math.min(thumbTop, trackLength - thumbLength));
        if (clamped == thumbTop(trackLength, thumbLength)) return offset;
        return offsetForThumbTop(thumbTop, trackLength, thumbLength);
    }

    /** Where a press {@code cursorInTrack} pixels down a scrollbar track grabs the thumb, as pixels below the
     * thumb's top edge: the pressed point when the press lands on the thumb (so it drags from where it was held),
     * otherwise the thumb's middle (so a track click centres the thumb under the cursor). */
    public double thumbGrabOffset(double cursorInTrack, int trackLength, int thumbLength) {
        int top = thumbTop(trackLength, thumbLength);
        if (cursorInTrack >= top && cursorInTrack < top + thumbLength) {
            return cursorInTrack - top;
        }
        return thumbLength / 2.0;
    }

    /** The offset for a thumb held {@code grabOffset} pixels below its top edge (see {@link #thumbGrabOffset}) by
     * a cursor {@code cursorInTrack} pixels down the track. Does not change this window. */
    public int offsetForCursor(double cursorInTrack, double grabOffset, int trackLength, int thumbLength) {
        int top = (int) Math.round(cursorInTrack - grabOffset);
        return offsetForThumbDrag(top, trackLength, thumbLength);
    }
}
