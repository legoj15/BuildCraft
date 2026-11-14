/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.ledger;

import java.util.function.IntSupplier;

/** Screen space a ledger must keep clear of. A ledger is capped to the height of the screen and scrolls past it;
 * something else that draws on the screen edge (a recipe viewer's buttons, say) registers here how much of the
 * bottom a left-side ledger has to stay above, and the ledgers read it live. Right-side ledgers never reserve. */
public final class LedgerViewport {
    private static volatile IntSupplier leftBottomReserve = () -> 0;

    private LedgerViewport() {}

    /** Sets how many GUI pixels at the bottom of the screen a LEFT-side ledger must stay above. Asked every frame,
     * so it may change while a screen is open. {@code null} puts it back to none. */
    public static void setLeftBottomReserve(IntSupplier reserve) {
        leftBottomReserve = reserve == null ? () -> 0 : reserve;
    }

    /** @return GUI pixels at the bottom of the screen a LEFT-side ledger must stay above; 0 unless set, never negative. */
    public static int leftBottomReserve() {
        return Math.max(0, leftBottomReserve.getAsInt());
    }
}
