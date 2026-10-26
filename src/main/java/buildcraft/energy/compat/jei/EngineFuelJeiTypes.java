/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.jei;

//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}

import buildcraft.energy.compat.viewer.StirlingFuel;
import buildcraft.energy.compat.viewer.CombustionCoolant;
import buildcraft.api.fuels.IFuel;

/**
 * Holds the JEI recipe types for the energy module's engine fuel/coolant
 * recipe-holder categories. ({@code IRecipeType} on the JEI 20+ nodes, where
 * {@code RecipeType} is deprecated for removal; 1.21.1's JEI 19.x predates it.)
 *
 * <ul>
 * <li>{@link #COMBUSTION_FUEL} — one entry per registered {@link IFuel} (the combustion
 *     engine's liquid fuels); the category reads power/burn-time straight off the interface
 *     and an extra residue output for {@code IFuelManager.IDirtyFuel}.</li>
 * <li>{@link #COMBUSTION_COOLANT} — water + the three ices that cool the combustion engine.</li>
 * <li>{@link #STIRLING_FUEL} — every vanilla furnace fuel (the Stirling engine burns solid
 *     furnace fuel; {@code FuelValues} before 26.3, the {@code COOKING_FUEL} component after).</li>
 * </ul>
 */
public final class EngineFuelJeiTypes {
    //? if >=1.21.10 {
    public static final IRecipeType<IFuel> COMBUSTION_FUEL = IRecipeType.create(
            "buildcraftunofficial", "combustion_engine_fuel", IFuel.class);

    public static final IRecipeType<CombustionCoolant> COMBUSTION_COOLANT = IRecipeType.create(
            "buildcraftunofficial", "combustion_engine_coolant", CombustionCoolant.class);

    public static final IRecipeType<StirlingFuel> STIRLING_FUEL = IRecipeType.create(
            "buildcraftunofficial", "stirling_engine_fuel", StirlingFuel.class);
    //?} else {
    /*public static final RecipeType<IFuel> COMBUSTION_FUEL = RecipeType.create(
            "buildcraftunofficial", "combustion_engine_fuel", IFuel.class);

    public static final RecipeType<CombustionCoolant> COMBUSTION_COOLANT = RecipeType.create(
            "buildcraftunofficial", "combustion_engine_coolant", CombustionCoolant.class);

    public static final RecipeType<StirlingFuel> STIRLING_FUEL = RecipeType.create(
            "buildcraftunofficial", "stirling_engine_fuel", StirlingFuel.class);*/
    //?}

    private EngineFuelJeiTypes() {}
}
