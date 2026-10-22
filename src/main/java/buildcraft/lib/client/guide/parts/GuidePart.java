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
import buildcraft.lib.client.guide.node.FormatString;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiPosition;

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

    /** Lays {@code line} out, tests the mouse against it on page {@code pageRenderIndex} only, and draws the parts of
     *  it that sit on that page (plus the hover tooltip). Use {@link #hitTestLine} when only the hover state is
     *  wanted. */
    protected PagePosition renderLine(PagePosition current, PageLine line, int x, int y, int width, int height,
        int pageRenderIndex) {
        return layoutAndHit(current, line, x, y, width, height, pageRenderIndex, true);
    }

    /** Same layout and the same page-restricted hover test as {@link #renderLine} for page {@code pageIndex}, but
     *  nothing is drawn and no tooltip is queued. Click handling calls this with the page that was clicked, so a line
     *  that wraps over a page break is hit only where it is drawn on that page. {@link #wasHovered()} holds the
     *  result.
     *  @return where the line ends, as {@code renderLine} does. */
    protected PagePosition hitTestLine(PagePosition current, PageLine line, int x, int y, int width, int height,
        int pageIndex) {
        return layoutAndHit(current, line, x, y, width, height, pageIndex, false);
    }

    private PagePosition layoutAndHit(PagePosition current, PageLine line, int x, int y, int width, int height,
        int page, boolean draw) {
        wasHovered = false;
        wasIconHovered = false;
        didRender = false;

        LineLayout layout = layoutLine(fontRenderer, current, line, x, y, width, height);

        // Hit-test pass: one hover flag for the whole entry — true if the mouse is
        // over the icon OR any wrapped text segment on this page, so icon and text act
        // as a single click target. wasIconHovered is also exposed for callers that
        // need to know whether the icon specifically was the target.
        boolean iconHovered = layout.iconHovered(gui.mouse, page);
        boolean entryHovered = iconHovered || layout.textHovered(gui.mouse, page);
        wasHovered = entryHovered;
        wasIconHovered = iconHovered;

        if (!draw) {
            return layout.end;
        }

        // Render pass — order matters for z-stacking:
        //   1) Highlight fills first, extended back over the icon column on the
        //      first rendered segment so the visual click-target matches the hit
        //      region (icon + text read as one row).
        //   2) Icon next, on top of the extended fill so it stays visible.
        //   3) Text last.
        // Hovering text now also swaps the icon to its hovered drawable, since both
        // share the unified entryHovered state.
        boolean iconVisibleHere = line.startIcon != null && layout.iconPage == page;
        boolean drewAny = false;
        if (entryHovered && line.link) {
            buildcraft.lib.gui.BCGraphics g = buildcraft.lib.gui.GuiIcon.getGuiGraphics();
            if (g != null) {
                boolean isFirstRendered = true;
                for (WrapSegment seg : layout.segments) {
                    if (seg.page != page) continue;
                    int fillLeft = (isFirstRendered && iconVisibleHere) ? layout.textX - 20 : layout.textX - 2;
                    g.fill(fillLeft, seg.rowTop, layout.textX + seg.width + 2, seg.rowTop + LINE_HEIGHT, 0xFFD3AD6C);
                    isFirstRendered = false;
                }
            }
        }
        if (iconVisibleHere) {
            ISimpleDrawable toDraw = (entryHovered && line.startIconHovered != null)
                ? line.startIconHovered
                : line.startIcon;
            toDraw.drawAt(layout.iconX, layout.iconY);
        }
        for (WrapSegment seg : layout.segments) {
            if (seg.page != page) continue;
            drewAny = true;
            if (fontRenderer != null) {
                fontRenderer.drawString(seg.text, layout.textX, seg.y, 0xFF000000);
            }
        }
        didRender = drewAny;
        // The hover test above is already restricted to this page; this only keeps a tooltip from
        // surfacing for a line none of which was drawn.
        if (entryHovered && didRender) {
            renderTooltip();
        }
        return layout.end;
    }

    /** Where one {@link PageLine} lands: its wrapped segments (each tagged with the page it falls on), the icon's hit
     *  rect and the position after it. Pure geometry — no mouse, no drawing — and package-private so a unit test can
     *  lay a line out without a client. */
    static final class LineLayout {
        final List<WrapSegment> segments;
        /** The icon's hit rect, or null when the line has no icon. */
        @Nullable
        final GuiRectangle iconRect;
        /** The page the icon sits on (the page the line starts on, after reserving its first row). */
        final int iconPage;
        final int textX;
        final int iconX;
        final int iconY;
        /** Where the next part starts, after the line's trailing spacing. */
        final PagePosition end;

        LineLayout(List<WrapSegment> segments, @Nullable GuiRectangle iconRect, int iconPage, int textX, int iconX,
            int iconY, PagePosition end) {
            this.segments = segments;
            this.iconRect = iconRect;
            this.iconPage = iconPage;
            this.textX = textX;
            this.iconX = iconX;
            this.iconY = iconY;
            this.end = end;
        }

        /** True if the mouse is over the icon, which counts only on the page the icon is drawn on. */
        boolean iconHovered(IGuiPosition mouse, int page) {
            return iconRect != null && iconPage == page && iconRect.contains(mouse);
        }

        /** True if the mouse is over a wrapped segment that is drawn on {@code page}. The other column's call lays
         *  the same line out again at its own x, so a segment of another page would otherwise answer for a point
         *  the player is not looking at. */
        boolean textHovered(IGuiPosition mouse, int page) {
            for (WrapSegment seg : segments) {
                if (seg.page == page && seg.rect.contains(mouse)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Layout pass: walks the wrap segments and records geometry for each. Decoupling layout from render lets us
     *  compute a single hover state for the whole entry BEFORE drawing, so a multi-line wrapped entry highlights as
     *  one cohesive block rather than each visual line lighting up independently. */
    static LineLayout layoutLine(@Nullable IFontRenderer fontRenderer, PagePosition current, PageLine line, int x,
        int y, int width, int height) {
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

        // Icon geometry up front so the hit-test can fold the icon rect into the unified entry-hover region —
        // making the icon part of the same click target as the text. The icon DRAW comes after the hit-test (and
        // after the highlight fill) so we can pick the hovered drawable from the whole-entry hover state, and so
        // the icon paints on top of the extended highlight rather than under it.
        //
        // The rect is computed whenever an icon is configured and carries its page; the hit-test accepts it only
        // on that page.
        int iconX = _x - 18;
        int iconY = y + current.pixel - 5;
        int iconPage = current.page;
        // Hit rect spans [_x - 20, _x] to match the highlight's leftward extent and to
        // meet the first text segment's expanded rect at _x — closing the dead strip
        // that used to sit between icon and text. Icon is still DRAWN at iconX (16×16).
        GuiRectangle iconRect = (icon != null) ? new GuiRectangle(_x - 20, iconY, 20, 16) : null;

        List<WrapSegment> segments = new ArrayList<>();
        PagePosition cursor = current;
        while (next != null) {
            FormatString[] strings = fontRenderer != null
                ? next.wrap(fontRenderer, allowedWidth)
                : new FormatString[] { next };

            String text = strings[0].getFormatted();
            int fontHeight = fontRenderer != null ? fontRenderer.getFontHeight(text) : 9;
            // Every wrapped segment must fit whole, not just start, above the page bottom;
            // otherwise a continuation line lands over the "X / Y" footer.
            cursor = cursor.guaranteeSpace(fontHeight, height);
            int _y = y + cursor.pixel;
            int _w = fontRenderer != null ? fontRenderer.getStringWidth(text) : text.length() * 6;
            // Hover "row" geometry: LINE_HEIGHT (16 px) tall, vertically aligned with the
            // start icon's range (_y-5 to _y+11) so the highlight matches the icon and
            // adjacent TOC entries (16 px apart) are flush.
            int rowTop = _y - 5;
            // Hit rect inflated by 2 px on each side to match the highlight's horizontal
            // padding (see the fill bounds in layoutAndHit). With the icon rect now ending at _x and
            // this rect starting at _x - 2, the two overlap by 2 px and leave no gap.
            GuiRectangle rect = new GuiRectangle(_x - 2, rowTop, _w + 4, LINE_HEIGHT);
            segments.add(new WrapSegment(text, rect, _y, rowTop, _w, cursor.page));

            next = strings.length == 1 ? null : strings[1];
            cursor = cursor.nextLine(fontHeight + 3, height);
        }

        int fontHeight = fontRenderer != null ? fontRenderer.getFontHeight(toRender) : 9;
        int additional = LINE_HEIGHT - fontHeight - 3;
        PagePosition end = cursor.nextLine(additional, height);
        return new LineLayout(segments, iconRect, iconPage, _x, iconX, iconY, end);
    }

    /** One wrapped segment of a PageLine — its text, hover rect, draw position, and
     *  the page it falls on. */
    static final class WrapSegment {
        final String text;
        final GuiRectangle rect;
        final int y;
        final int rowTop;
        final int width;
        final int page;

        WrapSegment(String text, GuiRectangle rect, int y, int rowTop, int width, int page) {
            this.text = text;
            this.rect = rect;
            this.y = y;
            this.rowTop = rowTop;
            this.width = width;
            this.page = page;
        }
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
