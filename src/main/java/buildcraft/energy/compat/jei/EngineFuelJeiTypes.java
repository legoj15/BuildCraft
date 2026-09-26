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
 *     fuel via {@code FuelValues}).</li>
 * </ul>
 */
public final class EngineFuelJeiTypes {
    //? if >=1.21.10 {
    public static final IRecipeType<IFuel> COMBUSTION_FUEL = IRecipeType.create(
            "buildcraftunofficial", "combustion_engine_fuel", IFuel.class);

    public static final IRecipeType<CombustionCoolantJei> COMBUSTION_COOLANT = IRecipeType.create(
            "buildcraftunofficial", "combustion_engine_coolant", CombustionCoolantJei.class);

    public static final IRecipeType<StirlingFuelJei> STIRLING_FUEL = IRecipeType.create(
            "buildcraftunofficial", "stirling_engine_fuel", StirlingFuelJei.class);
    //?} else {
    /*public static final RecipeType<IFuel> COMBUSTION_FUEL = RecipeType.create(
            "buildcraftunofficial", "combustion_engine_fuel", IFuel.class);

    public static final RecipeType<CombustionCoolantJei> COMBUSTION_COOLANT = RecipeType.create(
            "buildcraftunofficial", "combustion_engine_coolant", CombustionCoolantJei.class);

    public static final RecipeType<StirlingFuelJei> STIRLING_FUEL = RecipeType.create(
            "buildcraftunofficial", "stirling_engine_fuel", StirlingFuelJei.class);*/
    //?}

    private EngineFuelJeiTypes() {}
}
