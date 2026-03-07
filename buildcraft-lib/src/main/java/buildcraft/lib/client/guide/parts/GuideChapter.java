/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.sprite.SpriteNineSliced;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.gui.pos.GuiRectangle;

public abstract class GuideChapter extends GuidePart {
    public static final int[] COLOURS = { 0x9dd5c0, 0xfac174, 0x27a4dd };
    public static final int MAX_HOWEVER_PROGRESS = 5;
    public static final int MAX_HOVER_DISTANCE = 20;

    public final PageLine chapter;
    public final int level;

    @Nullable
    protected GuideChapter parent;
    protected final List<GuideChapter> children = new ArrayList<>();
    protected int colourIndex = -1;
    protected EnumGuiSide lastDrawn = null;

    public enum EnumGuiSide { LEFT, RIGHT }

    public GuideChapter(GuiGuide gui, String chapter) {
        this(gui, 0, chapter);
    }

    public GuideChapter(GuiGuide gui, int level, String text) {
        super(gui);
        this.level = Math.max(0, level);
        // Rendering icons stubbed — RenderUtil not yet ported
        this.chapter = new PageLine(null, null, this.level + 1, text, false);
    }

    public void reset() {
        lastDrawn = EnumGuiSide.LEFT;
    }

    public boolean hasParent() { return parent != null; }
    public boolean hasChildren() { return !children.isEmpty(); }

    void assignChildIndices() {
        int cIdx = 0;
        for (GuideChapter child : children) {
            if (cIdx % COLOURS.length == colourIndex) cIdx++;
            child.colourIndex = cIdx++ % COLOURS.length;
            if (child.hasChildren()) child.assignChildIndices();
        }
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        if (getFontRenderer() != null) {
            current = current.guaranteeSpace(getFontRenderer().getMaxFontHeight() * 4, height);
        }
        return renderLine(current, chapter, x, y, width, height, index);
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        if (getFontRenderer() != null) {
            current = current.guaranteeSpace(getFontRenderer().getMaxFontHeight() * 4, height);
        }
        return renderLine(current, chapter, x, y, width, height, -1);
    }

    /** Stub — full chapter sidebar rendering deferred until GuiGuide is fully ported. */
    public int draw(int yIndex, float partialTicks, boolean drawCentral) {
        return 1;
    }

    protected int getMousePart() {
        return 0;
    }

    public int handleClick() {
        int part = getMousePart();
        if (part == 1) {
            return onClick() ? 1 : 0;
        } else if (part == 2) {
            return 2;
        }
        return 0;
    }

    protected abstract boolean onClick();

    @Override
    public void updateScreen() {}
}
