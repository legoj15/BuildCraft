/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.resources.Identifier;

import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.factory.BCFactoryItems;
import buildcraft.lib.compat.rei.ReiCategory;
import buildcraft.lib.compat.rei.ReiDisplay;
import buildcraft.lib.compat.rei.ReiLayout;
import buildcraft.lib.compat.rei.ReiLayout.Role;
import buildcraft.lib.compat.viewer.MjLabel;

/**
 * REI category for the Distiller, laid out as JEI's {@code DistillerCategory}: the machine panel cropped from its GUI
 * texture, the input fluid on the left, the gas top-right and the liquid bottom-right, and the per-cycle MJ cost under
 * the panel.
 */
public class DistillerReiCategory extends ReiCategory<IDistillationRecipe> {
    public static final CategoryIdentifier<ReiDisplay<IDistillationRecipe>> ID =
            CategoryIdentifier.of("buildcraftunofficial", "distiller");

    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/distiller.png");

    // The JEI category's crop of the GUI texture, and the GUI's tank positions shifted by it.
    private static final int TEX_U = 3, TEX_V = 4;
    private static final int TEX_W = 170, TEX_H = 74;
    private static final int TANK_IN_X = 44 - TEX_U, TANK_IN_Y = 23 - TEX_V, TANK_IN_W = 16, TANK_IN_H = 38;
    private static final int TANK_GAS_X = 98 - TEX_U, TANK_GAS_Y = 10 - TEX_V, TANK_GAS_W = 34, TANK_GAS_H = 17;
    private static final int TANK_LIQ_X = 98 - TEX_U, TANK_LIQ_Y = 54 - TEX_V, TANK_LIQ_W = 34, TANK_LIQ_H = 17;
    private static final int POWER_X = 4, POWER_Y = TEX_H + 2;

    public DistillerReiCategory() {
        super(ID, "gui.jei.category.buildcraftunofficial.distiller", BCFactoryItems.DISTILLER::get, TEX_W,
                TEX_H + 12);
    }

    @Override
    protected void layout(IDistillationRecipe recipe, ReiLayout layout) {
        layout.background(TEXTURE, TEX_U, TEX_V, TEX_W, TEX_H);
        layout.fluid(TANK_IN_X, TANK_IN_Y, TANK_IN_W, TANK_IN_H, Role.INPUT, recipe.in());
        layout.fluid(TANK_GAS_X, TANK_GAS_Y, TANK_GAS_W, TANK_GAS_H, Role.OUTPUT, recipe.outGas());
        layout.fluid(TANK_LIQ_X, TANK_LIQ_Y, TANK_LIQ_W, TANK_LIQ_H, Role.OUTPUT, recipe.outLiquid());
        layout.text(POWER_X, POWER_Y, () -> MjLabel.text("gui.jei.category.buildcraftunofficial.distiller.power",
                recipe.powerRequired()));
    }
}
