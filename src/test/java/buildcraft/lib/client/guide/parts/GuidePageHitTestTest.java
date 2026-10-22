/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.font.IFontRenderer;
import buildcraft.lib.client.guide.parts.GuideInlineLinkText.InlineLayout;
import buildcraft.lib.client.guide.parts.GuideInlineLinkText.InlineLinkSpan;
import buildcraft.lib.client.guide.parts.GuidePart.LineLayout;
import buildcraft.lib.client.guide.parts.GuidePart.PagePosition;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.gui.pos.MousePosition;

/** Which page a click on a guide link counts for.
 *
 *  <p>The book lays the same part out twice per click, once per column, with the page index of that column. A line
 *  that wraps over a page break therefore has rects for the rows of the OTHER page too (laid out at this column's x),
 *  and click handling used to compare the line's END position with the clicked page instead: a link straddling the
 *  break could only be clicked on its last page, and a click on the far column at the near row's y opened it from
 *  nowhere. Every hit rect now carries its page and the hit test accepts only the clicked page's. Pure geometry, so
 *  no client is needed. */
public class GuidePageHitTestTest {

    private static final int HEIGHT = 100;
    private static final int X = 10;
    private static final int WIDTH = 60;

    /** 6 px per visible character (a formatting code is free, as in the real font), 9 px tall: a 60 px column holds
     *  ten characters. */
    private static final IFontRenderer FONT = new IFontRenderer() {
        @Override public int getStringWidth(String text) { return GuideInlineLinkText.visibleLengthOf(text) * 6; }
        @Override public int getFontHeight(String text) { return 9; }
        @Override public int getMaxFontHeight() { return 9; }
        @Override public int drawString(String text, int x, int y, int colour, boolean shadow, boolean centered, float scale) { return 0; }
        @Override public List<String> wrapString(String text, int maxWidth, boolean shadow, float scale) { return List.of(text); }
    };

    private static MousePosition mouse(double x, double y) {
        MousePosition mouse = new MousePosition();
        mouse.setMousePosition(x, y);
        return mouse;
    }

    @Test
    public void aWrappedLineIsHitOnlyOnThePageEachRowIsDrawnOn() {
        // Starting 20 px above the page bottom, the second wrapped row no longer fits and moves to the next page.
        PageLine line = new PageLine(0, "aaaa bbbb cccc dddd", true);
        LineLayout layout = GuidePart.layoutLine(FONT, new PagePosition(0, 80), line, X, 0, WIDTH, HEIGHT);

        assertEquals(2, layout.segments.size());
        assertEquals(0, layout.segments.get(0).page);
        assertEquals(1, layout.segments.get(1).page);

        MousePosition onFirstRow = mouse(20, 80);
        assertTrue(layout.textHovered(onFirstRow, 0));
        assertFalse(layout.textHovered(onFirstRow, 1), "a row of page 0 answered for page 1");

        MousePosition onSecondRow = mouse(20, 3);
        assertTrue(layout.textHovered(onSecondRow, 1));
        assertFalse(layout.textHovered(onSecondRow, 0), "a row of page 1 answered for page 0");
    }

    @Test
    public void aSingleLineAtThePageBottomIsHitOnItsOwnPage() {
        // The trailing spacing pushes the END position onto the next page although the line is drawn on page 0, which
        // is why the END position cannot decide which page a click belongs to.
        PageLine line = new PageLine(0, "Link", true);
        LineLayout layout = GuidePart.layoutLine(FONT, new PagePosition(0, 86), line, X, 0, WIDTH, HEIGHT);

        assertEquals(1, layout.segments.size());
        assertEquals(0, layout.segments.get(0).page);
        assertEquals(1, layout.end.page);

        MousePosition onLine = mouse(12, 85);
        assertTrue(layout.textHovered(onLine, 0));
        assertFalse(layout.textHovered(onLine, 1));
    }

    @Test
    public void theIconCountsOnlyOnThePageItIsDrawnOn() {
        ISimpleDrawable icon = (x, y) -> {};
        PageLine line = new PageLine(icon, icon, 0, "Link", true);

        // Room for the 16 px icon row: page 0.
        LineLayout onFirst = GuidePart.layoutLine(FONT, new PagePosition(0, 80), line, X, 0, WIDTH, HEIGHT);
        MousePosition atIcon = mouse(0, 80);
        assertTrue(onFirst.iconHovered(atIcon, 0));
        assertFalse(onFirst.iconHovered(atIcon, 1));

        // No room: the whole row, icon included, moves to page 1.
        LineLayout moved = GuidePart.layoutLine(FONT, new PagePosition(0, 90), line, X, 0, WIDTH, HEIGHT);
        assertEquals(1, moved.iconPage);
        MousePosition atMovedIcon = mouse(0, 0);
        assertTrue(moved.iconHovered(atMovedIcon, 1));
        assertFalse(moved.iconHovered(atMovedIcon, 0));
    }

    @Test
    public void anInlineLinkSpanIsHitOnlyOnThePageOfTheRowItsRectIsOn() {
        // "bbbb cccc" is the link: its first half ends row 0 (page 0), its second half starts row 1 (page 1).
        String text = "aaaa bbbb cccc dddd";
        List<InlineLinkSpan> spans = List.of(new InlineLinkSpan(5, 9, gui -> null, "bbbb cccc"));
        MousePosition onFirstHalf = mouse(45, 80);
        MousePosition onSecondHalf = mouse(12, 3);

        InlineLayout page0 = GuideInlineLinkText.layoutRows(FONT, text, spans, X, 0, WIDTH, HEIGHT,
            new PagePosition(0, 80), 0);
        assertEquals(2, page0.rows.size());
        assertEquals(0, page0.rows.get(0).page);
        assertEquals(1, page0.rows.get(1).page);
        assertEquals(0, GuideInlineLinkText.hitSpan(page0.spanRects, onFirstHalf));
        assertEquals(-1, GuideInlineLinkText.hitSpan(page0.spanRects, onSecondHalf),
            "the page 1 half of the link answered a click on page 0");

        InlineLayout page1 = GuideInlineLinkText.layoutRows(FONT, text, spans, X, 0, WIDTH, HEIGHT,
            new PagePosition(0, 80), 1);
        assertEquals(0, GuideInlineLinkText.hitSpan(page1.spanRects, onSecondHalf));
        assertEquals(-1, GuideInlineLinkText.hitSpan(page1.spanRects, onFirstHalf),
            "the page 0 half of the link answered a click on page 1");
    }
}
