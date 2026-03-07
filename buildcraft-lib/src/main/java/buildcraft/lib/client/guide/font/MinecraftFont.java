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
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Implements a font that delegates to Minecraft's own {@link Font}. */
public enum MinecraftFont implements IFontRenderer {
    INSTANCE;

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
        // Rendering stub — in 1.21, drawString requires a GuiGraphics context.
        // This will be properly implemented when GuiGuide passes its GuiGraphics down.
        // For now, return the width as if we drew it.
        int width = (int) (getStringWidth(text) * scale);
        if (centered) {
            return width;
        }
        return width;
    }

    @Override
    public List<String> wrapString(String text, int maxWidth, boolean shadow, float scale) {
        Font font = getFontRenderer();
        int scaledWidth = (int) (maxWidth / scale);
        List<FormattedCharSequence> wrapped = font.split(Component.literal(text), scaledWidth);
        List<String> result = new ArrayList<>(wrapped.size());
        for (FormattedCharSequence seq : wrapped) {
            // Extract the string content from the FormattedCharSequence
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
