/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.gui;

import net.minecraft.client.gui.GuiGraphics;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.ledger.Ledger_Neptune;
import buildcraft.lib.misc.LocaleUtil;

import buildcraft.silicon.tile.TileLaserTableBase;

public class LedgerTablePower extends Ledger_Neptune {
    private static final int OVERLAY_COLOUR = 0xFF_D4_6C_1F;
    private static final int SUB_HEADER_COLOUR = 0xFF_AA_AF_b8;
    private static final int TEXT_COLOUR = 0xFF_00_00_00;

    public final TileLaserTableBase tile;

    public LedgerTablePower(BuildCraftGui gui, TileLaserTableBase tile, boolean expandPositive) {
        super(gui, OVERLAY_COLOUR, expandPositive);
        this.tile = tile;
        title = "gui.power";

        appendText(LocaleUtil.localize("gui.assemblyCurrentRequired") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> LocaleUtil.localizeMj(tile.getTarget()), TEXT_COLOUR);
        appendText(LocaleUtil.localize("gui.stored") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> LocaleUtil.localizeMj(tile.power), TEXT_COLOUR);
        appendText(LocaleUtil.localize("gui.assemblyRate") + ":", SUB_HEADER_COLOUR).setDropShadow(true);
        appendText(() -> LocaleUtil.localizeMjFlow(tile.avgPowerClient), TEXT_COLOUR);
        calculateMaxSize();
    }

    @Override
    protected void drawIcon(double x, double y, GuiGraphics graphics) {
        // TODO: draw engine active/inactive sprite when BCLibSprites is re-enabled
    }
}
