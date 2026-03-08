/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.misc.LocaleUtil;

/**
 * Ledger that displays engine power stats: current output, stored power, and heat.
 * Uses supplier functions so data can come from synced ContainerData (client)
 * or directly from the tile entity (server/single-player).
 */
public class LedgerEngine extends Ledger_Neptune {
    private static final int HEADER_COLOUR = 0xE1C92F;
    private static final int SUB_HEADER_COLOUR = 0xAAAF78;
    private static final int TEXT_COLOUR = 0xFFFFFF;

    public LedgerEngine(BuildCraftGui gui, LongSupplier currentOutput, LongSupplier storedPower,
                        Supplier<Float> heatLevel, boolean expandPositive) {
        super(gui, HEADER_COLOUR, expandPositive);
        this.title = LocaleUtil.localize("gui.power");

        appendText(LocaleUtil.localize("gui.currentOutput") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> LocaleUtil.localizeMjFlow(currentOutput.getAsLong()), TEXT_COLOUR);
        appendText(LocaleUtil.localize("gui.stored") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> LocaleUtil.localizeMj(storedPower.getAsLong()), TEXT_COLOUR);
        appendText(LocaleUtil.localize("gui.heat") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> String.format("%.1f \u00B0C", heatLevel.get()), TEXT_COLOUR);

        calculateMaxSize();
    }
}
