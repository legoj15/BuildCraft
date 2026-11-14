/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.CommonComponents;

/**
 * Draws single-line GUI labels — window titles above all — that can never run past their box, whatever
 * the language: a string narrower than the box is drawn exactly where the screens used to draw it, a wider
 * one is uniformly scaled down about its anchor and, past {@link #MIN_SCALE}, cut with an ellipsis.
 */
public final class GuiTextFit {
    /** A string still wider than the box at this scale is cut with an ellipsis instead of shrunk further. */
    private static final float MIN_SCALE = 0.75F;

    private GuiTextFit() {
    }

    /**
     * Draws {@code text} horizontally centred in the box starting at {@code left}. A string that fits is drawn at
     * {@code left + (boxWidth - width) / 2} — the arithmetic every screen used before — so nothing moves on screen.
     */
    public static void drawCentred(BCGraphics graphics, Font font, String text, int left, int y, int boxWidth, int colour) {
        int width = font.width(text);
        if (width <= boxWidth) {
            graphics.text(font, text, left + (boxWidth - width) / 2, y, colour, false);
            return;
        }
        drawScaledOrCut(graphics, font, text, y, boxWidth, colour, left, true);
    }

    /**
     * Draws {@code text} anchored at {@code x}: a string that fits still starts there, a wider one keeps its left
     * edge at {@code x} while shrinking.
     */
    public static void drawLeft(BCGraphics graphics, Font font, String text, int x, int y, int boxWidth, int colour) {
        if (font.width(text) <= boxWidth) {
            graphics.text(font, text, x, y, colour, false);
            return;
        }
        drawScaledOrCut(graphics, font, text, y, boxWidth, colour, x, false);
    }

    /**
     * The too-wide path shared by both anchors: the string is drawn under a uniform scale about
     * ({@code originX}, the text's vertical centre) so the anchor stays fixed and the line keeps its vertical
     * centre while its width becomes {@code boxWidth}. Below {@link #MIN_SCALE} the string is instead cut with an
     * ellipsis to just fit at that scale — every language then shows the start of its title, never the bevels.
     *
     * @param anchor  the box's {@code left} when centred, the draw {@code x} when left-anchored
     * @param centred whether the string is centred in the box or hangs off the anchor
     */
    private static void drawScaledOrCut(BCGraphics graphics, Font font, String text, int y, int boxWidth,
            int colour, int anchor, boolean centred) {
        float scale = boxWidth / (float) font.width(text);
        if (scale < MIN_SCALE) {
            String ellipsis = CommonComponents.ELLIPSIS.getString();
            text = font.plainSubstrByWidth(text, Math.max(0, (int) (boxWidth / MIN_SCALE) - font.width(ellipsis))) + ellipsis;
            scale = MIN_SCALE;
        }
        // The scale origin is the box centre (centred draw) or the anchor itself, and the string is drawn
        // with that same point as its centre / left edge, so the transform only narrows it in place.
        float originX = centred ? anchor + boxWidth / 2.0F : anchor;
        float x = centred ? originX - font.width(text) / 2.0F : anchor;
        graphics.pushScale(scale, originX, y + font.lineHeight / 2.0F);
        graphics.text(font, text, Math.round(x), y, colour, false);
        graphics.popScale();
    }
}
