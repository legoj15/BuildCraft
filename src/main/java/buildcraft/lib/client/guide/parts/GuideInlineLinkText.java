/*
 * Copyright (c) SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.guide.node.FormatString;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.pos.GuiRectangle;

/** A text part with one or more clickable hyperlink spans embedded inside it.
 *  Produced by the {@code <link inline="true" to="..."/>} directive — see
 *  {@link buildcraft.lib.client.guide.loader.XmlPageLoader} for the parser side.
 *
 *  Unlike {@link GuidePartLink}, which produces a whole-row TOC-style entry with
 *  an icon column, this part renders inside the flow of paragraph text and only
 *  highlights / dispatches clicks on the link spans themselves. The wrap/render
 *  loop intentionally duplicates a slimmed-down version of {@link GuidePart#renderLine}
 *  rather than threading span-aware rendering through the shared code path —
 *  {@code renderLine} already carries icon, hovered-icon, whole-row-link, and
 *  tooltip concerns that don't apply here, so localising the duplication keeps
 *  the shared path simple. */
public class GuideInlineLinkText extends GuidePart {

    /** A clickable region inside a formatted line. {@code visibleStart} and
     *  {@code visibleLen} are indices into the line's visible (un-§) characters. */
    public static final class InlineLinkSpan {
        public final int visibleStart;
        public final int visibleLen;
        public final Function<GuiGuide, GuidePageFactory> factoryFn;
        public final String title;

        public InlineLinkSpan(int visibleStart, int visibleLen,
            Function<GuiGuide, GuidePageFactory> factoryFn, String title) {
            this.visibleStart = visibleStart;
            this.visibleLen = visibleLen;
            this.factoryFn = factoryFn;
            this.title = title;
        }
    }

    private final String formattedText;
    private final List<InlineLinkSpan> spans;

    /** Per-span hit rects, populated each render/click pass. A span can have multiple
     *  rects when its title wraps across visual lines — every fragment is clickable. */
    private final List<List<GuiRectangle>> spanRects;

    public GuideInlineLinkText(GuiGuide gui, String formattedText, List<InlineLinkSpan> spans) {
        super(gui);
        this.formattedText = formattedText;
        this.spans = spans;
        this.spanRects = new ArrayList<>(spans.size());
        for (int i = 0; i < spans.size(); i++) {
            this.spanRects.add(new ArrayList<>());
        }
    }

    @Override
    public PagePosition renderIntoArea(int x, int y, int width, int height, PagePosition current, int index) {
        return walk(x, y, width, height, current, index, true);
    }

    @Override
    public PagePosition handleMouseClick(int x, int y, int width, int height, PagePosition current, int index,
        int mouseX, int mouseY) {
        // Mirror GuidePartLink.handleMouseClick: re-run the layout with no drawing
        // (sentinel pageRenderIndex == -1 disables the rendered-on-this-page check)
        // to populate spanRects, then hit-test.
        PagePosition end = walk(x, y, width, height, current, -1, false);
        if (wasHovered) {
            for (int i = 0; i < spans.size(); i++) {
                InlineLinkSpan span = spans.get(i);
                for (GuiRectangle rect : spanRects.get(i)) {
                    if (rect.contains(gui.mouse)) {
                        GuidePageFactory factory = span.factoryFn.apply(gui);
                        if (factory != null) {
                            gui.openPage(factory.createNew(gui));
                        }
                        return end;
                    }
                }
            }
        }
        return end;
    }

    /** Per-row layout state captured during the layout pass and replayed during the
     *  render pass. Splitting layout from rendering lets us decide hover state PER SPAN
     *  (across all of its rects) before drawing — so a link that wraps across two
     *  visual lines highlights both rows together when the mouse is on either, instead
     *  of each row lighting up independently. */
    private static final class RowLayout {
        final String text;
        final int rowY;
        final boolean rendered;
        // spanIndex -> rect on this row (a row can have at most one rect per span)
        final java.util.Map<Integer, GuiRectangle> rectsBySpan;

        RowLayout(String text, int rowY, boolean rendered, java.util.Map<Integer, GuiRectangle> rectsBySpan) {
            this.text = text;
            this.rowY = rowY;
            this.rendered = rendered;
            this.rectsBySpan = rectsBySpan;
        }
    }

