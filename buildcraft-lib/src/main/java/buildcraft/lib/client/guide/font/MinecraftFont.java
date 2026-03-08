/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.font;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Implements a font that delegates to Minecraft's own {@link Font}.
 * Requires a {@link GuiGraphics} context to be set via {@link #setGuiGraphics} before
 * any draw calls can render visually. */
public enum MinecraftFont implements IFontRenderer {
    INSTANCE;

    /** The current GuiGraphics context — set by GuiGuide during rendering. */
    private static GuiGraphics currentGraphics;

    /** Set the GuiGraphics context for all font rendering. Must be called each frame. */
    public static void setGuiGraphics(GuiGraphics graphics) {
        currentGraphics = graphics;
    }

    private static Font getFontRenderer() {
        return Minecraft.getInstance().font;
    }

    @Override
    public int getStringWidth(String text) {
        return getFontRenderer().width(text);
    }

    @Override
    public int getFontHeight(String text) {
        return getMaxFontHeight();
    }

    @Override
    public int getMaxFontHeight() {
        return getFontRenderer().lineHeight;
    }

    @Override
    public int drawString(String text, int x, int y, int colour, boolean shadow, boolean centered, float scale) {
        if (currentGraphics == null) {
            // No graphics context — return width as if we drew it
            return (int) (getStringWidth(text) * scale);
        }
        Font font = getFontRenderer();
        int width = (int) (font.width(text) * scale);

        int drawX = x;
        if (centered) {
            drawX = x - width / 2;
        }

        // Note: in NeoForge 1.21.11, GuiGraphics.pose() returns Matrix3x2fStack
        // which does not have pushPose/popPose. For scaled text we skip the transform
        // and just draw at the given position for now.
        currentGraphics.drawString(font, text, drawX, y, colour, shadow);

        return width;
    }

    @Override
    public List<String> wrapString(String text, int maxWidth, boolean shadow, float scale) {
        Font font = getFontRenderer();
        int scaledWidth = (int) (maxWidth / scale);
        List<FormattedCharSequence> wrapped = font.split(Component.literal(text), scaledWidth);
        List<String> result = new ArrayList<>(wrapped.size());
        for (FormattedCharSequence seq : wrapped) {
            StringBuilder sb = new StringBuilder();
            seq.accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            result.add(sb.toString());
        }
        return result;
    }
}
