/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.rei;

import java.util.List;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.network.chat.Component;

import buildcraft.api.mj.MjAPI;

import buildcraft.energy.BCEnergyItems;
import buildcraft.energy.compat.viewer.StirlingFuel;
import buildcraft.lib.compat.rei.ReiCategory;
import buildcraft.lib.compat.rei.ReiDisplay;
import buildcraft.lib.compat.rei.ReiLayout;
import buildcraft.lib.compat.rei.ReiLayout.Role;
import buildcraft.lib.misc.LocaleUtil;

/**
 * REI category for the Stirling engine's solid fuels, laid out as JEI's {@code StirlingFuelCategory}: the fuel, the
 * engine's constant output ceiling ("≤ 1 MJ/t", PID-regulated whatever the fuel) and the fuel's burn time.
 */
public class StirlingFuelReiCategory extends ReiCategory<StirlingFuel> {
    public static final CategoryIdentifier<ReiDisplay<StirlingFuel>> ID =
            CategoryIdentifier.of("buildcraftunofficial", "stirling_engine_fuel");

    private static final int IN_X = 8, IN_Y = 4;

    public StirlingFuelReiCategory() {
        super(ID, "gui.jei.category.buildcraftunofficial.stirling_engine_fuel", BCEnergyItems.ENGINE_STONE::get,
                176, 48);
    }

    @Override
    protected void layout(StirlingFuel recipe, ReiLayout layout) {
        layout.item(IN_X, IN_Y, Role.INPUT, List.of(recipe.fuel()));
        layout.text(30, IN_Y + 1, () -> Component.translatable(
                "gui.jei.category.buildcraftunofficial.stirling_engine_fuel.rate",
                LocaleUtil.localizeMjFlow(MjAPI.MJ)));
        layout.text(30, IN_Y + 11, Component.translatable(
                "gui.jei.category.buildcraftunofficial.stirling_engine_fuel.burn", recipe.burnTime()));
    }
}