    /** Single wrap walk shared by render and click. Performs three passes:
     *  <ol>
     *    <li>Layout — compute one {@link RowLayout} per wrap segment, populating
     *        {@link #spanRects} as the source of truth for click hit-testing.</li>
     *    <li>Hover roll-up — a span counts as hovered if ANY of its rects contains the
     *        mouse, so highlights span across wrapped rows of the same link.</li>
     *    <li>Render — when {@code draw} is true, paint hover fills (for every rect of
     *        every hovered span on rendered rows) and the row text.</li>
     *  </ol> */
    private PagePosition walk(int x, int y, int width, int height, PagePosition current, int pageRenderIndex,
        boolean draw) {
        wasHovered = false;
        for (List<GuiRectangle> list : spanRects) {
            list.clear();
        }

        IFontRenderer font = getFontRenderer();
        int allowedWidth = width;
        FormatString next = FormatString.split(formattedText);
        int neededSpace = font != null ? font.getFontHeight(formattedText) : 9;
        current = current.guaranteeSpace(neededSpace, height);

        int visibleConsumed = 0;
        List<RowLayout> rows = new ArrayList<>();

        // Pass 1: layout — walk wrap segments, record rects + per-row metadata.
        while (next != null) {
            FormatString[] strings = font != null
                ? next.wrap(font, allowedWidth)
                : new FormatString[] { next };
            String segText = strings[0].getFormatted();
            int segVisibleLen = visibleLengthOf(segText);
            int rowY = y + current.pixel;
            int rowTop = rowY - 5;
            boolean rendered = current.page == pageRenderIndex;

            java.util.Map<Integer, GuiRectangle> rectsBySpan = new java.util.HashMap<>();
            for (int i = 0; i < spans.size(); i++) {
                InlineLinkSpan span = spans.get(i);
                int segVisibleStart = visibleConsumed;
                int segVisibleEnd = visibleConsumed + segVisibleLen;
                int spanVisibleStart = span.visibleStart;
                int spanVisibleEnd = span.visibleStart + span.visibleLen;
                int overlapStart = Math.max(spanVisibleStart, segVisibleStart);
                int overlapEnd = Math.min(spanVisibleEnd, segVisibleEnd);
                if (overlapStart >= overlapEnd) {
                    continue;
                }

                int linkStartInSegVisible = overlapStart - segVisibleStart;
                int linkEndInSegVisible = overlapEnd - segVisibleStart;
                int rawStart = visibleIndexToRawIndex(segText, linkStartInSegVisible);
                int rawEnd = visibleIndexToRawIndex(segText, linkEndInSegVisible);
                String beforeLink = segText.substring(0, rawStart);
                String linkText = segText.substring(rawStart, rawEnd);

                int offsetX = font != null ? font.getStringWidth(beforeLink) : 0;
                int linkWidth = font != null ? font.getStringWidth(linkText) : linkText.length() * 6;
                GuiRectangle rect = new GuiRectangle(x + offsetX, rowTop, linkWidth, LINE_HEIGHT);
                spanRects.get(i).add(rect);
                rectsBySpan.put(i, rect);
            }

            rows.add(new RowLayout(segText, rowY, rendered, rectsBySpan));

            visibleConsumed += segVisibleLen;
            next = strings.length == 1 ? null : strings[1];
            int fontHeight = font != null ? font.getFontHeight(segText) : 9;
            current = current.nextLine(fontHeight + 3, height);
        }

        // Pass 2: hover roll-up — a span is hovered if any of its rects contains the
        // mouse. {@link #wasHovered} reflects whether ANY span is hovered (used by
        // {@link #handleMouseClick} as a fast-path gate).
        boolean[] spanHovered = new boolean[spans.size()];
        for (int i = 0; i < spans.size(); i++) {
            for (GuiRectangle rect : spanRects.get(i)) {
                if (rect.contains(gui.mouse)) {
                    spanHovered[i] = true;
                    wasHovered = true;
                    break;
                }
            }
        }

        // Pass 3: render — for each rendered row, paint hover fills (every rect of
        // every hovered span, not just rects under the mouse), then the row text.
        boolean drewAny = false;
        if (draw) {
            GuiGraphicsExtractor g = GuiIcon.getGuiGraphics();
            for (RowLayout row : rows) {
                if (!row.rendered) {
                    continue;
                }
                if (g != null) {
                    for (java.util.Map.Entry<Integer, GuiRectangle> entry : row.rectsBySpan.entrySet()) {
                        if (!spanHovered[entry.getKey()]) {
                            continue;
                        }
                        GuiRectangle rect = entry.getValue();
                        int rx = (int) rect.x;
                        int ry = (int) rect.y;
                        int rw = (int) rect.width;
                        g.fill(rx - 1, ry, rx + rw + 1, ry + LINE_HEIGHT, 0xFFD3AD6C);
                    }
                }
                if (font != null) {
                    font.drawString(row.text, x, row.rowY, 0xFF000000);
                }
                drewAny = true;
            }
        }

        didRender = drewAny;
        int fontHeight = font != null ? font.getFontHeight(formattedText) : 9;
        int additional = LINE_HEIGHT - fontHeight - 3;
        current = current.nextLine(additional, height);
        return current;
    }

    /** Counts characters in {@code s} that are NOT part of a {@code §X} formatting code.
     *  Public so {@link buildcraft.lib.client.guide.loader.XmlPageLoader} (different
     *  package) can reuse the canonical impl when laying out spans, and so unit tests
     *  can exercise it directly. */
    public static int visibleLengthOf(String s) {
        int count = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i += 2;
                continue;
            }
            count++;
            i++;
        }
        return count;
    }

    /** Maps a visible-character index to the raw string index in {@code s} of that
     *  character. Returns {@code s.length()} when {@code visibleIndex} is past the end.
     *  Public for unit tests. */
    public static int visibleIndexToRawIndex(String s, int visibleIndex) {
        int v = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i += 2;
                continue;
            }
            if (v == visibleIndex) {
                return i;
            }
            v++;
            i++;
        }
        return s.length();
    }
}
