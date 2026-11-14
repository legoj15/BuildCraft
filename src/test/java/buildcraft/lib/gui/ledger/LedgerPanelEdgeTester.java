/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.ledger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Covers {@link Ledger_Neptune#panelX} and {@link Ledger_Neptune#overlapsWindow}, the two pure functions behind an open
 *  ledger that is wider than the gap beside its window (#199). The panel must never leave the screen, must sit exactly
 *  where the window anchors it while it fits the gap (so every screen that always worked draws as before), and the flag
 *  that routes it to the late draw pass and the mouse must be set exactly when the position had to move. */
public class LedgerPanelEdgeTester {

    private static final double EPS = 1e-9;

    /** A panel that fits the gap beside the window is where the anchor puts it: the pre-#199 geometry. */
    @Test
    public void aPanelThatFitsTheGapSitsAtItsAnchor() {
        // 854x480 at GUI scale 2: a 427 px screen, a 176 px window at 125..301.
        Assertions.assertEquals(25, Ledger_Neptune.panelX(false, 125, 100, 427), EPS, "left: right edge on the window");
        Assertions.assertFalse(Ledger_Neptune.overlapsWindow(false, 125, 100, 427));
        Assertions.assertEquals(301, Ledger_Neptune.panelX(true, 301, 100, 427), EPS, "right: left edge on the window");
        Assertions.assertFalse(Ledger_Neptune.overlapsWindow(true, 301, 100, 427));
    }

    /** The gap a 176 px window leaves on vanilla's smallest GUI screen is exactly the minimum open width: ledgers have
     *  always fit there and still must not be moved over the window. */
    @Test
    public void theGapOfAStandardWindowOnTheSmallestScreenIsEnough() {
        int screen = 320, window = 176;
        int gap = (screen - window) / 2;
        Assertions.assertEquals(Ledger_Neptune.MIN_OPEN_WIDTH, gap, "MIN_OPEN_WIDTH is that gap");
        Assertions.assertFalse(Ledger_Neptune.overlapsWindow(false, gap, Ledger_Neptune.MIN_OPEN_WIDTH, screen));
        Assertions.assertFalse(
            Ledger_Neptune.overlapsWindow(true, screen - gap, Ledger_Neptune.MIN_OPEN_WIDTH, screen));
    }

    /** A 256 px window on a 320 px screen leaves 32 px: a 72 px panel is pulled over the window, its outer edge on the
     *  screen's edge. */
    @Test
    public void aPanelWiderThanTheGapIsPulledOverTheWindow() {
        double leftX = Ledger_Neptune.panelX(false, 32, 72, 320);
        Assertions.assertEquals(0, leftX, EPS, "left: pinned to the screen's left edge");
        Assertions.assertTrue(Ledger_Neptune.overlapsWindow(false, 32, 72, 320));
        Assertions.assertEquals(40, leftX + 72 - 32, EPS, "it covers 40 px of the window");

        double rightX = Ledger_Neptune.panelX(true, 288, 72, 320);
        Assertions.assertEquals(248, rightX, EPS, "right: pinned to the screen's right edge");
        Assertions.assertEquals(320, rightX + 72, EPS);
        Assertions.assertTrue(Ledger_Neptune.overlapsWindow(true, 288, 72, 320));
    }

    /** A closed tab (22 px) in a gap narrower than itself is kept on the screen over the window's edge. */
    @Test
    public void aClosedTabInAGapNarrowerThanItselfStaysOnScreen() {
        Assertions.assertEquals(0, Ledger_Neptune.panelX(false, 10, Ledger_Neptune.CLOSED_WIDTH, 300), EPS);
        Assertions.assertTrue(Ledger_Neptune.overlapsWindow(false, 10, Ledger_Neptune.CLOSED_WIDTH, 300));
        Assertions.assertEquals(278, Ledger_Neptune.panelX(true, 290, Ledger_Neptune.CLOSED_WIDTH, 300), EPS);
        Assertions.assertTrue(Ledger_Neptune.overlapsWindow(true, 290, Ledger_Neptune.CLOSED_WIDTH, 300));
    }

    /** Over every anchor, width and screen: a panel narrower than the screen stays on it; it sits at its anchor
     *  exactly when it does not overlap; and the flag is set exactly when the panel reaches past the anchor into the
     *  window. */
    @Test
    public void thePanelStaysOnScreenAndTheFlagFollowsThePosition() {
        for (int screen = 200; screen <= 500; screen += 37) {
            for (int anchor = 0; anchor <= screen; anchor += 3) {
                for (double width = Ledger_Neptune.CLOSED_WIDTH; width <= screen; width += 4.5) {
                    for (boolean right : new boolean[] {false, true}) {
                        String at = "right=" + right + " screen=" + screen + " anchor=" + anchor + " width=" + width;
                        double x = Ledger_Neptune.panelX(right, anchor, width, screen);
                        boolean over = Ledger_Neptune.overlapsWindow(right, anchor, width, screen);
                        Assertions.assertTrue(x >= -EPS && x + width <= screen + EPS, "on screen: " + at);
                        // The window-side edge: the right edge of a left panel, the left edge of a right one.
                        boolean reachesPastAnchor = right ? x < anchor - EPS : x + width > anchor + EPS;
                        Assertions.assertEquals(reachesPastAnchor, over, "flag matches the position: " + at);
                        if (!over) {
                            double expected = right ? anchor : anchor - width;
                            Assertions.assertEquals(expected, x, EPS, "fits: at the anchor: " + at);
                        }
                    }
                }
            }
        }
    }
}
