/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager.IDirtyFuel;

import buildcraft.energy.BCEnergyItems;
import buildcraft.lib.compat.rei.ReiCategory;
import buildcraft.lib.compat.rei.ReiDisplay;
import buildcraft.lib.compat.rei.ReiLayout;
import buildcraft.lib.compat.rei.ReiLayout.Role;
import buildcraft.lib.misc.LocaleUtil;

/**
 * REI category for the combustion engine's liquid fuels, laid out as JEI's {@code CombustionFuelCategory}: the fuel
 * (one bucket, the unit the burn time is keyed to), the residue a dirty fuel leaves, the power rate and the burn time,
 * and a "runs hot" note on searing fuels.
 */
public class CombustionFuelReiCategory extends ReiCategory<IFuel> {
    public static final CategoryIdentifier<ReiDisplay<IFuel>> ID =
            CategoryIdentifier.of("buildcraftunofficial", "combustion_engine_fuel");

    private static final int BUCKET = 1000;
    private static final int IN_X = 8, IN_Y = 4, TANK_W = 16, TANK_H = 40;
    private static final int RESIDUE_X = 32, RESIDUE_Y = 4;
    private static final int RUNS_HOT_X = 60;

    public CombustionFuelReiCategory() {
        super(ID, "gui.jei.category.buildcraftunofficial.combustion_engine_fuel", BCEnergyItems.ENGINE_IRON::get,
                176, 66);
    }

    @Override
    protected void layout(IFuel recipe, ReiLayout layout) {
        layout.fluid(IN_X, IN_Y, TANK_W, TANK_H, Role.INPUT, recipe.getFluid(), BUCKET);
        if (recipe instanceof IDirtyFuel dirty) {
            FluidStack residue = dirty.getResidue();
            layout.fluid(RESIDUE_X, RESIDUE_Y, TANK_W, TANK_H, Role.OUTPUT, residue);
        }
        // getPowerPerCycle() is already micro-MJ per tick, which localizeMjFlow expects.
        layout.text(IN_X, 48, () -> Component.literal(LocaleUtil.localizeMjFlow(recipe.getPowerPerCycle())));
        layout.text(IN_X, 58, Component.translatable(
                "gui.jei.category.buildcraftunofficial.combustion_engine_fuel.burn", recipe.getTotalBurningTime()));
        if (recipe.getHeatPerCycle() > recipe.getPowerPerCycle()) {
            layout.text(RUNS_HOT_X, IN_Y + 4,
                    Component.translatable("gui.jei.category.buildcraftunofficial.combustion_engine_fuel.runs_hot"));
        }
    }
}
