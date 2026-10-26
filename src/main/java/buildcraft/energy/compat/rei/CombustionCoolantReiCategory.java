/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.rei;

import java.util.List;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.network.chat.Component;

import buildcraft.energy.BCEnergyItems;
import buildcraft.energy.compat.viewer.CombustionCoolant;
import buildcraft.lib.compat.rei.ReiCategory;
import buildcraft.lib.compat.rei.ReiDisplay;
import buildcraft.lib.compat.rei.ReiLayout;
import buildcraft.lib.compat.rei.ReiLayout.Role;
import buildcraft.lib.misc.LocaleUtil;

/**
 * REI category for the combustion engine's coolants, laid out as JEI's {@code CombustionCoolantCategory}: a fluid
 * coolant shows a bucket of it and its cooling rate, a solid one the item melting into its water.
 */
public class CombustionCoolantReiCategory extends ReiCategory<CombustionCoolant> {
    public static final CategoryIdentifier<ReiDisplay<CombustionCoolant>> ID =
            CategoryIdentifier.of("buildcraftunofficial", "combustion_engine_coolant");

    private static final int BUCKET = 1000;
    private static final int IN_X = 8, IN_Y = 4, TANK_W = 16, TANK_H = 40;
    private static final int OUT_X = 40, OUT_Y = 4;

    public CombustionCoolantReiCategory() {
        super(ID, "gui.jei.category.buildcraftunofficial.combustion_engine_coolant", BCEnergyItems.ENGINE_IRON.get(),
                176, 58);
    }

    @Override
    protected void layout(CombustionCoolant recipe, ReiLayout layout) {
        Component line;
        if (recipe.isSolid()) {
            layout.item(IN_X, IN_Y, Role.INPUT, List.of(recipe.item()));
            layout.fluid(OUT_X, OUT_Y, TANK_W, TANK_H, Role.OUTPUT, recipe.fluid());
            line = Component.translatable("gui.jei.category.buildcraftunofficial.combustion_engine_coolant.melts",
                    recipe.fluid().getAmount());
        } else {
            layout.fluid(IN_X, IN_Y, TANK_W, TANK_H, Role.INPUT, recipe.fluid(), BUCKET);
            line = Component.translatable("gui.jei.category.buildcraftunofficial.combustion_engine_coolant.cooling",
                    LocaleUtil.formatDouble(recipe.coolingPerMb(), 4));
        }
        layout.text(IN_X, 48, line);
    }
}
