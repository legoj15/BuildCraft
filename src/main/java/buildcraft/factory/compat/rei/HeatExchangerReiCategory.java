/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.resources.Identifier;

import buildcraft.factory.BCFactoryItems;
import buildcraft.factory.compat.viewer.HeatExchangerRecipePair;
import buildcraft.lib.compat.rei.ReiCategory;
import buildcraft.lib.compat.rei.ReiDisplay;
import buildcraft.lib.compat.rei.ReiLayout;
import buildcraft.lib.compat.rei.ReiLayout.Role;

/**
 * REI category for the Heat Exchanger, laid out as JEI's {@code HeatExchangerCategory}: one display per valid
 * heatable + coolable pair, on the machine panel cropped from its GUI texture. The cold fluid enters mid-left and
 * leaves heated top-right; the hot fluid enters top-left and leaves cooled mid-right, as {@code TileHeatExchange}
 * routes them.
 */
public class HeatExchangerReiCategory extends ReiCategory<HeatExchangerRecipePair> {
    public static final CategoryIdentifier<ReiDisplay<HeatExchangerRecipePair>> ID =
            CategoryIdentifier.of("buildcraftunofficial", "heat_exchanger");

    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/heat_exchanger.png");

    // The JEI category's crop of the GUI texture, and the GUI's tank positions shifted by it.
    private static final int TEX_U = 3, TEX_V = 4;
    private static final int TEX_W = 170, TEX_H = 84;
    private static final int START_IN_X = 44 - TEX_U, START_IN_Y = 64 - TEX_V, START_IN_W = 34, START_IN_H = 17;
    private static final int START_OUT_X = 116 - TEX_U, START_OUT_Y = 43 - TEX_V, START_OUT_W = 16, START_OUT_H = 38;
    private static final int END_IN_X = 44 - TEX_U, END_IN_Y = 12 - TEX_V, END_IN_W = 16, END_IN_H = 38;
    private static final int END_OUT_X = 98 - TEX_U, END_OUT_Y = 12 - TEX_V, END_OUT_W = 34, END_OUT_H = 17;

    public HeatExchangerReiCategory() {
        super(ID, "gui.jei.category.buildcraftunofficial.heat_exchanger", BCFactoryItems.HEAT_EXCHANGE.get(), TEX_W,
                TEX_H);
    }

    @Override
    protected void layout(HeatExchangerRecipePair pair, ReiLayout layout) {
        layout.background(TEXTURE, TEX_U, TEX_V, TEX_W, TEX_H);
        layout.fluid(START_IN_X, START_IN_Y, START_IN_W, START_IN_H, Role.INPUT, pair.heatable().in());
        layout.fluid(END_IN_X, END_IN_Y, END_IN_W, END_IN_H, Role.INPUT, pair.coolable().in());
        // The outputs cross diagonally, as TileHeatExchange.craft() routes them.
        layout.fluid(END_OUT_X, END_OUT_Y, END_OUT_W, END_OUT_H, Role.OUTPUT, pair.heatable().out());
        layout.fluid(START_OUT_X, START_OUT_Y, START_OUT_W, START_OUT_H, Role.OUTPUT, pair.coolable().out());
    }
}
