/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.guide.node.FormatString;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.gui.pos.GuiRectangle;

/** Represents a single page, image or crafting recipe for displaying. Only exists on the client. */
public abstract class GuidePart {
    public static final int INDENT_WIDTH = 16;
    public static final int LINE_HEIGHT = 16;

    public static class PagePosition {
        public final int page;
        public final int pixel;

        public PagePosition(int page, int pixel) {
            this.page = page;
            this.pixel = pixel;
        }

        public PagePosition nextLine(int pixelDifference, int maxHeight) {
            int added = pixel + pixelDifference;
            if (added >= maxHeight) {
                return nextPage();
            }
            return new PagePosition(page, added);
        }

        public PagePosition guaranteeSpace(int required, int maxPageHeight) {
            PagePosition next = nextLine(required, maxPageHeight);
            if (next.page == page) return this;
            return next;
        }

        public PagePosition nextPage() {
            return new PagePosition(page + 1, 0);
        }

        public PagePosition newPage() {
            if (pixel != 0) {
                return nextPage();
            }
            return this;
        }
    }

    protected final GuiGuide gui;
    private IFontRenderer fontRenderer;
    protected boolean wasHovered = false;
    protected boolean wasIconHovered = false;
    protected boolean didRender = false;

    public GuidePart(GuiGuide gui) {
        this.gui = gui;
    }

    public IFontRenderer getFontRenderer() {
        return fontRenderer;
    }

    public void setFontRenderer(IFontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
    }

    public boolean wasHovered() {
        return wasHovered;
    }

    public void updateScreen() {}

    protected void renderTextLine(String text, int x, int y, int colour) {
        if (fontRenderer != null) {
            fontRenderer.drawString(text, x, y + 8 - (fontRenderer.getFontHeight(text) / 2), colour);
        }
    }

    public abstract PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index);

    public abstract PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY);

    public void handleMouseDragPartial(int startX, int startY, int currentX, int currentY, int button) {}

    public void handleMouseDragFinish(int startX, int startY, int endX, int endY, int button) {}

    protected PagePosition renderLine(PagePosition current, PageLine line, int x, int y, int width, int height,
        int pageRenderIndex) {
        wasHovered = false;
        wasIconHovered = false;
        int allowedWidth = width - INDENT_WIDTH * line.indent;
        if (allowedWidth <= 0) {
            throw new IllegalStateException("Was indented too far");
        }

        String toRender = line.text;
        ISimpleDrawable icon = line.startIcon;
        FormatString next = FormatString.split(line.text);
        int neededSpace = fontRenderer != null ? fontRenderer.getFontHeight(line.text) : 9;
        if (icon != null) {
            neededSpace = Math.max(16, neededSpace);
        }
        current = current.guaranteeSpace(neededSpace, height);

        int _x = x + INDENT_WIDTH * line.indent;
        if (icon != null && current.page == pageRenderIndex) {
            int iconX = _x - 18;
            int iconY = y + current.pixel - 5;
            GuiRectangle rect = new GuiRectangle(iconX, iconY, 16, 16);
            if (rect.contains(gui.mouse) && line.startIconHovered != null) {
                icon = line.startIconHovered;
            }
            icon.drawAt(iconX, iconY);
        }
        didRender = false;

        while (next != null) {
            FormatString[] strings = fontRenderer != null
                ? next.wrap(fontRenderer, allowedWidth)
                : new FormatString[] { next };

            String text = strings[0].getFormatted();
            boolean render = current.page == pageRenderIndex;
            int _y = y + current.pixel;
            int _w = fontRenderer != null ? fontRenderer.getStringWidth(text) : text.length() * 6;
            GuiRectangle rect = new GuiRectangle(_x, _y - 2, _w, neededSpace + 3);
            wasHovered |= rect.contains(gui.mouse);
            if (render) {
                didRender = true;
                if (wasHovered) {
                    renderTooltip();
                }
                if (fontRenderer != null) {
                    fontRenderer.drawString(text, _x, _y, 0xFF000000);
                }
            }
            next = strings.length == 1 ? null : strings[1];
            int fontHeight = fontRenderer != null ? fontRenderer.getFontHeight(text) : 9;
            current = current.nextLine(fontHeight + 3, height);
        }

        int fontHeight = fontRenderer != null ? fontRenderer.getFontHeight(toRender) : 9;
        int additional = LINE_HEIGHT - fontHeight - 3;
        current = current.nextLine(additional, height);
        return current;
    }

    protected PagePosition renderLines(Iterable<PageLine> lines, PagePosition part, int x, int y, int width, int height,
        int index) {
        for (PageLine line : lines) {
            part = renderLine(part, line, x, y, width, height, index);
        }
        return part;
    }

    protected PagePosition renderLines(Iterable<PageLine> lines, int x, int y, int width, int height, int index) {
        return renderLines(lines, new PagePosition(0, 0), x, y, width, height, index);
    }

    protected void renderTooltip() {}
}
