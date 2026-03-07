/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;



import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.render.ISprite;

import buildcraft.lib.client.sprite.SpriteRaw;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.pos.IGuiPosition;

public class GuiIcon implements ISimpleDrawable {
    public final ISprite sprite;
    public final int textureSize;
    public final int width, height;

    public GuiIcon(ISprite sprite, int textureSize) {
        this.sprite = sprite;
        this.textureSize = textureSize;
        this.width = (int) (Math.abs(sprite.getInterpU(1) - sprite.getInterpU(0)) * textureSize);
        this.height = (int) (Math.abs(sprite.getInterpV(1) - sprite.getInterpV(0)) * textureSize);
    }

    public GuiIcon(Identifier texture, double u, double v, double width, double height, int texSize) {
        this(new SpriteRaw(texture, u, v, width, height, texSize), texSize);
    }

    public GuiIcon(Identifier texture, double u, double v, double width, double height) {
        this(texture, u, v, width, height, 256);
    }

    public GuiIcon offset(double u, double v) {
        SpriteRaw raw = (SpriteRaw) sprite;
        double uMin = raw.uMin + u / textureSize;
        double vMin = raw.vMin + v / textureSize;
        return new GuiIcon(new SpriteRaw(raw.location, uMin, vMin, raw.width, raw.height), textureSize);
    }

    public boolean containsGuiPos(double x, double y, IGuiPosition pos) {
        return new GuiRectangle(x, y, width, height).contains(pos);
    }

    public DynamicTexture createDynamicTexture(int scale) {
        return new DynamicTexture("bc_guide_icon", width * scale, height * scale, false);
    }

    @Override
    public void drawAt(double x, double y) {
        this.drawScaledInside(x, y, this.width, this.height);
    }

    public void drawScaledInside(IGuiArea element) {
        drawScaledInside(element.getX(), element.getY(), element.getWidth(), element.getHeight());
    }

    /** Draw this icon scaled to fit the given rectangle.
     * <p>
     * TODO: Implement modern rendering using GuiGraphics / BufferBuilder when the guide GUI is wired up.
     * Currently a no-op stub. */
    public void drawScaledInside(double x, double y, double drawnWidth, double drawnHeight) {
        // Rendering stub — will be implemented when GuiGuide is ported.
    }

    /** Draw this icon cut (clipped) to fit the given rectangle.
     * <p>
     * TODO: Implement modern rendering when the guide GUI is wired up. */
    public void drawCutInside(IGuiArea element) {
        drawCutInside(element.getX(), element.getY(), element.getWidth(), element.getHeight());
    }

    public void drawCutInside(double x, double y, double displayWidth, double displayHeight) {
        // Rendering stub — will be implemented when GuiGuide is ported.
    }

    public static void drawAt(ISprite sprite, double x, double y, double size) {
        drawAt(sprite, x, y, size, size);
    }

    public static void drawAt(ISprite sprite, double x, double y, double width, double height) {
        draw(sprite, x, y, x + width, y + height);
    }

    /** Draw a sprite filling the given rectangle.
     * <p>
     * TODO: Implement modern rendering when the guide GUI is wired up. */
    public static void draw(ISprite sprite, double xMin, double yMin, double xMax, double yMax) {
        // Rendering stub — will be implemented when GuiGuide is ported.
    }
}
