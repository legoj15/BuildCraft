/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.elem;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.IInteractionElement;
import buildcraft.lib.gui.pos.IGuiArea;

/**
 * A vertical scrollbar drawn from a GUI's own texture sheet (a track strip plus a thumb sprite), driving a
 * {@link ScrollWindow}. The 1.7.10 {@code ScrollbarWidget} brought back as a BC GUI element: it is hidden while the
 * whole list fits, a click on the thumb drags it from where it was grabbed, and a click elsewhere on the track
 * centres the thumb under the cursor and keeps dragging.
 * <p>
 * Mouse input arrives through {@link BuildCraftGui}'s element dispatch, which {@code GuiBC8} feeds from the
 * per-node vanilla mouse overrides, so this class needs no version directives. The mouse wheel is not routed
 * through that dispatch; the owning screen handles {@code mouseScrolled} (whose signature is the same on every
 * node) and scrolls the shared {@link ScrollWindow} directly.
 */
public class GuiElementScrollbar implements IInteractionElement {
    private final BuildCraftGui gui;
    private final IGuiArea track;
    private final ScrollWindow window;
    private final GuiIcon trackIcon;
    private final GuiIcon thumbIcon;

    private boolean dragging;
    /** Pixels between the thumb's top edge and the cursor while dragging. */
    private double grabOffset;

    /** @param track screen-space area of the track; the thumb travels its full height. */
    public GuiElementScrollbar(BuildCraftGui gui, IGuiArea track, ScrollWindow window, GuiIcon trackIcon,
        GuiIcon thumbIcon) {
        this.gui = gui;
        this.track = track;
        this.window = window;
        this.trackIcon = trackIcon;
        this.thumbIcon = thumbIcon;
    }

    @Override
    public double getX() {
        return track.getX();
    }

    @Override
    public double getY() {
        return track.getY();
    }

    @Override
    public double getWidth() {
        return track.getWidth();
    }

    @Override
    public double getHeight() {
        return track.getHeight();
    }

    public boolean isDragging() {
        return dragging;
    }

    private int trackLength() {
        return (int) track.getHeight();
    }

    private double thumbScreenTop() {
        return track.getY() + window.thumbTop(trackLength(), thumbIcon.height);
    }

    @Override
    public void drawBackground(float partialTicks) {
        if (!window.isScrollable()) return;
        trackIcon.drawAt(track.getX(), track.getY());
        thumbIcon.drawAt(track.getX(), thumbScreenTop());
    }

    @Override
    public void onMouseClicked(int button) {
        if (button != 0 || !window.isScrollable()) return;
        double mouseY = gui.mouse.getY();
        if (!contains(gui.mouse.getX(), mouseY)) return;
        double thumbTop = thumbScreenTop();
        boolean onThumb = mouseY >= thumbTop && mouseY < thumbTop + thumbIcon.height;
        grabOffset = onThumb ? mouseY - thumbTop : thumbIcon.height / 2.0;
        dragging = true;
        followMouse();
    }

    @Override
    public void onMouseDragged(int button, long ticksSinceClick) {
        // Left button only: a right-drag while the thumb is held must not scroll the list.
        if (dragging && button == 0) {
            followMouse();
        }
    }

    @Override
    public void onMouseReleased(int button) {
        if (dragging && button == 0) {
            followMouse();
            dragging = false;
        }
    }

    private void followMouse() {
        int thumbTop = (int) Math.round(gui.mouse.getY() - grabOffset - track.getY());
        window.setOffset(window.offsetForThumbDrag(thumbTop, trackLength(), thumbIcon.height));
    }
}
