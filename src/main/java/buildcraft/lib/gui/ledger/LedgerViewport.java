/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.ledger;

import java.util.function.IntUnaryOperator;

/** Screen space a ledger must keep clear of. A ledger is capped to the height of the screen and scrolls past it;
 * something else that draws on the screen edge (a recipe viewer's buttons, say) registers here how much of the
 * bottom a left-side ledger has to stay above, and the ledgers read it live. Right-side ledgers never reserve. */
public final class LedgerViewport {
    private static volatile IntUnaryOperator leftBottomReserve = ledgerLeftX -> 0;

    private LedgerViewport() {}

    /** Sets how many GUI pixels at the bottom of the screen a LEFT-side ledger must stay above. The operator gets the
     * left edge the ledger has at its full width (GUI pixels), because the strip a viewer keeps clear sits at the left
     * of the screen and only a ledger that reaches it needs to stay above it. Asked every frame, so it may change while
     * a screen is open. {@code null} puts it back to none. */
    public static void setLeftBottomReserve(IntUnaryOperator reserve) {
        leftBottomReserve = reserve == null ? ledgerLeftX -> 0 : reserve;
    }

    /** @param ledgerLeftX the ledger's left edge at its full width, in GUI pixels
     * @return GUI pixels at the bottom of the screen a LEFT-side ledger with that edge must stay above; 0 unless set,
     * never negative. */
    public static int leftBottomReserve(int ledgerLeftX) {
        return Math.max(0, leftBottomReserve.applyAsInt(ledgerLeftX));
    }
}
