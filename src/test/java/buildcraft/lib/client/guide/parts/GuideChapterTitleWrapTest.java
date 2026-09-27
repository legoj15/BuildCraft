/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import buildcraft.lib.client.guide.font.IFontRenderer;

/** How a chapter title is split into lines. Drawing, the click/hover hit-test and the slot count that places every
 *  following chapter all read this one rule, so it must match what is DRAWN.
 *
 *  <p>The small-screen "Chapters" overlay draws every title on one line. The hit-test used to wrap the title against
 *  the side-tab margin anyway — on a narrow window that is tiny, so each title counted as two lines: every entry's
 *  click area was twice as tall as its row and the areas drifted a row further down per chapter (picking "Blocks"
 *  opened "Actions", "Items" opened "Blocks"). Found by the scripted smoke run (scripts/smoke/). */
public class GuideChapterTitleWrapTest {

    /** 6 px per character; wraps by splitting the text in half. */
    private static final IFontRenderer FONT = new IFontRenderer() {
        @Override public int getStringWidth(String text) { return text.length() * 6; }
        @Override public int getFontHeight(String text) { return 9; }
        @Override public int getMaxFontHeight() { return 9; }
        @Override public int drawString(String text, int x, int y, int colour, boolean shadow, boolean centered, float scale) { return 0; }
        @Override public List<String> wrapString(String text, int maxWidth, boolean shadow, float scale) {
            int half = text.length() / 2;
            return List.of(text.substring(0, half), text.substring(half));
        }
    };

    @Test
    public void theCentralOverlayNeverWraps() {
        // "Triggers" is 48 px wide; a 20 px side margin would wrap it, the overlay must not.
        assertEquals(List.of("Triggers"), GuideChapter.wrapTitle(FONT, "Triggers", 20, true));
    }

    @Test
    public void sideTabsWrapWhenTheTitleIsWiderThanTheMargin() {
        assertEquals(List.of("Trig", "gers"), GuideChapter.wrapTitle(FONT, "Triggers", 20, false));
    }

    @Test
    public void sideTabsKeepATitleThatFitsOnOneLine() {
        assertEquals(List.of("Triggers"), GuideChapter.wrapTitle(FONT, "Triggers", 48, false));
        assertEquals(List.of("Triggers"), GuideChapter.wrapTitle(FONT, "Triggers", Integer.MAX_VALUE, false));
    }
}
