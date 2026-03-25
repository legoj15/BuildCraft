/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.font;

import java.util.Collections;
import java.util.List;

/**
 * A custom bitmap font renderer that uses AWT to rasterize text onto a dynamic texture.
 * <p>
 * In 1.12 this used {@code java.awt.Font} + {@code DynamicTextureBC} to render smooth fonts.
 * That rendering pipeline is deferred until {@code DynamicTextureBC} is ported. For now, this
 * class delegates entirely to {@link MinecraftFont} as a functional placeholder.
 */
public class GuideFont implements IFontRenderer {

    // The Font parameter is accepted to maintain API compatibility with FontManager,
    // but is currently unused since we delegate to MinecraftFont.
    public GuideFont(java.awt.Font font) {
        // Deferred — AWT rendering pipeline needs DynamicTextureBC
    }

    @Override
    public int getStringWidth(String text) {
        return MinecraftFont.INSTANCE.getStringWidth(text);
    }

    @Override
    public int getFontHeight(String text) {
        return MinecraftFont.INSTANCE.getFontHeight(text);
    }

    @Override
    public int getMaxFontHeight() {
        return MinecraftFont.INSTANCE.getMaxFontHeight();
    }

    @Override
    public int drawString(String text, int x, int y, int colour, boolean shadow, boolean centered, float scale) {
        return MinecraftFont.INSTANCE.text(text, x, y, colour, shadow, centered, scale);
    }

    @Override
    public List<String> wrapString(String text, int maxWidth, boolean shadow, float scale) {
        return MinecraftFont.INSTANCE.wrapString(text, maxWidth, shadow, scale);
    }
}
