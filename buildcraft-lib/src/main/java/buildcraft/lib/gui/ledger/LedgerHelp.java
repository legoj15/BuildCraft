/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.misc.LocaleUtil;

/** Ledger that shows contextual help text for the GUI. */
public class LedgerHelp extends Ledger_Neptune {
    private static final int COLOUR = 0x4189C5;

    public LedgerHelp(BuildCraftGui gui, boolean expandPositive, String... helpKeys) {
        super(gui, COLOUR, expandPositive);
        this.title = LocaleUtil.localize("gui.help");

        for (String key : helpKeys) {
            appendText(LocaleUtil.localize(key), 0xFFFFFF);
        }

        calculateMaxSize();
    }
}
