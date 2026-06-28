/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.ledger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Covers {@link Ledger_Neptune#computeUpShift}, the pure function behind the ledger "auto-fit"
 *  behaviour. This is the half of the collapse-fly-up fix that is testable without a running
 *  client: the bug was that the shift was derived once from the fully-open height and reused while
 *  the panel collapsed, so a 24px stub kept the big shift and slid off the top. Deriving the shift
 *  from the CURRENT drawn height makes that impossible — encoded directly by
 *  {@link #collapsedStubIsNeverShifted()} below. */
public class LedgerUpShiftTester {

    private static final double EPS = 1e-9;

    /** A ledger that fits below its anchor needs no shift. */
    @Test
    public void noShiftWhenItFits() {
        Assertions.assertEquals(0, Ledger_Neptune.computeUpShift(40, 100, 200), EPS,
            "content that ends above the screen bottom must not move");
        // Exactly touching the bottom edge is still "fits".
        Assertions.assertEquals(0, Ledger_Neptune.computeUpShift(40, 160, 200), EPS,
            "bottom exactly at the screen edge is not an overflow");
    }

    /** Moderate overflow: shift equals the overflow, and the result keeps the whole ledger on
     *  screen (top >= 0, bottom == screen edge). */
    @Test
    public void moderateOverflowShiftsJustEnough() {
        double anchorY = 40, screenHeight = 200, height = 180; // ends 20px past the bottom
        double shift = Ledger_Neptune.computeUpShift(anchorY, height, (int) screenHeight);
        Assertions.assertEquals(20, shift, EPS, "shift should equal the overflow amount");
        double top = anchorY - shift;
        Assertions.assertTrue(top >= 0, "top must not leave the top of the screen");
        Assertions.assertEquals(screenHeight, top + height, EPS, "bottom should land exactly on the screen edge");
    }

    /** Content taller than the whole screen: the top is clamped to the screen top (shift == anchorY)
     *  and the remainder overflows off the BOTTOM — the behaviour the user accepts. */
    @Test
    public void tallerThanScreenClampsTopToScreen() {
        double anchorY = 40, height = 400;
        double shift = Ledger_Neptune.computeUpShift(anchorY, height, 200);
        Assertions.assertEquals(anchorY, shift, EPS, "shift is clamped so the top pins to y=0");
        Assertions.assertEquals(0, anchorY - shift, EPS, "top sits exactly at the screen top, never above");
    }

    /** The core regression guard. No matter how large the OPEN overflow was, once the panel has
     *  collapsed to its closed stub the shift is 0 — so the background can never fly off the top
     *  leaving the icon behind. */
    @Test
    public void collapsedStubIsNeverShifted() {
        // Same anchor/screen that produced a large shift while open...
        double anchorY = 40;
        int screenHeight = 200;
        Assertions.assertTrue(Ledger_Neptune.computeUpShift(anchorY, 400, screenHeight) > 0,
            "sanity: this configuration DID shift while open");
        // ...must produce zero shift at the closed height.
        Assertions.assertEquals(0,
            Ledger_Neptune.computeUpShift(anchorY, Ledger_Neptune.CLOSED_HEIGHT, screenHeight), EPS,
            "a collapsed ledger must sit at its anchor, not retain the open-state shift");
    }

    /** As the panel animates closed, the shift must decrease monotonically to 0 — the property that
     *  makes the collapse a smooth glide back to the anchor rather than a jump. */
    @Test
    public void shiftRelaxesMonotonicallyAsItCollapses() {
        double anchorY = 40;
        int screenHeight = 200;
        double prev = Ledger_Neptune.computeUpShift(anchorY, 400, screenHeight);
        for (double h = 380; h >= Ledger_Neptune.CLOSED_HEIGHT; h -= 20) {
            double shift = Ledger_Neptune.computeUpShift(anchorY, h, screenHeight);
            Assertions.assertTrue(shift <= prev + EPS,
                "shift must not increase as the ledger shrinks (h=" + h + ", shift=" + shift + ", prev=" + prev + ")");
            Assertions.assertTrue(shift >= 0, "shift is never negative");
            prev = shift;
        }
        Assertions.assertEquals(0, prev, EPS, "fully collapsed → no shift");
    }
}
