/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.font;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/** Implements a font that delegates to Minecraft's own {@link Font}.
 * Requires a {@link BCGraphics} context to be set via {@link #setGuiGraphics} before
 * any draw calls can render visually. */
public enum MinecraftFont implements IFontRenderer {
    INSTANCE;

    /** The current BCGraphics context — set by GuiGuide during rendering. */
    private static BCGraphics currentGraphics;

    /** Set the BCGraphics context for all font rendering. Must be called each frame. */
    public static void setGuiGraphics(BCGraphics graphics) {
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

        // Ensure alpha is set — colour 0x00000000 would be transparent
        if ((colour & 0xFF000000) == 0) {
            colour |= 0xFF000000;
        }

        // Note: in NeoForge 1.21.11, BCGraphics.pose() returns Matrix3x2fStack
        // which does not have pushPose/popPose. For scaled text we skip the transform
        // and just draw at the given position for now.
        currentGraphics.text(font, text, drawX, y, colour, shadow);

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
            // Track the previous style so we can re-emit legacy §X codes when it
            // changes. Without this, `Component.literal(text)` parses §-codes from
            // the input into per-character Style data and the bare codePoint loop
            // strips them on the way out — so syntax-highlighted code blocks
            // (json_insn, guide_md) lose all their colour.
            Style[] last = { Style.EMPTY };
            seq.accept((index, style, codePoint) -> {
                if (!stylesEquivalent(style, last[0])) {
                    appendLegacyCodes(sb, style);
                    last[0] = style;
                }
                sb.appendCodePoint(codePoint);
                return true;
            });
            result.add(sb.toString());
        }
        return result;
    }

    private static boolean stylesEquivalent(Style a, Style b) {
        return Objects.equals(a.getColor(), b.getColor())
            && a.isBold() == b.isBold()
            && a.isItalic() == b.isItalic()
            && a.isUnderlined() == b.isUnderlined()
            && a.isStrikethrough() == b.isStrikethrough()
            && a.isObfuscated() == b.isObfuscated();
    }

    /** Emit a §r reset followed by the legacy codes for the given style's
     *  colour and decoration flags, so that the resulting String renders the
     *  same way the styled FormattedCharSequence did. */
    private static void appendLegacyCodes(StringBuilder sb, Style style) {
        //? if >=26.2 {
        /*// On 26.2 ChatFormatting.toString() already yields the full "§x" prefixed code,
        // so append the constant directly instead of PREFIX_CODE + getChar().
        sb.append(ChatFormatting.RESET);
        TextColor color = style.getColor();
        if (color != null) {
            for (ChatFormatting fmt : ChatFormatting.values()) {
                if (!isColour(fmt)) {
                    continue;
                }
                TextColor fmtColor = TextColor.fromLegacyFormat(fmt);
                if (fmtColor != null && fmtColor.getValue() == color.getValue()) {
                    sb.append(fmt);
                    break;
                }
            }
        }
        if (style.isBold()) sb.append(ChatFormatting.BOLD);
        if (style.isItalic()) sb.append(ChatFormatting.ITALIC);
        if (style.isUnderlined()) sb.append(ChatFormatting.UNDERLINE);
        if (style.isStrikethrough()) sb.append(ChatFormatting.STRIKETHROUGH);
        if (style.isObfuscated()) sb.append(ChatFormatting.OBFUSCATED);
        *///?} else {
        sb.append(ChatFormatting.PREFIX_CODE).append(ChatFormatting.RESET.getChar());
        TextColor color = style.getColor();
        if (color != null) {
            for (ChatFormatting fmt : ChatFormatting.values()) {
                if (fmt.isColor() && fmt.getColor() != null && fmt.getColor() == color.getValue()) {
                    sb.append(ChatFormatting.PREFIX_CODE).append(fmt.getChar());
                    break;
                }
            }
        }
        if (style.isBold()) sb.append(ChatFormatting.PREFIX_CODE).append(ChatFormatting.BOLD.getChar());
        if (style.isItalic()) sb.append(ChatFormatting.PREFIX_CODE).append(ChatFormatting.ITALIC.getChar());
        if (style.isUnderlined()) sb.append(ChatFormatting.PREFIX_CODE).append(ChatFormatting.UNDERLINE.getChar());
        if (style.isStrikethrough()) sb.append(ChatFormatting.PREFIX_CODE).append(ChatFormatting.STRIKETHROUGH.getChar());
        if (style.isObfuscated()) sb.append(ChatFormatting.PREFIX_CODE).append(ChatFormatting.OBFUSCATED.getChar());
        //?}
    }

    //? if >=26.2 {
    /*// On 26.2 ChatFormatting lost isColor(); colours are the first 16 enum constants
    // (BLACK=0 .. WHITE=15), styles and RESET come after.
    private static boolean isColour(ChatFormatting f) {
        return f.ordinal() <= ChatFormatting.WHITE.ordinal();
    }
    *///?}
}
