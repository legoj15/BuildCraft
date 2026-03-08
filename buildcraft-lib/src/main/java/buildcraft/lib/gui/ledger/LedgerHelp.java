/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.misc.LocaleUtil;

/** Ledger that shows contextual help text for the GUI.
 *  Positioned on the LEFT side, matching 1.12.2 behavior. */
public class LedgerHelp extends Ledger_Neptune {
    private static final int COLOUR = 0xCC99FF;

    public LedgerHelp(BuildCraftGui gui, String... helpKeys) {
        // expandPositive=false → opens to the LEFT side
        super(gui, COLOUR, false);
        this.title = "gui.help";

        for (String key : helpKeys) {
            appendText(LocaleUtil.localize(key), 0xFFFFFF);
        }

        calculateMaxSize();
    }

    @Override
    protected void drawIcon(double x, double y, GuiGraphics graphics) {
        // Draw a purple square with a "?" character
        graphics.fill((int) x, (int) y, (int) x + 16, (int) y + 16, 0xFF000000 | COLOUR);
        Font font = Minecraft.getInstance().font;
        int textX = (int) x + (16 - font.width("?")) / 2;
        int textY = (int) y + (16 - font.lineHeight) / 2;
        graphics.drawString(font, "?", textX, textY, 0xFFFFFFFF, true);
    }
}
