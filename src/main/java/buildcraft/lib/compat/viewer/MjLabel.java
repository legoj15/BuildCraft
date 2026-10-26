/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.viewer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import buildcraft.api.mj.MjAPI;
import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.misc.LocaleUtil;

/**
 * The plain-text MJ cost line the MJ-priced JEI and REI categories (Assembly Table, Distiller, Programming Table) draw under
 * their panel. Costs differ per recipe by orders of magnitude, so the number is a load-bearing recipe property.
 * Always full precision at one decimal — never abbreviated, and cast through double so a sub-MJ cost does not read
 * as "0 MJ" — with the unit following the player's full/short unit-name setting. The translation key is the
 * category's own {@code "%s %s"} (amount, unit) so a translator can reorder the two.
 */
public final class MjLabel {
    /** Dark grey, drawn without a shadow — the vanilla container-label colour. */
    public static final int COLOR = 0xFF404040;

    private MjLabel() {}

    /** The label's text: {@code translationKey} filled with the cost in MJ (one decimal) and the unit name. */
    public static Component text(String translationKey, long microJoules) {
        double mj = microJoules / (double) MjAPI.MJ;
        return Component.translatable(translationKey, LocaleUtil.formatDouble(mj, 1), LocaleUtil.mjUnit());
    }

    public static void draw(BCGraphics graphics, String translationKey, long microJoules, int x, int y) {
        String text = text(translationKey, microJoules).getString();
        graphics.text(Minecraft.getInstance().font, text, x, y, COLOR, false);
    }
}
